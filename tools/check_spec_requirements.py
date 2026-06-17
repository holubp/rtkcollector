#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path


REQUIREMENT_HEADING = re.compile(r"^###\s+([A-Z][A-Z0-9]+(?:-[A-Z0-9]+)+-\d{3}):\s+", re.MULTILINE)
MATRIX_ID = re.compile(r"`([A-Z][A-Z0-9]+(?:-[A-Z0-9]+)+-\d{3})`")


def markdown_files(root: Path) -> list[Path]:
    """Return Markdown files below the formal specification root."""
    return sorted(path for path in root.rglob("*.md") if path.is_file())


def find_requirement_ids(files: list[Path]) -> dict[str, list[Path]]:
    """Map each formal requirement ID to the files that define it."""
    found: dict[str, list[Path]] = defaultdict(list)
    for path in files:
        if path.name == "verification-matrix.md":
            continue
        text = path.read_text(encoding="utf-8")
        for match in REQUIREMENT_HEADING.finditer(text):
            found[match.group(1)].append(path)
    return dict(found)


def find_matrix_ids(matrix: Path) -> set[str]:
    """Return requirement IDs referenced from the verification matrix."""
    if not matrix.exists():
        return set()
    return set(MATRIX_ID.findall(matrix.read_text(encoding="utf-8")))


def validate_spec_tree(root: Path) -> list[str]:
    """Validate requirement ID uniqueness and matrix coverage."""
    files = markdown_files(root)
    requirements = find_requirement_ids(files)
    matrix_ids = find_matrix_ids(root / "verification-matrix.md")
    errors: list[str] = []

    for requirement_id, paths in sorted(requirements.items()):
        if len(paths) > 1:
            locations = ", ".join(str(path) for path in paths)
            errors.append(f"Duplicate requirement ID {requirement_id}: {locations}")

    for matrix_id in sorted(matrix_ids):
        if matrix_id not in requirements:
            errors.append(f"Matrix references missing requirement ID {matrix_id}")

    for requirement_id in sorted(requirements):
        if requirement_id not in matrix_ids:
            errors.append(f"Requirement ID {requirement_id} missing from verification matrix")

    return errors


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("docs/specification")
    errors = validate_spec_tree(root)
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
