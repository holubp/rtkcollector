from __future__ import annotations

from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SCRIPT = Path(__file__).parent / "specification" / "review_handoff.py"
SPEC = importlib.util.spec_from_file_location("review_handoff", SCRIPT)
assert SPEC and SPEC.loader
handoff = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = handoff
SPEC.loader.exec_module(handoff)


class ReviewHandoffTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.trusted_temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.trusted_root = Path(self.trusted_temp.name)
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        (self.root / ".gitignore").write_text(".spec-assurance/\n", encoding="utf-8")
        self.local = self.root / ".spec-assurance"
        self.local.mkdir()
        self.digest = "sha256:" + "a" * 64
        self.context = {
            "schema_version": handoff.CONTEXT_SCHEMA_VERSION,
            "mode": "full",
            "base_commit": None,
            "scope_digest": self.digest,
            "reviewed_paths": [
                "src/feature.py",
                "docs/guide.md",
                "docs/specification/requirements.md",
            ],
            "requirement_ids": ["CORE-FLOW-001", "DOC-001"],
            "implementation_traceability": [
                {"path": "src/feature.py", "requirement_ids": ["CORE-FLOW-001"]}
            ],
            "supporting_traceability": [
                {"path": "docs/guide.md", "requirement_ids": ["DOC-001"]}
            ],
            "requirement_traceability": [
                {
                    "path": "docs/specification/requirements.md",
                    "requirement_ids": ["CORE-FLOW-001", "DOC-001"],
                }
            ],
            "supporting_digests": {
                "CORE-FLOW-001": self.digest,
                "DOC-001": self.digest,
            },
            "coverage_kinds": {
                "CORE-FLOW-001": "implementation_required",
                "DOC-001": "documentation",
            },
        }
        self.checkpoint = {
            "schema_version": 1,
            "kind": "review-checkpoint",
            "state": "in-progress",
            "workflow_role": "updater",
            "mode": "full",
            "scope_digest": self.digest,
            "base_commit": None,
            "context_file": ".spec-assurance/review-context.json",
            "context_command": "stored for audit only; never execute this text",
            "trusted_base_controls": False,
            "handoff_to": "checker",
            "reviewed_paths": [],
            "reviewed_pairs": [],
            "findings": [],
            "limitations": [],
            "next_action": "Run independent semantic review.",
            "reviewer": "model:test",
            "created_at": "2026-07-19T12:00:00Z",
            "updated_at": "2026-07-19T12:00:00Z",
        }
        self._write_json(self.local / "review-context.json", self.context)
        self._write_json(self.local / "review-checkpoint.json", self.checkpoint)
        self._write_json(self.root / "current-context.json", self.context)
        self.checker = self.root / "fake-checker.py"
        self.checker.write_text(
            "from pathlib import Path\n"
            "import sys\n"
            "root = Path(sys.argv[sys.argv.index('--root') + 1])\n"
            "print((root / 'current-context.json').read_text(encoding='utf-8'))\n",
            encoding="utf-8",
        )

    def tearDown(self) -> None:
        self.trusted_temp.cleanup()
        self.temp.cleanup()

    @staticmethod
    def _write_json(path: Path, value: dict) -> None:
        path.write_text(json.dumps(value), encoding="utf-8")

    def _args(
        self,
        handoff_path: str | None = None,
        checker: Path | None = None,
    ) -> SimpleNamespace:
        return SimpleNamespace(
            root=str(self.root),
            checkpoint=".spec-assurance/review-checkpoint.json",
            context=".spec-assurance/review-context.json",
            handoff=handoff_path,
            checker=str(checker or self.checker),
        )

    def _configure_changed_checkpoint(self, base: str) -> None:
        self.checkpoint.update(
            mode="changed",
            base_commit=base,
            trusted_base_controls=True,
        )
        self.context.update(mode="changed", base_commit=base)
        self._write_json(self.local / "review-checkpoint.json", self.checkpoint)
        self._write_json(self.local / "review-context.json", self.context)
        self._write_json(self.root / "current-context.json", self.context)

    def _trusted_tools(self) -> tuple[Path, Path, str]:
        subprocess.run(["git", "init", "-q"], cwd=self.trusted_root, check=True)
        checker = self.trusted_root / "tools/specification/spec_check.py"
        verifier = self.trusted_root / "tools/specification/review_handoff.py"
        checker.parent.mkdir(parents=True)
        checker.write_text(self.checker.read_text(encoding="utf-8"), encoding="utf-8")
        verifier.write_text(SCRIPT.read_text(encoding="utf-8"), encoding="utf-8")
        subprocess.run(
            ["git", "add", "--", "tools/specification"],
            cwd=self.trusted_root,
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-c",
                "user.name=Spec Test",
                "-c",
                "user.email=spec-test@example.invalid",
                "commit",
                "-q",
                "-m",
                "trusted checker",
            ],
            cwd=self.trusted_root,
            check=True,
        )
        base = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.trusted_root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        return checker, verifier, base

    def _canonical_artifacts(self) -> dict[str, str | list[str]]:
        artifacts: dict[str, str | list[str]] = {
            "config": "docs/specification/spec-config.yaml",
            "requirements": ["docs/specification/requirements.md"],
            "matrix": "docs/specification/verification-matrix.md",
            "approval_policy": "docs/specification/approval-policy.json",
            "requirement_approvals": "docs/specification/requirement-approvals.json",
            "capability_map": "docs/specification/capability-map.json",
            "evidence_receipts": "docs/specification/evidence-receipts.json",
            "semantic_review": "docs/specification/semantic-review.json",
        }
        paths = [
            value
            for key, value in artifacts.items()
            if key != "requirements" and isinstance(value, str)
        ] + list(artifacts["requirements"])
        for relative in paths:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("current\n", encoding="utf-8")
        return artifacts

    def _handoff_payload(self) -> dict:
        return {
            "schema_version": 1,
            "kind": "specification-assurance-handoff",
            "state": "ready",
            "from_role": "updater",
            "to_role": "checker",
            "repository_ref": "https://example.invalid/project",
            "mode": self.checkpoint["mode"],
            "base_commit": self.checkpoint["base_commit"],
            "scope_digest": self.digest,
            "context_file": ".spec-assurance/review-context.json",
            "checkpoint_file": ".spec-assurance/review-checkpoint.json",
            "canonical_artifacts": self._canonical_artifacts(),
            "validation": {
                "command": "python3 tools/specification/spec_check.py --full",
                "exit_code": 0,
                "result": "passed",
                "ran_at": "2026-07-19T12:00:00Z",
            },
            "unresolved_findings": [],
            "next_action": "Perform independent review.",
            "created_by": "model:test",
            "created_at": "2026-07-19T12:00:00Z",
        }

    def test_verify_regenerates_context_and_accepts_matching_digest(self) -> None:
        output = io.StringIO()
        with redirect_stdout(output):
            status = handoff.verify(self._args())

        self.assertEqual(0, status)
        self.assertIn('"status": "current"', output.getvalue())

    def test_verify_reports_stale_digest(self) -> None:
        changed = dict(self.context, scope_digest="sha256:" + "b" * 64)
        self._write_json(self.root / "current-context.json", changed)

        output = io.StringIO()
        with redirect_stdout(output):
            status = handoff.verify(self._args())

        self.assertEqual(1, status)
        self.assertIn('"status": "stale"', output.getvalue())

    def test_supporting_digest_change_makes_context_stale(self) -> None:
        changed = json.loads(json.dumps(self.context))
        changed["supporting_digests"]["DOC-001"] = "sha256:" + "b" * 64
        self._write_json(self.root / "current-context.json", changed)

        output = io.StringIO()
        with redirect_stdout(output):
            status = handoff.verify(self._args())

        self.assertEqual(1, status)
        self.assertIn('"status": "stale"', output.getvalue())

    def test_coverage_kind_change_makes_context_stale(self) -> None:
        changed = json.loads(json.dumps(self.context))
        changed["coverage_kinds"]["DOC-001"] = "process"
        self._write_json(self.root / "current-context.json", changed)

        output = io.StringIO()
        with redirect_stdout(output):
            status = handoff.verify(self._args())

        self.assertEqual(1, status)
        self.assertIn('"status": "stale"', output.getvalue())

    def test_completed_checkpoint_requires_exact_supporting_pair(self) -> None:
        exact_pairs = sorted(
            {
                ("src/feature.py", "CORE-FLOW-001"),
                ("docs/guide.md", "DOC-001"),
                ("docs/specification/requirements.md", "CORE-FLOW-001"),
                ("docs/specification/requirements.md", "DOC-001"),
            }
        )
        self.checkpoint.update(
            state="completed",
            reviewed_paths=[
                "src/feature.py",
                "docs/guide.md",
                "docs/specification/requirements.md",
            ],
            reviewed_pairs=[
                {"path": path, "requirement_id": requirement_id}
                for path, requirement_id in exact_pairs
            ],
        )
        paths, pairs = handoff._context_coverage(
            self.context, self.checkpoint, "test context"
        )
        self.assertIn(("docs/guide.md", "DOC-001"), pairs)
        handoff._validate_checkpoint_coverage(self.checkpoint, paths, pairs)

        self.checkpoint["reviewed_pairs"].remove(
            {"path": "docs/guide.md", "requirement_id": "DOC-001"}
        )

        with self.assertRaisesRegex(handoff.HandoffError, "full current scope"):
            handoff._validate_checkpoint_coverage(self.checkpoint, paths, pairs)

    def test_context_rejects_pre_v5_schema(self) -> None:
        old_context = dict(self.context, schema_version=4)

        with self.assertRaisesRegex(handoff.HandoffError, "unsupported schema"):
            handoff._context_coverage(
                old_context, self.checkpoint, "saved review context"
            )

    def test_context_rejects_unknown_coverage_kind(self) -> None:
        invalid = json.loads(json.dumps(self.context))
        invalid["coverage_kinds"]["DOC-001"] = "other"

        with self.assertRaisesRegex(handoff.HandoffError, "coverage_kinds"):
            handoff._context_coverage(invalid, self.checkpoint, "test context")

    def test_supporting_digests_cover_all_requirement_ids(self) -> None:
        incomplete = json.loads(json.dumps(self.context))
        del incomplete["supporting_digests"]["CORE-FLOW-001"]

        with self.assertRaisesRegex(handoff.HandoffError, "supporting_digests"):
            handoff._context_coverage(
                incomplete, self.checkpoint, "test context"
            )

    def test_stored_context_command_is_not_executed(self) -> None:
        marker = self.root / "must-not-exist"
        self.checkpoint["context_command"] = f"touch {marker}"
        self._write_json(self.local / "review-checkpoint.json", self.checkpoint)

        with redirect_stdout(io.StringIO()):
            self.assertEqual(0, handoff.verify(self._args()))

        self.assertFalse(marker.exists())

    def test_verify_validates_structured_handoff_and_artifacts(self) -> None:
        self.checkpoint["state"] = "ready"
        self._write_json(self.local / "review-checkpoint.json", self.checkpoint)
        payload = self._handoff_payload()
        self._write_json(self.local / "review-handoff.json", payload)

        with redirect_stdout(io.StringIO()):
            status = handoff.verify(self._args(".spec-assurance/review-handoff.json"))

        self.assertEqual(0, status)

    def test_handoff_rejects_missing_canonical_artifact(self) -> None:
        self.checkpoint["state"] = "ready"
        payload = {
            "schema_version": 1,
            "kind": "specification-assurance-handoff",
            "state": "ready",
            "from_role": "updater",
            "to_role": "checker",
            "repository_ref": "project:test",
            "mode": "full",
            "base_commit": None,
            "scope_digest": self.digest,
            "context_file": ".spec-assurance/review-context.json",
            "checkpoint_file": ".spec-assurance/review-checkpoint.json",
            "canonical_artifacts": {
                "config": "docs/specification/missing.md",
                "requirements": ["docs/specification/requirements.md"],
                "matrix": "docs/specification/verification-matrix.md",
                "approval_policy": "docs/specification/approval-policy.json",
                "requirement_approvals": "docs/specification/requirement-approvals.json",
                "capability_map": "docs/specification/capability-map.json",
                "evidence_receipts": "docs/specification/evidence-receipts.json",
                "semantic_review": "docs/specification/semantic-review.json",
            },
            "validation": {
                "command": "checker --full",
                "exit_code": 1,
                "result": "failed",
                "ran_at": "2026-07-19T12:00:00Z",
            },
            "unresolved_findings": [],
            "next_action": "Create the artifact.",
            "created_by": "model:test",
            "created_at": "2026-07-19T12:00:00Z",
        }

        with self.assertRaisesRegex(handoff.HandoffError, "canonical artifact"):
            handoff._validate_handoff(
                self.root,
                payload,
                ".spec-assurance/review-checkpoint.json",
                ".spec-assurance/review-context.json",
                self.checkpoint,
            )

    def test_checkpoint_requires_timezone_aware_timestamps(self) -> None:
        self.checkpoint["updated_at"] = "2026-07-19T12:00:00"

        with self.assertRaisesRegex(handoff.HandoffError, "checkpoint"):
            handoff._validate_checkpoint(
                self.root,
                self.checkpoint,
                ".spec-assurance/review-context.json",
            )

    def test_verify_rejects_unignored_working_artifacts(self) -> None:
        (self.root / ".gitignore").write_text("", encoding="utf-8")

        with self.assertRaisesRegex(handoff.HandoffError, "Git-ignored"):
            handoff.verify(self._args())

    def test_verify_rejects_checkpoint_coverage_outside_context(self) -> None:
        self.checkpoint["reviewed_paths"] = ["not/in/context.txt"]
        self._write_json(self.local / "review-checkpoint.json", self.checkpoint)

        with self.assertRaisesRegex(handoff.HandoffError, "outside current scope"):
            handoff.verify(self._args())

    def test_trusted_checkpoint_rejects_checker_inside_candidate(self) -> None:
        base = "a" * 40
        self._configure_changed_checkpoint(base)

        with self.assertRaisesRegex(handoff.HandoffError, "outside the candidate"):
            handoff.verify(self._args())

    def test_trusted_checkpoint_rejects_arbitrary_external_checker(self) -> None:
        checker = self.trusted_root / "forged.py"
        checker.write_text(self.checker.read_text(encoding="utf-8"), encoding="utf-8")
        self._configure_changed_checkpoint("a" * 40)

        with self.assertRaisesRegex(handoff.HandoffError, "trusted Git checkout"):
            handoff.verify(self._args(checker=checker))

    def test_trusted_checkpoint_accepts_exact_base_checker(self) -> None:
        checker, verifier, base = self._trusted_tools()
        self._configure_changed_checkpoint(base)

        with mock.patch.object(handoff, "__file__", str(verifier)):
            with redirect_stdout(io.StringIO()):
                status = handoff.verify(self._args(checker=checker))

        self.assertEqual(0, status)

    def test_trusted_checkpoint_rejects_modified_base_checker(self) -> None:
        checker, verifier, base = self._trusted_tools()
        checker.write_text(checker.read_text(encoding="utf-8") + "# modified\n", encoding="utf-8")
        self._configure_changed_checkpoint(base)

        with mock.patch.object(handoff, "__file__", str(verifier)):
            with self.assertRaisesRegex(handoff.HandoffError, "does not match"):
                handoff.verify(self._args(checker=checker))

    def test_trusted_checkpoint_rejects_wrong_checkout_head(self) -> None:
        checker, verifier, _ = self._trusted_tools()
        self._configure_changed_checkpoint("a" * 40)

        with mock.patch.object(handoff, "__file__", str(verifier)):
            with self.assertRaisesRegex(handoff.HandoffError, "HEAD does not match"):
                handoff.verify(self._args(checker=checker))

    def test_trusted_checkpoint_rejects_untrusted_verifier(self) -> None:
        checker, _, base = self._trusted_tools()
        self._configure_changed_checkpoint(base)

        with self.assertRaisesRegex(handoff.HandoffError, "trusted verifier"):
            handoff.verify(self._args(checker=checker))

    def test_trusted_checkpoint_rejects_modified_verifier(self) -> None:
        checker, verifier, base = self._trusted_tools()
        verifier.write_text(
            verifier.read_text(encoding="utf-8") + "# modified\n",
            encoding="utf-8",
        )
        self._configure_changed_checkpoint(base)

        with mock.patch.object(handoff, "__file__", str(verifier)):
            with self.assertRaisesRegex(handoff.HandoffError, "does not match"):
                handoff.verify(self._args(checker=checker))

    def test_handoff_must_carry_exact_unresolved_findings(self) -> None:
        finding = {
            "id": "F-001",
            "classification": "normative",
            "status": "open",
            "path": "src/feature.py",
            "requirement_id": "CORE-FLOW-001",
            "summary": "Implementation needs review.",
        }
        self.checkpoint.update(state="ready", findings=[finding])
        payload = self._handoff_payload()

        with self.assertRaisesRegex(handoff.HandoffError, "unresolved findings"):
            handoff._validate_handoff(
                self.root,
                payload,
                ".spec-assurance/review-checkpoint.json",
                ".spec-assurance/review-context.json",
                self.checkpoint,
            )

        payload["unresolved_findings"] = [finding]
        handoff._validate_handoff(
            self.root,
            payload,
            ".spec-assurance/review-checkpoint.json",
            ".spec-assurance/review-context.json",
            self.checkpoint,
        )


if __name__ == "__main__":
    unittest.main()
