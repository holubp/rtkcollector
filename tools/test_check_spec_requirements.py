from pathlib import Path

from check_spec_requirements import find_requirement_ids, validate_spec_tree


def test_find_requirement_ids_detects_heading_ids(tmp_path: Path):
    spec = tmp_path / "spec.md"
    spec.write_text("### ARCH-RAW-001: Raw stream\n\n### WF-ROVER-001: Rover\n", encoding="utf-8")

    assert find_requirement_ids([spec]) == {"ARCH-RAW-001": [spec], "WF-ROVER-001": [spec]}


def test_validate_spec_tree_rejects_duplicate_requirement_ids(tmp_path: Path):
    first = tmp_path / "a.md"
    second = tmp_path / "b.md"
    matrix = tmp_path / "verification-matrix.md"
    first.write_text("### ARCH-RAW-001: Raw stream\n", encoding="utf-8")
    second.write_text("### ARCH-RAW-001: Duplicate\n", encoding="utf-8")
    matrix.write_text("| Requirement ID |\n| --- |\n| `ARCH-RAW-001` |\n", encoding="utf-8")

    errors = validate_spec_tree(tmp_path)

    assert any("Duplicate requirement ID ARCH-RAW-001" in error for error in errors)


def test_validate_spec_tree_rejects_matrix_id_without_requirement(tmp_path: Path):
    spec = tmp_path / "spec.md"
    matrix = tmp_path / "verification-matrix.md"
    spec.write_text("### ARCH-RAW-001: Raw stream\n", encoding="utf-8")
    matrix.write_text("| Requirement ID |\n| --- |\n| `WF-MISSING-001` |\n", encoding="utf-8")

    errors = validate_spec_tree(tmp_path)

    assert any("Matrix references missing requirement ID WF-MISSING-001" in error for error in errors)

