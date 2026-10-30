#!/usr/bin/env python3
"""Regenerate specification review context and verify checkpoint handoffs."""

from __future__ import annotations

import argparse
from datetime import datetime
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import Any


CONTEXT_SCHEMA_VERSION = 5
CHECKPOINT_SCHEMA_VERSION = 1
HANDOFF_SCHEMA_VERSION = 1
ROLES = {"initializer", "updater", "checker", "human", "none"}
SHA256_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40,64}$")
REVIEWER_RE = re.compile(r"^(?:human|model):[A-Za-z0-9][A-Za-z0-9._/@:-]*$")
CANONICAL_ARTIFACT_KEYS = {
    "config",
    "requirements",
    "matrix",
    "approval_policy",
    "requirement_approvals",
    "capability_map",
    "evidence_receipts",
    "semantic_review",
}
TRUSTED_CHECKER_RELATIVE = Path("tools/specification/spec_check.py")
TRUSTED_VERIFIER_RELATIVE = Path("tools/specification/review_handoff.py")


class HandoffError(ValueError):
    """A checkpoint or handoff cannot be verified safely."""


def _read_object(path: Path, label: str) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise HandoffError(f"{label} must be a regular non-symlink file: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise HandoffError(f"{label} is not valid UTF-8 JSON: {path}") from error
    if not isinstance(value, dict):
        raise HandoffError(f"{label} root must be an object: {path}")
    return value


def _project_path(root: Path, value: str, label: str) -> Path:
    if not isinstance(value, str) or not value.strip():
        raise HandoffError(f"{label} must be a non-empty project-relative path")
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts:
        raise HandoffError(f"{label} must stay inside the project: {value}")
    candidate = root / relative
    try:
        candidate.resolve().relative_to(root)
    except ValueError as error:
        raise HandoffError(f"{label} escapes the project: {value}") from error
    return candidate


def _timestamp(value: Any) -> bool:
    if not isinstance(value, str) or not value:
        return False
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return False
    return parsed.tzinfo is not None


def _require_ignored(root: Path, path: Path, label: str) -> None:
    relative = path.relative_to(root).as_posix()
    result = subprocess.run(
        ["git", "check-ignore", "-q", "--", relative],
        cwd=root,
        check=False,
        capture_output=True,
    )
    if result.returncode != 0:
        raise HandoffError(
            f"{label} must be Git-ignored; the checker also requires local classification: {relative}"
        )


def _unresolved_checkpoint_findings(
    root: Path,
    findings: list[Any],
) -> list[dict[str, Any]]:
    unresolved: list[dict[str, Any]] = []
    finding_ids: set[str] = set()
    for finding in findings:
        if not isinstance(finding, dict):
            raise HandoffError("checkpoint findings must be structured objects")
        finding_id = finding.get("id")
        classification = finding.get("classification")
        status = finding.get("status")
        path = finding.get("path")
        requirement_id = finding.get("requirement_id")
        summary = finding.get("summary")
        if (
            not isinstance(finding_id, str)
            or not finding_id.strip()
            or finding_id in finding_ids
            or classification not in {"normative", "non-normative"}
            or status not in {"open", "resolved", "blocked"}
            or not isinstance(path, str)
            or not path.strip()
            or not isinstance(summary, str)
            or not summary.strip()
            or (
                classification == "normative"
                and (not isinstance(requirement_id, str) or not requirement_id.strip())
            )
            or (
                classification == "non-normative"
                and requirement_id is not None
                and (not isinstance(requirement_id, str) or not requirement_id.strip())
            )
        ):
            raise HandoffError("checkpoint finding is incomplete or duplicated")
        _project_path(root, path, "checkpoint finding path")
        finding_ids.add(finding_id)
        if status != "resolved":
            unresolved.append(finding)
    return unresolved


def _validate_checkpoint(
    root: Path,
    checkpoint: dict[str, Any],
    context_relative: str,
) -> None:
    required_lists = ("reviewed_paths", "reviewed_pairs", "findings", "limitations")
    if (
        checkpoint.get("schema_version") != CHECKPOINT_SCHEMA_VERSION
        or checkpoint.get("kind") != "review-checkpoint"
        or checkpoint.get("state") not in {"in-progress", "ready", "completed"}
        or checkpoint.get("workflow_role") not in ROLES - {"human", "none"}
        or checkpoint.get("mode") not in {"changed", "full"}
        or checkpoint.get("handoff_to") not in ROLES
        or checkpoint.get("context_file") != context_relative
        or not isinstance(checkpoint.get("scope_digest"), str)
        or SHA256_RE.fullmatch(checkpoint["scope_digest"]) is None
        or not isinstance(checkpoint.get("context_command"), str)
        or not checkpoint["context_command"].strip()
        or not isinstance(checkpoint.get("next_action"), str)
        or not checkpoint["next_action"].strip()
        or not isinstance(checkpoint.get("reviewer"), str)
        or REVIEWER_RE.fullmatch(checkpoint["reviewer"]) is None
        or not _timestamp(checkpoint.get("created_at"))
        or not _timestamp(checkpoint.get("updated_at"))
        or any(not isinstance(checkpoint.get(key), list) for key in required_lists)
        or not isinstance(checkpoint.get("trusted_base_controls"), bool)
    ):
        raise HandoffError("checkpoint is incomplete or has an unsupported schema")
    base = checkpoint.get("base_commit")
    if checkpoint["mode"] == "changed" and (
        not isinstance(base, str) or COMMIT_RE.fullmatch(base) is None
    ):
        raise HandoffError("changed checkpoint requires base_commit")
    if checkpoint["mode"] == "full" and base is not None:
        raise HandoffError("full checkpoint base_commit must be null")
    if checkpoint["mode"] == "full" and checkpoint["trusted_base_controls"]:
        raise HandoffError("trusted_base_controls is valid only for changed mode")
    resumed_at = checkpoint.get("resumed_at")
    if resumed_at is not None and not _timestamp(resumed_at):
        raise HandoffError("checkpoint resumed_at must be null or timezone-aware")
    _unresolved_checkpoint_findings(root, checkpoint["findings"])


def _context_coverage(
    context: dict[str, Any],
    checkpoint: dict[str, Any],
    label: str,
) -> tuple[set[str], set[tuple[str, str]]]:
    if (
        context.get("schema_version") != CONTEXT_SCHEMA_VERSION
        or context.get("mode") != checkpoint["mode"]
        or context.get("base_commit") != checkpoint.get("base_commit")
        or not isinstance(context.get("scope_digest"), str)
        or SHA256_RE.fullmatch(context["scope_digest"]) is None
    ):
        raise HandoffError(f"{label} has an unsupported schema or checkpoint binding")
    paths = context.get("reviewed_paths")
    if (
        not isinstance(paths, list)
        or not all(isinstance(path, str) for path in paths)
        or len(paths) != len(set(paths))
    ):
        raise HandoffError(f"{label} has invalid reviewed_paths")
    pairs: set[tuple[str, str]] = set()
    requirement_ids = context.get("requirement_ids")
    if (
        not isinstance(requirement_ids, list)
        or not all(isinstance(item, str) and item for item in requirement_ids)
        or len(requirement_ids) != len(set(requirement_ids))
    ):
        raise HandoffError(f"{label} has invalid requirement_ids")
    requirement_id_set = set(requirement_ids)

    supporting_digests = context.get("supporting_digests")
    coverage_kinds = context.get("coverage_kinds")
    if (
        not isinstance(coverage_kinds, dict)
        or set(coverage_kinds) != requirement_id_set
        or any(
            not isinstance(value, str)
            or value not in {"implementation_required", "documentation", "process"}
            for value in coverage_kinds.values()
        )
    ):
        raise HandoffError(f"{label} has invalid coverage_kinds")

    for key in (
        "implementation_traceability",
        "supporting_traceability",
        "requirement_traceability",
    ):
        records = context.get(key)
        if not isinstance(records, list):
            raise HandoffError(f"{label} is missing {key}")
        for record in records:
            if (
                not isinstance(record, dict)
                or not isinstance(record.get("path"), str)
                or not isinstance(record.get("requirement_ids"), list)
                or not all(isinstance(item, str) for item in record["requirement_ids"])
                or not set(record["requirement_ids"]).issubset(requirement_id_set)
                or len(record["requirement_ids"]) != len(set(record["requirement_ids"]))
            ):
                raise HandoffError(f"{label} has invalid {key}")
            pairs.update((record["path"], item) for item in record["requirement_ids"])
    if (
        not isinstance(supporting_digests, dict)
        or set(supporting_digests) != requirement_id_set
        or any(
            not isinstance(value, str) or SHA256_RE.fullmatch(value) is None
            for value in supporting_digests.values()
        )
    ):
        raise HandoffError(f"{label} has invalid supporting_digests")
    return set(paths), pairs


def _validate_checkpoint_coverage(
    checkpoint: dict[str, Any],
    context_paths: set[str],
    context_pairs: set[tuple[str, str]],
) -> None:
    reviewed_paths = checkpoint["reviewed_paths"]
    if (
        not all(isinstance(path, str) for path in reviewed_paths)
        or len(reviewed_paths) != len(set(reviewed_paths))
        or not set(reviewed_paths).issubset(context_paths)
    ):
        raise HandoffError("checkpoint reviewed_paths are duplicate or outside current scope")
    reviewed_pairs: list[tuple[str, str]] = []
    for record in checkpoint["reviewed_pairs"]:
        if (
            not isinstance(record, dict)
            or not isinstance(record.get("path"), str)
            or not isinstance(record.get("requirement_id"), str)
        ):
            raise HandoffError("checkpoint reviewed_pairs are malformed")
        reviewed_pairs.append((record["path"], record["requirement_id"]))
    if (
        len(reviewed_pairs) != len(set(reviewed_pairs))
        or not set(reviewed_pairs).issubset(context_pairs)
    ):
        raise HandoffError("checkpoint reviewed_pairs are duplicate or outside current scope")
    if checkpoint["state"] == "completed":
        unresolved = [
            item
            for item in checkpoint["findings"]
            if not isinstance(item, dict) or item.get("status") != "resolved"
        ]
        if (
            set(reviewed_paths) != context_paths
            or set(reviewed_pairs) != context_pairs
            or checkpoint["limitations"]
            or unresolved
        ):
            raise HandoffError("completed checkpoint does not cover the full current scope")


def _validate_handoff(
    root: Path,
    handoff: dict[str, Any],
    checkpoint_relative: str,
    context_relative: str,
    checkpoint: dict[str, Any],
) -> None:
    if (
        handoff.get("schema_version") != HANDOFF_SCHEMA_VERSION
        or handoff.get("kind") != "specification-assurance-handoff"
        or handoff.get("state") != "ready"
        or checkpoint.get("state") not in {"ready", "completed"}
        or handoff.get("from_role") != checkpoint["workflow_role"]
        or handoff.get("to_role") != checkpoint["handoff_to"]
        or handoff.get("mode") != checkpoint["mode"]
        or handoff.get("base_commit") != checkpoint.get("base_commit")
        or handoff.get("scope_digest") != checkpoint["scope_digest"]
        or handoff.get("context_file") != context_relative
        or handoff.get("checkpoint_file") != checkpoint_relative
        or not isinstance(handoff.get("repository_ref"), str)
        or not handoff["repository_ref"].strip()
        or not isinstance(handoff.get("next_action"), str)
        or not handoff["next_action"].strip()
        or not isinstance(handoff.get("created_by"), str)
        or REVIEWER_RE.fullmatch(handoff["created_by"]) is None
        or not _timestamp(handoff.get("created_at"))
        or not isinstance(handoff.get("unresolved_findings"), list)
    ):
        raise HandoffError("handoff is incomplete, stale or has an unsupported schema")

    unresolved = _unresolved_checkpoint_findings(root, checkpoint["findings"])
    if handoff["unresolved_findings"] != unresolved:
        raise HandoffError("handoff unresolved findings do not match the checkpoint")

    artifacts = handoff.get("canonical_artifacts")
    if (
        not isinstance(artifacts, dict)
        or set(artifacts) != CANONICAL_ARTIFACT_KEYS
        or not isinstance(artifacts.get("requirements"), list)
        or not artifacts["requirements"]
        or not all(isinstance(item, str) for item in artifacts["requirements"])
        or len(artifacts["requirements"]) != len(set(artifacts["requirements"]))
        or any(
            not isinstance(artifacts.get(key), str)
            for key in CANONICAL_ARTIFACT_KEYS - {"requirements"}
        )
    ):
        raise HandoffError("handoff canonical_artifacts must identify every required artifact role")
    artifact_paths = [
        artifacts[key] for key in sorted(CANONICAL_ARTIFACT_KEYS - {"requirements"})
    ] + artifacts["requirements"]
    if len(artifact_paths) != len(set(artifact_paths)):
        raise HandoffError("handoff canonical artifact paths must be unique")
    for value in artifact_paths:
        artifact = _project_path(root, value, "canonical artifact")
        if artifact.is_symlink() or not artifact.is_file():
            raise HandoffError(f"canonical artifact is missing or a symlink: {value}")

    validation = handoff.get("validation")
    if (
        not isinstance(validation, dict)
        or not isinstance(validation.get("command"), str)
        or not validation["command"].strip()
        or not isinstance(validation.get("exit_code"), int)
        or isinstance(validation.get("exit_code"), bool)
        or validation.get("result") not in {"passed", "failed", "blocked"}
        or not _timestamp(validation.get("ran_at"))
        or (validation["exit_code"] == 0) != (validation["result"] == "passed")
    ):
        raise HandoffError("handoff validation command/result is incomplete or inconsistent")


def _checker_command(
    root: Path,
    checker: Path,
    checkpoint: dict[str, Any],
) -> list[str]:
    command = [sys.executable, "-I", str(checker), "--root", str(root)]
    if checkpoint["mode"] == "full":
        command.append("--full")
    else:
        command.extend(["--changed-only", "--base", checkpoint["base_commit"]])
        if checkpoint["trusted_base_controls"]:
            command.append("--trusted-base-controls")
    command.append("--review-context")
    return command


def _git_output(
    cwd: Path,
    arguments: list[str],
    label: str,
    timeout_seconds: int,
) -> str:
    try:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=cwd,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_seconds,
        )
    except subprocess.TimeoutExpired as error:
        raise HandoffError(f"{label} timed out") from error
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise HandoffError(f"{label} failed: {detail}")
    return completed.stdout.strip()


