#!/usr/bin/env python3
"""Enforce approved specifications, traceability, evidence and review state.

The checker is deliberately strict by default. It validates the deterministic
records that make a model-led semantic review auditable; it does not pretend
that path existence or a green test name proves semantic compliance.
"""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import posixpath
import re
import subprocess
import sys
from dataclasses import asdict, dataclass, field, replace
from datetime import date, datetime
from pathlib import Path
from typing import Any, Iterable


SCHEMA_VERSION = 6
SEMANTIC_SCHEMA_VERSION = 5
COVERAGE_KINDS = {"implementation_required", "documentation", "process"}
REQ_ID_PATTERN = r"[A-Z][A-Z0-9]*(?:-[A-Z][A-Z0-9]*)+-\d{3,}"
REQ_HEADING_RE = re.compile(rf"^###\s+({REQ_ID_PATTERN}):\s+(.+?)\s*$", re.MULTILINE)
STATUS_RE = re.compile(r"^Status:\s*(.+?)\s*$", re.MULTILINE)
REQ_ID_FULL_RE = re.compile(rf"^`?({REQ_ID_PATTERN})`?$")
STRUCTURED_EVIDENCE_RE = re.compile(
    r"^(receipt|test|source|task|review|manual|hardware|provider):(.*)$"
)
MANUAL_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]*$")
EVIDENCE_OBLIGATIONS_RE = re.compile(r"^Evidence obligations:\s*(.*?)\s*$", re.MULTILINE)
NORMATIVE_KEYWORD_RE = re.compile(r"\b(?:MUST|MUST NOT|SHOULD|SHOULD NOT|MAY)\b")
HUMAN_ID_RE = re.compile(r"^human:[A-Za-z0-9][A-Za-z0-9._@-]*$")
REVIEWER_ID_RE = re.compile(r"^(?:human|model):[A-Za-z0-9][A-Za-z0-9._/@:-]*$")
CAPABILITY_ID_RE = re.compile(r"^CAP-[A-Z0-9]+(?:-[A-Z0-9]+)*-\d{3,}$")
SHA256_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
EVIDENCE_ISSUER_RE = re.compile(r"^(?:ci|human|service):[A-Za-z0-9][A-Za-z0-9._/@:-]*$")
FILE_CLASSIFICATION_CATEGORIES = {
    "implementation",
    "test",
    "documentation",
    "specification",
    "configuration",
    "tooling",
    "asset",
    "generated",
    "vendor",
    "metadata",
    "local",
}


DEFAULT_REQUIREMENT_STATUSES = {
    "Normative",
    "Experimental",
    "Future",
    "Deprecated",
    "Informative",
}
DEFAULT_MATRIX_STATUSES = {
    "Passing",
    "Implemented, evidence pending",
    "Implemented, not field-tested",
    "In progress",
    "Needs review",
    "Needs field validation",
    "Needs field retest",
    "Needs provider testing",
    "Open",
    "Blocked",
}
DEFAULT_BLOCKING_CODES = {
    "duplicate_requirement_id",
    "unknown_matrix_requirement",
    "duplicate_matrix_row",
    "missing_matrix_row",
    "missing_status",
    "unknown_requirement_status",
    "missing_verification",
    "missing_provenance",
    "missing_evidence_path",
    "missing_evidence_symbol",
    "empty_requirement_inventory",
    "empty_verification",
    "empty_provenance",
    "missing_normative_keyword",
    "missing_evidence_obligations",
    "missing_requirement_approval",
    "stale_requirement_approval",
    "invalid_requirement_approval",
    "missing_approval_policy",
    "invalid_approval_policy",
    "unauthorized_approver",
    "unauthorized_evidence_issuer",
    "missing_capability_map",
    "missing_capability_approval",
    "stale_capability_approval",
    "invalid_capability_map",
    "unclassified_project_file",
    "ambiguous_file_classification",
    "invalid_local_classification",
    "ignored_implementation_file",
    "invalid_implementation_mapping",
    "invalid_supporting_mapping",
    "invalid_coverage_kind",
    "unmapped_requirement",
    "unmapped_implementation",
    "missing_evidence_receipts",
    "missing_evidence_receipt",
    "stale_evidence_requirement",
    "stale_evidence_implementation",
    "stale_evidence_supporting",
    "missing_evidence_artifact",
    "stale_evidence_artifact",
    "passing_without_evidence",
    "passing_with_nonpassing_evidence",
    "matrix_evidence_mismatch",
    "missing_matrix_columns",
    "missing_matrix_status",
    "unknown_matrix_status",
    "missing_semantic_review",
    "stale_semantic_review",
    "failed_semantic_review",
    "incomplete_semantic_review",
    "invalid_semantic_conclusion",
    "deleted_requirement_without_tombstone",
    "invalid_requirement_retirement",
    "non_normative_matrix_row",
}

NON_BASELINABLE_CODES = {
    "missing_approval_policy",
    "invalid_approval_policy",
    "unauthorized_approver",
    "unauthorized_evidence_issuer",
    "missing_requirement_approval",
    "stale_requirement_approval",
    "invalid_requirement_approval",
    "unknown_requirement_status",
    "missing_capability_approval",
    "stale_capability_approval",
    "unmapped_implementation",
    "unclassified_project_file",
    "ambiguous_file_classification",
    "invalid_local_classification",
    "ignored_implementation_file",
    "invalid_implementation_mapping",
    "invalid_supporting_mapping",
    "invalid_coverage_kind",
    "missing_semantic_review",
    "stale_semantic_review",
    "failed_semantic_review",
    "invalid_semantic_conclusion",
    "deleted_requirement_without_tombstone",
    "invalid_requirement_retirement",
    "non_normative_matrix_row",
}


class ConfigurationError(ValueError):
    """Raised when checker configuration cannot be loaded safely."""


@dataclass
class CheckerConfig:
    model_version: int = SCHEMA_VERSION
    enforcement_mode: str = "strict"
    migration_approved_by: str = ""
    migration_decision_ref: str = ""
    migration_expires_at: str = ""
    spec_dir: str = "docs/specification"
    matrix: str = "docs/specification/verification-matrix.md"
    baseline: str = "docs/specification/spec-baseline.json"
    approval_policy: str = "docs/specification/approval-policy.json"
    approvals: str = "docs/specification/requirement-approvals.json"
    capabilities: str = "docs/specification/capability-map.json"
    evidence_receipts: str = "docs/specification/evidence-receipts.json"
    semantic_review: str = "docs/specification/semantic-review.json"
    evidence_artifact_paths: list[str] = field(
        default_factory=lambda: ["docs/specification/evidence/**"]
    )
    ignored_spec_files: list[str] = field(default_factory=lambda: ["templates/**"])
    requirement_statuses: set[str] = field(default_factory=lambda: set(DEFAULT_REQUIREMENT_STATUSES))
    matrix_statuses: set[str] = field(default_factory=lambda: set(DEFAULT_MATRIX_STATUSES))
    requirement_status_aliases: dict[str, str] = field(default_factory=dict)
    matrix_status_aliases: dict[str, str] = field(default_factory=dict)
    require_provenance: bool = True
    require_structured_evidence: bool = True
    require_human_approvals: bool = True
    require_capability_coverage: bool = True
    require_evidence_receipts: bool = True
    require_semantic_review: bool = True
    user_documentation_paths: list[str] = field(default_factory=lambda: ["README.md", "docs/**"])
    blocking_codes: set[str] = field(default_factory=lambda: set(DEFAULT_BLOCKING_CODES))
    full_blocking_codes: set[str] = field(default_factory=lambda: set(DEFAULT_BLOCKING_CODES))


@dataclass
class Requirement:
    id: str
    title: str
    path: str
    line: int
    lifecycle_status: str | None
    has_verification: bool
    has_provenance: bool
    verification_text: str
    provenance_text: str
    evidence_obligations: tuple[str, ...]
    superseded_by: str
    digest: str
    coverage_kind: str = "implementation_required"


@dataclass
class MatrixRow:
    requirement_id: str
    verification_type: str
    evidence: str
    evidence_state: str
    implementation_status: str
    verified_at: str
    notes: str
    path: str
    line: int
    computed_evidence_state: str = ""
    computed_implementation_status: str = ""


@dataclass(frozen=True)
class ApprovalPolicy:
    authority_ref: str
    authorized_approvers: tuple[str, ...]
    authorized_evidence_issuers: tuple[str, ...]
    authorized_semantic_reviewers: tuple[str, ...]
    digest: str


@dataclass(frozen=True)
class FileClassification:
    pattern: str
    category: str
    reason: str


@dataclass(frozen=True)
class ImplementationMapping:
    pattern: str
    requirement_ids: tuple[str, ...]


@dataclass(frozen=True)
class SupportingMapping:
    pattern: str
    requirement_ids: tuple[str, ...]
    coverage_kind: str
    reason: str


@dataclass
class Capability:
    id: str
    name: str
    status: str
    requirement_ids: tuple[str, ...]
    implementation_mappings: tuple[ImplementationMapping, ...]
    supporting_mappings: tuple[SupportingMapping, ...] = ()


@dataclass
class CapabilityMap:
    file_classifications: tuple[FileClassification, ...]
    capabilities: tuple[Capability, ...]
    digest: str
    supporting_files: tuple[str, ...] = ()


@dataclass
class EvidenceReceipt:
    id: str
    requirement_id: str
    requirement_digest: str
    implementation_digest: str
    kind: str
    state: str
    target: str
    verified_at: str
    verified_commit: str
    runner: str
    artifact_ref: str
    artifact_digest: str
    supporting_digest: str = ""
    valid_until: str | None = None
    reason: str = ""
    approved_by: str = ""
    decision_ref: str = ""


def _finding_fingerprint(code: str, requirement_id: str | None, path: str | None, message: str) -> str:
    stable_message = re.sub(r"(?<=\S):\d+\b", ":#", " ".join(message.split()))
    normalized = "|".join(
        [
            code.strip(),
            (requirement_id or "").strip(),
            (path or "").replace("\\", "/").strip(),
            stable_message,
        ]
    )
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:24]


@dataclass
class Finding:
    severity: str
    code: str
    message: str
    path: str | None = None
    line: int | None = None
    requirement_id: str | None = None
    fingerprint: str = field(init=False)
    baseline_accepted: bool = False
    blocking: bool = False

    def __post_init__(self) -> None:
        self.fingerprint = _finding_fingerprint(self.code, self.requirement_id, self.path, self.message)


@dataclass
class BaselineEntry:
    code: str
    requirement_id: str | None
    fingerprint: str
    reason: str
    accepted_at: str
    accepted_by: str
    decision_ref: str
    expires_at: str

    @classmethod
    def from_finding(
        cls,
        finding: Finding,
        reason: str,
        accepted_by: str,
        decision_ref: str,
        expires_at: str,
    ) -> "BaselineEntry":
        return cls(
            code=finding.code,
            requirement_id=finding.requirement_id,
            fingerprint=finding.fingerprint,
            reason=reason,
            accepted_at=date.today().isoformat(),
            accepted_by=accepted_by,
            decision_ref=decision_ref,
            expires_at=expires_at,
        )

    def is_active(self, policy: ApprovalPolicy | None) -> bool:
        return (
            HUMAN_ID_RE.fullmatch(self.accepted_by) is not None
            and policy is not None
            and self.accepted_by in policy.authorized_approvers
            and bool(self.reason.strip())
            and _valid_date(self.accepted_at)
            and bool(self.decision_ref.strip())
            and _valid_date(self.expires_at)
            and self.expires_at >= date.today().isoformat()
        )


