from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from test_spec_assurance import spec_check
from test_spec_review_handoff import handoff


REQ_ID = "OPS-RELEASE-001"
POLICY_RAW = {
    "schema_version": 1,
    "authority_ref": "policy:test-owner",
    "authorized_approvers": ["human:owner"],
    "authorized_evidence_issuers": ["ci:test"],
    "authorized_semantic_reviewers": ["model:reviewer"],
}


class SupportingCoverageSecurityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.spec_dir = self.root / "docs/specification"
        self.spec_dir.mkdir(parents=True)
        self.config = spec_check.CheckerConfig()
        (self.spec_dir / "requirements.md").write_text(
            f"### {REQ_ID}: Release procedure\n\n"
            "Status: Normative\n"
            "Coverage kind: documentation\n\n"
            "The release process MUST follow the documented procedure.\n\n"
            "Verification:\n- Automated: release test.\n",
            encoding="utf-8",
        )
        (self.spec_dir / "verification-matrix.md").write_text("| Requirement | Verification | Evidence | Evidence state | Status | Verified at | Notes |\n", encoding="utf-8")
        (self.spec_dir / "approval-policy.json").write_text(json.dumps(POLICY_RAW), encoding="utf-8")
        self.policy, failures = spec_check._approval_policy_from_raw(POLICY_RAW, "policy")
        self.assertEqual([], failures)
        self.requirements = spec_check.parse_requirements(self.spec_dir, self.root, self.config)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_capabilities(
        self,
        supporting: list[dict[str, object]],
        *,
        classifications: list[dict[str, str]] | None = None,
        requirement_ids: list[str] | None = None,
        implementation: list[dict[str, object]] | None = None,
        approved: bool = True,
    ) -> Path:
        cap_path = self.spec_dir / "capability-map.json"
        body: dict[str, object] = {
            "file_classifications": classifications or [
                {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance records."},
                {"pattern": "docs/**", "category": "documentation", "reason": "Operator documentation."},
                {"pattern": "tools/**", "category": "tooling", "reason": "Release tooling."},
                {"pattern": "config/**", "category": "configuration", "reason": "Release configuration."},
                {"pattern": "src/**", "category": "implementation", "reason": "Runtime implementation."},
            ],
            "capabilities": [{
                "id": "CAP-OPS-RELEASE-001",
                "name": "Release governance",
                "status": "active",
                "requirement_ids": requirement_ids if requirement_ids is not None else [REQ_ID],
                "implementation_mappings": implementation if implementation is not None else [],
                "supporting_mappings": supporting,
            }],
        }
        approval: dict[str, str] = {
            "inventory_digest": spec_check._canonical_json_digest(body),
            "state": "approved",
            "approved_by": "human:owner",
            "approved_at": "2026-10-01T12:00:00Z",
            "decision_ref": "decision:test",
        }
        if not approved:
            approval["inventory_digest"] = "sha256:" + "0" * 64
        cap_path.write_text(json.dumps({"schema_version": 3, **body, "approval": approval}), encoding="utf-8")
        return cap_path

    def load(self) -> tuple[spec_check.CapabilityMap | None, list[spec_check.Finding], set[str]]:
        return spec_check.load_and_check_capabilities(
            self.root, self.config, self.requirements, self.policy
        )

    @staticmethod
    def codes(findings: list[spec_check.Finding]) -> set[str]:
        return {item.code for item in findings}

    def test_reclassified_canonical_assurance_file_cannot_support_itself(self) -> None:
        self.write_capabilities(
            [{"pattern": "docs/specification/requirements.md", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "Self-support."}],
            classifications=[
                {"pattern": "docs/specification/**", "category": "tooling", "reason": "Relabeled."},
                {"pattern": "docs/**", "category": "documentation", "reason": "Docs."},
            ],
        )
        _, findings, _ = self.load()
        self.assertIn("invalid_supporting_mapping", self.codes(findings))

    def test_mixed_allowed_and_forbidden_wildcard_is_rejected(self) -> None:
        (self.root / "docs/guide.md").parent.mkdir(parents=True, exist_ok=True)
        (self.root / "docs/guide.md").write_text("Procedure.\n", encoding="utf-8")
        (self.root / "docs/specification/requirements.md").write_text("Canonical.\n", encoding="utf-8")
        self.write_capabilities(
            [{"pattern": "docs/**", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "Docs."}]
        )
        _, findings, _ = self.load()
        self.assertIn("invalid_supporting_mapping", self.codes(findings))

    def test_symlink_to_external_document_is_not_accepted_as_support(self) -> None:
        outside = self.root.parent / (self.root.name + "-external.md")
        outside.write_text("External release procedure.\n", encoding="utf-8")
        link = self.root / "docs/external.md"
        link.parent.mkdir(parents=True, exist_ok=True)
        try:
            link.symlink_to(outside)
            self.write_capabilities(
                [{"pattern": "docs/external.md", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "External."}],
                classifications=[
                    {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance."},
                    {"pattern": "docs/external.md", "category": "documentation", "reason": "External documentation."},
                ],
            )
            _, findings, _ = self.load()
            self.assertIn("invalid_supporting_mapping", self.codes(findings))
        finally:
            outside.unlink(missing_ok=True)

    def test_ignored_document_cannot_realize_supporting_mapping(self) -> None:
        (self.root / ".gitignore").write_text("private/**\n", encoding="utf-8")
        (self.root / "private/runbook.md").parent.mkdir(parents=True, exist_ok=True)
        (self.root / "private/runbook.md").write_text("Ignored procedure.\n", encoding="utf-8")
        self.write_capabilities(
            [{"pattern": "private/**", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "Ignored."}],
            classifications=[
                {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance."},
                {"pattern": "private/**", "category": "documentation", "reason": "Private docs."},
            ],
        )
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        _, findings, _ = self.load()
        self.assertIn("invalid_supporting_mapping", self.codes(findings))

    def test_process_prose_alone_is_not_realization(self) -> None:
        (self.root / "docs/procedure.md").parent.mkdir(parents=True, exist_ok=True)
        (self.root / "docs/procedure.md").write_text("Release steps.\n", encoding="utf-8")
        (self.spec_dir / "requirements.md").write_text(
            f"### {REQ_ID}: Release control\n\nStatus: Normative\nCoverage kind: process\n\n"
            "The release process MUST be followed.\n\nVerification:\n- Automated: release test.\n",
            encoding="utf-8",
        )
        self.requirements = spec_check.parse_requirements(self.spec_dir, self.root, self.config)
        self.write_capabilities(
            [{"pattern": "docs/procedure.md", "coverage_kind": "process", "requirement_ids": [REQ_ID], "reason": "Runbook."}]
        )
        _, findings, _ = self.load()
        self.assertIn("invalid_supporting_mapping", self.codes(findings))

    def test_support_edit_changes_receipt_and_review_digest_inputs(self) -> None:
        doc = self.root / "docs/guide.md"
        doc.parent.mkdir(parents=True, exist_ok=True)
        doc.write_text("Original.\n", encoding="utf-8")
        self.write_capabilities(
            [{"pattern": "docs/guide.md", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "Guide."}]
        )
        cap_map, findings, _ = self.load()
        self.assertNotIn("invalid_supporting_mapping", self.codes(findings))
        before = spec_check.requirement_supporting_digest(self.root, REQ_ID, cap_map)
        before_context = spec_check.build_review_context(
            self.root, self.config, "full", None, self.requirements, cap_map, set(), set(), []
        )
        doc.write_text("Changed after evidence.\n", encoding="utf-8")
        after = spec_check.requirement_supporting_digest(self.root, REQ_ID, cap_map)
        after_context = spec_check.build_review_context(
            self.root, self.config, "full", None, self.requirements, cap_map, set(), set(), []
        )
        self.assertNotEqual(before, after)
        self.assertNotEqual(
            before_context["supporting_digests"][REQ_ID],
            after_context["supporting_digests"][REQ_ID],
        )

    def test_capability_map_removal_does_not_drop_unchanged_base_support_path(self) -> None:
        tool = self.root / "tools/release.py"
        tool.parent.mkdir(parents=True, exist_ok=True)
        tool.write_text("release()\n", encoding="utf-8")
        cap_path = self.write_capabilities(
            [{"pattern": "tools/release.py", "coverage_kind": "process", "requirement_ids": [REQ_ID], "reason": "Release tool."}],
            classifications=[
                {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance."},
                {"pattern": "tools/**", "category": "tooling", "reason": "Release tooling."},
            ],
        )
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-m", "trusted base"], cwd=self.root, check=True, capture_output=True)
        base = subprocess.run(["git", "rev-parse", "HEAD"], cwd=self.root, check=True, capture_output=True, text=True).stdout.strip()

        self.write_capabilities([], classifications=[
            {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance."},
            {"pattern": "tools/**", "category": "tooling", "reason": "Release tooling."},
        ])
        requirements = spec_check.parse_requirements(self.spec_dir, self.root, self.config)
        cap_map, _, implementations = self.load()
        context = spec_check.build_review_context(
            self.root, self.config, "changed", base, requirements, cap_map, implementations,
            {"docs/specification/capability-map.json"}, set(),
        )
        self.assertIn("tools/release.py", context["reviewed_paths"])

    def test_human_capability_approval_remains_required(self) -> None:
        self.write_capabilities([], approved=False)
        _, findings, _ = self.load()
        self.assertIn("stale_capability_approval", self.codes(findings))

    def test_implementation_required_runtime_mapping_cannot_be_replaced_by_support(self) -> None:
        (self.spec_dir / "requirements.md").write_text(
            f"### {REQ_ID}: Runtime behavior\n\nStatus: Normative\n\n"
            "The receiver MUST record bytes.\n\nVerification:\n- Automated: test.\n",
            encoding="utf-8",
        )
        self.requirements = spec_check.parse_requirements(self.spec_dir, self.root, self.config)
        (self.root / "docs/guide.md").parent.mkdir(parents=True, exist_ok=True)
        (self.root / "docs/guide.md").write_text("Docs.\n", encoding="utf-8")
        self.write_capabilities(
            [{"pattern": "docs/guide.md", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "Guide."}]
        )
        _, findings, _ = self.load()
        self.assertIn("unmapped_requirement", self.codes(findings))

    def test_removed_base_tool_keeps_base_blob_without_reading_ignored_copy(self) -> None:
        requirement_path = self.spec_dir / "requirements.md"
        requirement_path.write_text(
            f"### {REQ_ID}: Release procedure\n\nStatus: Normative\nCoverage kind: process\n\n"
            "The release process MUST use the approved tool.\n\nVerification:\n- Automated: release test.\n",
            encoding="utf-8",
        )
        self.requirements = spec_check.parse_requirements(
            self.spec_dir, self.root, self.config
        )
        old_tool = self.root / "tools/old-release.py"
        old_tool.parent.mkdir(parents=True, exist_ok=True)
        old_tool.write_text("trusted base implementation\n", encoding="utf-8")
        (self.root / ".gitignore").write_text("tools/old-release.py\n", encoding="utf-8")
        base_classifications = [
            {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance records."},
            {"pattern": "tools/old-release.py", "category": "tooling", "reason": "Release tooling."},
            {"pattern": ".gitignore", "category": "metadata", "reason": "Ignore rules."},
        ]
        self.write_capabilities(
            [{"pattern": "tools/old-release.py", "coverage_kind": "process", "requirement_ids": [REQ_ID], "reason": "Trusted release tool."}],
            classifications=base_classifications,
        )
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        subprocess.run(["git", "add", "-f", "tools/old-release.py"], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-m", "trusted old release tool"], cwd=self.root, check=True, capture_output=True)
        base = subprocess.run(["git", "rev-parse", "HEAD"], cwd=self.root, check=True, capture_output=True, text=True).stdout.strip()

        subprocess.run(["git", "rm", "--cached", "-q", "tools/old-release.py"], cwd=self.root, check=True)
        new_tool = self.root / "tools/new-release.py"
        new_tool.write_text("replacement release tool\n", encoding="utf-8")
        local_copy = old_tool
        local_copy.write_text("ignored private copy one\n", encoding="utf-8")
        candidate_classifications = [
            {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance records."},
            {"pattern": "tools/new-release.py", "category": "tooling", "reason": "Replacement release tooling."},
            {"pattern": "tools/old-release.py", "category": "local", "reason": "Ignored local copy."},
            {"pattern": ".gitignore", "category": "metadata", "reason": "Ignore rules."},
        ]
        self.write_capabilities(
            [{"pattern": "tools/new-release.py", "coverage_kind": "process", "requirement_ids": [REQ_ID], "reason": "Replacement release tool."}],
            classifications=candidate_classifications,
        )
        requirements = spec_check.parse_requirements(self.spec_dir, self.root, self.config)
        capability_map, findings, implementations = self.load()
        self.assertNotIn("invalid_supporting_mapping", self.codes(findings))
        changed = {"tools/old-release.py", "docs/specification/capability-map.json"}
        original_read_bytes = Path.read_bytes
        ignored_reads: list[str] = []
        original_project_read = spec_check._project_file_bytes
        project_reads: list[str] = []

        def guarded_read_bytes(path: Path) -> bytes:
            if path == local_copy:
                ignored_reads.append(str(path))
            return original_read_bytes(path)

        def guarded_project_read(root: Path, path: str, label: str) -> bytes:
            if path == "tools/old-release.py":
                project_reads.append(path)
            return original_project_read(root, path, label)

        def build() -> dict:
            return spec_check.build_review_context(
                self.root, self.config, "changed", base, requirements, capability_map,
                implementations, changed, {REQ_ID},
            )

        with patch.object(Path, "read_bytes", guarded_read_bytes), patch.object(
            spec_check, "_project_file_bytes", guarded_project_read
        ):
            context_before = build()
            local_copy.write_text("ignored private copy two\n", encoding="utf-8")
            context_after = build()

        self.assertEqual([], ignored_reads)
        self.assertEqual([], project_reads)
        self.assertEqual(context_before["scope_digest"], context_after["scope_digest"])
        self.assertIn("tools/old-release.py", context_before["reviewed_paths"])
        base_blob = next(
            item for item in context_before["paths"] if item["path"] == "tools/old-release.py"
        )
        self.assertEqual("deleted", base_blob["digest"])
        self.assertTrue(base_blob["base_ref"].endswith(":tools/old-release.py"))
        self.assertTrue(base_blob["base_digest"].startswith("sha256:"))

    def test_shared_support_path_closes_requirement_ids_for_handoff(self) -> None:
        req_a = "OPS-ALPHA-001"
        req_b = "OPS-BETA-001"
        (self.spec_dir / "requirements.md").unlink()
        (self.spec_dir / "a.md").write_text(
            f"### {req_a}: Alpha release\n\nStatus: Normative\nCoverage kind: process\n\n"
            "Alpha MUST use the shared release control.\n\nVerification:\n- Automated: alpha test.\n",
            encoding="utf-8",
        )
        (self.spec_dir / "b.md").write_text(
            f"### {req_b}: Beta release\n\nStatus: Normative\nCoverage kind: process\n\n"
            "Beta MUST use the shared release control.\n\nVerification:\n- Automated: beta test.\n",
            encoding="utf-8",
        )
        self.requirements = spec_check.parse_requirements(self.spec_dir, self.root, self.config)
        shared = self.root / "tools/shared-release.py"
        shared.parent.mkdir(parents=True, exist_ok=True)
        shared.write_text("shared release control\n", encoding="utf-8")
        beta_config = self.root / "config/beta-release.toml"
        beta_config.parent.mkdir(parents=True, exist_ok=True)
        beta_config.write_text("enabled = true\n", encoding="utf-8")
        classifications = [
            {"pattern": "docs/specification/**", "category": "specification", "reason": "Assurance records."},
            {"pattern": "tools/**", "category": "tooling", "reason": "Shared release tooling."},
            {"pattern": "config/**", "category": "configuration", "reason": "Release settings."},
        ]
        mappings = [
            {"pattern": "tools/shared-release.py", "coverage_kind": "process", "requirement_ids": [req_a, req_b], "reason": "Shared realization."},
            {"pattern": "config/beta-release.toml", "coverage_kind": "process", "requirement_ids": [req_b], "reason": "Beta release setting."},
        ]
        self.write_capabilities(
            mappings, classifications=classifications, requirement_ids=[req_a, req_b]
        )
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-m", "shared alpha beta baseline"], cwd=self.root, check=True, capture_output=True)
        base = subprocess.run(["git", "rev-parse", "HEAD"], cwd=self.root, check=True, capture_output=True, text=True).stdout.strip()

        (self.spec_dir / "a.md").write_text(
            f"### {req_a}: Alpha release\n\nStatus: Normative\nCoverage kind: process\n\n"
            "Alpha MUST use the updated shared release control.\n\nVerification:\n- Automated: alpha test.\n",
            encoding="utf-8",
        )
        capability_map, findings, implementations = self.load()
        self.assertNotIn("invalid_supporting_mapping", self.codes(findings))
        self.config.user_documentation_paths = ["README.md"]
        context = spec_check.build_review_context(
            self.root, self.config, "changed", base, self.requirements, capability_map,
            implementations, {"docs/specification/a.md"}, {req_a},
        )
        self.assertIn(req_b, context["requirement_ids"])
        self.assertIn(req_b, context["supporting_digests"])
        self.assertTrue(any(
            item["path"] == "docs/specification/b.md" and req_b in item["requirement_ids"]
            for item in context["requirement_traceability"]
        ))
        self.assertIn("docs/specification/b.md", context["reviewed_paths"])
        pair_ids = {
            requirement_id
            for key in ("implementation_traceability", "supporting_traceability", "requirement_traceability")
            for item in context[key]
            for requirement_id in item["requirement_ids"]
        }
        self.assertLessEqual(pair_ids, set(context["requirement_ids"]))
        paths, pairs = handoff._context_coverage(
            context, {"mode": "changed", "base_commit": context["base_commit"]}, "adversarial test context"
        )
        self.assertIn("docs/specification/b.md", paths)
        self.assertIn(("docs/specification/b.md", req_b), pairs)

    def test_normalized_spec_and_explicit_control_paths_cannot_self_support(self) -> None:
        self.config.spec_dir = "./docs/specification"
        self.requirements = spec_check.parse_requirements(
            self.spec_dir, self.root, self.config
        )
        (self.spec_dir / "requirements.md").write_text(
            f"### {REQ_ID}: Release procedure\n\nStatus: Normative\nCoverage kind: documentation\n\n"
            "The release process MUST follow the documented procedure.\n\nVerification:\n- Automated: release test.\n",
            encoding="utf-8",
        )
        self.requirements = spec_check.parse_requirements(self.spec_dir, self.root, self.config)
        self.write_capabilities(
            [{"pattern": "docs/specification/requirements.md", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "Canonical spec."}],
            classifications=[
                {"pattern": "docs/specification/**", "category": "documentation", "reason": "Relabeled canonical docs."},
            ],
        )
        _, spec_findings, _ = self.load()
        with self.subTest("normalized spec directory"):
            self.assertIn("invalid_supporting_mapping", self.codes(spec_findings))
        for root_spelling in (".", "./"):
            with self.subTest("project-root specification directory", spelling=root_spelling):
                self.config.spec_dir = root_spelling
                _, root_findings, _ = self.load()
                self.assertIn("invalid_supporting_mapping", self.codes(root_findings))

        self.config.spec_dir = "docs/specification"
        self.config.approvals = "./security/approvals.json"
        control = self.root / "security/approvals.json"
        control.parent.mkdir(parents=True, exist_ok=True)
        control.write_text("human approval record\n", encoding="utf-8")
        self.write_capabilities(
            [{"pattern": "security/approvals.json", "coverage_kind": "documentation", "requirement_ids": [REQ_ID], "reason": "Assurance control."}],
            classifications=[
                {"pattern": "docs/specification/**", "category": "specification", "reason": "Canonical specification."},
                {"pattern": "security/approvals.json", "category": "documentation", "reason": "Relabeled control."},
            ],
        )
        _, control_findings, _ = self.load()
        with self.subTest("normalized explicit control path"):
            self.assertIn("invalid_supporting_mapping", self.codes(control_findings))


if __name__ == "__main__":
    unittest.main()