def _validate_trusted_tools(
    candidate_root: Path,
    checker: Path,
    base_commit: str,
    timeout_seconds: int,
) -> None:
    trusted_root_text = _git_output(
        checker.parent,
        ["rev-parse", "--show-toplevel"],
        "checker must come from a trusted Git checkout",
        timeout_seconds,
    )
    trusted_root = Path(trusted_root_text).resolve()
    for child, parent in (
        (trusted_root, candidate_root),
        (candidate_root, trusted_root),
    ):
        try:
            child.relative_to(parent)
        except ValueError:
            continue
        raise HandoffError("trusted and candidate Git checkouts must be separate")

    head = _git_output(
        trusted_root,
        ["rev-parse", "HEAD^{commit}"],
        "trusted checker HEAD lookup",
        timeout_seconds,
    )
    if head != base_commit:
        raise HandoffError("trusted checker checkout HEAD does not match checkpoint base_commit")

    trusted_tools = (
        (checker, TRUSTED_CHECKER_RELATIVE, "trusted checker"),
        (Path(__file__), TRUSTED_VERIFIER_RELATIVE, "trusted verifier"),
    )
    for tool_input, expected_relative, label in trusted_tools:
        if tool_input.is_symlink():
            raise HandoffError(f"{label} must not be a symbolic link")
        tool = tool_input.resolve()
        try:
            tool_relative = tool.relative_to(trusted_root)
        except ValueError as error:
            raise HandoffError(f"{label} is outside the trusted Git checkout") from error
        if tool_relative != expected_relative:
            raise HandoffError(f"{label} must be {expected_relative.as_posix()}")
        expected_blob = _git_output(
            trusted_root,
            ["rev-parse", f"{base_commit}:{expected_relative.as_posix()}"],
            f"{label} base blob lookup",
            timeout_seconds,
        )
        observed_blob = _git_output(
            trusted_root,
            ["hash-object", "--no-filters", "--", expected_relative.as_posix()],
            f"{label} content hash",
            timeout_seconds,
        )
        if observed_blob != expected_blob:
            raise HandoffError(f"{label} content does not match checkpoint base_commit")