def _load_yaml_or_json(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    return _load_yaml_or_json_text(text, str(path))


def _load_yaml_or_json_text(text: str, label: str) -> dict[str, Any]:
    """Parse JSON-compatible YAML from a file or trusted Git object."""
    try:
        value = json.loads(text)
    except json.JSONDecodeError:
        try:
            import yaml  # type: ignore[import-not-found]
        except ImportError as error:
            raise ConfigurationError(
                f"{label} is not JSON-compatible YAML and PyYAML is not installed."
            ) from error
        value = yaml.safe_load(text)
    if not isinstance(value, dict):
        raise ConfigurationError(f"Configuration must be an object: {label}")
    return value


def _as_string_set(value: Any, key: str, default: set[str]) -> set[str]:
    if value is None:
        return set(default)
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ConfigurationError(f"{key} must be a list of strings.")
    return set(value)


def _as_string_dict(value: Any, key: str) -> dict[str, str]:
    if value is None:
        return {}
    if not isinstance(value, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in value.items()):
        raise ConfigurationError(f"{key} must map strings to strings.")
    return dict(value)


def _as_string_list(value: Any, key: str, default: list[str]) -> list[str]:
    if value is None:
        return list(default)
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ConfigurationError(f"{key} must be a list of strings.")
    return list(value)


def _as_bool(value: Any, key: str, default: bool) -> bool:
    if value is None:
        return default
    if not isinstance(value, bool):
        raise ConfigurationError(f"{key} must be true or false.")
    return value


def _valid_date(value: str) -> bool:
    try:
        date.fromisoformat(value)
    except (TypeError, ValueError):
        return False
    return True


def _valid_timestamp(value: str) -> bool:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (AttributeError, TypeError, ValueError):
        return False
    return parsed.tzinfo is not None


def _sha256_bytes(value: bytes) -> str:
    return "sha256:" + hashlib.sha256(value).hexdigest()


def _canonical_json_digest(value: Any) -> str:
    payload = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return _sha256_bytes(payload.encode("utf-8"))


def _normalized_markdown_digest(value: str) -> str:
    normalized = "\n".join(line.rstrip() for line in value.replace("\r\n", "\n").split("\n")).strip()
    return _sha256_bytes((normalized + "\n").encode("utf-8"))


def load_config(root: Path, config_path: Path | None) -> CheckerConfig:
    root = root.resolve()
    default_path = root / "docs/specification/spec-config.yaml"
    selected = config_path.resolve() if config_path else default_path
    raw: dict[str, Any] = _load_yaml_or_json(selected) if selected.is_file() else {}
    return _config_from_raw(root, raw)


def load_base_config(
    root: Path,
    base: str,
    config_path: str = "docs/specification/spec-config.yaml",
) -> CheckerConfig:
    """Load the complete checker control plane from a trusted Git revision."""
    root = root.resolve()
    _project_path(root, config_path, "trusted base configuration")
    content = _git_show_bytes(root, base, config_path)
    raw = (
        _load_yaml_or_json_text(content.decode("utf-8"), f"{base}:{config_path}")
        if content is not None
        else {}
    )
    return _config_from_raw(root, raw)


def _config_from_raw(root: Path, raw: dict[str, Any]) -> CheckerConfig:
    """Build and validate one checker configuration object."""
    config = CheckerConfig(
        model_version=int(raw.get("model_version", SCHEMA_VERSION)),
        enforcement_mode=str(raw.get("enforcement_mode", "strict")),
        migration_approved_by=str(raw.get("migration_approved_by", "")),
        migration_decision_ref=str(raw.get("migration_decision_ref", "")),
        migration_expires_at=str(raw.get("migration_expires_at", "")),
        spec_dir=str(raw.get("spec_dir", "docs/specification")),
        matrix=str(raw.get("matrix", "docs/specification/verification-matrix.md")),
        baseline=str(raw.get("baseline", "docs/specification/spec-baseline.json")),
        approval_policy=str(
            raw.get("approval_policy", "docs/specification/approval-policy.json")
        ),
        approvals=str(raw.get("approvals", "docs/specification/requirement-approvals.json")),
        capabilities=str(raw.get("capabilities", "docs/specification/capability-map.json")),
        evidence_receipts=str(
            raw.get("evidence_receipts", "docs/specification/evidence-receipts.json")
        ),
        semantic_review=str(raw.get("semantic_review", "docs/specification/semantic-review.json")),
        evidence_artifact_paths=_as_string_list(
            raw.get("evidence_artifact_paths"),
            "evidence_artifact_paths",
            ["docs/specification/evidence/**"],
        ),
        ignored_spec_files=_as_string_list(raw.get("ignored_spec_files"), "ignored_spec_files", ["templates/**"]),
        requirement_statuses=_as_string_set(
            raw.get("requirement_statuses"), "requirement_statuses", DEFAULT_REQUIREMENT_STATUSES
        ),
        matrix_statuses=_as_string_set(raw.get("matrix_statuses"), "matrix_statuses", DEFAULT_MATRIX_STATUSES),
        requirement_status_aliases=_as_string_dict(
            raw.get("requirement_status_aliases"), "requirement_status_aliases"
        ),
        matrix_status_aliases=_as_string_dict(raw.get("matrix_status_aliases"), "matrix_status_aliases"),
        require_provenance=_as_bool(raw.get("require_provenance"), "require_provenance", True),
        require_structured_evidence=_as_bool(
            raw.get("require_structured_evidence"), "require_structured_evidence", True
        ),
        require_human_approvals=_as_bool(
            raw.get("require_human_approvals"), "require_human_approvals", True
        ),
        require_capability_coverage=_as_bool(
            raw.get("require_capability_coverage"), "require_capability_coverage", True
        ),
        require_evidence_receipts=_as_bool(
            raw.get("require_evidence_receipts"), "require_evidence_receipts", True
        ),
        require_semantic_review=_as_bool(
            raw.get("require_semantic_review"), "require_semantic_review", True
        ),
        user_documentation_paths=_as_string_list(
            raw.get("user_documentation_paths"),
            "user_documentation_paths",
            ["README.md", "docs/**"],
        ),
        blocking_codes=_as_string_set(raw.get("blocking_codes"), "blocking_codes", DEFAULT_BLOCKING_CODES),
        full_blocking_codes=_as_string_set(
            raw.get("full_blocking_codes"), "full_blocking_codes", DEFAULT_BLOCKING_CODES
        ),
    )
    if config.model_version != SCHEMA_VERSION:
        raise ConfigurationError(
            f"Unsupported specification model version {config.model_version}; expected {SCHEMA_VERSION}."
        )
    if config.enforcement_mode not in {"strict", "migration"}:
        raise ConfigurationError("enforcement_mode must be 'strict' or 'migration'.")
    strict_switches = {
        "require_provenance": config.require_provenance,
        "require_structured_evidence": config.require_structured_evidence,
        "require_human_approvals": config.require_human_approvals,
        "require_capability_coverage": config.require_capability_coverage,
        "require_evidence_receipts": config.require_evidence_receipts,
        "require_semantic_review": config.require_semantic_review,
    }
    disabled = sorted(key for key, enabled in strict_switches.items() if not enabled)
    if config.enforcement_mode == "strict" and disabled:
        raise ConfigurationError(
            "Strict mode cannot disable assurance controls: " + ", ".join(disabled)
        )
    if config.enforcement_mode == "migration":
        if not HUMAN_ID_RE.fullmatch(config.migration_approved_by):
            raise ConfigurationError("Migration mode requires migration_approved_by=human:<id>.")
        if not config.migration_decision_ref.strip():
            raise ConfigurationError("Migration mode requires migration_decision_ref.")
        if not _valid_date(config.migration_expires_at) or config.migration_expires_at < date.today().isoformat():
            raise ConfigurationError("Migration mode requires a current migration_expires_at date.")
    for alias, target in config.requirement_status_aliases.items():
        if target not in config.requirement_statuses:
            raise ConfigurationError(f"Requirement status alias {alias!r} targets unknown status {target!r}.")
    for alias, target in config.matrix_status_aliases.items():
        if target not in config.matrix_statuses:
            raise ConfigurationError(f"Matrix status alias {alias!r} targets unknown status {target!r}.")
    for key in (
        "spec_dir",
        "matrix",
        "baseline",
        "approval_policy",
        "approvals",
        "capabilities",
        "evidence_receipts",
        "semantic_review",
    ):
        _project_path(root, getattr(config, key), key)
    if not config.evidence_artifact_paths:
        raise ConfigurationError("evidence_artifact_paths must not be empty.")
    for pattern in config.evidence_artifact_paths:
        _validate_project_pattern(pattern, "evidence_artifact_paths")
    if not config.user_documentation_paths:
        raise ConfigurationError("user_documentation_paths must not be empty.")
    for pattern in config.user_documentation_paths:
        _validate_project_pattern(pattern, "user_documentation_paths")
    return config


def _relative(path: Path, root: Path) -> str:
    try:
        return str(path.resolve().relative_to(root.resolve())).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def _project_path(root: Path, value: str, label: str) -> Path:
    relative = Path(value)
    if relative.is_absolute():
        raise ConfigurationError(f"{label} must be project-relative: {value}")
    candidate = root / relative
    try:
        candidate.resolve().relative_to(root.resolve())
    except ValueError as error:
        raise ConfigurationError(f"{label} escapes the project root: {value}") from error
    return candidate


def _validate_project_pattern(pattern: str, label: str) -> None:
    normalized = pattern.replace("\\", "/")
    if (
        not normalized
        or normalized.startswith("/")
        or any(part == ".." for part in normalized.split("/"))
        or "\r" in normalized
        or "\n" in normalized
    ):
        raise ConfigurationError(f"{label} contains an unsafe project pattern: {pattern}")


def _project_file_bytes(root: Path, value: str, label: str) -> bytes:
    candidate = _project_path(root, value, label)
    if candidate.is_symlink():
        return b"symlink\0" + str(candidate.readlink()).encode("utf-8", "surrogateescape")
    if not candidate.is_file():
        raise FileNotFoundError(value)
    return candidate.read_bytes()


def _ignored(relative_to_spec: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(relative_to_spec, pattern) for pattern in patterns)


def iter_markdown_files(spec_dir: Path, ignored_patterns: list[str]) -> Iterable[Path]:
    for path in sorted(spec_dir.rglob("*.md")):
        relative = str(path.relative_to(spec_dir)).replace("\\", "/")
        if path.name == "verification-matrix.md" or _ignored(relative, ignored_patterns):
            continue
        yield path


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def _field_text(body: str, label: str) -> str:
    marker = re.search(rf"^{re.escape(label)}:[ \t]*([^\r\n]*)$", body, re.MULTILINE)
    if marker is None:
        return ""
    pieces = [marker.group(1).strip()]
    tail = body[marker.end() :]
    for line in tail.splitlines():
        if re.match(r"^(?:Status|Coverage kind|Verification|Provenance|Evidence obligations|Superseded by):", line):
            break
        pieces.append(line.strip())
    return "\n".join(piece for piece in pieces if piece).strip()


def _evidence_obligations(body: str) -> tuple[str, ...]:
    match = EVIDENCE_OBLIGATIONS_RE.search(body)
    if match is None:
        return ()
    values = [item.strip().strip("`") for item in re.split(r"[,;]", match.group(1))]
    return tuple(item for item in values if item)


def parse_requirements(spec_dir: Path, root: Path, config: CheckerConfig) -> list[Requirement]:
    requirements: list[Requirement] = []
    _, ignored_paths = _project_files_by_state(root)
    for path in iter_markdown_files(spec_dir, config.ignored_spec_files):
        if _relative(path, root) in ignored_paths:
            continue
        if path.is_symlink():
            raise ConfigurationError(
                f"Canonical requirement files must not be symbolic links: {_relative(path, root)}"
            )
        text = path.read_text(encoding="utf-8")
        requirements.extend(_parse_requirements_text(text, _relative(path, root), config))
    return requirements


def _parse_requirements_text(text: str, path: str, config: CheckerConfig) -> list[Requirement]:
    """Parse requirement records from one candidate or base-revision Markdown file."""
    requirements: list[Requirement] = []
    matches = list(REQ_HEADING_RE.finditer(text))
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        body = text[start:end]
        requirement_text = match.group(0) + body
        status_match = STATUS_RE.search(body)
        raw_status = status_match.group(1).strip() if status_match else None
        lifecycle_status = config.requirement_status_aliases.get(raw_status, raw_status) if raw_status else None
        coverage_matches = re.findall(r"^Coverage kind:[ \t]*([^\r\n]*)$", body, re.MULTILINE)
        requirements.append(
            Requirement(
                id=match.group(1),
                title=match.group(2).strip(),
                path=path,
                line=line_number(text, match.start()),
                lifecycle_status=lifecycle_status,
                has_verification=re.search(r"^Verification:", body, re.MULTILINE) is not None,
                has_provenance=re.search(r"^Provenance:", body, re.MULTILINE) is not None,
                verification_text=_field_text(body, "Verification"),
                provenance_text=_field_text(body, "Provenance"),
                evidence_obligations=_evidence_obligations(body),
                superseded_by=_field_text(body, "Superseded by"),
                digest=_normalized_markdown_digest(requirement_text),
                coverage_kind=(coverage_matches[0].strip() if len(coverage_matches) == 1
                               else "invalid: duplicate Coverage kind fields" if coverage_matches
                               else "implementation_required"),
            )
        )
    return requirements


def split_markdown_row(line: str) -> list[str]:
    content = line.strip()
    if content.startswith("|"):
        content = content[1:]
    if content.endswith("|"):
        content = content[:-1]
    cells: list[str] = []
    current: list[str] = []
    escaped = False
    in_code = False
    for character in content:
        if escaped:
            current.append(character if character == "|" else "\\" + character)
            escaped = False
        elif character == "\\":
            escaped = True
        elif character == "`":
            in_code = not in_code
            current.append(character)
        elif character == "|" and not in_code:
            cells.append("".join(current).strip())
            current = []
        else:
            current.append(character)
    if escaped:
        current.append("\\")
    cells.append("".join(current).strip())
    return cells


def parse_matrix(matrix_path: Path, root: Path, config: CheckerConfig) -> tuple[list[MatrixRow], list[Finding]]:
    if matrix_path.is_symlink():
        raise ConfigurationError(
            f"Verification matrix must not be a symbolic link: {_relative(matrix_path, root)}"
        )
    if not matrix_path.exists():
        return [], [
            Finding("failure", "missing_matrix", "Verification matrix is missing.", _relative(matrix_path, root))
        ]
    rows: list[MatrixRow] = []
    findings: list[Finding] = []
    header: dict[str, int] | None = None
    required_columns = {
        "requirement",
        "verification",
        "evidence",
        "evidence state",
        "status",
        "verified at",
        "notes",
    }
    for line_no, line in enumerate(matrix_path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.lstrip().startswith("|"):
            continue
        cells = split_markdown_row(line)
        if not cells:
            continue
        normalized = [cell.strip().lower() for cell in cells]
        if normalized[0] in {"requirement", "requirement id"}:
            header = {name: index for index, name in enumerate(normalized)}
            if "requirement id" in header and "requirement" not in header:
                header["requirement"] = header["requirement id"]
            if "type" in header and "verification" not in header:
                header["verification"] = header["type"]
            missing = sorted(required_columns - set(header))
            if missing:
                findings.append(
                    Finding(
                        "failure",
                        "missing_matrix_columns",
                        "Verification matrix is missing required columns: " + ", ".join(missing),
                        _relative(matrix_path, root),
                        line_no,
                    )
                )
            continue
        if all(set(cell) <= {"-", ":"} for cell in cells):
            continue
        if header is None:
            continue
        requirement_index = header.get("requirement", 0)
        if requirement_index >= len(cells):
            continue
        id_match = REQ_ID_FULL_RE.fullmatch(cells[requirement_index])
        if not id_match:
            continue

        def cell(name: str) -> str:
            index = header.get(name)
            return cells[index] if index is not None and index < len(cells) else ""

        raw_status = cell("status")
        status = config.matrix_status_aliases.get(raw_status, raw_status)
        row = MatrixRow(
            requirement_id=id_match.group(1),
            verification_type=cell("verification"),
            evidence=cell("evidence"),
            evidence_state=cell("evidence state"),
            implementation_status=status,
            verified_at=cell("verified at"),
            notes=cell("notes"),
            path=_relative(matrix_path, root),
            line=line_no,
        )
        rows.append(row)
        if not status:
            findings.append(
                Finding("failure", "missing_matrix_status", f"Matrix row for {row.requirement_id} has empty status.", row.path, line_no, row.requirement_id)
            )
        elif status not in config.matrix_statuses:
            findings.append(
                Finding("failure", "unknown_matrix_status", f"Matrix status {raw_status!r} is not configured.", row.path, line_no, row.requirement_id)
            )
    return rows, findings


def _structured_evidence_tokens(value: str) -> Iterable[tuple[str, str]]:
    for part in value.split(";"):
        token = part.strip().strip("`")
        match = STRUCTURED_EVIDENCE_RE.match(token)
        if match:
            yield match.group(1), match.group(2).strip()


def check_evidence(rows: list[MatrixRow], root: Path, config: CheckerConfig) -> list[Finding]:
    findings: list[Finding] = []
    for row in rows:
        tokens = list(_structured_evidence_tokens(row.evidence))
        if config.require_structured_evidence and row.evidence.strip() and not tokens:
            findings.append(
                Finding("warning", "unstructured_evidence", "Evidence is not expressed with structured tokens.", row.path, row.line, row.requirement_id)
            )
        for kind, value in tokens:
            if not value:
                findings.append(
                    Finding("warning", "invalid_evidence_token", f"Empty {kind} evidence token.", row.path, row.line, row.requirement_id)
                )
                continue
            if kind in {"test", "source"}:
                raw_path, separator, symbol = value.partition("#")
                try:
                    candidate = _project_path(root, raw_path, f"{kind} evidence path")
                except ConfigurationError:
                    findings.append(
                        Finding("failure", "invalid_evidence_path", f"Referenced {kind} evidence path escapes the project: {raw_path}", row.path, row.line, row.requirement_id)
                    )
                    continue
                if not candidate.exists():
                    findings.append(
                        Finding("failure", "missing_evidence_path", f"Referenced {kind} evidence path does not exist: {raw_path}", row.path, row.line, row.requirement_id)
                    )
                elif separator and symbol and candidate.is_file():
                    try:
                        source = candidate.read_text(encoding="utf-8")
                    except UnicodeDecodeError:
                        source = ""
                    if symbol not in source:
                        findings.append(
                            Finding("failure", "missing_evidence_symbol", f"Referenced evidence symbol {symbol!r} was not found in {raw_path}.", row.path, row.line, row.requirement_id)
                        )
            elif kind in {"review", "manual", "hardware", "provider"} and not MANUAL_ID_RE.fullmatch(value):
                findings.append(
                    Finding("warning", "invalid_evidence_id", f"{kind} evidence ID must use lowercase letters, digits, dots, underscores or hyphens: {value}", row.path, row.line, row.requirement_id)
                )
    return findings


def _read_json_object(path: Path) -> dict[str, Any]:
    if path.is_symlink():
        raise ConfigurationError(f"Assurance JSON must not be a symbolic link: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ConfigurationError(f"JSON ledger must contain an object: {path}")
    return value


def _is_authorized(value: str, patterns: Iterable[str]) -> bool:
    return any(fnmatch.fnmatchcase(value, pattern) for pattern in patterns)


def load_and_check_approval_policy(
    root: Path,
    config: CheckerConfig,
) -> tuple[ApprovalPolicy | None, list[Finding]]:
    path = _project_path(root, config.approval_policy, "approval_policy")
    relative = _relative(path, root)
    if not path.is_file():
        return None, [
            Finding(
                "failure",
                "missing_approval_policy",
                "Approval authority policy is missing.",
                relative,
            )
        ]
    raw = _read_json_object(path)
    return _approval_policy_from_raw(raw, relative)


def _approval_policy_from_raw(
    raw: dict[str, Any],
    path: str,
) -> tuple[ApprovalPolicy | None, list[Finding]]:
    """Validate one candidate or base approval-policy JSON object."""
    authority_ref = raw.get("authority_ref")
    approvers = raw.get("authorized_approvers")
    issuers = raw.get("authorized_evidence_issuers")
    reviewers = raw.get("authorized_semantic_reviewers")
    valid = (
        raw.get("schema_version") == 1
        and isinstance(authority_ref, str)
        and bool(authority_ref.strip())
        and isinstance(approvers, list)
        and bool(approvers)
        and all(isinstance(item, str) and HUMAN_ID_RE.fullmatch(item) for item in approvers)
        and len(approvers) == len(set(approvers))
        and isinstance(issuers, list)
        and bool(issuers)
        and all(isinstance(item, str) and EVIDENCE_ISSUER_RE.fullmatch(item) for item in issuers)
        and len(issuers) == len(set(issuers))
        and isinstance(reviewers, list)
        and bool(reviewers)
        and all(
            isinstance(item, str)
            and item.startswith(("human:", "model:"))
            and not any(character.isspace() for character in item)
            and "[" not in item
            for item in reviewers
        )
        and len(reviewers) == len(set(reviewers))
    )
    if not valid:
        return None, [
            Finding(
                "failure",
                "invalid_approval_policy",
                "Approval policy needs schema_version 1, an authority reference, unique human approvers, trusted evidence issuers and semantic-reviewer patterns.",
                path,
            )
        ]
    digest_source = {
        "authority_ref": authority_ref,
        "authorized_approvers": approvers,
        "authorized_evidence_issuers": issuers,
        "authorized_semantic_reviewers": reviewers,
    }
    return (
        ApprovalPolicy(
            authority_ref.strip(),
            tuple(approvers),
            tuple(issuers),
            tuple(reviewers),
            _canonical_json_digest(digest_source),
        ),
        [],
    )


def load_base_approval_policy(
    root: Path,
    base: str,
    config: CheckerConfig,
) -> tuple[ApprovalPolicy | None, list[Finding]]:
    """Load the approval policy pinned in the trusted base revision."""
    policy_path = config.approval_policy
    _project_path(root, policy_path, "trusted base approval_policy")
    content = _git_show_bytes(root, base, policy_path)
    relative = f"{base}:{policy_path}"
    if content is None:
        return None, [Finding("failure", "missing_approval_policy", "Trusted base approval authority policy is missing.", relative)]
    try:
        raw = json.loads(content.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None, [Finding("failure", "invalid_approval_policy", "Trusted base approval authority policy is not valid JSON.", relative)]
    if not isinstance(raw, dict):
        return None, [Finding("failure", "invalid_approval_policy", "Trusted base approval authority policy must contain an object.", relative)]
    return _approval_policy_from_raw(raw, relative)


def _valid_human_approval(
    record: Any,
    digest_key: str,
    expected_digest: str,
    policy: ApprovalPolicy | None,
) -> bool:
    if not isinstance(record, dict):
        return False
    return (
        record.get("state") == "approved"
        and record.get(digest_key) == expected_digest
        and isinstance(record.get("approved_by"), str)
        and HUMAN_ID_RE.fullmatch(record["approved_by"]) is not None
        and policy is not None
        and record["approved_by"] in policy.authorized_approvers
        and isinstance(record.get("approved_at"), str)
        and _valid_timestamp(record["approved_at"])
        and isinstance(record.get("decision_ref"), str)
        and bool(record["decision_ref"].strip())
    )


def check_requirement_approvals(
    root: Path,
    config: CheckerConfig,
    requirements: list[Requirement],
    policy: ApprovalPolicy | None,
) -> list[Finding]:
    if not config.require_human_approvals:
        return []
    path = root / config.approvals
    approval_required = [
        item
        for item in requirements
        if item.lifecycle_status in {"Normative", "Deprecated"}
    ]
    if not path.is_file():
        return [
            Finding(
                "failure",
                "missing_requirement_approval",
                f"{item.id} has no human approval ledger entry for {item.digest}.",
                item.path,
                item.line,
                item.id,
            )
            for item in approval_required
        ]
    raw = _read_json_object(path)
    if raw.get("schema_version") != 1 or not isinstance(raw.get("approvals"), list):
        return [
            Finding(
                "failure",
                "invalid_requirement_approval",
                "Requirement approval ledger must use schema_version 1 and an approvals list.",
                _relative(path, root),
            )
        ]
    records = raw["approvals"]
    findings: list[Finding] = []
    known_ids = {item.id for item in requirements}
    for item in approval_required:
        candidates = [record for record in records if isinstance(record, dict) and record.get("requirement_id") == item.id]
        current = [record for record in candidates if record.get("requirement_digest") == item.digest]
        valid = [
            record
            for record in current
            if _valid_human_approval(record, "requirement_digest", item.digest, policy)
        ]
        if len(valid) == 1:
            continue
        if len(valid) > 1:
            findings.append(
                Finding("failure", "invalid_requirement_approval", f"{item.id} has multiple active approvals for the same revision.", _relative(path, root), requirement_id=item.id)
            )
        elif current and any(
            isinstance(record, dict)
            and isinstance(record.get("approved_by"), str)
            and HUMAN_ID_RE.fullmatch(record["approved_by"])
            and (
                policy is None
                or record["approved_by"] not in policy.authorized_approvers
            )
            for record in current
        ):
            findings.append(
                Finding(
                    "failure",
                    "unauthorized_approver",
                    f"{item.id} approval is attributed to an identity outside the approval policy.",
                    _relative(path, root),
                    requirement_id=item.id,
                )
            )
        elif current:
            findings.append(
                Finding("failure", "invalid_requirement_approval", f"{item.id} approval lacks valid human identity, timestamp, decision reference or approved state.", _relative(path, root), requirement_id=item.id)
            )
        elif candidates:
            findings.append(
                Finding("failure", "stale_requirement_approval", f"{item.id} approval does not match current digest {item.digest}.", _relative(path, root), requirement_id=item.id)
            )
        else:
            findings.append(
                Finding("failure", "missing_requirement_approval", f"{item.id} has no human approval for current digest {item.digest}.", item.path, item.line, item.id)
            )
    for record in records:
        if isinstance(record, dict) and isinstance(record.get("requirement_id"), str) and record["requirement_id"] not in known_ids:
            findings.append(
                Finding("warning", "orphaned_requirement_approval", f"Approval references unknown requirement {record['requirement_id']}.", _relative(path, root), requirement_id=record["requirement_id"])
            )
    return findings


def _git_paths(root: Path, arguments: list[str], label: str) -> set[str]:
    """Return Git path records without line splitting or filename normalization."""
    listed = subprocess.run(
        ["git", "ls-files", "-z", *arguments],
        cwd=root,
        check=False,
        capture_output=True,
    )
    if listed.returncode != 0:
        message = listed.stderr.decode("utf-8", "replace").strip()
        raise ConfigurationError(message or f"Could not enumerate {label} files.")
    return {
        item.decode("utf-8", "surrogateescape")
        for item in listed.stdout.split(b"\0")
        if item
    }


def _project_files_by_state(root: Path) -> tuple[set[str], set[str]]:
    """Enumerate normal and ignored Git paths while preserving unusual names."""
    git = subprocess.run(
        ["git", "rev-parse", "--is-inside-work-tree"],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    if git.returncode == 0:
        normal = _git_paths(root, ["--cached", "--others", "--exclude-standard"], "project")
        ignored = _git_paths(
            root,
            ["--others", "--ignored", "--exclude-standard"],
            "ignored project",
        )
        present = lambda path: (root / path).exists() or (root / path).is_symlink()
        return {path for path in normal if present(path)}, {path for path in ignored if present(path)}
    normal = {
        str(path.relative_to(root)).replace("\\", "/")
        for path in root.rglob("*")
        if (path.is_file() or path.is_symlink()) and ".git" not in path.parts
    }
    return normal, set()


def _project_files(root: Path) -> set[str]:
    """Return all inventory paths, including ignored paths needing classification."""
    normal, ignored = _project_files_by_state(root)
    return normal | ignored


def _literal_pattern_prefix(pattern: str) -> str:
    wildcard_at = min(
        (index for index, character in enumerate(pattern) if character in "*?["),
        default=len(pattern),
    )
    prefix = pattern[:wildcard_at].rstrip("/")
    return prefix or "."


def _matches(path: str, patterns: Iterable[str]) -> bool:
    return any(fnmatch.fnmatch(path, pattern) for pattern in patterns)


def implementation_traceability(
    capability_map: CapabilityMap | None,
    implementation_files: Iterable[str],
) -> dict[str, tuple[str, ...]]:
    if capability_map is None:
        return {}
    active = [item for item in capability_map.capabilities if item.status == "active"]
    result: dict[str, tuple[str, ...]] = {}
    for implementation_path in sorted(implementation_files):
        requirement_ids = {
            requirement_id
            for capability in active
            for mapping in capability.implementation_mappings
            if fnmatch.fnmatch(implementation_path, mapping.pattern)
            for requirement_id in mapping.requirement_ids
        }
        result[implementation_path] = tuple(sorted(requirement_ids))
    return result


def supporting_traceability(capability_map: CapabilityMap | None) -> dict[str, tuple[str, ...]]:
    if capability_map is None:
        return {}
    return {
        path: tuple(sorted({
            requirement_id
            for capability in capability_map.capabilities if capability.status == "active"
            for mapping in capability.supporting_mappings
            if fnmatch.fnmatch(path, mapping.pattern)
            for requirement_id in mapping.requirement_ids
        }))
        for path in capability_map.supporting_files
    }


def _assurance_control_path(path: str, config: CheckerConfig) -> bool:
    # An assurance record cannot prove itself, even if reclassified as tooling.
    normalize = lambda value: posixpath.normpath(value.replace("\\", "/"))
    path = normalize(path)
    spec_dir = normalize(config.spec_dir)
    return (
        spec_dir == "." or path == spec_dir or path.startswith(spec_dir.rstrip("/") + "/")
        or path in {normalize(value) for value in (
            config.matrix, config.baseline, config.approval_policy, config.approvals,
            config.capabilities, config.evidence_receipts, config.semantic_review
        ) if value}
        or path.startswith(("tools/specification/", "templates/specification-assurance/"))
        or fnmatch.fnmatch(path, "tools/test_spec*.py")
        or path in {"tools/check_spec_requirements.py", "scripts/pre_push_check.sh",
                    ".github/CODEOWNERS", ".github/workflows/spec-compliance.yml"}
    )


def _supporting_file_is_regular(root: Path, path: str) -> bool:
    candidate = root / path
    return candidate.is_file() and not candidate.is_symlink() and not any(
        parent.is_symlink() for parent in candidate.parents if parent != root and root in parent.parents
    )


def load_and_check_capabilities(
    root: Path,
    config: CheckerConfig,
    requirements: list[Requirement],
    policy: ApprovalPolicy | None = None,
) -> tuple[CapabilityMap | None, list[Finding], set[str]]:
    if not config.require_capability_coverage:
        return None, [], set()
    path = _project_path(root, config.capabilities, "capabilities")
    if not path.is_file():
        return None, [Finding("failure", "missing_capability_map", "Capability map is missing.", _relative(path, root))], set()
    raw = _read_json_object(path)
    findings: list[Finding] = []
    classifications_raw = raw.get("file_classifications")
    capabilities_raw = raw.get("capabilities")
    if (
        raw.get("schema_version") != 3
        or not isinstance(classifications_raw, list)
        or not classifications_raw
        or not isinstance(capabilities_raw, list)
    ):
        return None, [Finding("failure", "invalid_capability_map", "Capability map schema is invalid.", _relative(path, root))], set()

    classifications: list[FileClassification] = []
    for entry in classifications_raw:
        if (
            not isinstance(entry, dict)
            or not isinstance(entry.get("pattern"), str)
            or not entry["pattern"]
            or entry.get("category") not in FILE_CLASSIFICATION_CATEGORIES
            or not isinstance(entry.get("reason"), str)
            or not entry["reason"].strip()
        ):
            findings.append(
                Finding(
                    "failure",
                    "invalid_capability_map",
                    "Every file classification needs a pattern, supported category and reason.",
                    _relative(path, root),
                )
            )
            continue
        _validate_project_pattern(entry["pattern"], "file classification")
        classifications.append(
            FileClassification(entry["pattern"], entry["category"], entry["reason"].strip())
        )

    capabilities: list[Capability] = []
    seen_capability_ids: set[str] = set()
    requirement_ids = {item.id for item in requirements}
    normative_ids = {item.id for item in requirements if item.lifecycle_status == "Normative"}
    coverage_kinds = {item.id: item.coverage_kind for item in requirements}
    for entry in capabilities_raw:
        if not isinstance(entry, dict):
            findings.append(Finding("failure", "invalid_capability_map", "Capability entries must be objects.", _relative(path, root)))
            continue
        capability_id = entry.get("id")
        name = entry.get("name")
        status = entry.get("status")
        mapped = entry.get("requirement_ids")
        mappings_raw = entry.get("implementation_mappings")
        supporting_raw = entry.get("supporting_mappings", [])
        if (
            not isinstance(capability_id, str)
            or not CAPABILITY_ID_RE.fullmatch(capability_id)
            or capability_id in seen_capability_ids
            or not isinstance(name, str)
            or not name.strip()
            or status not in {"active", "planned", "deprecated"}
            or not isinstance(mapped, list)
            or not all(isinstance(item, str) for item in mapped)
            or len(mapped) != len(set(mapped))
            or not isinstance(mappings_raw, list)
            or not isinstance(supporting_raw, list)
        ):
            findings.append(Finding("failure", "invalid_capability_map", f"Invalid or duplicate capability entry: {capability_id!r}.", _relative(path, root)))
            continue
        seen_capability_ids.add(capability_id)
        mappings: list[ImplementationMapping] = []
        for mapping_raw in mappings_raw:
            mapping_ids = mapping_raw.get("requirement_ids") if isinstance(mapping_raw, dict) else None
            if (
                not isinstance(mapping_raw, dict)
                or not isinstance(mapping_raw.get("pattern"), str)
                or not mapping_raw["pattern"]
                or not isinstance(mapping_ids, list)
                or not mapping_ids
                or not all(isinstance(item, str) for item in mapping_ids)
                or len(mapping_ids) != len(set(mapping_ids))
            ):
                findings.append(
                    Finding(
                        "failure",
                        "invalid_implementation_mapping",
                        f"{capability_id} has an invalid implementation mapping.",
                        _relative(path, root),
                    )
                )
                continue
            _validate_project_pattern(mapping_raw["pattern"], "implementation mapping")
            mappings.append(ImplementationMapping(mapping_raw["pattern"], tuple(mapping_ids)))
        supporting: list[SupportingMapping] = []
        for mapping_raw in supporting_raw:
            mapping_ids = mapping_raw.get("requirement_ids") if isinstance(mapping_raw, dict) else None
            if (
                not isinstance(mapping_raw, dict)
                or not isinstance(mapping_raw.get("pattern"), str) or not mapping_raw["pattern"]
                or mapping_raw.get("coverage_kind") not in {"documentation", "process"}
                or not isinstance(mapping_raw.get("reason"), str) or not mapping_raw["reason"].strip()
                or not isinstance(mapping_ids, list) or not mapping_ids
                or not all(isinstance(item, str) for item in mapping_ids)
                or len(mapping_ids) != len(set(mapping_ids))
            ):
                findings.append(Finding("failure", "invalid_supporting_mapping", f"{capability_id} needs a valid supporting pattern, coverage kind, exact requirement IDs and reason.", _relative(path, root)))
                continue
            _validate_project_pattern(mapping_raw["pattern"], "supporting mapping")
            supporting.append(SupportingMapping(mapping_raw["pattern"], tuple(mapping_ids), mapping_raw["coverage_kind"], mapping_raw["reason"].strip()))
        capability = Capability(capability_id, name.strip(), status, tuple(mapped), tuple(mappings), tuple(supporting))
        capabilities.append(capability)
        for requirement_id in capability.requirement_ids:
            if requirement_id not in requirement_ids:
                findings.append(Finding("failure", "invalid_capability_map", f"{capability.id} references unknown requirement {requirement_id}.", _relative(path, root), requirement_id=requirement_id))
    digest_source = {"file_classifications": classifications_raw, "capabilities": capabilities_raw}
    digest = _canonical_json_digest(digest_source)
    approval = raw.get("approval")
    if not isinstance(approval, dict) or not approval:
        findings.append(Finding("failure", "missing_capability_approval", f"Capability inventory lacks human approval for {digest}.", _relative(path, root)))
    elif approval.get("inventory_digest") != digest:
        findings.append(Finding("failure", "stale_capability_approval", f"Capability inventory approval does not match current digest {digest}.", _relative(path, root)))
    elif (
        isinstance(approval.get("approved_by"), str)
        and HUMAN_ID_RE.fullmatch(approval["approved_by"])
        and (policy is None or approval["approved_by"] not in policy.authorized_approvers)
    ):
        findings.append(Finding("failure", "unauthorized_approver", "Capability inventory approval identity is outside the approval policy.", _relative(path, root)))
    elif not _valid_human_approval(approval, "inventory_digest", digest, policy):
        findings.append(Finding("failure", "missing_capability_approval", "Capability inventory approval is not a valid authorized human approval record.", _relative(path, root)))
    capability_map = CapabilityMap(tuple(classifications), tuple(capabilities), digest)
    active = [item for item in capabilities if item.status == "active"]
    mapped_normative = {requirement_id for item in active for requirement_id in item.requirement_ids}
    for requirement_id in sorted(normative_ids - mapped_normative):
        findings.append(Finding("failure", "unmapped_requirement", f"Normative requirement {requirement_id} is not assigned to an active capability.", _relative(path, root), requirement_id=requirement_id))
    normal_files, ignored_files = _project_files_by_state(root)
    all_files = normal_files | ignored_files
    implementation_files: set[str] = set()
    categories_by_path: dict[str, str] = {}
    ignored_implementation_files: set[str] = set()
    nonimplementation_files: set[str] = set()
    for project_file in sorted(all_files):
        matched = [item for item in classifications if fnmatch.fnmatch(project_file, item.pattern)]
        if not matched:
            findings.append(Finding("failure", "unclassified_project_file", f"Project file has no approved classification: {project_file}", project_file))
            continue
        categories = {item.category for item in matched}
        if len(categories) != 1:
            findings.append(Finding("failure", "ambiguous_file_classification", f"Project file matches conflicting classifications {sorted(categories)}: {project_file}", project_file))
            continue
        category = next(iter(categories))
        categories_by_path[project_file] = category
        if category == "local" and project_file not in ignored_files:
            findings.append(Finding("failure", "invalid_local_classification", f"Local file must be ignored so its contents stay outside assurance scope: {project_file}", project_file))
        elif project_file in ignored_files and category != "local":
            findings.append(Finding("failure", "invalid_local_classification", f"Ignored file must use the local classification: {project_file}", project_file))
        if category == "implementation":
            if project_file in ignored_files:
                ignored_implementation_files.add(project_file)
            else:
                implementation_files.add(project_file)
        else:
            nonimplementation_files.add(project_file)
    for ignored_path in sorted(ignored_implementation_files):
        findings.append(Finding("failure", "ignored_implementation_file", f"Ignored implementation file cannot be governed by a committed inventory: {ignored_path}", ignored_path))

    traceability = implementation_traceability(capability_map, implementation_files)
    for implementation_path, mapped_ids in traceability.items():
        if not mapped_ids:
            findings.append(Finding("failure", "unmapped_implementation", f"Implementation file has no explicit active requirement mapping: {implementation_path}", implementation_path))

    for capability in active:
        if not capability.requirement_ids:
            findings.append(Finding("failure", "invalid_capability_map", f"Active capability {capability.id} has no requirement IDs.", _relative(path, root)))
        mapping_ids = {
            requirement_id
            for mapping in (*capability.implementation_mappings, *capability.supporting_mappings)
            for requirement_id in mapping.requirement_ids
        }
        if set(capability.requirement_ids) != mapping_ids:
            findings.append(Finding("failure", "invalid_implementation_mapping", f"{capability.id} requirement IDs must exactly equal the IDs used by its implementation and supporting mappings.", _relative(path, root)))
        for mapping in capability.implementation_mappings:
            if any(requirement_id not in capability.requirement_ids for requirement_id in mapping.requirement_ids):
                findings.append(Finding("failure", "invalid_implementation_mapping", f"{capability.id} mapping {mapping.pattern!r} references an undeclared requirement.", _relative(path, root)))
            if any(requirement_id not in normative_ids for requirement_id in mapping.requirement_ids):
                findings.append(Finding("failure", "invalid_implementation_mapping", f"Active capability {capability.id} mappings must reference normative requirements.", _relative(path, root)))
            if any(coverage_kinds.get(requirement_id) != "implementation_required" for requirement_id in mapping.requirement_ids):
                findings.append(Finding("failure", "invalid_implementation_mapping", f"{capability.id} runtime mappings must reference implementation-required requirements.", _relative(path, root)))
            matched_implementation = {
                item for item in implementation_files if fnmatch.fnmatch(item, mapping.pattern)
            }
            if not matched_implementation:
                findings.append(Finding("failure", "invalid_implementation_mapping", f"{capability.id} mapping matches no implementation file: {mapping.pattern}", _relative(path, root)))
            if any(fnmatch.fnmatch(item, mapping.pattern) for item in nonimplementation_files):
                findings.append(Finding("failure", "invalid_implementation_mapping", f"{capability.id} mapping also matches a non-implementation file: {mapping.pattern}", _relative(path, root)))
        for mapping in capability.supporting_mappings:
            if any(requirement_id not in normative_ids or requirement_id not in capability.requirement_ids
                   or coverage_kinds.get(requirement_id) != mapping.coverage_kind
                   for requirement_id in mapping.requirement_ids):
                findings.append(Finding("failure", "invalid_supporting_mapping", f"{capability.id} supporting mapping must reference declared normative requirements with matching coverage kind.", _relative(path, root)))
            matching = {item for item in all_files if fnmatch.fnmatch(item, mapping.pattern)}
            allowed = {"documentation"} if mapping.coverage_kind == "documentation" else {"documentation", "tooling", "configuration"}
            invalid = {item for item in matching if item in ignored_files
                       or categories_by_path.get(item) not in allowed
                       or not _supporting_file_is_regular(root, item)
                       or _assurance_control_path(item, config)}
            if not matching or invalid:
                findings.append(Finding("failure", "invalid_supporting_mapping", f"{capability.id} supporting mapping has no valid realization or includes forbidden paths: {mapping.pattern}; invalid={sorted(invalid)}", _relative(path, root)))
            valid = matching - invalid
            if mapping.coverage_kind == "process" and not any(categories_by_path.get(item) in {"tooling", "configuration"} for item in valid):
                # A process needs executable/configured realization, not prose alone.
                process_ids = set(mapping.requirement_ids)
                realized = all(any(
                    requirement_id in other.requirement_ids
                    and any(fnmatch.fnmatch(item, other.pattern) and item not in ignored_files
                            and categories_by_path.get(item) in {"tooling", "configuration"}
                            and _supporting_file_is_regular(root, item)
                            and not _assurance_control_path(item, config) for item in all_files)
                    for other in capability.supporting_mappings if other.coverage_kind == "process"
                ) for requirement_id in process_ids)
                if not realized:
                    findings.append(Finding("failure", "invalid_supporting_mapping", f"{capability.id} process requirement lacks tooling or configuration realization.", _relative(path, root)))
    for requirement in requirements:
        if requirement.lifecycle_status != "Normative" or requirement.coverage_kind != "implementation_required":
            continue
        if not any(requirement.id in ids for ids in traceability.values()):
            findings.append(Finding("failure", "unmapped_requirement", f"{requirement.id} requires runtime implementation coverage, not supporting documentation alone.", _relative(path, root), requirement_id=requirement.id))
    capability_map.supporting_files = tuple(sorted({
        item for item in normal_files
        for capability in active for mapping in capability.supporting_mappings
        if fnmatch.fnmatch(item, mapping.pattern)
        and categories_by_path.get(item) in ({"documentation"} if mapping.coverage_kind == "documentation" else {"documentation", "tooling", "configuration"})
        and not _assurance_control_path(item, config)
        and _supporting_file_is_regular(root, item)
    }))
    return capability_map, findings, implementation_files


def requirement_implementation_digest(
    root: Path,
    requirement_id: str,
    capability_map: CapabilityMap | None,
    implementation_files: set[str],
) -> str:
    if capability_map is None:
        return _canonical_json_digest([])
    traceability = implementation_traceability(capability_map, implementation_files)
    entries: list[dict[str, str]] = []
    for path in sorted(
        item for item, mapped_ids in traceability.items() if requirement_id in mapped_ids
    ):
        entries.append({"path": path, "digest": _sha256_bytes(_project_file_bytes(root, path, "implementation path"))})
    return _canonical_json_digest(entries)


def requirement_supporting_digest(root: Path, requirement_id: str, capability_map: CapabilityMap | None) -> str:
    if capability_map is None:
        return _canonical_json_digest([])
    paths = supporting_traceability(capability_map)
    return _canonical_json_digest([
        {
            "path": path,
            "digest": (_sha256_bytes(_project_file_bytes(root, path, "supporting path"))
                       if (root / path).is_file() else "deleted"),
            "mappings": sorted({
                (mapping.pattern, mapping.coverage_kind, mapping.reason)
                for capability in capability_map.capabilities if capability.status == "active"
                for mapping in capability.supporting_mappings
                if requirement_id in mapping.requirement_ids and fnmatch.fnmatch(path, mapping.pattern)
            }),
        }
        for path, ids in sorted(paths.items()) if requirement_id in ids
    ])


def _receipt_tokens(value: str) -> tuple[str, ...]:
    return tuple(token_value for kind, token_value in _structured_evidence_tokens(value) if kind == "receipt" and token_value)


def _aggregate_evidence_state(states: list[str]) -> str:
    if not states:
        return "not-run"
    for state in ("failed", "stale", "blocked", "not-run", "available"):
        if state in states:
            return state
    return "passed" if all(state in {"passed", "not-applicable"} for state in states) else "failed"


def _expected_implementation_status(kinds: list[str], states: list[str]) -> str:
    aggregate = _aggregate_evidence_state(states)
    if aggregate == "passed":
        return "Passing"
    if any(state in {"failed", "stale"} for state in states):
        return "Needs review"
    if aggregate == "blocked":
        return "Blocked"
    # A receipt that has not run establishes neither implementation presence nor
    # validation maturity.  Reserve the "Implemented" states for requirements
    # with at least one passing test or source-review receipt.
    implementation_evidence = any(
        kind in {"test", "source", "review"} and state == "passed"
        for kind, state in zip(kinds, states)
    )
    if not implementation_evidence:
        return "Needs review"
    pending_kinds = [kind for kind, state in zip(kinds, states) if state not in {"passed", "not-applicable"}]
    if "provider" in pending_kinds:
        return "Needs provider testing"
    if any(kind in {"manual", "hardware"} for kind in pending_kinds):
        return "Implemented, not field-tested"
    return "Implemented, evidence pending"


def _is_index_tracked(root: Path, path: str) -> bool:
    """Return whether a path is present in the current Git index."""
    result = subprocess.run(
        ["git", "ls-files", "--error-unmatch", "--", path],
        cwd=root,
        check=False,
        capture_output=True,
    )
    return result.returncode == 0


def load_and_check_receipts(
    root: Path,
    config: CheckerConfig,
    requirements: list[Requirement],
    rows: list[MatrixRow],
    capability_map: CapabilityMap | None,
    implementation_files: set[str],
    policy: ApprovalPolicy | None,
) -> tuple[dict[str, EvidenceReceipt], list[Finding]]:
    if not config.require_evidence_receipts:
        return {}, []
    path = root / config.evidence_receipts
    findings: list[Finding] = []
    receipts: dict[str, EvidenceReceipt] = {}
    if not path.is_file():
        findings.append(Finding("failure", "missing_evidence_receipts", "Evidence receipt ledger is missing.", _relative(path, root)))
    else:
        raw = _read_json_object(path)
        if raw.get("schema_version") != 2 or not isinstance(raw.get("receipts"), list):
            findings.append(Finding("failure", "missing_evidence_receipts", "Evidence receipt ledger schema is invalid.", _relative(path, root)))
        else:
            for entry in raw["receipts"]:
                if not isinstance(entry, dict) or not isinstance(entry.get("id"), str) or not entry["id"] or entry["id"] in receipts:
                    findings.append(Finding("failure", "invalid_evidence_receipt", "Evidence receipts need unique non-empty IDs.", _relative(path, root)))
                    continue
                receipt = EvidenceReceipt(
                    id=entry["id"],
                    requirement_id=str(entry.get("requirement_id", "")),
                    requirement_digest=str(entry.get("requirement_digest", "")),
                    implementation_digest=str(entry.get("implementation_digest", "")),
                    kind=str(entry.get("kind", "")),
                    state=str(entry.get("state", "")),
                    target=str(entry.get("target", "")),
                    verified_at=str(entry.get("verified_at", "")),
                    verified_commit=str(entry.get("verified_commit", "")),
                    runner=str(entry.get("runner", "")),
                    artifact_ref=str(entry.get("artifact_ref", "")),
                    artifact_digest=str(entry.get("artifact_digest", "")),
                    supporting_digest=str(entry.get("supporting_digest", "")),
                    valid_until=entry.get("valid_until") if isinstance(entry.get("valid_until"), str) else None,
                    reason=str(entry.get("reason", "")),
                    approved_by=str(entry.get("approved_by", "")),
                    decision_ref=str(entry.get("decision_ref", "")),
                )
                receipts[receipt.id] = receipt
    requirements_by_id = {item.id: item for item in requirements}
    rows_by_id = {item.requirement_id: item for item in rows}
    valid_states = {"available", "passed", "failed", "blocked", "not-run", "not-applicable"}
    valid_kinds = {"test", "source", "task", "review", "manual", "hardware", "provider"}
    for requirement in requirements:
        if requirement.lifecycle_status != "Normative":
            row = rows_by_id.get(requirement.id)
            if row is not None:
                row.computed_evidence_state = row.evidence_state or "not-applicable"
                row.computed_implementation_status = row.implementation_status
            continue
        if not requirement.evidence_obligations:
            findings.append(Finding("failure", "missing_evidence_obligations", f"{requirement.id} has no Evidence obligations line.", requirement.path, requirement.line, requirement.id))
        elif len(set(requirement.evidence_obligations)) != len(requirement.evidence_obligations) or any(MANUAL_ID_RE.fullmatch(item.lower()) is None for item in requirement.evidence_obligations):
            findings.append(Finding("failure", "invalid_evidence_obligations", f"{requirement.id} evidence obligation IDs must be unique stable identifiers.", requirement.path, requirement.line, requirement.id))
        row = rows_by_id.get(requirement.id)
        if row is None:
            continue
        receipt_ids = _receipt_tokens(row.evidence)
        if row.implementation_status == "Passing" and not receipt_ids:
            findings.append(Finding("failure", "passing_without_evidence", f"{requirement.id} claims Passing without receipt evidence.", row.path, row.line, requirement.id))
        if len(receipt_ids) != len(set(receipt_ids)):
            findings.append(Finding("failure", "matrix_evidence_mismatch", f"{requirement.id} matrix contains duplicate receipt evidence.", row.path, row.line, requirement.id))
        if set(receipt_ids) != set(requirement.evidence_obligations):
            findings.append(Finding("failure", "matrix_evidence_mismatch", f"{requirement.id} matrix receipts do not exactly match its evidence obligations.", row.path, row.line, requirement.id))
        current_states: list[str] = []
        current_kinds: list[str] = []
        current_verified_at: list[str] = []
        expected_implementation = requirement_implementation_digest(root, requirement.id, capability_map, implementation_files)
        expected_supporting = requirement_supporting_digest(root, requirement.id, capability_map)
        for receipt_id in sorted(set(requirement.evidence_obligations) | set(receipt_ids)):
            receipt = receipts.get(receipt_id)
            if receipt is None:
                findings.append(Finding("failure", "missing_evidence_receipt", f"Missing evidence receipt {receipt_id} for {requirement.id}.", row.path, row.line, requirement.id))
                current_states.append("not-run")
                current_kinds.append("")
                continue
            state = receipt.state
            if receipt.requirement_id != requirement.id:
                findings.append(Finding("failure", "invalid_evidence_receipt", f"Receipt {receipt.id} is assigned to the wrong requirement.", _relative(path, root), requirement_id=requirement.id))
                state = "failed"
            if receipt.requirement_digest != requirement.digest:
                findings.append(Finding("failure", "stale_evidence_requirement", f"Receipt {receipt.id} does not match current requirement digest.", _relative(path, root), requirement_id=requirement.id))
                state = "stale"
            if receipt.implementation_digest != expected_implementation:
                findings.append(Finding("failure", "stale_evidence_implementation", f"Receipt {receipt.id} does not match current implementation digest.", _relative(path, root), requirement_id=requirement.id))
                state = "stale"
            if receipt.supporting_digest != expected_supporting:
                findings.append(Finding("failure", "stale_evidence_supporting", f"Receipt {receipt.id} does not match current supporting realization digest.", _relative(path, root), requirement_id=requirement.id))
                state = "stale"
            if receipt.state not in valid_states or not _valid_timestamp(receipt.verified_at):
                findings.append(Finding("failure", "invalid_evidence_receipt", f"Receipt {receipt.id} has invalid state or timestamp.", _relative(path, root), requirement_id=requirement.id))
                state = "failed"
            if receipt.kind not in valid_kinds:
                findings.append(Finding("failure", "invalid_evidence_receipt", f"Receipt {receipt.id} has unknown kind {receipt.kind!r}.", _relative(path, root), requirement_id=requirement.id))
                state = "failed"
            if receipt.valid_until and (not _valid_date(receipt.valid_until) or receipt.valid_until < date.today().isoformat()):
                findings.append(Finding("failure", "expired_evidence_receipt", f"Receipt {receipt.id} is expired or has an invalid valid_until date.", _relative(path, root), requirement_id=requirement.id))
                state = "stale"
            if receipt.state == "passed" and (not receipt.target or not receipt.verified_commit or not receipt.runner or not receipt.artifact_ref or not receipt.artifact_digest):
                findings.append(Finding("failure", "invalid_evidence_receipt", f"Passed receipt {receipt.id} lacks target, commit, runner, artifact reference or artifact digest.", _relative(path, root), requirement_id=requirement.id))
                state = "failed"
            if receipt.state == "passed" and (
                policy is None
                or receipt.runner not in policy.authorized_evidence_issuers
            ):
                findings.append(Finding("failure", "unauthorized_evidence_issuer", f"Passed receipt {receipt.id} was not issued by a trusted evidence identity.", _relative(path, root), requirement_id=requirement.id))
                state = "failed"
            if receipt.state == "passed" and receipt.artifact_ref:
                artifact_kind, separator, artifact_value = receipt.artifact_ref.partition(":")
                if artifact_kind != "artifact" or not separator or not artifact_value:
                    findings.append(Finding("failure", "missing_evidence_artifact", f"Passed receipt {receipt.id} must reference a committed artifact:<path>.", _relative(path, root), requirement_id=requirement.id))
                    state = "failed"
                elif not _matches(artifact_value, config.evidence_artifact_paths):
                    findings.append(Finding("failure", "missing_evidence_artifact", f"Receipt {receipt.id} artifact is outside configured evidence artifact paths: {artifact_value}", _relative(path, root), requirement_id=requirement.id))
                    state = "failed"
                else:
                    if not _is_index_tracked(root, artifact_value):
                        findings.append(Finding("failure", "missing_evidence_artifact", f"Receipt {receipt.id} artifact must be Git-index-tracked: {artifact_value}", _relative(path, root), requirement_id=requirement.id))
                        state = "failed"
                    else:
                        try:
                            artifact_bytes = _project_file_bytes(root, artifact_value, "receipt artifact")
                        except (ConfigurationError, FileNotFoundError):
                            findings.append(Finding("failure", "missing_evidence_artifact", f"Receipt {receipt.id} artifact does not exist inside the project: {artifact_value}", _relative(path, root), requirement_id=requirement.id))
                            state = "failed"
                            artifact_bytes = None
                        if artifact_bytes is not None and (not SHA256_RE.fullmatch(receipt.artifact_digest) or receipt.artifact_digest != _sha256_bytes(artifact_bytes)):
                            findings.append(Finding("failure", "stale_evidence_artifact", f"Receipt {receipt.id} artifact digest does not match {artifact_value}.", _relative(path, root), requirement_id=requirement.id))
                            state = "stale"
            if receipt.kind in {"test", "source"} and receipt.target:
                raw_path, separator, symbol = receipt.target.partition("#")
                try:
                    target_path = _project_path(root, raw_path, "receipt target")
                except ConfigurationError:
                    findings.append(Finding("failure", "invalid_evidence_path", f"Receipt {receipt.id} target escapes the project: {raw_path}", _relative(path, root), requirement_id=requirement.id))
                    state = "failed"
                    target_path = None
                if target_path is None:
                    pass
                elif not target_path.is_file():
                    findings.append(Finding("failure", "missing_evidence_path", f"Receipt {receipt.id} target does not exist: {raw_path}", _relative(path, root), requirement_id=requirement.id))
                    state = "failed"
                elif separator and symbol:
                    try:
                        target_source = target_path.read_text(encoding="utf-8")
                    except UnicodeDecodeError:
                        target_source = ""
                    if symbol not in target_source:
                        findings.append(Finding("failure", "missing_evidence_symbol", f"Receipt {receipt.id} target symbol {symbol!r} is absent from {raw_path}.", _relative(path, root), requirement_id=requirement.id))
                        state = "failed"
            if receipt.kind in {"review", "manual", "hardware", "provider"} and receipt.state == "passed" and not receipt.valid_until:
                findings.append(Finding("failure", "missing_evidence_expiry", f"Passed {receipt.kind} receipt {receipt.id} requires valid_until.", _relative(path, root), requirement_id=requirement.id))
                state = "failed"
            if receipt.state == "not-applicable" and (
                not HUMAN_ID_RE.fullmatch(receipt.approved_by)
                or policy is None
                or receipt.approved_by not in policy.authorized_approvers
                or not receipt.decision_ref
                or not receipt.reason
            ):
                findings.append(Finding("failure", "invalid_evidence_receipt", f"Not-applicable receipt {receipt.id} lacks human-approved justification.", _relative(path, root), requirement_id=requirement.id))
                state = "failed"
            current_states.append(state)
            current_kinds.append(receipt.kind)
            if _valid_timestamp(receipt.verified_at):
                current_verified_at.append(receipt.verified_at)
        aggregate = _aggregate_evidence_state(current_states)
        expected_status = _expected_implementation_status(current_kinds, current_states)
        row.computed_evidence_state = aggregate
        row.computed_implementation_status = expected_status
        if row.evidence_state != aggregate:
            findings.append(Finding("failure", "stale_matrix_evidence_state", f"{requirement.id} matrix evidence state {row.evidence_state!r} should be {aggregate!r}.", row.path, row.line, requirement.id))
        expected_verified_at = max(current_verified_at, default="")
        if row.verified_at != expected_verified_at:
            findings.append(Finding("failure", "stale_matrix_verified_at", f"{requirement.id} matrix verified-at value is stale.", row.path, row.line, requirement.id))
        if row.implementation_status == "Passing" and aggregate != "passed":
            findings.append(Finding("failure", "passing_with_nonpassing_evidence", f"{requirement.id} claims Passing while evidence state is {aggregate}.", row.path, row.line, requirement.id))
        if row.implementation_status != expected_status:
            findings.append(Finding("failure", "stale_implementation_status", f"{requirement.id} matrix status {row.implementation_status!r} should be {expected_status!r} from current evidence.", row.path, row.line, requirement.id))
    for receipt in receipts.values():
        if receipt.requirement_id not in requirements_by_id:
            findings.append(Finding("warning", "orphaned_evidence_receipt", f"Receipt {receipt.id} references unknown requirement {receipt.requirement_id}.", _relative(path, root), requirement_id=receipt.requirement_id))
    return receipts, findings


def run_checks(
    root: Path,
    config: CheckerConfig,
    authorization_policy: ApprovalPolicy | None = None,
) -> tuple[list[Requirement], list[Finding], list[MatrixRow]]:
    root = root.resolve()
    spec_dir = _project_path(root, config.spec_dir, "spec_dir")
    matrix_path = _project_path(root, config.matrix, "matrix")
    if not spec_dir.exists():
        raise ConfigurationError(f"Specification directory does not exist: {spec_dir}")
    _, ignored_paths = _project_files_by_state(root)
    for label in (
        "matrix",
        "approval_policy",
        "approvals",
        "capabilities",
        "evidence_receipts",
        "semantic_review",
    ):
        configured_path = getattr(config, label).replace("\\", "/")
        if configured_path in ignored_paths:
            raise ConfigurationError(
                f"{label} is ignored local content and cannot be read as an assurance record: {configured_path}"
            )
    requirements = parse_requirements(spec_dir, root, config)
    rows, findings = parse_matrix(matrix_path, root, config)
    findings.extend(check_evidence(rows, root, config))
    candidate_policy, policy_findings = load_and_check_approval_policy(root, config)
    findings.extend(policy_findings)
    policy = authorization_policy or candidate_policy
    if (
        config.enforcement_mode == "migration"
        and policy is not None
        and config.migration_approved_by not in policy.authorized_approvers
    ):
        findings.append(
            Finding(
                "failure",
                "unauthorized_approver",
                "Migration mode was not approved by an identity in the approval policy.",
                config.approval_policy,
            )
        )

    if not requirements:
        findings.append(
            Finding(
                "failure",
                "empty_requirement_inventory",
                "No canonical requirements were parsed; an empty inventory is never compliant.",
                _relative(spec_dir, root),
            )
        )
    for path in iter_markdown_files(spec_dir, config.ignored_spec_files):
        if _relative(path, root) in ignored_paths:
            continue
        if path.is_symlink():
            raise ConfigurationError(
                f"Canonical requirement files must not be symbolic links: {_relative(path, root)}"
            )
        text = path.read_text(encoding="utf-8")
        parsed_lines = {item.line for item in requirements if item.path == _relative(path, root)}
        for line_no, line in enumerate(text.splitlines(), start=1):
            if re.search(REQ_ID_PATTERN, line) and line.lstrip().startswith("#") and line_no not in parsed_lines:
                findings.append(Finding("failure", "unparsed_requirement_heading", "Requirement-like heading does not use '### ID: title' format.", _relative(path, root), line_no))

    by_id: dict[str, list[Requirement]] = {}
    for requirement in requirements:
        by_id.setdefault(requirement.id, []).append(requirement)
        if requirement.coverage_kind not in COVERAGE_KINDS:
            findings.append(Finding("failure", "invalid_coverage_kind", f"{requirement.id} has unsupported coverage kind {requirement.coverage_kind!r}.", requirement.path, requirement.line, requirement.id))
        if requirement.lifecycle_status is None:
            findings.append(Finding("failure", "missing_status", f"{requirement.id} has no Status line.", requirement.path, requirement.line, requirement.id))
        elif requirement.lifecycle_status not in config.requirement_statuses:
            findings.append(Finding("failure", "unknown_requirement_status", f"{requirement.id} has unconfigured lifecycle status {requirement.lifecycle_status!r}.", requirement.path, requirement.line, requirement.id))
        if requirement.lifecycle_status == "Normative" and not requirement.has_verification:
            findings.append(Finding("failure", "missing_verification", f"{requirement.id} is Normative but has no Verification section.", requirement.path, requirement.line, requirement.id))
        elif requirement.lifecycle_status == "Normative" and not requirement.verification_text:
            findings.append(Finding("failure", "empty_verification", f"{requirement.id} has an empty Verification section.", requirement.path, requirement.line, requirement.id))
        if config.require_provenance and requirement.lifecycle_status == "Normative" and not requirement.has_provenance:
            findings.append(Finding("failure", "missing_provenance", f"{requirement.id} is Normative but has no Provenance section.", requirement.path, requirement.line, requirement.id))
        elif config.require_provenance and requirement.lifecycle_status == "Normative" and not requirement.provenance_text:
            findings.append(Finding("failure", "empty_provenance", f"{requirement.id} has an empty Provenance section.", requirement.path, requirement.line, requirement.id))
        if requirement.lifecycle_status == "Normative":
            requirement_source = (root / requirement.path).read_text(encoding="utf-8")
            matches = list(REQ_HEADING_RE.finditer(requirement_source))
            source_match = next((match for match in matches if line_number(requirement_source, match.start()) == requirement.line), None)
            if source_match is not None:
                index = matches.index(source_match)
                end = matches[index + 1].start() if index + 1 < len(matches) else len(requirement_source)
                if not NORMATIVE_KEYWORD_RE.search(requirement_source[source_match.start():end]):
                    findings.append(Finding("failure", "missing_normative_keyword", f"{requirement.id} has no MUST/SHOULD/MAY statement.", requirement.path, requirement.line, requirement.id))

    for requirement_id, duplicates in by_id.items():
        if len(duplicates) > 1:
            locations = ", ".join(f"{item.path}:{item.line}" for item in duplicates)
            findings.append(Finding("failure", "duplicate_requirement_id", f"{requirement_id} appears multiple times: {locations}", requirement_id=requirement_id))

    for requirement in requirements:
        if requirement.lifecycle_status != "Deprecated":
            continue
        target = requirement.superseded_by.strip().strip("`")
        target_match = REQ_ID_FULL_RE.fullmatch(target)
        target_is_valid = False
        if target_match is not None:
            target_id = target_match.group(1)
            successors = by_id.get(target_id, [])
            target_is_valid = (
                target_id != requirement.id
                and len(successors) == 1
                and successors[0].lifecycle_status == "Normative"
            )
        elif target.startswith("decision:"):
            target_is_valid = bool(target.removeprefix("decision:").strip())
        if not requirement.provenance_text.strip() or not target_is_valid:
            findings.append(
                Finding(
                    "failure",
                    "invalid_requirement_retirement",
                    f"Deprecated requirement {requirement.id} needs Provenance and Superseded by set to a distinct existing Normative requirement ID or decision:<durable-ref>.",
                    requirement.path,
                    requirement.line,
                    requirement.id,
                )
            )

    matrix_counts: dict[str, int] = {}
    for row in rows:
        matrix_counts[row.requirement_id] = matrix_counts.get(row.requirement_id, 0) + 1
        if row.requirement_id not in by_id:
            findings.append(Finding("failure", "unknown_matrix_requirement", f"Matrix references unknown requirement {row.requirement_id}.", row.path, row.line, row.requirement_id))
        elif any(item.lifecycle_status != "Normative" for item in by_id[row.requirement_id]):
            findings.append(Finding("failure", "non_normative_matrix_row", f"{row.requirement_id} is non-Normative and must not have a matrix status row.", row.path, row.line, row.requirement_id))
    for requirement_id, count in matrix_counts.items():
        if count > 1:
            findings.append(Finding("failure", "duplicate_matrix_row", f"Matrix contains {count} rows for {requirement_id}.", _relative(matrix_path, root), requirement_id=requirement_id))
    matrix_ids = set(matrix_counts)
    for requirement in requirements:
        if requirement.lifecycle_status == "Normative" and requirement.id not in matrix_ids:
            findings.append(Finding("failure", "missing_matrix_row", f"{requirement.id} is Normative but has no matrix row.", requirement.path, requirement.line, requirement.id))
    findings.extend(check_requirement_approvals(root, config, requirements, policy))
    capability_map, capability_findings, implementation_files = load_and_check_capabilities(
        root, config, requirements, policy
    )
    findings.extend(capability_findings)
    _, receipt_findings = load_and_check_receipts(
        root,
        config,
        requirements,
        rows,
        capability_map,
        implementation_files,
        policy,
    )
    findings.extend(receipt_findings)
    return requirements, findings, rows


def load_baseline(path: Path | None) -> dict[str, BaselineEntry]:
    if path is None or not path.is_file():
        return {}
    if path.is_symlink():
        raise ConfigurationError(f"Baseline must not be a symbolic link: {path}")
    raw = json.loads(path.read_text(encoding="utf-8"))
    if (
        not isinstance(raw, dict)
        or raw.get("schema_version") != SCHEMA_VERSION
        or not isinstance(raw.get("findings"), list)
    ):
        raise ConfigurationError(
            f"Baseline must use schema_version {SCHEMA_VERSION} and contain a findings list: {path}"
        )
    entries = raw["findings"]
    result: dict[str, BaselineEntry] = {}
    for item in entries:
        if isinstance(item, dict):
            try:
                entry = BaselineEntry(**item)
            except TypeError as error:
                raise ConfigurationError(f"Invalid baseline entry in {path}: {error}") from error
            if entry.fingerprint in result:
                raise ConfigurationError(
                    f"Duplicate baseline fingerprint {entry.fingerprint} in {path}."
                )
            result[entry.fingerprint] = entry
    return result


def normalize_finding_path(path: str | None, root: Path | None = None) -> str | None:
    if path is None:
        return None
    candidate = Path(path)
    if root and candidate.is_absolute():
        return _relative(candidate, root)
    return path.replace("\\", "/")


def classify_findings(
    findings: list[Finding],
    mode: str,
    config: CheckerConfig,
    baseline: dict[str, BaselineEntry],
    changed_paths: set[str],
    root: Path | None = None,
    changed_requirement_ids: set[str] | None = None,
    policy: ApprovalPolicy | None = None,
) -> list[Finding]:
    normalized_changes = {path.replace("\\", "/") for path in changed_paths}
    spec_changed = any(path.startswith(config.spec_dir.rstrip("/") + "/") for path in normalized_changes)
    changed_ids = changed_requirement_ids or set()
    for finding in findings:
        entry = baseline.get(finding.fingerprint)
        finding.baseline_accepted = bool(
            entry and entry.is_active(policy) and finding.code not in NON_BASELINABLE_CODES
        )
        code_set = config.full_blocking_codes if mode == "full" else config.blocking_codes
        potentially_blocking = finding.severity == "failure" and (
            config.enforcement_mode == "strict" or finding.code in code_set
        )
        if mode == "full":
            finding.blocking = potentially_blocking
            continue
        normalized_path = normalize_finding_path(finding.path, root)
        related = config.enforcement_mode == "strict" or (
            (normalized_path in normalized_changes if normalized_path else spec_changed)
            or bool(finding.requirement_id and finding.requirement_id in changed_ids)
            or (finding.code in {"missing_semantic_review", "stale_semantic_review", "failed_semantic_review", "incomplete_semantic_review"} and bool(normalized_changes))
        )
        finding.blocking = potentially_blocking and related and not finding.baseline_accepted
    return findings


def git_changed_paths(root: Path, base: str) -> set[str]:
    command = ["git", "diff", "--no-renames", "--name-only", "-z", base, "--"]
    result = subprocess.run(command, cwd=root, check=False, capture_output=True)
    if result.returncode != 0:
        raise ConfigurationError(result.stderr.decode("utf-8", "replace").strip() or f"Could not compare current tree with {base}.")
    changed = {
        item.decode("utf-8", "surrogateescape")
        for item in result.stdout.split(b"\0")
        if item
    }
    changed.update(_git_paths(root, ["--others", "--exclude-standard"], "untracked"))
    return changed


def changed_requirement_ids(
    root: Path,
    base: str | None,
    requirements: list[Requirement],
    changed_paths: set[str],
    matrix_path: str,
) -> list[str]:
    result = {requirement.id for requirement in requirements if requirement.path in changed_paths}
    normalized_matrix = matrix_path.replace("\\", "/")
    spec_directory = str(Path(normalized_matrix).parent).replace("\\", "/")
    if base and any(path == normalized_matrix or path.startswith(spec_directory + "/") for path in changed_paths):
        diff = subprocess.run(
            ["git", "diff", "--unified=0", base, "--", spec_directory],
            cwd=root,
            check=False,
            capture_output=True,
            text=True,
        )
        if diff.returncode != 0:
            raise ConfigurationError(diff.stderr.strip() or f"Could not inspect changes to {normalized_matrix}.")
        for line in diff.stdout.splitlines():
            if not line.startswith(("+", "-")) or line.startswith(("+++", "---")):
                continue
            result.update(re.findall(REQ_ID_PATTERN, line))
    return sorted(result)


def _git_show_bytes(root: Path, revision: str, path: str) -> bytes | None:
    """Read one Git object without consulting the working-tree path."""
    result = subprocess.run(
        ["git", "show", f"{revision}:{path}"],
        cwd=root,
        check=False,
        capture_output=True,
    )
    if result.returncode == 0:
        return result.stdout
    return None


def base_requirements(root: Path, base: str, config: CheckerConfig) -> list[Requirement]:
    """Parse canonical requirements directly from a base revision."""
    result = subprocess.run(
        ["git", "ls-tree", "-r", "-z", "--name-only", base, "--", config.spec_dir],
        cwd=root,
        check=False,
        capture_output=True,
    )
    if result.returncode != 0:
        raise ConfigurationError(result.stderr.decode("utf-8", "replace").strip() or f"Could not inspect requirements at {base}.")
    requirements: list[Requirement] = []
    for raw_path in result.stdout.split(b"\0"):
        if not raw_path:
            continue
        path = raw_path.decode("utf-8", "surrogateescape")
        relative = str(Path(path).relative_to(config.spec_dir)).replace("\\", "/")
        if not path.endswith(".md") or Path(path).name == "verification-matrix.md" or _ignored(relative, config.ignored_spec_files):
            continue
        content = _git_show_bytes(root, base, path)
        if content is None:
            raise ConfigurationError(f"Could not retrieve base requirement file {path}.")
        requirements.extend(_parse_requirements_text(content.decode("utf-8"), path, config))
    return requirements


def deleted_requirement_findings(
    root: Path,
    base: str | None,
    requirements: list[Requirement],
    config: CheckerConfig,
) -> list[Finding]:
    if not base:
        return []
    current = {item.id: item for item in requirements}
    findings: list[Finding] = []
    for requirement in base_requirements(root, base, config):
        if requirement.lifecycle_status != "Normative":
            continue
        candidate = current.get(requirement.id)
        if candidate is None:
            findings.append(Finding("failure", "deleted_requirement_without_tombstone", f"Requirement {requirement.id} was deleted; retain a Deprecated tombstone with Superseded by and provenance.", config.spec_dir, requirement_id=requirement.id))
        elif candidate.lifecycle_status != "Normative" and (
            candidate.lifecycle_status != "Deprecated"
            or not candidate.superseded_by.strip()
            or not candidate.provenance_text.strip()
        ):
            findings.append(Finding("failure", "invalid_requirement_retirement", f"Formerly Normative requirement {requirement.id} must retain a Deprecated tombstone with non-empty Superseded by and Provenance fields.", candidate.path, candidate.line, candidate.id))
    return findings


def _git_revision(root: Path, revision: str) -> str:
    result = subprocess.run(
        ["git", "rev-parse", revision],
        cwd=root,
        check=False,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip() if result.returncode == 0 else "unversioned"


def _paths_matching(root: Path, files: Iterable[str], patterns: Iterable[str]) -> set[str]:
    return {
        path
        for path in files
        if _matches(path, patterns) and (root / path).is_file()
    }


def _base_traceability_for_deleted_paths(
    root: Path,
    base: str | None,
    config: CheckerConfig,
    deleted_paths: set[str],
    mapping_key: str = "implementation_mappings",
) -> dict[str, tuple[str, ...]]:
    """Retain trusted-base relationships when a realization and its mapping vanish."""
    if not base or not deleted_paths:
        return {}
    content = _git_show_bytes(root, base, config.capabilities)
    if content is None:
        return {}
    try:
        raw = json.loads(content.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return {}
    capabilities = raw.get("capabilities") if isinstance(raw, dict) else None
    if not isinstance(capabilities, list):
        return {}
    result: dict[str, tuple[str, ...]] = {}
    for path in deleted_paths:
        mapped = {
            requirement_id
            for capability in capabilities
            if isinstance(capability, dict) and capability.get("status") == "active"
            for mapping in capability.get(mapping_key, [])
            if isinstance(mapping, dict)
            and isinstance(mapping.get("pattern"), str)
            and fnmatch.fnmatch(path, mapping["pattern"])
            for requirement_id in mapping.get("requirement_ids", [])
            if isinstance(requirement_id, str)
        }
        if mapped:
            result[path] = tuple(sorted(mapped))
    return result


def _base_realization_paths(root: Path, base: str) -> set[str]:
    listed = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", "-z", base],
        cwd=root, capture_output=True, check=False,
    )
    if listed.returncode != 0:
        raise ConfigurationError("Cannot enumerate trusted-base realization paths.")
    return {path.decode("utf-8", "surrogateescape") for path in listed.stdout.split(b"\0") if path}


def build_review_context(
    root: Path,
    config: CheckerConfig,
    mode: str,
    base: str | None,
    requirements: list[Requirement],
    capability_map: CapabilityMap | None,
    implementation_files: set[str],
    changed_paths: set[str],
    changed_ids: Iterable[str],
) -> dict[str, Any]:
    root = root.resolve()
    non_ignored_files, _ = _project_files_by_state(root)
    policy, _ = load_and_check_approval_policy(root, config)
    traceability = implementation_traceability(capability_map, implementation_files)
    supporting = supporting_traceability(capability_map)
    semantic_path = config.semantic_review.replace("\\", "/")
    if mode == "full":
        review_paths = non_ignored_files | (implementation_files & non_ignored_files)
        requirement_ids = {
            item.id
            for item in requirements
            if item.lifecycle_status in {"Normative", "Deprecated"}
        }
    else:
        review_paths = set(changed_paths)
        requirement_ids = set(changed_ids)
        if capability_map is not None:
            active = [item for item in capability_map.capabilities if item.status == "active"]
            for changed_path in changed_paths:
                requirement_ids.update(
                    requirement_id
                    for capability in active
                    for mapping in (*capability.implementation_mappings, *capability.supporting_mappings)
                    if fnmatch.fnmatch(changed_path, mapping.pattern)
                    for requirement_id in mapping.requirement_ids
                )
            if config.capabilities in changed_paths or config.approval_policy in changed_paths:
                requirement_ids.update(
                    item.id
                    for item in requirements
                    if item.lifecycle_status in {"Normative", "Deprecated"}
                )
            if any(_matches(path, config.user_documentation_paths) for path in changed_paths):
                requirement_ids.update(
                    item.id
                    for item in requirements
                    if item.lifecycle_status in {"Normative", "Deprecated"}
                )
            review_paths.update(
                implementation_path
                for implementation_path, mapped_ids in traceability.items()
                if set(mapped_ids) & requirement_ids and implementation_path in non_ignored_files
            )
            review_paths.update(path for path, ids in supporting.items() if set(ids) & requirement_ids)
    review_paths.discard(semantic_path)
    deleted_paths = {
        path for path in review_paths if path not in non_ignored_files
    }
    base_traceability = _base_traceability_for_deleted_paths(
        root, base, config, deleted_paths
    )
    base_supporting = _base_traceability_for_deleted_paths(root, base, config, deleted_paths, "supporting_mappings")
    base_impl_all: dict[str, tuple[str, ...]] = {}
    base_support_all: dict[str, tuple[str, ...]] = {}
    for mapped_ids in (*base_traceability.values(), *base_supporting.values()):
        requirement_ids.update(mapped_ids)
    if mode == "changed" and base:
        # Removing a mapping must not remove its still-present realization from
        # review. Close over both inventories, not just deleted candidate files.
        base_paths = _base_realization_paths(root, base)
        base_impl_all = _base_traceability_for_deleted_paths(root, base, config, base_paths)
        base_support_all = _base_traceability_for_deleted_paths(root, base, config, base_paths, "supporting_mappings")
        for path in changed_paths:
            requirement_ids.update(base_impl_all.get(path, ()))
            requirement_ids.update(base_support_all.get(path, ()))
        for old, selected in ((base_impl_all, base_traceability), (base_support_all, base_supporting)):
            for path, ids in old.items():
                if set(ids) & requirement_ids:
                    review_paths.add(path)
                    selected[path] = ids
        review_paths.update(path for path, ids in traceability.items() if set(ids) & requirement_ids)
        review_paths.update(path for path, ids in supporting.items() if set(ids) & requirement_ids)
    if mode == "changed" and requirement_ids:
        review_paths.update(
            _paths_matching(root, non_ignored_files, config.user_documentation_paths)
        )
    if mode == "changed":
        scoped_requirements = {item.id: item for item in requirements}
        if base:
            for item in base_requirements(root, base, config):
                scoped_requirements.setdefault(item.id, item)
        relationships = (traceability, supporting, base_impl_all, base_support_all)
        while True:
            previous = (frozenset(review_paths), frozenset(requirement_ids))
            for mapping in relationships:
                for path, ids in mapping.items():
                    if path in review_paths:
                        requirement_ids.update(ids)
                    if set(ids) & requirement_ids:
                        review_paths.add(path)
            # Newly selected shared-path requirements may bring further source
            # paths and operator documentation into scope on the next iteration.
            review_paths.update(scoped_requirements[item].path for item in requirement_ids if item in scoped_requirements)
            if requirement_ids:
                review_paths.update(_paths_matching(root, non_ignored_files, config.user_documentation_paths))
            review_paths.discard(semantic_path)
            if previous == (frozenset(review_paths), frozenset(requirement_ids)):
                break
        base_traceability.update({path: ids for path, ids in base_impl_all.items() if path in review_paths})
        base_supporting.update({path: ids for path, ids in base_support_all.items() if path in review_paths})
        deleted_paths = review_paths - non_ignored_files
    # The attestation records this scope and therefore cannot participate in
    # its own digest, even when a broad user-documentation pattern matches it.
    review_paths.discard(semantic_path)
    traceability_records: list[dict[str, Any]] = []
    supporting_records: list[dict[str, Any]] = []
    for review_path in sorted(review_paths):
        mapped_ids = set(traceability.get(review_path, ()))
        if not mapped_ids and capability_map is not None:
            mapped_ids.update(
                requirement_id
                for capability in capability_map.capabilities
                if capability.status == "active"
                for mapping in capability.implementation_mappings
                if fnmatch.fnmatch(review_path, mapping.pattern)
                for requirement_id in mapping.requirement_ids
            )
        mapped_ids.update(base_traceability.get(review_path, ()))
        if mapped_ids:
            traceability_records.append(
                {"path": review_path, "requirement_ids": sorted(mapped_ids)}
            )
        supporting_ids = set(supporting.get(review_path, ()))
        supporting_ids.update(base_supporting.get(review_path, ()))
        if capability_map is not None and review_path in deleted_paths:
            supporting_ids.update(
                requirement_id for capability in capability_map.capabilities if capability.status == "active"
                for mapping in capability.supporting_mappings if fnmatch.fnmatch(review_path, mapping.pattern)
                for requirement_id in mapping.requirement_ids
            )
        # Deleted base paths remain in exact semantic scope; the union above also
        # retains their requirement pairs when the candidate removed its mapping.
        if supporting_ids:
            supporting_records.append({"path": review_path, "requirement_ids": sorted(supporting_ids)})
    path_records: list[dict[str, str]] = []
    for path in sorted(review_paths):
        # A retained local copy is not candidate source. Never resolve or read it;
        # retain the trusted-base blob as the deletion/transition evidence.
        candidate = _project_path(root, path, "semantic review path") if path in non_ignored_files else None
        if candidate is not None and candidate.is_file():
            path_records.append({"path": path, "digest": _sha256_bytes(_project_file_bytes(root, path, "semantic review path"))})
        else:
            base_bytes = _git_show_bytes(root, base, path) if base else None
            record: dict[str, str] = {"path": path, "digest": "deleted"}
            if base_bytes is not None:
                record["base_digest"] = _sha256_bytes(base_bytes)
                record["base_ref"] = f"{base}:{path}"
            path_records.append(record)
    base_commit = _git_revision(root, base) if base else None
    head_commit = _git_revision(root, "HEAD")
    base_requirements_by_id = {
        item.id: item for item in base_requirements(root, base, config)
    } if base else {}
    current_requirements_by_id = {item.id: item for item in requirements}
    requirement_paths: dict[str, list[str]] = {}
    for requirement_id in sorted(requirement_ids):
        requirement = current_requirements_by_id.get(
            requirement_id, base_requirements_by_id.get(requirement_id)
        )
        if requirement is not None:
            requirement_paths.setdefault(requirement.path, []).append(requirement_id)
    requirement_traceability = [
        {"path": path, "requirement_ids": sorted(ids)}
        for path, ids in sorted(requirement_paths.items())
    ]
    scope_material = {
        "mode": mode,
        "base_commit": base_commit,
        "paths": path_records,
        "requirement_ids": sorted(requirement_ids),
        "capability_inventory_digest": capability_map.digest if capability_map else None,
        "approval_policy_digest": policy.digest if policy else None,
        "implementation_traceability": traceability_records,
        "requirement_traceability": requirement_traceability,
        "supporting_traceability": supporting_records,
    }
    return {
        "schema_version": SEMANTIC_SCHEMA_VERSION,
        **scope_material,
        "observed_head_commit": head_commit,
        "scope_digest": _canonical_json_digest(scope_material),
        "reviewed_paths": sorted(review_paths),
        "requirement_digests": {
            requirement_id: current_requirements_by_id.get(
                requirement_id, base_requirements_by_id.get(requirement_id)
            ).digest if requirement_id in current_requirements_by_id or requirement_id in base_requirements_by_id else ""
            for requirement_id in sorted(requirement_ids)
        },
        "implementation_digests": {
            requirement_id: requirement_implementation_digest(
                root, requirement_id, capability_map, implementation_files
            )
            for requirement_id in sorted(requirement_ids)
        },
        "supporting_digests": {
            requirement_id: requirement_supporting_digest(root, requirement_id, capability_map)
            for requirement_id in sorted(requirement_ids)
        },
        "coverage_kinds": {
            requirement_id: (current_requirements_by_id.get(requirement_id) or base_requirements_by_id.get(requirement_id)).coverage_kind
            for requirement_id in sorted(requirement_ids)
            if requirement_id in current_requirements_by_id or requirement_id in base_requirements_by_id
        },
    }


def _semantic_record_has_shape(record: dict[str, Any]) -> bool:
    if record.get("mode") == "changed" and not str(record.get("base_commit") or "").strip():
        return False
    expected_types = {
        "reviewed_paths": list, "requirement_ids": list,
        "requirement_digests": dict, "implementation_digests": dict,
        "supporting_digests": dict, "coverage_kinds": dict,
        "implementation_traceability": list, "supporting_traceability": list,
        "requirement_traceability": list, "conclusions": list,
        "findings": list, "limitations": list, "reviewer": str,
        "reviewed_at": str, "result": str,
    }
    if any(not isinstance(record.get(key), kind) for key, kind in expected_types.items()):
        return False
    for key in ("capability_inventory_digest", "approval_policy_digest"):
        if not isinstance(record.get(key), str) or not SHA256_RE.fullmatch(record[key]):
            return False
    for key in ("reviewed_paths", "requirement_ids"):
        values = record[key]
        if not all(isinstance(value, str) and value.strip() for value in values) or len(values) != len(set(values)):
            return False
    if any(REQ_ID_FULL_RE.fullmatch(value) is None for value in record["requirement_ids"]):
        return False
    if record["reviewer"] and REVIEWER_ID_RE.fullmatch(record["reviewer"]) is None:
        return False
    if record["reviewed_at"] and not _valid_timestamp(record["reviewed_at"]):
        return False
    if record["result"] != "pending" and (not record["reviewer"] or not record["reviewed_at"]):
        return False
    ids = set(record["requirement_ids"])
    for key in ("requirement_digests", "implementation_digests", "supporting_digests"):
        if set(record[key]) != ids or any(not isinstance(value, str) or not SHA256_RE.fullmatch(value) for value in record[key].values()):
            return False
    if set(record["coverage_kinds"]) != ids or any(not isinstance(value, str) or value not in COVERAGE_KINDS for value in record["coverage_kinds"].values()):
        return False
    for key in ("implementation_traceability", "supporting_traceability", "requirement_traceability"):
        seen_paths: set[str] = set()
        for entry in record[key]:
            if (not isinstance(entry, dict) or not isinstance(entry.get("path"), str)
                or entry["path"] not in record["reviewed_paths"]
                or not isinstance(entry.get("requirement_ids"), list)
                or not all(isinstance(value, str) and value in ids for value in entry["requirement_ids"])):
                return False
            if entry["path"] in seen_paths or len(entry["requirement_ids"]) != len(set(entry["requirement_ids"])):
                return False
            seen_paths.add(entry["path"])
    for conclusion in record["conclusions"]:
        if not isinstance(conclusion, dict) or not all(isinstance(conclusion.get(key), str) and conclusion[key].strip() for key in ("path", "requirement_id", "result", "notes")):
            return False
        if conclusion["path"] not in record["reviewed_paths"] or conclusion["requirement_id"] not in ids:
            return False
    return record["result"] in {"pass", "fail", "pending"}


def check_semantic_review(
    root: Path,
    config: CheckerConfig,
    context: dict[str, Any],
    policy: ApprovalPolicy | None = None,
) -> list[Finding]:
    if not config.require_semantic_review:
        return []
    path = root / config.semantic_review
    relative = _relative(path, root)
    if not path.is_file():
        return [Finding("failure", "missing_semantic_review", "Current semantic-review attestation is missing.", relative)]
    raw = _read_json_object(path)
    if "attestations" in raw:
        # Full local/release and changed PR scopes coexist without accepting a
        # broader or neighboring record in place of the exact requested scope.
        records = raw["attestations"]
        if type(raw.get("schema_version")) is not int or raw["schema_version"] != 1 or not isinstance(records, list) or not records:
            return [Finding("failure", "incomplete_semantic_review", "Semantic-review ledger requires schema 1 and a non-empty attestations list.", relative)]
        keys: set[tuple[str, str | None, str]] = set()
        for record in records:
            if (
                not isinstance(record, dict)
                or type(record.get("schema_version")) is not int
                or record.get("schema_version") != SEMANTIC_SCHEMA_VERSION
                or not isinstance(record.get("mode"), str)
                or record.get("mode") not in {"full", "changed"}
                or not isinstance(record.get("scope_digest"), str)
                or not SHA256_RE.fullmatch(record["scope_digest"])
                or (record.get("base_commit") is not None and not isinstance(record["base_commit"], str))
                or not _semantic_record_has_shape(record)
            ):
                return [Finding("failure", "incomplete_semantic_review", "Semantic-review ledger contains a malformed attestation.", relative)]
            key = (record["mode"], record.get("base_commit"), record["scope_digest"])
            if key in keys:
                return [Finding("failure", "incomplete_semantic_review", "Semantic-review ledger contains duplicate exact scopes.", relative)]
            keys.add(key)
        matches = [record for record in records if (
            record["mode"] == context["mode"]
            and record.get("base_commit") == context["base_commit"]
            and record["scope_digest"] == context["scope_digest"]
        )]
        if len(matches) != 1:
            return [Finding("failure", "stale_semantic_review", "Semantic-review ledger has no exact current mode/base/scope attestation.", relative)]
        raw = matches[0]
    effective_policy = policy
    if effective_policy is None:
        effective_policy, _ = load_and_check_approval_policy(root, config)
    required_exact = {
        "schema_version": SEMANTIC_SCHEMA_VERSION,
        "mode": context["mode"],
        "base_commit": context["base_commit"],
        "scope_digest": context["scope_digest"],
        "capability_inventory_digest": context["capability_inventory_digest"],
        "approval_policy_digest": context["approval_policy_digest"],
        "reviewed_paths": context["reviewed_paths"],
        "requirement_ids": context["requirement_ids"],
        "implementation_traceability": context["implementation_traceability"],
        "requirement_traceability": context["requirement_traceability"],
        "requirement_digests": context["requirement_digests"],
        "implementation_digests": context["implementation_digests"],
        "supporting_traceability": context["supporting_traceability"],
        "supporting_digests": context["supporting_digests"],
        "coverage_kinds": context["coverage_kinds"],
    }
    mismatches = [key for key, expected in required_exact.items() if raw.get(key) != expected]
    if mismatches:
        return [Finding("failure", "stale_semantic_review", "Semantic-review attestation is stale for: " + ", ".join(mismatches) + ".", relative)]
    reviewer = raw.get("reviewer")
    reviewed_at = raw.get("reviewed_at")
    findings = raw.get("findings")
    limitations = raw.get("limitations")
    if not isinstance(reviewer, str) or REVIEWER_ID_RE.fullmatch(reviewer) is None or not isinstance(reviewed_at, str) or not _valid_timestamp(reviewed_at) or not isinstance(findings, list) or not isinstance(limitations, list):
        return [Finding("failure", "incomplete_semantic_review", "Semantic-review attestation lacks a valid reviewer, timestamp, findings or limitations list.", relative)]
    if effective_policy is None or not _is_authorized(reviewer, effective_policy.authorized_semantic_reviewers):
        return [Finding("failure", "incomplete_semantic_review", "Semantic-review attestation reviewer is outside the approval policy.", relative)]
    if raw.get("result") != "pass" or findings:
        return [Finding("failure", "failed_semantic_review", "Semantic review did not pass without unresolved findings.", relative)]
    if limitations:
        return [Finding("failure", "incomplete_semantic_review", "Semantic review has unresolved limitations and cannot attest complete coverage.", relative)]
    conclusions = raw.get("conclusions")
    if not isinstance(conclusions, list):
        return [Finding("failure", "invalid_semantic_conclusion", "Semantic review lacks per-path requirement conclusions.", relative)]
    expected_pairs = {
        (entry["path"], requirement_id)
        for entry in (
            context["implementation_traceability"]
            + context["supporting_traceability"]
            + context["requirement_traceability"]
        )
        for requirement_id in entry["requirement_ids"]
    }
    actual_pairs: list[tuple[str, str]] = []
    for conclusion in conclusions:
        if (
            not isinstance(conclusion, dict)
            or not isinstance(conclusion.get("path"), str)
            or not isinstance(conclusion.get("requirement_id"), str)
            or conclusion.get("result") != "pass"
            or not isinstance(conclusion.get("notes"), str)
            or not conclusion["notes"].strip()
        ):
            return [Finding("failure", "invalid_semantic_conclusion", "Every semantic conclusion needs one path, one requirement ID, pass result and non-empty notes.", relative)]
        actual_pairs.append((conclusion["path"], conclusion["requirement_id"]))
    if len(actual_pairs) != len(set(actual_pairs)) or set(actual_pairs) != expected_pairs:
        return [Finding("failure", "invalid_semantic_conclusion", "Semantic conclusions do not exactly cover every scoped implementation-path/requirement pair.", relative)]
    return []


def write_baseline(
    path: Path,
    findings: list[Finding],
    reason: str,
    accepted_by: str,
    decision_ref: str,
    expires_at: str,
) -> None:
    if HUMAN_ID_RE.fullmatch(accepted_by) is None:
        raise ConfigurationError("Baseline acceptance requires accepted_by=human:<id>.")
    if not decision_ref.strip():
        raise ConfigurationError("Baseline acceptance requires a decision reference.")
    if not _valid_date(expires_at) or expires_at < date.today().isoformat():
        raise ConfigurationError("Baseline acceptance requires a current expiry date.")
    entries = [
        asdict(BaselineEntry.from_finding(finding, reason, accepted_by, decision_ref, expires_at))
        for finding in findings
        if finding.severity == "failure" and finding.code not in NON_BASELINABLE_CODES
    ]
    payload = {"schema_version": SCHEMA_VERSION, "findings": entries}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def _trusted_base_config(config: CheckerConfig) -> CheckerConfig:
    """Force every assurance control for a trusted-base changed-only audit."""
    return replace(
        config,
        enforcement_mode="strict",
        require_provenance=True,
        require_structured_evidence=True,
        require_human_approvals=True,
        require_capability_coverage=True,
        require_evidence_receipts=True,
        require_semantic_review=True,
    )


def build_report(
    mode: str,
    requirements: list[Requirement],
    findings: list[Finding],
    changed_paths: set[str],
    changed_ids: list[str],
    rows: list[MatrixRow],
    include_requirements: bool,
) -> dict[str, Any]:
    blocking = [finding for finding in findings if finding.blocking]
    baseline_debt = [finding for finding in findings if finding.baseline_accepted]
    warnings = [finding for finding in findings if finding.severity == "warning"]
    report: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "mode": mode,
        "summary": {
            "requirements": len(requirements),
            "normative_requirements": sum(1 for item in requirements if item.lifecycle_status == "Normative"),
            "blocking_failures": len(blocking),
            "baseline_debt": len(baseline_debt),
            "warnings": len(warnings),
        },
        "findings": [asdict(finding) for finding in findings],
        "changed_paths": sorted(changed_paths),
        "changed_requirement_ids": changed_ids,
        "semantic_review_required": mode == "full" or bool(changed_paths),
        "trust_boundary": {
            "record_identities_are_assertions": True,
            "external_codeowner_review_required": True,
            "protected_required_check_required": True,
        },
        "requirement_status": [
            {
                "requirement_id": row.requirement_id,
                "declared_status": row.implementation_status,
                "computed_status": row.computed_implementation_status,
                "declared_evidence_state": row.evidence_state,
                "computed_evidence_state": row.computed_evidence_state,
                "verified_at": row.verified_at,
            }
            for row in rows
        ],
    }
    if include_requirements:
        report["requirements"] = [asdict(requirement) for requirement in requirements]
    return report


def compact_report(report: dict[str, Any], limit: int = 25) -> dict[str, Any]:
    finding_counts: dict[str, int] = {}
    for finding in report["findings"]:
        finding_counts[finding["code"]] = finding_counts.get(finding["code"], 0) + 1
    status_counts: dict[str, int] = {}
    status_mismatches: list[dict[str, Any]] = []
    for status in report["requirement_status"]:
        computed = status["computed_status"] or "unknown"
        status_counts[computed] = status_counts.get(computed, 0) + 1
        if (
            status["declared_status"] != status["computed_status"]
            or status["declared_evidence_state"] != status["computed_evidence_state"]
        ):
            status_mismatches.append(status)
    findings = report["findings"][:limit]
    mismatches = status_mismatches[:limit]
    return {
        "schema_version": report["schema_version"],
        "mode": report["mode"],
        "summary": report["summary"],
        "finding_counts": dict(sorted(finding_counts.items())),
        "findings": findings,
        "findings_truncated": len(report["findings"]) - len(findings),
        "changed_paths": report["changed_paths"],
        "changed_requirement_ids": report["changed_requirement_ids"],
        "semantic_review_required": report["semantic_review_required"],
        "trust_boundary": report["trust_boundary"],
        "requirement_status_counts": dict(sorted(status_counts.items())),
        "requirement_status_mismatches": mismatches,
        "requirement_status_mismatches_truncated": len(status_mismatches) - len(mismatches),
    }


def print_text_report(report: dict[str, Any]) -> None:
    summary = report["summary"]
    findings = report["findings"]
    print("Specification compliance:")
    print(f"- Mode: {report['mode']}")
    print(f"- Requirements: {summary['requirements']} total, {summary['normative_requirements']} normative")
    print(f"- New blocking failures: {summary['blocking_failures']}")
    print(f"- Accepted baseline debt: {summary['baseline_debt']}")
    print(f"- Warnings: {summary['warnings']}")
    print("- Identity assurance: external CODEOWNERS and protected-review controls required")
    groups = [
        ("New failures", lambda item: item["blocking"]),
        ("Baseline debt", lambda item: item["baseline_accepted"]),
        ("Warnings", lambda item: item["severity"] == "warning"),
    ]
    for title, predicate in groups:
        selected = [item for item in findings if predicate(item)]
        if selected:
            print(f"\n{title}:")
            for item in selected:
                location = f" ({item['path']}:{item['line']})" if item.get("path") and item.get("line") else f" ({item['path']})" if item.get("path") else ""
                print(f"- {item['code']}: {item['message']}{location}")
    if report["changed_requirement_ids"]:
        print("\nChanged requirement IDs:")
        for requirement_id in report["changed_requirement_ids"]:
            print(f"- {requirement_id}")
    if report["semantic_review_required"]:
        print("\nTo do:")
        print("- Perform model-led semantic review of the current diff and affected requirements.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Check living specification structure and evidence references.")
    parser.add_argument("--root", default=".", help="Project root.")
    parser.add_argument("--config", help="Configuration file; defaults to docs/specification/spec-config.yaml.")
    parser.add_argument("--spec-dir", help="Override specification directory.")
    parser.add_argument("--matrix", help="Override verification matrix path.")
    parser.add_argument("--baseline", help="Override baseline path.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--full", action="store_true", help="Run strict full/release audit (default).")
    mode.add_argument("--changed-only", action="store_true", help="Block only regressions related to changed paths.")
    parser.add_argument("--base", help="Git base ref required by --changed-only.")
    parser.add_argument("--trusted-base-controls", action="store_true", help="In changed-only mode, force strict controls and authorize assurance records with the base revision policy.")
    output = parser.add_mutually_exclusive_group()
    output.add_argument("--json", action="store_true", help="Print full JSON report.")
    output.add_argument("--summary-json", action="store_true", help="Print compact JSON report.")
    output.add_argument("--review-context", action="store_true", help="Print the exact semantic-review scope and digests.")
    parser.add_argument("--write-baseline", help="Write current failures as an explicit baseline.")
    parser.add_argument("--baseline-reason", help="Required reason when writing a baseline.")
    parser.add_argument("--baseline-accepted-by", help="Human identity in human:<id> form.")
    parser.add_argument("--baseline-decision-ref", help="Durable human decision reference.")
    parser.add_argument("--baseline-expires-at", help="Mandatory baseline expiry date (YYYY-MM-DD).")
    args = parser.parse_args()

    try:
        root = Path(args.root).resolve()
        if args.trusted_base_controls and (not args.changed_only or not args.base):
            raise ConfigurationError("--trusted-base-controls requires --changed-only and --base <git-ref>.")
        if args.trusted_base_controls:
            if any((args.config, args.spec_dir, args.matrix, args.baseline)):
                raise ConfigurationError(
                    "--trusted-base-controls does not allow candidate-selected config or path overrides."
                )
            config = _trusted_base_config(load_base_config(root, args.base))
        else:
            config_path = Path(args.config) if args.config else None
            if config_path is not None and not config_path.is_absolute():
                config_path = root / config_path
            config = load_config(root, config_path)
            if args.spec_dir:
                config.spec_dir = args.spec_dir
            if args.matrix:
                config.matrix = args.matrix
            if args.baseline:
                config.baseline = args.baseline
        for key in (
            "spec_dir",
            "matrix",
            "baseline",
            "approvals",
            "capabilities",
            "evidence_receipts",
            "semantic_review",
        ):
            _project_path(root, getattr(config, key), key)
        selected_mode = "changed" if args.changed_only else "full"
        if selected_mode == "changed" and not args.base:
            raise ConfigurationError("--changed-only requires --base <git-ref>.")
        trusted_policy: ApprovalPolicy | None = None
        trusted_policy_findings: list[Finding] = []
        if args.trusted_base_controls:
            trusted_policy, trusted_policy_findings = load_base_approval_policy(
                root, args.base, config
            )
        requirements, findings, rows = run_checks(root, config, trusted_policy)
        findings.extend(trusted_policy_findings)
        candidate_policy, _ = load_and_check_approval_policy(root, config)
        effective_policy = trusted_policy or candidate_policy
        baseline_path = (root / config.baseline).resolve() if config.baseline else None
        baseline = load_baseline(baseline_path)
        changed_paths = git_changed_paths(root, args.base) if selected_mode == "changed" else set()
        changed_ids = changed_requirement_ids(root, args.base, requirements, changed_paths, config.matrix)
        findings.extend(deleted_requirement_findings(root, args.base, requirements, config))
        capability_map, _, implementation_files = load_and_check_capabilities(root, config, requirements)
        review_context = build_review_context(
            root,
            config,
            selected_mode,
            args.base,
            requirements,
            capability_map,
            implementation_files,
            changed_paths,
            changed_ids,
        )
        if args.review_context:
            review_context["structural_findings"] = [asdict(finding) for finding in findings]
            print(json.dumps(review_context, indent=2))
            return 0
        findings.extend(check_semantic_review(root, config, review_context, effective_policy))
        classify_findings(
            findings,
            selected_mode,
            config,
            baseline,
            changed_paths,
            root,
            set(changed_ids),
            effective_policy,
        )
        if args.write_baseline:
            if not all(
                [
                    args.baseline_reason,
                    args.baseline_accepted_by,
                    args.baseline_decision_ref,
                    args.baseline_expires_at,
                ]
            ):
                raise ConfigurationError(
                    "--write-baseline requires --baseline-reason, --baseline-accepted-by, "
                    "--baseline-decision-ref and --baseline-expires-at."
                )
            write_baseline(
                _project_path(root, args.write_baseline, "write-baseline"),
                findings,
                args.baseline_reason,
                args.baseline_accepted_by,
                args.baseline_decision_ref,
                args.baseline_expires_at,
            )
        report = build_report(
            selected_mode,
            requirements,
            findings,
            changed_paths,
            changed_ids,
            rows,
            include_requirements=args.json,
        )
        if args.json:
            print(json.dumps(report, indent=2))
        elif args.summary_json:
            print(json.dumps(compact_report(report), indent=2))
        else:
            print_text_report(report)
        return 1 if report["summary"]["blocking_failures"] else 0
    except (ConfigurationError, json.JSONDecodeError, OSError) as error:
        print(f"Specification checker error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
