from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


TOOLS = Path("/storage/emulated/0/GitHub/rtkcollector/.worktrees/ntrip-tls-integration/tools")
sys.path.insert(0, str(TOOLS))
from test_spec_assurance import SpecProject, spec_check  # noqa: E402


REQUIREMENT_ID = "CORE-FLOW-001"
IMPLEMENTATION_PATH = "src/feature.py"
REQUIREMENTS_PATH = "docs/specification/requirements.md"
REVIEWER = "model:test-reviewer"


def make_context(mode: str = "full", base_commit: str | None = None) -> dict:
    context = {
        "schema_version": spec_check.SEMANTIC_SCHEMA_VERSION,
        "mode": mode,
        "base_commit": base_commit,
        "capability_inventory_digest": "sha256:" + "1" * 64,
        "approval_policy_digest": "sha256:" + "2" * 64,
        "reviewed_paths": [IMPLEMENTATION_PATH, REQUIREMENTS_PATH],
        "requirement_ids": [REQUIREMENT_ID],
        "implementation_traceability": [
            {"path": IMPLEMENTATION_PATH, "requirement_ids": [REQUIREMENT_ID]}
        ],
        "requirement_traceability": [
            {"path": REQUIREMENTS_PATH, "requirement_ids": [REQUIREMENT_ID]}
        ],
        "supporting_traceability": [],
        "requirement_digests": {REQUIREMENT_ID: "sha256:" + "3" * 64},
        "implementation_digests": {REQUIREMENT_ID: "sha256:" + "4" * 64},
        "supporting_digests": {REQUIREMENT_ID: "sha256:" + "5" * 64},
        "coverage_kinds": {REQUIREMENT_ID: "implementation_required"},
    }
    scope_material = {
        key: context[key]
        for key in (
            "mode",
            "base_commit",
            "capability_inventory_digest",
            "approval_policy_digest",
            "implementation_traceability",
            "requirement_traceability",
            "supporting_traceability",
            "requirement_ids",
        )
    }
    scope_material["paths"] = [
        {"path": IMPLEMENTATION_PATH, "digest": "sha256:" + "6" * 64},
        {"path": REQUIREMENTS_PATH, "digest": "sha256:" + "7" * 64},
    ]
    context["scope_digest"] = spec_check._canonical_json_digest(scope_material)
    return context


def clone_context(context: dict, *, mode: str, base_commit: str | None) -> dict:
    """Change the semantic scope identity and recompute its canonical digest."""
    clone = dict(context)
    clone["mode"] = mode
    clone["base_commit"] = base_commit
    material = {
        key: context[key]
        for key in (
            "capability_inventory_digest",
            "approval_policy_digest",
            "implementation_traceability",
            "requirement_traceability",
            "supporting_traceability",
            "requirement_ids",
        )
    }
    material.update(
        {
            "mode": mode,
            "base_commit": base_commit,
            "paths": [
                {"path": IMPLEMENTATION_PATH, "digest": "sha256:" + "6" * 64},
                {"path": REQUIREMENTS_PATH, "digest": "sha256:" + "7" * 64},
            ],
        }
    )
    clone["scope_digest"] = spec_check._canonical_json_digest(material)
    return clone


def make_attestation(context: dict) -> dict:
    attestation = {
        key: context[key]
        for key in (
            "schema_version",
            "mode",
            "base_commit",
            "scope_digest",
            "capability_inventory_digest",
            "approval_policy_digest",
            "reviewed_paths",
            "requirement_ids",
            "implementation_traceability",
            "requirement_traceability",
            "requirement_digests",
            "implementation_digests",
            "supporting_traceability",
            "supporting_digests",
            "coverage_kinds",
        )
    }
    attestation.update(
        {
            "result": "pass",
            "reviewer": REVIEWER,
            "reviewed_at": "2026-10-30T12:00:00Z",
            "findings": [],
            "limitations": [],
            "conclusions": [
                {
                    "path": entry["path"],
                    "requirement_id": requirement_id,
                    "result": "pass",
                    "notes": "Synthetic scoped review passed.",
                }
                for entry in (
                    context["implementation_traceability"]
                    + context["supporting_traceability"]
                    + context["requirement_traceability"]
                )
                for requirement_id in entry["requirement_ids"]
            ],
        }
    )
    return attestation


