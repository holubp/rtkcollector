"""Unittest regressions for supporting requirement coverage."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import Any
from unittest.mock import patch

from test_spec_assurance import SpecProject, spec_check


TIMESTAMP = "2026-10-30T12:00:00Z"


def _write_requirements(project: SpecProject, records: list[tuple[str, str]]) -> None:
    sections = []
    for requirement_id, coverage_kind in records:
        sections.append(
            f"### {requirement_id}: {requirement_id} requirement\n\n"
            "Status: Normative\n\n"
            f"Coverage kind: {coverage_kind}\n\n"
            "The system MUST satisfy this requirement.\n\n"
            "Verification:\n- Automated: focused test.\n\n"
            f"Evidence obligations: EV-{requirement_id}\n"
        )
    (project.spec_dir / "requirements.md").write_text("\n".join(sections), encoding="utf-8")


def _write_file(root: Path, relative: str, content: str = "fixture\n") -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def _classifications() -> list[dict[str, str]]:
    return [
        {"pattern": "src/**", "category": "implementation", "reason": "Runtime source."},
        {"pattern": "docs/operator/**", "category": "documentation", "reason": "Operator docs."},
        {"pattern": "tools/**", "category": "tooling", "reason": "Release tooling."},
        {"pattern": "config/**", "category": "configuration", "reason": "Release config."},
        {"pattern": "tests/**", "category": "test", "reason": "Test-only files."},
        {"pattern": "private/**", "category": "local", "reason": "Ignored local files."},
        {"pattern": "vendor/**", "category": "vendor", "reason": "Third-party files."},
        {"pattern": "assets/**", "category": "asset", "reason": "Product assets."},
        {"pattern": "build/**", "category": "generated", "reason": "Generated files."},
        {
            "pattern": "docs/specification/**",
            "category": "specification",
            "reason": "Canonical requirements and assurance records.",
        },
    ]


def _capability(
    requirement_ids: list[str],
    implementation_mappings: list[dict[str, Any]] | None = None,
    supporting_mappings: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "id": "CAP-SUPPORTING-COVERAGE-001",
        "name": "Supporting coverage fixture",
        "status": "active",
        "requirement_ids": requirement_ids,
        "implementation_mappings": implementation_mappings or [],
        "supporting_mappings": supporting_mappings or [],
    }


def _write_capability_map(
    project: SpecProject,
    capabilities: list[dict[str, Any]],
    classifications: list[dict[str, str]] | None = None,
) -> dict[str, Any]:
    body = {
        "file_classifications": classifications or _classifications(),
        "capabilities": capabilities,
    }
    payload = {
        "schema_version": 3,
        **body,
        "approval": {
            "inventory_digest": spec_check._canonical_json_digest(body),
            "state": "approved",
            "approved_by": "human:product-owner",
            "approved_at": TIMESTAMP,
            "decision_ref": "decision:supporting-coverage-test",
        },
    }
    (project.spec_dir / "capability-map.json").write_text(
        json.dumps(payload), encoding="utf-8"
    )
    return payload


class SupportingCoverageTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_directory.cleanup)
        self.root = Path(self.temp_directory.name)
        self._patchers: list[Any] = []
        self.addCleanup(self._stop_patches)

    def _stop_patches(self) -> None:
        for patcher in reversed(self._patchers):
            patcher.stop()

    def _install_inventory_stub(
        self, root: Path, ignored: set[str] | None = None
    ) -> None:
        ignored_paths = ignored or set()

        def project_files(_: Path) -> tuple[set[str], set[str]]:
            all_paths = {
                path.relative_to(root).as_posix()
                for path in root.rglob("*")
                if path.is_file() or path.is_symlink()
            }
            return all_paths - ignored_paths, ignored_paths

        inventory_patcher = patch.object(
            spec_check, "_project_files_by_state", project_files
        )
        revision_patcher = patch.object(
            spec_check, "_git_revision", lambda *_: "unversioned"
        )
        inventory_patcher.start()
        revision_patcher.start()
        self._patchers.extend((inventory_patcher, revision_patcher))

    def _load_capabilities(
        self, project: SpecProject, ignored: set[str] | None = None
    ) -> tuple[list[Any], Any, list[Any], set[str]]:
        self._install_inventory_stub(project.root, ignored)
        policy_payload = {
            "schema_version": 1,
            "authority_ref": "policy:test-authority",
            "authorized_approvers": ["human:product-owner"],
            "authorized_evidence_issuers": ["ci:test"],
            "authorized_semantic_reviewers": ["model:test-reviewer"],
        }
        (project.spec_dir / "approval-policy.json").write_text(
            json.dumps(policy_payload), encoding="utf-8"
        )
        config = spec_check.load_config(project.root, None)
        requirements = spec_check.parse_requirements(
            project.spec_dir, project.root, config
        )
        policy, policy_findings = spec_check.load_and_check_approval_policy(
            project.root, config
        )
        self.assertFalse(
            [item for item in policy_findings if item.severity == "failure"]
        )
        capability_map, findings, implementation_files = (
            spec_check.load_and_check_capabilities(
                project.root, config, requirements, policy
            )
        )
        return requirements, capability_map, findings, implementation_files

    def _valid_fixture(
        self, root: Path | None = None
    ) -> tuple[SpecProject, list[Any], Any, list[Any], set[str]]:
        root = root or self.root
        project = SpecProject(root)
        _write_requirements(
            project,
            [
                ("DOC-COVERAGE-001", "documentation"),
                ("PROCESS-COVERAGE-001", "process"),
                ("RUNTIME-COVERAGE-001", "implementation_required"),
            ],
        )
        _write_file(root, "docs/operator/release.md", "Release procedure.\n")
        _write_file(root, "tools/release.py", "release_config = True\n")
        _write_file(root, "src/runtime.py", "runtime = True\n")
        capability = _capability(
            ["DOC-COVERAGE-001", "PROCESS-COVERAGE-001", "RUNTIME-COVERAGE-001"],
            implementation_mappings=[
                {"pattern": "src/runtime.py", "requirement_ids": ["RUNTIME-COVERAGE-001"]}
            ],
            supporting_mappings=[
                {
                    "pattern": "docs/operator/release.md",
                    "requirement_ids": ["DOC-COVERAGE-001"],
                    "coverage_kind": "documentation",
                    "reason": "The operator procedure fulfills the documentation obligation.",
                },
                {
                    "pattern": "tools/release.py",
                    "requirement_ids": ["PROCESS-COVERAGE-001"],
                    "coverage_kind": "process",
                    "reason": "The release tool enforces the required process.",
                },
            ],
        )
        _write_capability_map(project, [capability])
        requirements, capability_map, findings, implementation_files = (
            self._load_capabilities(project)
        )
        return project, requirements, capability_map, findings, implementation_files

    def test_supporting_contract_uses_requested_schema_versions(self) -> None:
        self.assertEqual(6, spec_check.SCHEMA_VERSION)
        self.assertEqual(5, spec_check.SEMANTIC_SCHEMA_VERSION)

    def test_documentation_and_process_supporting_mappings_are_valid(self) -> None:
        _, requirements, capability_map, findings, implementation_files = (
            self._valid_fixture()
        )

        self.assertEqual(
            {"documentation", "process", "implementation_required"},
            {item.coverage_kind for item in requirements},
        )
        self.assertIsNotNone(capability_map)
        self.assertFalse([item for item in findings if item.severity == "failure"])
        self.assertEqual({"src/runtime.py"}, implementation_files)

    def test_unknown_coverage_kind_fails_closed(self) -> None:
        project = SpecProject(self.root)
        _write_requirements(project, [("UNKNOWN-COVERAGE-001", "docs-only")])
        _write_file(self.root, "docs/operator/claim.md")
        _write_capability_map(
            project,
            [
                _capability(
                    ["UNKNOWN-COVERAGE-001"],
                    supporting_mappings=[
                        {
                            "pattern": "docs/operator/claim.md",
                            "requirement_ids": ["UNKNOWN-COVERAGE-001"],
                            "coverage_kind": "documentation",
                            "reason": "Claimed documentation coverage.",
                        }
                    ],
                )
            ],
        )

        requirements, _, findings, _ = self._load_capabilities(project)
        config = spec_check.load_config(project.root, None)
        _, requirement_findings, _ = spec_check.run_checks(project.root, config)

        self.assertEqual("docs-only", requirements[0].coverage_kind)
        self.assertIn(
            "invalid_coverage_kind",
            {item.code for item in [*findings, *requirement_findings]},
        )

    def test_duplicate_coverage_kind_is_rejected_even_when_values_agree(self) -> None:
        project = SpecProject(self.root)
        _write_requirements(project, [("DOC-COVERAGE-001", "documentation")])
        path = project.spec_dir / "requirements.md"
        body = path.read_text(encoding="utf-8")
        path.write_text(body.replace("Coverage kind: documentation", "Coverage kind: documentation\nCoverage kind: documentation"), encoding="utf-8")
        self._install_inventory_stub(self.root)
        config = spec_check.load_config(self.root, None)
        _, findings, _ = spec_check.run_checks(self.root, config)
        self.assertIn("invalid_coverage_kind", {item.code for item in findings})

    def test_implementation_required_requirement_still_needs_runtime_mapping(self) -> None:
        project = SpecProject(self.root)
        _write_requirements(
            project, [("RUNTIME-REQUIRED-001", "implementation_required")]
        )
        _write_file(self.root, "docs/operator/claim.md")
        _write_capability_map(
            project,
            [
                _capability(
                    ["RUNTIME-REQUIRED-001"],
                    supporting_mappings=[
                        {
                            "pattern": "docs/operator/claim.md",
                            "requirement_ids": ["RUNTIME-REQUIRED-001"],
                            "coverage_kind": "documentation",
                            "reason": "Documentation is not runtime implementation.",
                        }
                    ],
                )
            ],
        )

        _, _, findings, _ = self._load_capabilities(project)

        self.assertTrue(
            any(
                item.requirement_id == "RUNTIME-REQUIRED-001"
                and "implementation" in item.message.lower()
                for item in findings
            ),
            [(item.code, item.requirement_id, item.message) for item in findings],
        )

    def test_unmapped_runtime_file_remains_a_failure(self) -> None:
        project, _, _, _, _ = self._valid_fixture()
        _write_file(self.root, "src/unmapped.py", "unmapped = True\n")

        _, _, findings, _ = self._load_capabilities(project)

        self.assertIn("unmapped_implementation", {item.code for item in findings})

    def test_supporting_mapping_must_match_category(self) -> None:
        project = SpecProject(self.root)
        _write_requirements(project, [("DOC-CATEGORY-001", "documentation")])
        _write_file(self.root, "tools/release.py")
        _write_capability_map(
            project,
            [
                _capability(
                    ["DOC-CATEGORY-001"],
                    supporting_mappings=[
                        {
                            "pattern": "tools/release.py",
                            "requirement_ids": ["DOC-CATEGORY-001"],
                            "coverage_kind": "documentation",
                            "reason": "Wrong-category target must be rejected.",
                        }
                    ],
                )
            ],
        )

        _, _, findings, _ = self._load_capabilities(project)

        self.assertIn("invalid_supporting_mapping", {item.code for item in findings})

    def test_forbidden_paths_cannot_realize_supporting_coverage(self) -> None:
        cases = [
            ("private/claim.md", "local", True),
            ("tests/claim.md", "test", False),
            ("vendor/claim.md", "vendor", False),
            ("assets/claim.md", "asset", False),
            ("build/claim.md", "generated", False),
            ("docs/specification/requirements.md", "specification", False),
            ("docs/specification/capability-map.json", "specification", False),
        ]
        declared_categories = {item["category"] for item in _classifications()}
        for index, (target, category, ignored) in enumerate(cases):
            with self.subTest(target=target, category=category):
                case_root = self.root / str(index)
                case_root.mkdir()
                self.assertIn(category, declared_categories)
                project = SpecProject(case_root)
                _write_requirements(
                    project, [("DOC-FORBIDDEN-001", "documentation")]
                )
                _write_file(case_root, target)
                _write_capability_map(
                    project,
                    [
                        _capability(
                            ["DOC-FORBIDDEN-001"],
                            supporting_mappings=[
                                {
                                    "pattern": target,
                                    "requirement_ids": ["DOC-FORBIDDEN-001"],
                                    "coverage_kind": "documentation",
                                    "reason": "Forbidden target must never establish coverage.",
                                }
                            ],
                        )
                    ],
                )

                _, _, findings, _ = self._load_capabilities(
                    project, {target} if ignored else None
                )

                self.assertIn(
                    "invalid_supporting_mapping",
                    {item.code for item in findings},
                )

    def test_supporting_content_change_stales_receipt_and_review_context(self) -> None:
        project, requirements, capability_map, _, implementation_files = (
            self._valid_fixture()
        )
        config = spec_check.load_config(project.root, None)
        requirement = next(
            item for item in requirements if item.id == "DOC-COVERAGE-001"
        )
        supporting_digest = spec_check.requirement_supporting_digest(
            project.root, requirement.id, capability_map
        )
        receipt_path = project.spec_dir / "evidence-receipts.json"
        receipt_path.write_text(
            json.dumps(
                {
                    "schema_version": 2,
                    "receipts": [
                        {
                            "id": "EV-DOC-COVERAGE-001",
                            "requirement_id": requirement.id,
                            "requirement_digest": requirement.digest,
                            "implementation_digest": spec_check.requirement_implementation_digest(
                                project.root,
                                requirement.id,
                                capability_map,
                                implementation_files,
                            ),
                            "supporting_digest": supporting_digest,
                            "kind": "test",
                            "state": "not-run",
                            "target": "",
                            "verified_at": TIMESTAMP,
                            "verified_commit": "",
                            "runner": "",
                            "artifact_ref": "",
                            "artifact_digest": "",
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        row = spec_check.MatrixRow(
            requirement_id=requirement.id,
            verification_type="Automated",
            evidence="receipt:EV-DOC-COVERAGE-001",
            evidence_state="not-run",
            implementation_status="Open",
            verified_at="",
            notes="",
            path=config.matrix,
            line=1,
        )
        _write_file(project.root, "docs/specification/evidence/placeholder.txt")
        policy, _ = spec_check.load_and_check_approval_policy(project.root, config)
        first_context = spec_check.build_review_context(
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

        _write_file(
            project.root,
            "docs/operator/release.md",
            "Revised release procedure.\n",
        )
        new_supporting_digest = spec_check.requirement_supporting_digest(
            project.root, requirement.id, capability_map
        )
        _, receipt_findings = spec_check.load_and_check_receipts(
            project.root,
            config,
            requirements,
            [row],
            capability_map,
            implementation_files,
            policy,
        )
        second_context = spec_check.build_review_context(
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

        self.assertNotEqual(supporting_digest, new_supporting_digest)
        self.assertIn(
            "stale_evidence_supporting",
            {item.code for item in receipt_findings},
            [(item.code, item.message) for item in receipt_findings],
        )
        self.assertNotEqual(
            first_context["supporting_digests"][requirement.id],
            second_context["supporting_digests"][requirement.id],
        )
        self.assertNotEqual(first_context["scope_digest"], second_context["scope_digest"])

    def _install_base_snapshots(
        self,
        base: str,
        capabilities: dict[str, Any],
        supporting_path: str,
    ) -> None:
        config = spec_check.load_config(self.root, None)

        def show_bytes(_root: Path, revision: str, path: str) -> bytes | None:
            if revision != base:
                return None
            if path == config.capabilities:
                return json.dumps(capabilities).encode("utf-8")
            if path == supporting_path:
                return b"Base operator procedure.\n"
            return None

        show_patcher = patch.object(spec_check, "_git_show_bytes", show_bytes)
        requirements_patcher = patch.object(
            spec_check, "base_requirements", lambda *_: self._base_requirements
        )
        paths_patcher = patch.object(
            spec_check, "_base_realization_paths", lambda *_: {supporting_path, config.capabilities}
        )
        show_patcher.start()
        requirements_patcher.start()
        paths_patcher.start()
        self._patchers.extend((show_patcher, requirements_patcher, paths_patcher))

    def test_deleted_supporting_file_retains_base_traceability(self) -> None:
        project, requirements, capability_map, _, implementation_files = (
            self._valid_fixture()
        )
        self._base_requirements = requirements
        config = spec_check.load_config(project.root, None)
        supporting_path = "docs/operator/release.md"
        base_capabilities = json.loads(
            (project.spec_dir / "capability-map.json").read_text(encoding="utf-8")
        )
        (project.root / supporting_path).unlink()
        self._install_base_snapshots("trusted-base", base_capabilities, supporting_path)

        context = spec_check.build_review_context(
            project.root,
            config,
            "changed",
            "trusted-base",
            requirements,
            capability_map,
            implementation_files,
            {supporting_path},
            [],
        )

        entry = next(
            item
            for item in context["supporting_traceability"]
            if item["path"] == supporting_path
        )
        path_record = next(
            item for item in context["paths"] if item["path"] == supporting_path
        )
        self.assertEqual(["DOC-COVERAGE-001"], entry["requirement_ids"])
        self.assertEqual("deleted", path_record["digest"])
        self.assertTrue(path_record["base_digest"].startswith("sha256:"))

    def test_deleted_supporting_file_and_candidate_mapping_keep_base_scope(self) -> None:
        project, requirements, _, _, implementation_files = self._valid_fixture()
        self._base_requirements = requirements
        config = spec_check.load_config(project.root, None)
        supporting_path = "docs/operator/release.md"
        capability_path = project.spec_dir / "capability-map.json"
        base_capabilities = json.loads(capability_path.read_text(encoding="utf-8"))
        candidate_capabilities = json.loads(json.dumps(base_capabilities))
        capability = candidate_capabilities["capabilities"][0]
        capability["requirement_ids"].remove("DOC-COVERAGE-001")
        capability["supporting_mappings"] = [
            mapping
            for mapping in capability["supporting_mappings"]
            if "DOC-COVERAGE-001" not in mapping["requirement_ids"]
        ]
        _write_capability_map(
            project,
            candidate_capabilities["capabilities"],
            candidate_capabilities["file_classifications"],
        )
        (project.root / supporting_path).unlink()
        candidate_requirements, candidate_map, _, candidate_implementation_files = (
            self._load_capabilities(project)
        )
        self._install_base_snapshots(
            "trusted-base", base_capabilities, supporting_path
        )

        context = spec_check.build_review_context(
            project.root,
            config,
            "changed",
            "trusted-base",
            candidate_requirements,
            candidate_map,
            candidate_implementation_files,
            {supporting_path},
            [],
        )

        entry = next(
            item
            for item in context["supporting_traceability"]
            if item["path"] == supporting_path
        )
        self.assertEqual(["DOC-COVERAGE-001"], entry["requirement_ids"])
        self.assertIn("DOC-COVERAGE-001", context["requirement_ids"])

    def test_semantic_review_requires_conclusion_for_supporting_pair(self) -> None:
        project, requirements, capability_map, _, implementation_files = (
            self._valid_fixture()
        )
        config = spec_check.load_config(project.root, None)
        policy, _ = spec_check.load_and_check_approval_policy(project.root, config)
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
                "supporting_traceability",
                "requirement_traceability",
                "requirement_digests",
                "implementation_digests",
                "supporting_digests",
                "coverage_kinds",
            )
        }
        attestation.update(
            {
                "result": "pass",
                "reviewer": "model:test-reviewer",
                "reviewed_at": TIMESTAMP,
                "findings": [],
                "limitations": [],
                "conclusions": [],
            }
        )
        (project.spec_dir / "semantic-review.json").write_text(
            json.dumps(attestation), encoding="utf-8"
        )

        findings = spec_check.check_semantic_review(
            project.root, config, context, policy
        )

        self.assertIn(
            "invalid_semantic_conclusion", {item.code for item in findings}
        )

    def test_requirement_approval_remains_blocking_when_missing(self) -> None:
        project = SpecProject(self.root)
        _write_requirements(project, [("APPROVAL-REQUIRED-001", "documentation")])
        config = spec_check.load_config(project.root, None)
        policy_payload = {
            "schema_version": 1,
            "authority_ref": "policy:test-authority",
            "authorized_approvers": ["human:product-owner"],
            "authorized_evidence_issuers": ["ci:test"],
            "authorized_semantic_reviewers": ["model:test-reviewer"],
        }
        (project.spec_dir / "approval-policy.json").write_text(
            json.dumps(policy_payload), encoding="utf-8"
        )
        requirements = spec_check.parse_requirements(
            project.spec_dir, project.root, config
        )
        policy, policy_findings = spec_check.load_and_check_approval_policy(
            project.root, config
        )
        self.assertFalse(
            [item for item in policy_findings if item.severity == "failure"]
        )

        findings = spec_check.check_requirement_approvals(
            project.root, config, requirements, policy
        )

        self.assertIn(
            "missing_requirement_approval", {item.code for item in findings}
        )


if __name__ == "__main__":
    unittest.main()
