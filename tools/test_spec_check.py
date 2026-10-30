import unittest
import tempfile
from pathlib import Path

from specification.spec_check import _expected_implementation_status
from check_spec_requirements import validate_spec_tree


class SpecificationLifecycleTest(unittest.TestCase):
    def check_tree(self, status: str, include_row: bool) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "requirements.md").write_text(
                f"### WF-EXAMPLE-001: Example\n\nStatus: {status}\n", encoding="utf-8",
            )
            (root / "verification-matrix.md").write_text(
                "| Requirement |\n| --- |\n" + ("| `WF-EXAMPLE-001` |\n" if include_row else ""), encoding="utf-8",
            )
            return validate_spec_tree(root)

    def test_future_requirement_is_not_required_in_matrix(self) -> None:
        self.assertEqual([], self.check_tree("Future", False))

    def test_non_normative_matrix_row_is_rejected(self) -> None:
        for status in ("Future", "Experimental", "Deprecated", "Informative"):
            with self.subTest(status=status):
                self.assertTrue(any("must not appear" in error for error in self.check_tree(status, True)))

    def test_normative_requirement_still_requires_matrix_coverage(self) -> None:
        self.assertEqual(["Requirement ID WF-EXAMPLE-001 missing from verification matrix"],
                         self.check_tree("Normative", False))


class SpecificationStatusTest(unittest.TestCase):
    def test_not_run_evidence_is_not_implementation_proof(self) -> None:
        self.assertEqual(
            _expected_implementation_status(["test", "manual"], ["not-run", "not-run"]),
            "Needs review",
        )

    def test_passing_implementation_evidence_can_wait_for_field_test(self) -> None:
        self.assertEqual(
            _expected_implementation_status(["test", "manual"], ["passed", "not-run"]),
            "Implemented, not field-tested",
        )

    def test_failed_evidence_requires_review(self) -> None:
        self.assertEqual(
            _expected_implementation_status(["test"], ["failed"]),
            "Needs review",
        )

    def test_blocked_evidence_is_reported_as_blocked(self) -> None:
        self.assertEqual(
            _expected_implementation_status(["review", "manual"], ["blocked", "not-run"]),
            "Blocked",
        )


if __name__ == "__main__":
    unittest.main()