def make_ledger(*attestations: dict) -> dict:
    return {"schema_version": 1, "attestations": list(attestations)}


class SemanticLedgerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.project = SpecProject(Path(self.temp.name))
        self.config = spec_check.load_config(self.project.root, None)
        self.policy = spec_check.ApprovalPolicy(
            authority_ref="policy:test-authority",
            authorized_approvers=("human:product-owner",),
            authorized_evidence_issuers=("ci:test",),
            authorized_semantic_reviewers=(REVIEWER,),
            digest="sha256:" + "2" * 64,
        )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def check(self, context: dict, payload: dict) -> list:
        (self.project.spec_dir / "semantic-review.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        return spec_check.check_semantic_review(
            self.project.root, self.config, context, self.policy
        )

    @staticmethod
    def codes(findings: list) -> set[str]:
        return {finding.code for finding in findings}

    def test_one_ledger_validates_full_and_changed_scopes_independently(self) -> None:
        full = make_context()
        changed = clone_context(
            full, mode="changed", base_commit="a" * 40
        )
        self.assertNotEqual(full["scope_digest"], changed["scope_digest"])
        ledger = make_ledger(make_attestation(full), make_attestation(changed))

        self.assertEqual([], self.check(full, ledger))
        self.assertEqual([], self.check(changed, ledger))

    def test_single_flat_schema_five_attestation_remains_supported(self) -> None:
        context = make_context()

        self.assertEqual([], self.check(context, make_attestation(context)))

    def test_wrong_mode_cannot_borrow_full_scope_record(self) -> None:
        full = make_context()
        changed = clone_context(full, mode="changed", base_commit="b" * 40)

        findings = self.check(changed, make_ledger(make_attestation(full)))

        self.assertIn("stale_semantic_review", self.codes(findings))

    def test_neighboring_scope_is_not_a_fallback_for_missing_exact_record(self) -> None:
        current = make_context(mode="changed", base_commit="c" * 40)
        neighbor = clone_context(current, mode="changed", base_commit="d" * 40)

        findings = self.check(current, make_ledger(make_attestation(neighbor)))

        self.assertIn("stale_semantic_review", self.codes(findings))

    def test_exact_record_still_rejects_stale_metadata(self) -> None:
        context = make_context()
        record = make_attestation(context)
        record["implementation_digests"] = {
            **record["implementation_digests"],
            REQUIREMENT_ID: "sha256:" + "8" * 64,
        }

        findings = self.check(context, make_ledger(record))

        self.assertIn("stale_semantic_review", self.codes(findings))

    def test_exact_record_still_rejects_unauthorized_reviewer(self) -> None:
        context = make_context()
        record = make_attestation(context)
        record["reviewer"] = "model:outsider"

        findings = self.check(context, make_ledger(record))

        self.assertIn("incomplete_semantic_review", self.codes(findings))

    def test_exact_record_still_rejects_failing_review(self) -> None:
        context = make_context()
        record = make_attestation(context)
        record["result"] = "fail"

        findings = self.check(context, make_ledger(record))

        self.assertIn("failed_semantic_review", self.codes(findings))

    def test_exact_record_still_requires_complete_conclusions(self) -> None:
        context = make_context()
        record = make_attestation(context)
        record["conclusions"] = []

        findings = self.check(context, make_ledger(record))

        self.assertIn("invalid_semantic_conclusion", self.codes(findings))

    def test_duplicate_exact_scope_records_are_rejected(self) -> None:
        context = make_context()
        record = make_attestation(context)

        findings = self.check(context, make_ledger(record, dict(record)))

        self.assertIn("incomplete_semantic_review", self.codes(findings))

    def test_malformed_ledgers_and_records_are_rejected_without_exceptions(self) -> None:
        context = make_context()
        valid = make_attestation(context)
        cases = {
            "boolean envelope schema": {"schema_version": True, "attestations": [valid]},
            "missing envelope schema": {"attestations": [valid]},
            "missing attestations": {"schema_version": 1},
            "non-list attestations": {"schema_version": 1, "attestations": valid},
            "empty attestations": make_ledger(),
            "non-object record": make_ledger("not-an-object"),
            "old record schema": make_ledger({**valid, "schema_version": 4}),
            "list record mode": make_ledger({**valid, "mode": []}),
            "object record mode": make_ledger({**valid, "mode": {}}),
        }

        for label, payload in cases.items():
            with self.subTest(label=label):
                findings = self.check(context, payload)
                self.assertTrue(findings, label)

    def test_unselected_selector_only_record_is_incomplete(self) -> None:
        full = make_context()
        changed = clone_context(full, mode="changed", base_commit="e" * 40)
        for base_commit in (changed["base_commit"], None):
            with self.subTest(base_commit=base_commit):
                selector_only = {
                    "schema_version": 5,
                    "mode": "changed",
                    "base_commit": base_commit,
                    "scope_digest": changed["scope_digest"],
                }

                findings = self.check(
                    full, make_ledger(make_attestation(full), selector_only)
                )

                self.assertIn("incomplete_semantic_review", self.codes(findings))

    def test_unselected_changed_record_requires_nonblank_base_commit(self) -> None:
        full = make_context()
        for base_commit in (None, "", "   "):
            with self.subTest(base_commit=base_commit):
                changed = clone_context(
                    full, mode="changed", base_commit=base_commit
                )

                findings = self.check(
                    full,
                    make_ledger(make_attestation(full), make_attestation(changed)),
                )

                self.assertIn("incomplete_semantic_review", self.codes(findings))

    def test_unselected_record_missing_conclusions_is_incomplete(self) -> None:
        full = make_context()
        changed = clone_context(full, mode="changed", base_commit="e" * 40)
        unselected = make_attestation(changed)
        del unselected["conclusions"]

        findings = self.check(
            full, make_ledger(make_attestation(full), unselected)
        )

        self.assertIn("incomplete_semantic_review", self.codes(findings))

    def test_unselected_record_digest_dictionaries_require_object_shape(self) -> None:
        full = make_context()
        changed = clone_context(full, mode="changed", base_commit="e" * 40)
        for field in (
            "requirement_digests", "implementation_digests", "supporting_digests"
        ):
            for invalid_value in (None, [], "not-a-dictionary"):
                with self.subTest(field=field, invalid_value=invalid_value):
                    unselected = make_attestation(changed)
                    unselected[field] = invalid_value

                    findings = self.check(
                        full, make_ledger(make_attestation(full), unselected)
                    )

                    self.assertIn("incomplete_semantic_review", self.codes(findings))

    def test_well_shaped_unselected_stale_record_does_not_invalidate_current(self) -> None:
        full = make_context()
        changed = clone_context(full, mode="changed", base_commit="e" * 40)
        stale = make_attestation(changed)
        stale["implementation_digests"] = {
            REQUIREMENT_ID: "sha256:" + "9" * 64
        }
        ledger = make_ledger(make_attestation(full), stale)

        self.assertEqual([], self.check(full, ledger))
        self.assertIn("stale_semantic_review", self.codes(self.check(changed, ledger)))

    def test_reviewer_authority_is_checked_only_for_selected_member(self) -> None:
        full = make_context()
        changed = clone_context(full, mode="changed", base_commit="e" * 40)
        unselected = make_attestation(changed)
        unselected["reviewer"] = "model:outsider"
        ledger = make_ledger(make_attestation(full), unselected)

        self.assertEqual([], self.check(full, ledger))
        self.assertIn(
            "incomplete_semantic_review", self.codes(self.check(changed, ledger))
        )


if __name__ == "__main__":
    unittest.main()
