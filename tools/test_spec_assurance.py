from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parent / "specification" / "spec_check.py"
BUNDLE_ROOT = Path(__file__).parents[1]
SPEC = importlib.util.spec_from_file_location("spec_check", SCRIPT)
assert SPEC and SPEC.loader
spec_check = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = spec_check
SPEC.loader.exec_module(spec_check)


class SpecProject:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.spec_dir = root / "docs" / "specification"
        self.spec_dir.mkdir(parents=True)

    def write_requirement(
        self,
        requirement_id: str = "CORE-FLOW-001",
        status: str = "Normative",
        verification: str = "Verification:\n- Automated: test evidence.\n",
        provenance: str | None = None,
        evidence_obligations: str | None = None,
        superseded_by: str | None = None,
    ) -> Path:
        path = self.spec_dir / "requirements.md"
        suffix = ""
        if evidence_obligations is not None:
            suffix += f"\nEvidence obligations: {evidence_obligations}\n"
        if provenance is not None:
            suffix += f"\nProvenance: {provenance}\n"
        if superseded_by is not None:
            suffix += f"\nSuperseded by: {superseded_by}\n"
        path.write_text(
            f"### {requirement_id}: Example requirement\n\n"
            f"Status: {status}\n\n"
            "The system MUST preserve the example behaviour.\n\n"
            f"{verification}{suffix}",
            encoding="utf-8",
        )
        return path

    def write_matrix(
        self,
        requirement_id: str = "CORE-FLOW-001",
        evidence: str = "manual:example-check",
        status: str = "Open",
    ) -> Path:
        path = self.spec_dir / "verification-matrix.md"
        path.write_text(
            "| Requirement | Type | Evidence | Status | Notes |\n"
            "| --- | --- | --- | --- | --- |\n"
            f"| `{requirement_id}` | Manual | {evidence} | {status} | pending |\n",
            encoding="utf-8",
        )
        return path

    def write_strict_contract(self) -> None:
        source = self.root / "src" / "feature.py"
        source.parent.mkdir()
        source.write_text("FEATURE = True\n", encoding="utf-8")
        self.write_requirement(
            provenance="user-decision:test-contract",
            evidence_obligations="EV-CORE-FLOW-001",
        )
        (self.spec_dir / "approval-policy.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "authority_ref": "policy:test-authority",
                    "authorized_approvers": ["human:product-owner"],
                    "authorized_evidence_issuers": ["ci:test"],
                    "authorized_semantic_reviewers": ["model:test-reviewer"],
                }
            ),
            encoding="utf-8",
        )
        config = spec_check.load_config(self.root, None)
        requirements = spec_check.parse_requirements(self.spec_dir, self.root, config)
        requirement = requirements[0]
        (self.spec_dir / "requirement-approvals.json").write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "approvals": [
                        {
                            "requirement_id": requirement.id,
                            "requirement_digest": requirement.digest,
                            "state": "approved",
                            "approved_by": "human:product-owner",
                            "approved_at": "2026-07-19T12:00:00Z",
                            "decision_ref": "user-decision:test-contract",
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        artifact = self.spec_dir / "evidence" / "EV-CORE-FLOW-001.txt"
        artifact.parent.mkdir()
        artifact.write_text("fixture test passed\n", encoding="utf-8")
        subprocess.run(["git", "init"], cwd=self.root, check=True, capture_output=True)
        subprocess.run(
            ["git", "add", "docs/specification/evidence/EV-CORE-FLOW-001.txt"],
            cwd=self.root,
            check=True,
            capture_output=True,
        )
        capability_body = {
            "file_classifications": [
                {
                    "pattern": "src/**",
                    "category": "implementation",
                    "reason": "Product implementation source.",
                },
                {
                    "pattern": "docs/specification/**",
                    "category": "specification",
                    "reason": "Specification assurance records and evidence.",
                },
            ],
            "capabilities": [
                {
                    "id": "CAP-CORE-FLOW-001",
                    "name": "Example capability",
                    "status": "active",
                    "requirement_ids": [requirement.id],
                    "implementation_mappings": [
                        {
                            "pattern": "src/feature.py",
                            "requirement_ids": [requirement.id],
                        }
                    ],
                }
            ],
        }
        capability_payload = {
            "schema_version": 3,
            **capability_body,
            "approval": {
                "inventory_digest": spec_check._canonical_json_digest(capability_body),
                "state": "approved",
                "approved_by": "human:product-owner",
                "approved_at": "2026-07-19T12:00:00Z",
                "decision_ref": "user-decision:test-capabilities",
            },
        }
        (self.spec_dir / "capability-map.json").write_text(
            json.dumps(capability_payload), encoding="utf-8"
        )
        policy, policy_findings = spec_check.load_and_check_approval_policy(
            self.root, config
        )
        self.assert_no_failures(policy_findings)
        capability_map, capability_findings, implementation_files = (
            spec_check.load_and_check_capabilities(
                self.root, config, requirements, policy
            )
        )
        self.assert_no_failures(capability_findings)
        implementation_digest = spec_check.requirement_implementation_digest(
            self.root,
            requirement.id,
            capability_map,
            implementation_files,
        )
        verified_at = "2026-07-19T12:00:00Z"
        (self.spec_dir / "evidence-receipts.json").write_text(
            json.dumps(
                {
                    "schema_version": 2,
                    "receipts": [
                        {
                            "id": "EV-CORE-FLOW-001",
                            "requirement_id": requirement.id,
                            "requirement_digest": requirement.digest,
                            "implementation_digest": implementation_digest,
                            "supporting_digest": spec_check.requirement_supporting_digest(self.root, requirement.id, capability_map),
                            "kind": "test",
                            "state": "passed",
                            "target": "src/feature.py#FEATURE",
                            "verified_at": verified_at,
                            "verified_commit": "test-worktree",
                            "runner": "ci:test",
                            "artifact_ref": "artifact:docs/specification/evidence/EV-CORE-FLOW-001.txt",
                            "artifact_digest": spec_check._sha256_bytes(
                                artifact.read_bytes()
                            ),
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        (self.spec_dir / "verification-matrix.md").write_text(
            "| Requirement | Verification | Evidence | Evidence state | Status | Verified at | Notes |\n"
            "| --- | --- | --- | --- | --- | --- | --- |\n"
            f"| `{requirement.id}` | Automated | receipt:EV-CORE-FLOW-001 | passed | Passing | {verified_at} | Current |\n",
            encoding="utf-8",
        )
        requirements, findings, _ = spec_check.run_checks(self.root, config)
        self.assert_no_failures(findings)
        capability_map, _, implementation_files = spec_check.load_and_check_capabilities(
            self.root, config, requirements
        )
        context = spec_check.build_review_context(
            self.root,
            config,
            "full",
            None,
            requirements,
            capability_map,
            implementation_files,
            set(),
            [],
        )
        attestation = {
            key: context[key]
            for key in (
                "schema_version",
                "mode",
                "base_commit",
                "observed_head_commit",
                "scope_digest",
                "capability_inventory_digest",
                "approval_policy_digest",
                "reviewed_paths",
                "requirement_ids",
                "implementation_traceability",
                "requirement_traceability",
                "requirement_digests",
                "implementation_digests",
                "supporting_digests",
                "supporting_traceability",
                "coverage_kinds",
            )
        }
        attestation.update(
            {
                "result": "pass",
                "reviewer": "model:test-reviewer",
                "reviewed_at": verified_at,
                "findings": [],
                "limitations": [],
                "conclusions": [
                    {
                        "path": entry["path"],
                        "requirement_id": requirement_id,
                        "result": "pass",
                        "notes": "Fixture implementation satisfies the requirement.",
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
        (self.spec_dir / "semantic-review.json").write_text(
            json.dumps(attestation), encoding="utf-8"
        )

    @staticmethod
    def assert_no_failures(findings: list[object]) -> None:
        failures = [item for item in findings if getattr(item, "severity", "") == "failure"]
        if failures:
            raise AssertionError([getattr(item, "code", "unknown") for item in failures])


class MarkdownParserTest(unittest.TestCase):
    def test_split_row_preserves_escaped_pipe_and_code_span(self) -> None:
        cells = spec_check.split_markdown_row(
            r"| `CORE-FLOW-001` | Review | source:docs/a\|b.md | Open | use `a|b` |"
        )
        self.assertEqual("source:docs/a|b.md", cells[2])
        self.assertEqual("use `a|b`", cells[4])


class ConfigurationTest(unittest.TestCase):
    def test_canonical_paths_cannot_escape_project(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            config_path = project.spec_dir / "spec-config.yaml"
            config_path.write_text(
                json.dumps(
                    {
                        "model_version": spec_check.SCHEMA_VERSION,
                        "approvals": "../../outside.json",
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                spec_check.ConfigurationError, "escapes the project root"
            ):
                spec_check.load_config(project.root, config_path)


    def test_project_status_aliases_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement(status="Required")
            project.write_matrix(status="Ready")
            config_path = project.spec_dir / "spec-config.yaml"
            config_path.write_text(
                json.dumps(
                    {
                        "requirement_status_aliases": {"Required": "Normative"},
                        "matrix_status_aliases": {"Ready": "Passing"},
                    }
                ),
                encoding="utf-8",
            )

            config = spec_check.load_config(project.root, config_path)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertNotIn("unknown_requirement_status", {finding.code for finding in findings})
            self.assertNotIn("unknown_matrix_status", {finding.code for finding in findings})

    def test_strict_mode_cannot_disable_assurance_controls(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            config_path = project.spec_dir / "spec-config.yaml"
            config_path.write_text(
                json.dumps(
                    {
                        "model_version": spec_check.SCHEMA_VERSION,
                        "require_human_approvals": False,
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                spec_check.ConfigurationError,
                "Strict mode cannot disable assurance controls",
            ):
                spec_check.load_config(project.root, config_path)

    def test_migration_mode_requires_human_approval_and_expiry(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            config_path = project.spec_dir / "spec-config.yaml"
            config_path.write_text(
                json.dumps(
                    {
                        "model_version": spec_check.SCHEMA_VERSION,
                        "enforcement_mode": "migration",
                        "require_human_approvals": False,
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                spec_check.ConfigurationError,
                "migration_approved_by",
            ):
                spec_check.load_config(project.root, config_path)

    def test_user_documentation_patterns_must_be_safe(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            config_path = project.spec_dir / "spec-config.yaml"
            config_path.write_text(
                json.dumps({"user_documentation_paths": ["../outside/**"]}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(spec_check.ConfigurationError, "user_documentation_paths"):
                spec_check.load_config(project.root, config_path)

    def test_unknown_requirement_lifecycle_is_a_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement(status="Normativ")
            project.write_matrix()

            _, findings, _ = spec_check.run_checks(
                project.root, spec_check.load_config(project.root, None)
            )

            finding = next(item for item in findings if item.code == "unknown_requirement_status")
            self.assertEqual("failure", finding.severity)

    def test_deprecated_requirement_requires_explicit_tombstone_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement(status="Deprecated", provenance="decision:retire")

            _, findings, _ = spec_check.run_checks(
                project.root, spec_check.load_config(project.root, None)
            )

            self.assertIn(
                "invalid_requirement_retirement", {item.code for item in findings}
            )

    def test_deprecated_requirement_rejects_self_or_missing_successor(self) -> None:
        for target in ("CORE-FLOW-001", "OTHER-FLOW-001"):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                project = SpecProject(Path(directory))
                project.write_requirement(
                    status="Deprecated",
                    provenance="decision:retire",
                    superseded_by=target,
                )

                _, findings, _ = spec_check.run_checks(
                    project.root, spec_check.load_config(project.root, None)
                )

                self.assertIn(
                    "invalid_requirement_retirement", {item.code for item in findings}
                )

    def test_deprecated_requirement_accepts_durable_decision_reference(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement(
                status="Deprecated",
                provenance="decision:retire",
                superseded_by="decision:architecture-record-17",
            )

            _, findings, _ = spec_check.run_checks(
                project.root, spec_check.load_config(project.root, None)
            )

            self.assertNotIn(
                "invalid_requirement_retirement", {item.code for item in findings}
            )

    def test_deprecated_requirement_accepts_existing_normative_successor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            (project.spec_dir / "requirements.md").write_text(
                "### CORE-FLOW-001: Retired requirement\n\n"
                "Status: Deprecated\n\n"
                "Superseded by: CORE-FLOW-002\n\n"
                "Provenance: decision:retire\n\n"
                "### CORE-FLOW-002: Current requirement\n\n"
                "Status: Normative\n\n"
                "The system MUST preserve current behavior.\n\n"
                "Verification:\n- Automated: current behavior.\n\n"
                "Evidence obligations: EV-CORE-FLOW-002\n\n"
                "Provenance: decision:replacement\n",
                encoding="utf-8",
            )

            _, findings, _ = spec_check.run_checks(
                project.root, spec_check.load_config(project.root, None)
            )

            self.assertNotIn(
                "invalid_requirement_retirement", {item.code for item in findings}
            )

    def test_canonical_requirement_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            external = Path(directory).parent / f"{Path(directory).name}-outside.md"
            external.write_text(
                "### CORE-FLOW-001: External\n\nStatus: Normative\n",
                encoding="utf-8",
            )
            link = project.spec_dir / "requirements.md"
            link.symlink_to(external)
            try:
                with self.assertRaisesRegex(
                    spec_check.ConfigurationError, "symbolic links"
                ):
                    spec_check.parse_requirements(
                        project.spec_dir,
                        project.root,
                        spec_check.load_config(project.root, None),
                    )
            finally:
                external.unlink(missing_ok=True)


class DistributionContractTest(unittest.TestCase):
    def test_owner_governance_exemption_uses_pr_author_not_merge_actor(self) -> None:
        workflow = (BUNDLE_ROOT / ".github/workflows/spec-compliance.yml").read_text(encoding="utf-8")
        self.assertIn("if: github.event_name == 'pull_request_target' && github.event.pull_request.user.login != 'holubp'", workflow)
        self.assertIn("if: github.event_name != 'pull_request_target'", workflow)
        self.assertIn("--trusted-base-controls", workflow)

    def test_shared_templates_match_current_checker_schemas(self) -> None:
        templates = BUNDLE_ROOT / "templates" / "specification-assurance"
        config = json.loads((templates / "spec-config.yaml").read_text(encoding="utf-8"))
        capabilities = json.loads(
            (templates / "capability-map.json").read_text(encoding="utf-8")
        )
        policy = json.loads(
            (templates / "approval-policy.json").read_text(encoding="utf-8")
        )
        semantic = json.loads(
            (templates / "semantic-review.json").read_text(encoding="utf-8")
        )
        evidence = json.loads(
            (templates / "evidence-receipts.json").read_text(encoding="utf-8")
        )

        self.assertEqual(spec_check.SCHEMA_VERSION, config["model_version"])
        self.assertEqual(
            "docs/specification/approval-policy.json", config["approval_policy"]
        )
        self.assertEqual(3, capabilities["schema_version"])
        self.assertTrue(capabilities["file_classifications"])
        self.assertTrue(
            capabilities["capabilities"][0]["implementation_mappings"]
        )
        self.assertEqual(1, policy["schema_version"])
        self.assertTrue(policy["authorized_approvers"])
        self.assertEqual(spec_check.SEMANTIC_SCHEMA_VERSION, semantic["schema_version"])
        for key in (
            "approval_policy_digest",
            "implementation_traceability",
            "requirement_traceability",
            "conclusions",
        ):
            self.assertIn(key, semantic)
        receipt = evidence["receipts"][0]
        self.assertTrue(receipt["artifact_ref"].startswith("artifact:"))
        self.assertTrue(receipt["artifact_digest"].startswith("sha256:"))

        workflow = (
            BUNDLE_ROOT
            / ".github"
            / "workflows"
            / "spec-compliance.yml"
        ).read_text(encoding="utf-8")
        self.assertIn("trusted-base/tools/specification/spec_check.py", workflow)
        self.assertNotIn("candidate/tools/specification/spec_check.py", workflow)
        self.assertIn("--trusted-base-controls", workflow)

class EvidenceTest(unittest.TestCase):
    def test_evidence_path_cannot_escape_project(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix(evidence="source:../../etc/passwd")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("invalid_evidence_path", {finding.code for finding in findings})

    def test_missing_structured_evidence_path_is_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix(evidence="test:tests/missing_test.py#test_example")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("missing_evidence_path", {finding.code for finding in findings})

    def test_existing_path_and_symbol_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            test_path = project.root / "tests" / "test_example.py"
            test_path.parent.mkdir()
            test_path.write_text("def test_example():\n    pass\n", encoding="utf-8")
            project.write_matrix(evidence="test:tests/test_example.py#test_example")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            codes = {finding.code for finding in findings}
            self.assertNotIn("missing_evidence_path", codes)
            self.assertNotIn("missing_evidence_symbol", codes)

    def test_missing_symbol_is_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            source_path = project.root / "src" / "feature.py"
            source_path.parent.mkdir()
            source_path.write_text("def existing_symbol():\n    pass\n", encoding="utf-8")
            project.write_matrix(evidence="source:src/feature.py#missing_symbol")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("missing_evidence_symbol", {finding.code for finding in findings})


class RequirementIntegrityTest(unittest.TestCase):
    def test_provenance_can_be_required_by_project_policy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix()
            config_path = project.spec_dir / "spec-config.yaml"
            config_path.write_text('{"require_provenance": true}', encoding="utf-8")

            config = spec_check.load_config(project.root, config_path)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("missing_provenance", {finding.code for finding in findings})

    def test_duplicate_requirement_and_matrix_ids_are_failures(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            first = project.write_requirement()
            duplicate = project.spec_dir / "duplicate.md"
            duplicate.write_text(first.read_text(encoding="utf-8"), encoding="utf-8")
            matrix = project.write_matrix()
            matrix.write_text(matrix.read_text(encoding="utf-8") + matrix.read_text(encoding="utf-8").splitlines()[-1] + "\n", encoding="utf-8")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            codes = {finding.code for finding in findings}
            self.assertIn("duplicate_requirement_id", codes)
            self.assertIn("duplicate_matrix_row", codes)

    def test_implementation_maturity_is_not_requirement_lifecycle(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement(status="Implemented, not field-tested")
            project.write_matrix()

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("unknown_requirement_status", {finding.code for finding in findings})


class BaselineTest(unittest.TestCase):
    @staticmethod
    def approval_policy() -> object:
        return spec_check.ApprovalPolicy(
            authority_ref="policy:test-authority",
            authorized_approvers=("human:product-owner",),
            authorized_evidence_issuers=("ci:test",),
            authorized_semantic_reviewers=("model:test-reviewer",),
            digest="sha256:" + "0" * 64,
        )

    def test_line_number_movement_does_not_change_fingerprint(self) -> None:
        first = spec_check.Finding(
            severity="failure",
            code="duplicate_requirement_id",
            message="CORE-FLOW-001 appears at docs/spec.md:10",
            requirement_id="CORE-FLOW-001",
        )
        moved = spec_check.Finding(
            severity="failure",
            code="duplicate_requirement_id",
            message="CORE-FLOW-001 appears at docs/spec.md:42",
            requirement_id="CORE-FLOW-001",
        )

        self.assertEqual(first.fingerprint, moved.fingerprint)

    def test_written_baseline_round_trips(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "baseline.json"
            finding = spec_check.Finding(
                severity="failure",
                code="missing_matrix_row",
                message="CORE-FLOW-001 is missing",
                path="docs/specification/requirements.md",
                requirement_id="CORE-FLOW-001",
            )

            spec_check.write_baseline(
                path,
                [finding],
                "reviewed existing debt",
                "human:product-owner",
                "decision:test-baseline",
                "2099-12-31",
            )
            baseline = spec_check.load_baseline(path)

            self.assertIn(finding.fingerprint, baseline)
            self.assertEqual("reviewed existing debt", baseline[finding.fingerprint].reason)

    def test_baseline_requires_current_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "baseline.json"
            path.write_text(json.dumps({"findings": []}), encoding="utf-8")

            with self.assertRaisesRegex(spec_check.ConfigurationError, "schema_version 6"):
                spec_check.load_baseline(path)

    def test_exact_baseline_finding_is_nonblocking_in_changed_mode(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            requirement_path = project.write_requirement()
            project.write_matrix(requirement_id="OTHER-FLOW-001")
            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)
            target = next(f for f in findings if f.code == "missing_matrix_row")
            baseline = {
                target.fingerprint: spec_check.BaselineEntry.from_finding(
                    target,
                    "accepted debt",
                    "human:product-owner",
                    "decision:test-baseline",
                    "2099-12-31",
                )
            }

            classified = spec_check.classify_findings(
                findings,
                mode="changed",
                config=config,
                baseline=baseline,
                changed_paths={str(requirement_path.relative_to(project.root))},
                policy=self.approval_policy(),
            )

            result = next(f for f in classified if f.code == "missing_matrix_row")
            self.assertTrue(result.baseline_accepted)
            self.assertFalse(result.blocking)

    def test_changed_fingerprint_is_not_suppressed(self) -> None:
        finding = spec_check.Finding(
            severity="failure",
            code="missing_matrix_row",
            message="CORE-FLOW-001 is missing",
            path="docs/specification/requirements.md",
            requirement_id="CORE-FLOW-001",
        )
        baseline = {
            "different": spec_check.BaselineEntry.from_finding(
                finding,
                "old finding",
                "human:product-owner",
                "decision:test-baseline",
                "2099-12-31",
            )
        }
        config = spec_check.CheckerConfig()

        classified = spec_check.classify_findings(
            [finding],
            mode="changed",
            config=config,
            baseline=baseline,
            changed_paths={"docs/specification/requirements.md"},
        )

        self.assertTrue(classified[0].blocking)
        self.assertFalse(classified[0].baseline_accepted)

    def test_full_mode_still_blocks_accepted_debt(self) -> None:
        finding = spec_check.Finding(
            severity="failure",
            code="missing_matrix_row",
            message="CORE-FLOW-001 is missing",
            path="docs/specification/requirements.md",
            requirement_id="CORE-FLOW-001",
        )
        baseline = {
            finding.fingerprint: spec_check.BaselineEntry.from_finding(
                finding,
                "accepted debt",
                "human:product-owner",
                "decision:test-baseline",
                "2099-12-31",
            )
        }

        classified = spec_check.classify_findings(
            [finding],
            mode="full",
            config=spec_check.CheckerConfig(),
            baseline=baseline,
            changed_paths=set(),
            policy=self.approval_policy(),
        )

        self.assertTrue(classified[0].baseline_accepted)
        self.assertTrue(classified[0].blocking)


class ChangedModeTest(unittest.TestCase):
    def test_renames_report_old_and_new_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init"], cwd=root, check=True, capture_output=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=root, check=True)
            (root / "old.txt").write_text("same\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-m", "base"], cwd=root, check=True, capture_output=True)
            (root / "old.txt").rename(root / "new.txt")

            changed = spec_check.git_changed_paths(root, "HEAD")

            self.assertEqual({"old.txt", "new.txt"}, changed)

    def test_strict_changed_mode_blocks_existing_unapproved_failure(self) -> None:
        finding = spec_check.Finding(
            severity="failure",
            code="missing_requirement_approval",
            message="OLD-FLOW-001 is unapproved",
            path="docs/specification/old.md",
            requirement_id="OLD-FLOW-001",
        )

        spec_check.classify_findings(
            [finding],
            mode="changed",
            config=spec_check.CheckerConfig(),
            baseline={},
            changed_paths={"src/new_feature.py"},
        )

        self.assertTrue(finding.blocking)

    def test_unchanged_legacy_failure_does_not_block_changed_mode(self) -> None:
        finding = spec_check.Finding(
            severity="failure",
            code="missing_matrix_row",
            message="OLD-FLOW-001 is missing",
            path="docs/specification/old.md",
            requirement_id="OLD-FLOW-001",
        )

        classified = spec_check.classify_findings(
            [finding],
            mode="changed",
            config=spec_check.CheckerConfig(enforcement_mode="migration"),
            baseline={},
            changed_paths={"src/new_feature.py"},
        )

        self.assertFalse(classified[0].blocking)

    def test_changed_requirement_ids_use_actual_matrix_diff(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            matrix = project.write_matrix()
            subprocess.run(["git", "init"], cwd=project.root, check=True, capture_output=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "baseline"], cwd=project.root, check=True, capture_output=True)
            matrix.write_text(
                matrix.read_text(encoding="utf-8").replace("| Open |", "| Passing |"),
                encoding="utf-8",
            )

            config = spec_check.load_config(project.root, None)
            requirements, _, _ = spec_check.run_checks(project.root, config)
            changed_paths = spec_check.git_changed_paths(project.root, "HEAD")
            changed_ids = spec_check.changed_requirement_ids(
                project.root,
                "HEAD",
                requirements,
                changed_paths,
                config.matrix,
            )

            self.assertEqual(["CORE-FLOW-001"], changed_ids)


class AssuranceRegressionTest(unittest.TestCase):
    def test_complete_strict_contract_has_current_status_and_review(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            config = spec_check.load_config(project.root, None)
            requirements, findings, rows = spec_check.run_checks(project.root, config)
            capability_map, _, implementation_files = spec_check.load_and_check_capabilities(
                project.root, config, requirements
            )
            context = spec_check.build_review_context(
                project.root,
                config,
                "full",
                None,
                requirements,
                capability_map,
                implementation_files,
                set(),
                [],
            )
            findings.extend(spec_check.check_semantic_review(project.root, config, context))

            self.assertFalse([item for item in findings if item.severity == "failure"])
            self.assertEqual("passed", rows[0].computed_evidence_state)
            self.assertEqual("Passing", rows[0].computed_implementation_status)

    def test_unauthorized_requirement_approver_is_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            path = project.spec_dir / "requirement-approvals.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["approvals"][0]["approved_by"] = "human:outsider"
            path.write_text(json.dumps(payload), encoding="utf-8")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("unauthorized_approver", {item.code for item in findings})

    def test_unclassified_project_file_is_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            (project.root / "unexpected.txt").write_text("unclassified\n", encoding="utf-8")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn(
                "unclassified_project_file", {item.code for item in findings}
            )

    def test_ignored_local_file_is_classified_but_not_reviewed_or_hashed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            (project.root / ".gitignore").write_text("private/\n", encoding="utf-8")
            private = project.root / "private" / "token.md"
            private.parent.mkdir()
            private.write_bytes(b"\xff\xfe-do-not-read")
            capability_path = project.spec_dir / "capability-map.json"
            payload = json.loads(capability_path.read_text(encoding="utf-8"))
            payload["file_classifications"].append(
                {"pattern": "private/**", "category": "local", "reason": "Private machine state."}
            )
            payload["file_classifications"].append(
                {"pattern": ".gitignore", "category": "metadata", "reason": "Git ignore policy."}
            )
            body = {
                "file_classifications": payload["file_classifications"],
                "capabilities": payload["capabilities"],
            }
            payload["approval"]["inventory_digest"] = spec_check._canonical_json_digest(body)
            capability_path.write_text(json.dumps(payload), encoding="utf-8")
            requirements = spec_check.parse_requirements(project.spec_dir, project.root, spec_check.load_config(project.root, None))
            capability_map, findings, implementation_files = spec_check.load_and_check_capabilities(
                project.root, spec_check.load_config(project.root, None), requirements
            )
            self.assertNotIn("unclassified_project_file", {finding.code for finding in findings})
            context = spec_check.build_review_context(
                project.root, spec_check.load_config(project.root, None), "full", None,
                requirements, capability_map, implementation_files, set(), [],
            )
            self.assertNotIn("private/token.md", context["reviewed_paths"])
            spec_check.run_checks(project.root, spec_check.load_config(project.root, None))

    def test_nonignored_file_cannot_use_local_classification(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            (project.root / "checkpoint.json").write_text("{}\n", encoding="utf-8")
            capability_path = project.spec_dir / "capability-map.json"
            payload = json.loads(capability_path.read_text(encoding="utf-8"))
            payload["file_classifications"].append(
                {"pattern": "checkpoint.json", "category": "local", "reason": "Local progress state."}
            )
            body = {
                "file_classifications": payload["file_classifications"],
                "capabilities": payload["capabilities"],
            }
            payload["approval"]["inventory_digest"] = spec_check._canonical_json_digest(body)
            capability_path.write_text(json.dumps(payload), encoding="utf-8")

            requirements = spec_check.parse_requirements(
                project.spec_dir, project.root, spec_check.load_config(project.root, None)
            )
            _, findings, _ = spec_check.load_and_check_capabilities(
                project.root, spec_check.load_config(project.root, None), requirements
            )

            self.assertIn("invalid_local_classification", {item.code for item in findings})

    def test_ignored_file_must_use_local_classification(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            (project.root / ".gitignore").write_text("private/\n", encoding="utf-8")
            private = project.root / "private" / "notes.txt"
            private.parent.mkdir()
            private.write_text("private\n", encoding="utf-8")
            capability_path = project.spec_dir / "capability-map.json"
            payload = json.loads(capability_path.read_text(encoding="utf-8"))
            payload["file_classifications"].extend(
                [
                    {"pattern": ".gitignore", "category": "metadata", "reason": "Git ignore policy."},
                    {"pattern": "private/**", "category": "documentation", "reason": "Incorrect ignored classification."},
                ]
            )
            body = {
                "file_classifications": payload["file_classifications"],
                "capabilities": payload["capabilities"],
            }
            payload["approval"]["inventory_digest"] = spec_check._canonical_json_digest(body)
            capability_path.write_text(json.dumps(payload), encoding="utf-8")

            requirements = spec_check.parse_requirements(
                project.spec_dir, project.root, spec_check.load_config(project.root, None)
            )
            _, findings, _ = spec_check.load_and_check_capabilities(
                project.root, spec_check.load_config(project.root, None), requirements
            )

            self.assertIn("invalid_local_classification", {item.code for item in findings})

    def test_non_normative_requirements_cannot_have_matrix_rows(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement(status="Informative")
            project.write_matrix()

            _, findings, _ = spec_check.run_checks(project.root, spec_check.load_config(project.root, None))

            self.assertIn("non_normative_matrix_row", {finding.code for finding in findings})

    def test_passed_evidence_artifact_must_be_index_tracked(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            subprocess.run(
                ["git", "rm", "--cached", "docs/specification/evidence/EV-CORE-FLOW-001.txt"],
                cwd=project.root,
                check=True,
                capture_output=True,
            )

            _, findings, _ = spec_check.run_checks(project.root, spec_check.load_config(project.root, None))

            self.assertIn("missing_evidence_artifact", {finding.code for finding in findings})

    def test_duplicate_matrix_receipts_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            matrix = project.spec_dir / "verification-matrix.md"
            text = matrix.read_text(encoding="utf-8")
            matrix.write_text(
                text.replace(
                    "receipt:EV-CORE-FLOW-001 |",
                    "receipt:EV-CORE-FLOW-001; receipt:EV-CORE-FLOW-001 |",
                ),
                encoding="utf-8",
            )

            _, findings, _ = spec_check.run_checks(
                project.root, spec_check.load_config(project.root, None)
            )

            self.assertIn("matrix_evidence_mismatch", {item.code for item in findings})

    def test_deleted_implementation_retains_base_traceability_in_review_context(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "base"], cwd=project.root, check=True, capture_output=True)
            (project.root / "src" / "feature.py").unlink()
            config = spec_check.load_config(project.root, None)
            requirements = spec_check.parse_requirements(project.spec_dir, project.root, config)
            capability_map, _, implementation_files = spec_check.load_and_check_capabilities(project.root, config, requirements)
            context = spec_check.build_review_context(
                project.root, config, "changed", "HEAD", requirements, capability_map,
                implementation_files, spec_check.git_changed_paths(project.root, "HEAD"), [],
            )

            record = next(item for item in context["paths"] if item["path"] == "src/feature.py")
            trace = next(item for item in context["implementation_traceability"] if item["path"] == "src/feature.py")
            self.assertTrue(record["base_digest"].startswith("sha256:"))
            self.assertEqual(["CORE-FLOW-001"], trace["requirement_ids"])

    def test_changed_implementation_adds_configured_user_documentation_to_scope(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            (project.root / "README.md").write_text("Operator guide\n", encoding="utf-8")
            capability_path = project.spec_dir / "capability-map.json"
            payload = json.loads(capability_path.read_text(encoding="utf-8"))
            payload["file_classifications"].append(
                {
                    "pattern": "README.md",
                    "category": "documentation",
                    "reason": "User documentation.",
                }
            )
            body = {
                "file_classifications": payload["file_classifications"],
                "capabilities": payload["capabilities"],
            }
            payload["approval"]["inventory_digest"] = spec_check._canonical_json_digest(body)
            capability_path.write_text(json.dumps(payload), encoding="utf-8")
            config = spec_check.load_config(project.root, None)
            requirements = spec_check.parse_requirements(project.spec_dir, project.root, config)
            capability_map, _, implementation_files = spec_check.load_and_check_capabilities(
                project.root, config, requirements
            )

            context = spec_check.build_review_context(
                project.root,
                config,
                "changed",
                None,
                requirements,
                capability_map,
                implementation_files,
                {"src/feature.py"},
                [],
            )

            self.assertIn("README.md", context["reviewed_paths"])

    def test_evidence_artifact_content_change_invalidates_receipt(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            artifact = project.spec_dir / "evidence" / "EV-CORE-FLOW-001.txt"
            artifact.write_text("different result\n", encoding="utf-8")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("stale_evidence_artifact", {item.code for item in findings})

    def test_approval_policy_change_invalidates_semantic_review(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            path = project.spec_dir / "approval-policy.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["authorized_semantic_reviewers"].append("model:backup-reviewer")
            path.write_text(json.dumps(payload), encoding="utf-8")

            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--summary-json"],
                check=False,
                capture_output=True,
                text=True,
            )
            codes = {item["code"] for item in json.loads(result.stdout)["findings"]}

            self.assertIn("stale_semantic_review", codes)

    def test_semantic_review_requires_exact_per_mapping_conclusions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            path = project.spec_dir / "semantic-review.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["conclusions"] = []
            path.write_text(json.dumps(payload), encoding="utf-8")

            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--summary-json"],
                check=False,
                capture_output=True,
                text=True,
            )
            codes = {item["code"] for item in json.loads(result.stdout)["findings"]}

            self.assertIn("invalid_semantic_conclusion", codes)

    def test_semantic_review_requires_requirement_level_conclusions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            path = project.spec_dir / "semantic-review.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["conclusions"] = [
                item
                for item in payload["conclusions"]
                if item["path"] != "docs/specification/requirements.md"
            ]
            path.write_text(json.dumps(payload), encoding="utf-8")

            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--summary-json"],
                check=False,
                capture_output=True,
                text=True,
            )
            codes = {item["code"] for item in json.loads(result.stdout)["findings"]}

            self.assertIn("invalid_semantic_conclusion", codes)

    def test_semantic_review_requires_exact_digest_echoes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            path = project.spec_dir / "semantic-review.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["requirement_digests"] = {}
            path.write_text(json.dumps(payload), encoding="utf-8")

            _, findings, _ = spec_check.run_checks(project.root, spec_check.load_config(project.root, None))
            requirements = spec_check.parse_requirements(project.spec_dir, project.root, spec_check.load_config(project.root, None))
            capability_map, _, implementation_files = spec_check.load_and_check_capabilities(project.root, spec_check.load_config(project.root, None), requirements)
            context = spec_check.build_review_context(project.root, spec_check.load_config(project.root, None), "full", None, requirements, capability_map, implementation_files, set(), [])
            findings.extend(spec_check.check_semantic_review(project.root, spec_check.load_config(project.root, None), context))

            self.assertIn("stale_semantic_review", {item.code for item in findings})

    def test_empty_requirement_inventory_is_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_matrix(requirement_id="OTHER-FLOW-001")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("empty_requirement_inventory", {finding.code for finding in findings})

    def test_normative_requirement_requires_matching_human_approval(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix()

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("missing_requirement_approval", {finding.code for finding in findings})

    def test_empty_verification_and_provenance_are_failures(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement(verification="Verification:\n\nProvenance:\n")
            project.write_matrix(status="Passing", evidence="")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)
            codes = {finding.code for finding in findings}

            self.assertIn("empty_verification", codes)
            self.assertIn("empty_provenance", codes)
            self.assertIn("passing_without_evidence", codes)

    def test_untracked_implementation_file_is_reported_as_changed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init"], cwd=root, check=True, capture_output=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=root, check=True)
            (root / "README.md").write_text("baseline\n", encoding="utf-8")
            subprocess.run(["git", "add", "README.md"], cwd=root, check=True)
            subprocess.run(["git", "commit", "-m", "baseline"], cwd=root, check=True, capture_output=True)
            untracked = root / "src" / "new_feature.py"
            untracked.parent.mkdir()
            untracked.write_text("FEATURE = True\n", encoding="utf-8")

            self.assertIn("src/new_feature.py", spec_check.git_changed_paths(root, "HEAD"))

    def test_matrix_row_deletion_blocks_changed_mode(self) -> None:
        finding = spec_check.Finding(
            severity="failure",
            code="missing_matrix_row",
            message="CORE-FLOW-001 is missing",
            path="docs/specification/requirements.md",
            requirement_id="CORE-FLOW-001",
        )
        config = spec_check.CheckerConfig()

        spec_check.classify_findings(
            [finding],
            mode="changed",
            config=config,
            baseline={},
            changed_paths={"docs/specification/verification-matrix.md"},
            changed_requirement_ids={"CORE-FLOW-001"},
        )

        self.assertTrue(finding.blocking)

    def test_unmapped_implementation_is_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix()
            source = project.root / "src" / "feature.py"
            source.parent.mkdir()
            source.write_text("FEATURE = True\n", encoding="utf-8")
            (project.spec_dir / "capability-map.json").write_text(
                json.dumps(
                    {
                        "schema_version": 3,
                        "file_classifications": [
                            {
                                "pattern": "src/**",
                                "category": "implementation",
                                "reason": "Product implementation source.",
                            },
                            {
                                "pattern": "docs/specification/**",
                                "category": "specification",
                                "reason": "Specification records.",
                            },
                        ],
                        "capabilities": [],
                        "approval": {},
                    }
                ),
                encoding="utf-8",
            )

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("unmapped_implementation", {finding.code for finding in findings})

    def test_passing_receipt_must_match_current_requirement_digest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            path = project.spec_dir / "evidence-receipts.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["receipts"][0]["requirement_digest"] = "sha256:stale"
            path.write_text(json.dumps(payload), encoding="utf-8")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("stale_evidence_requirement", {finding.code for finding in findings})

    def test_semantic_review_attestation_is_required_by_cli(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix()
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--summary-json"],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertNotEqual(0, result.returncode)
            report = json.loads(result.stdout)
            self.assertIn(
                "missing_semantic_review",
                {finding["code"] for finding in report["findings"]},
            )

    def test_implementation_change_invalidates_evidence_and_semantic_review(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            (project.root / "src" / "feature.py").write_text(
                "FEATURE = False\n", encoding="utf-8"
            )
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--summary-json"],
                check=False,
                capture_output=True,
                text=True,
            )
            codes = {finding["code"] for finding in json.loads(result.stdout)["findings"]}

            self.assertIn("stale_evidence_implementation", codes)
            self.assertIn("stale_semantic_review", codes)

    def test_semantic_scope_digest_survives_committing_its_attestation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            source = project.root / "src" / "feature.py"
            source.parent.mkdir()
            source.write_text("FEATURE = True\n", encoding="utf-8")
            subprocess.run(["git", "init"], cwd=project.root, check=True, capture_output=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "baseline"], cwd=project.root, check=True, capture_output=True)
            base = subprocess.run(
                ["git", "rev-parse", "HEAD"],
                cwd=project.root,
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
            source.write_text("FEATURE = False\n", encoding="utf-8")
            config = spec_check.load_config(project.root, None)
            requirements = spec_check.parse_requirements(project.spec_dir, project.root, config)
            capability_map = spec_check.CapabilityMap(
                (
                    spec_check.FileClassification(
                        "src/**", "implementation", "Product implementation source."
                    ),
                ),
                (
                    spec_check.Capability(
                        "CAP-CORE-FLOW-001",
                        "Example",
                        "active",
                        ("CORE-FLOW-001",),
                        (
                            spec_check.ImplementationMapping(
                                "src/feature.py", ("CORE-FLOW-001",)
                            ),
                        ),
                    ),
                ),
                "sha256:" + "0" * 64,
            )
            first = spec_check.build_review_context(
                project.root,
                config,
                "changed",
                base,
                requirements,
                capability_map,
                {"src/feature.py"},
                spec_check.git_changed_paths(project.root, base),
                [],
            )
            (project.spec_dir / "semantic-review.json").write_text(
                json.dumps({"scope_digest": first["scope_digest"]}), encoding="utf-8"
            )
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "reviewed change"], cwd=project.root, check=True, capture_output=True)
            second = spec_check.build_review_context(
                project.root,
                config,
                "changed",
                base,
                requirements,
                capability_map,
                {"src/feature.py"},
                spec_check.git_changed_paths(project.root, base),
                [],
            )

            self.assertEqual(first["scope_digest"], second["scope_digest"])
            self.assertNotEqual(first["observed_head_commit"], second["observed_head_commit"])

    def test_capability_change_invalidates_human_inventory_approval(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            path = project.spec_dir / "capability-map.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["capabilities"][0]["name"] = "Changed capability"
            path.write_text(json.dumps(payload), encoding="utf-8")

            config = spec_check.load_config(project.root, None)
            _, findings, _ = spec_check.run_checks(project.root, config)

            self.assertIn("stale_capability_approval", {finding.code for finding in findings})

    def test_requirement_deletion_requires_retained_tombstone(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            subprocess.run(["git", "init"], cwd=project.root, check=True, capture_output=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "baseline"], cwd=project.root, check=True, capture_output=True)
            (project.spec_dir / "requirements.md").write_text("# Requirements\n", encoding="utf-8")
            (project.spec_dir / "verification-matrix.md").write_text(
                "| Requirement | Verification | Evidence | Evidence state | Status | Verified at | Notes |\n"
                "| --- | --- | --- | --- | --- | --- | --- |\n",
                encoding="utf-8",
            )
            config = spec_check.load_config(project.root, None)
            requirements, _, _ = spec_check.run_checks(project.root, config)
            findings = spec_check.deleted_requirement_findings(
                project.root, "HEAD", requirements, config
            )

            self.assertEqual(
                ["deleted_requirement_without_tombstone"],
                [finding.code for finding in findings],
            )


class CliTest(unittest.TestCase):
    def test_cli_path_override_cannot_escape_project(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix()
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--root",
                    str(project.root),
                    "--spec-dir",
                    "../../outside",
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(2, result.returncode)
            self.assertIn("escapes the project root", result.stderr)

    def test_summary_json_has_stable_shape_and_success_exit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--summary-json"],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            report = json.loads(result.stdout)
            self.assertEqual(6, report["schema_version"])
            self.assertEqual("full", report["mode"])
            self.assertIn("summary", report)
            self.assertIn("findings", report)
            self.assertIn("changed_requirement_ids", report)
            self.assertTrue(report["semantic_review_required"])
            self.assertTrue(report["trust_boundary"]["external_codeowner_review_required"])

    def test_changed_only_requires_base(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix()
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--changed-only"],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(2, result.returncode)
            self.assertIn("requires --base", result.stderr)

    def test_write_baseline_requires_reason(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix(requirement_id="OTHER-FLOW-001")
            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--root",
                    str(project.root),
                    "--write-baseline",
                    "baseline.json",
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(2, result.returncode)
            self.assertIn("requires --baseline-reason", result.stderr)

    def test_write_baseline_cannot_escape_project(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix(requirement_id="OTHER-FLOW-001")
            result = subprocess.run(
                [
                    sys.executable, str(SCRIPT), "--root", str(project.root),
                    "--write-baseline", "../baseline.json",
                    "--baseline-reason", "test", "--baseline-accepted-by", "human:test",
                    "--baseline-decision-ref", "decision:test", "--baseline-expires-at", "2099-12-31",
                ],
                check=False, capture_output=True, text=True,
            )

            self.assertEqual(2, result.returncode)
            self.assertIn("write-baseline escapes the project root", result.stderr)

    def test_trusted_base_controls_use_base_policy_for_baseline_acceptance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "base"], cwd=project.root, check=True, capture_output=True)
            policy_path = project.spec_dir / "approval-policy.json"
            policy = json.loads(policy_path.read_text(encoding="utf-8"))
            policy["authorized_approvers"] = ["human:candidate-owner"]
            policy_path.write_text(json.dumps(policy), encoding="utf-8")
            matrix = project.spec_dir / "verification-matrix.md"
            matrix.write_text("| Requirement | Verification | Evidence | Evidence state | Status | Verified at | Notes |\n| --- | --- | --- | --- | --- | --- | --- |\n", encoding="utf-8")
            finding = spec_check.Finding("failure", "missing_matrix_row", "CORE-FLOW-001 is Normative but has no matrix row.", "docs/specification/requirements.md", requirement_id="CORE-FLOW-001")
            baseline = spec_check.BaselineEntry.from_finding(finding, "accepted", "human:product-owner", "decision:test", "2099-12-31")
            (project.spec_dir / "spec-baseline.json").write_text(
                json.dumps(
                    {
                        "schema_version": spec_check.SCHEMA_VERSION,
                        "findings": [spec_check.asdict(baseline)],
                    }
                ),
                encoding="utf-8",
            )

            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root), "--changed-only", "--base", "HEAD", "--trusted-base-controls", "--json"],
                check=False, capture_output=True, text=True,
            )
            report = json.loads(result.stdout)
            match = next(item for item in report["findings"] if item["code"] == "missing_matrix_row")

            self.assertTrue(match["baseline_accepted"])

    def test_trusted_base_controls_ignore_candidate_control_plane_redirects(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "base"], cwd=project.root, check=True, capture_output=True)

            replacement = project.root / "candidate-assurance"
            replacement.mkdir()
            (replacement / "requirements.md").write_text(
                "### BYPASS-FLOW-001: Candidate redirect\n\n"
                "Status: Informative\n\nNo contract.\n",
                encoding="utf-8",
            )
            (replacement / "verification-matrix.md").write_text(
                "| Requirement | Verification | Evidence | Evidence state | Status | Verified at | Notes |\n"
                "| --- | --- | --- | --- | --- | --- | --- |\n",
                encoding="utf-8",
            )
            (project.spec_dir / "spec-config.yaml").write_text(
                json.dumps(
                    {
                        "model_version": spec_check.SCHEMA_VERSION,
                        "spec_dir": "candidate-assurance",
                        "matrix": "candidate-assurance/verification-matrix.md",
                        "baseline": "candidate-assurance/baseline.json",
                        "approval_policy": "candidate-assurance/policy.json",
                        "approvals": "candidate-assurance/approvals.json",
                        "capabilities": "candidate-assurance/capabilities.json",
                        "evidence_receipts": "candidate-assurance/receipts.json",
                        "semantic_review": "candidate-assurance/review.json",
                        "requirement_status_aliases": {"Normative": "Informative"},
                    }
                ),
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--root",
                    str(project.root),
                    "--changed-only",
                    "--base",
                    "HEAD",
                    "--trusted-base-controls",
                    "--review-context",
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            context = json.loads(result.stdout)
            self.assertIn("CORE-FLOW-001", context["requirement_ids"])
            self.assertNotIn("BYPASS-FLOW-001", context["requirement_ids"])

    def test_trusted_base_controls_reject_cli_path_overrides(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "base"], cwd=project.root, check=True, capture_output=True)

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--root",
                    str(project.root),
                    "--changed-only",
                    "--base",
                    "HEAD",
                    "--trusted-base-controls",
                    "--spec-dir",
                    "candidate-assurance",
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(2, result.returncode)
            self.assertIn("does not allow candidate-selected", result.stderr)

    def test_changed_cli_uses_candidate_policy_for_baseline_acceptance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_strict_contract()
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=project.root, check=True)
            subprocess.run(["git", "config", "user.name", "Spec Test"], cwd=project.root, check=True)
            subprocess.run(["git", "add", "."], cwd=project.root, check=True)
            subprocess.run(["git", "commit", "-m", "base"], cwd=project.root, check=True, capture_output=True)
            matrix = project.spec_dir / "verification-matrix.md"
            matrix.write_text(
                "| Requirement | Verification | Evidence | Evidence state | Status | Verified at | Notes |\n"
                "| --- | --- | --- | --- | --- | --- | --- |\n",
                encoding="utf-8",
            )
            finding = spec_check.Finding(
                "failure",
                "missing_matrix_row",
                "CORE-FLOW-001 is Normative but has no matrix row.",
                "docs/specification/requirements.md",
                requirement_id="CORE-FLOW-001",
            )
            baseline = spec_check.BaselineEntry.from_finding(
                finding,
                "accepted",
                "human:product-owner",
                "decision:test",
                "2099-12-31",
            )
            (project.spec_dir / "spec-baseline.json").write_text(
                json.dumps(
                    {
                        "schema_version": spec_check.SCHEMA_VERSION,
                        "findings": [spec_check.asdict(baseline)],
                    }
                ),
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--root",
                    str(project.root),
                    "--changed-only",
                    "--base",
                    "HEAD",
                    "--json",
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            report = json.loads(result.stdout)
            match = next(
                item for item in report["findings"] if item["code"] == "missing_matrix_row"
            )

            self.assertTrue(match["baseline_accepted"])

    def test_invalid_config_returns_configuration_exit_code(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = SpecProject(Path(directory))
            project.write_requirement()
            project.write_matrix()
            config = project.spec_dir / "spec-config.yaml"
            config.write_text('{"model_version": 99}', encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--root", str(project.root)],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(2, result.returncode)
            self.assertIn("Unsupported specification model version", result.stderr)


if __name__ == "__main__":
    unittest.main()