def verify(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve()
    timeout_seconds = getattr(args, "timeout_seconds", 300)
    if not isinstance(timeout_seconds, int) or isinstance(timeout_seconds, bool) or timeout_seconds <= 0:
        raise HandoffError("timeout_seconds must be a positive integer")
    if not (root / ".git").exists():
        raise HandoffError(f"project root is not a Git repository: {root}")
    checkpoint_path = _project_path(root, args.checkpoint, "checkpoint")
    context_path = _project_path(root, args.context, "review context")
    _require_ignored(root, checkpoint_path, "checkpoint")
    _require_ignored(root, context_path, "review context")
    checkpoint = _read_object(checkpoint_path, "checkpoint")
    saved_context = _read_object(context_path, "review context")
    _validate_checkpoint(root, checkpoint, args.context)
    context_paths, context_pairs = _context_coverage(
        saved_context, checkpoint, "saved review context"
    )
    _validate_checkpoint_coverage(checkpoint, context_paths, context_pairs)

    checker_input = Path(args.checker).expanduser() if args.checker else root / "tools/specification/spec_check.py"
    if checker_input.is_symlink():
        raise HandoffError(f"checker must not be a symbolic link: {checker_input}")
    checker = checker_input.resolve()
    if not checker.is_file():
        raise HandoffError(f"checker must be a regular non-symlink file: {checker}")
    if checkpoint["trusted_base_controls"]:
        if not args.checker:
            raise HandoffError(
                "trusted-base checkpoint requires an explicit checker from the trusted base checkout"
            )
        try:
            checker.relative_to(root)
        except ValueError:
            pass
        else:
            raise HandoffError(
                "trusted-base checker must be outside the candidate project checkout"
            )
        _validate_trusted_tools(
            root,
            checker,
            checkpoint["base_commit"],
            timeout_seconds,
        )
    try:
        completed = subprocess.run(
            _checker_command(root, checker, checkpoint),
            cwd=root,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout_seconds,
        )
    except subprocess.TimeoutExpired as error:
        raise HandoffError("review-context regeneration timed out") from error
    if completed.returncode != 0:
        raise HandoffError(
            "review-context regeneration failed: "
            + (completed.stderr.strip() or completed.stdout.strip())
        )
    try:
        current_context = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise HandoffError("checker did not emit valid review-context JSON") from error
    if not isinstance(current_context, dict):
        raise HandoffError("checker review context root is not an object")
    _context_coverage(current_context, checkpoint, "current review context")

    expected_digest = checkpoint["scope_digest"]
    observed = {
        "checkpoint": expected_digest,
        "saved_context": saved_context.get("scope_digest"),
        "current_context": current_context.get("scope_digest"),
    }
    stale_fields = [
        key
        for key in ("supporting_traceability", "supporting_digests", "coverage_kinds")
        if saved_context[key] != current_context[key]
    ]
    if len(set(observed.values())) != 1 or stale_fields:
        print(
            json.dumps(
                {
                    "status": "stale",
                    "scope_digests": observed,
                    "stale_context_fields": stale_fields,
                },
                indent=2,
            )
        )
        return 1

    if args.handoff:
        handoff_path = _project_path(root, args.handoff, "handoff")
        _require_ignored(root, handoff_path, "handoff")
        handoff = _read_object(handoff_path, "handoff")
        _validate_handoff(
            root,
            handoff,
            args.checkpoint,
            args.context,
            checkpoint,
        )

    print(
        json.dumps(
            {
                "status": "current",
                "scope_digest": expected_digest,
                "handoff_verified": bool(args.handoff),
            },
            indent=2,
        )
    )
    return 0


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="Target project root.")
    parser.add_argument(
        "--checkpoint",
        default=".spec-assurance/review-checkpoint.json",
        help="Project-relative checkpoint path.",
    )
    parser.add_argument(
        "--context",
        default=".spec-assurance/review-context.json",
        help="Project-relative saved review-context path.",
    )
    parser.add_argument(
        "--handoff",
        help="Optional project-relative structured handoff path to validate.",
    )
    parser.add_argument(
        "--checker",
        help="Checker script to execute; defaults to the project-vendored checker.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=300,
        help="Maximum time allowed for context regeneration (default: 300).",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    try:
        return verify(parse_args(argv))
    except (HandoffError, OSError, subprocess.SubprocessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
