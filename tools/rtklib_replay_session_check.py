#!/usr/bin/env python3
"""Check RTKLIB artifacts inside an RtkCollector session ZIP.

This is not a native RTKLIB solver. It verifies that a session which claims
RTKLIB was enabled contains coherent RTKLIB output/status artifacts. It is a
fast regression guard for the publication/output gates and complements native
replay tests on hosts where the RTKLIB shared library can run.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path


RTKLIB_STATUS = "rtklib-status.jsonl"
RTKLIB_NMEA = "rtklib-solution.nmea"
RTKLIB_POS = "rtklib-solution.pos"
SESSION_JSON = "session.json"


@dataclass(frozen=True)
class CheckResult:
    errors: list[str]
    warnings: list[str]

    @property
    def ok(self) -> bool:
        return not self.errors


def read_text(zip_file: zipfile.ZipFile, name: str) -> str:
    try:
        with zip_file.open(name) as handle:
            return handle.read().decode("utf-8", errors="replace")
    except KeyError:
        return ""


def session_rtklib_enabled(session_json: str) -> bool:
    if not session_json.strip():
        return False
    data = json.loads(session_json)
    return bool(data.get("rtklibEnabled", False))


def parse_status_lines(text: str) -> list[dict]:
    rows: list[dict] = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))
    return rows


def has_solution_status(rows: list[dict]) -> bool:
    for row in rows:
        fix = row.get("fix")
        lat = row.get("latDeg")
        lon = row.get("lonDeg")
        if fix and fix not in {"NONE", "INVALID"} and lat is not None and lon is not None:
            return True
    return False


def check_session(path: Path) -> CheckResult:
    errors: list[str] = []
    warnings: list[str] = []
    with zipfile.ZipFile(path) as zip_file:
        names = set(zip_file.namelist())
        session_json = read_text(zip_file, SESSION_JSON)
        enabled = session_rtklib_enabled(session_json)
        if not enabled:
            warnings.append("RTKLIB is not enabled in session metadata.")
            return CheckResult(errors, warnings)

        for required in (RTKLIB_STATUS, RTKLIB_NMEA, RTKLIB_POS):
            if required not in names:
                errors.append(f"Missing {required}")

        status_rows = parse_status_lines(read_text(zip_file, RTKLIB_STATUS))
        nmea = read_text(zip_file, RTKLIB_NMEA)
        pos = read_text(zip_file, RTKLIB_POS)

        if not status_rows:
            errors.append("RTKLIB enabled session has empty rtklib-status.jsonl")
        if has_solution_status(status_rows):
            if not any(line.startswith("$") for line in nmea.splitlines()):
                errors.append("RTKLIB status has a solution but rtklib-solution.nmea has no NMEA sentences")
            if not any(line and not line.startswith("%") for line in pos.splitlines()):
                errors.append("RTKLIB status has a solution but rtklib-solution.pos has no solution rows")
        else:
            warnings.append("No valid RTKLIB solution found in status artifact.")

    return CheckResult(errors, warnings)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("session_zip", type=Path)
    args = parser.parse_args(argv)
    result = check_session(args.session_zip)
    for warning in result.warnings:
        print(f"warning: {warning}", file=sys.stderr)
    for error in result.errors:
        print(f"error: {error}", file=sys.stderr)
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
