#!/usr/bin/env python3
"""Check UM980 session captures for RTKLIB-EX input-route readiness.

This is a lightweight replay preflight. It does not run RTKLIB-EX. It inspects
session ZIPs/directories for receiver raw data, correction input and configured
Unicore observation logs so unsupported OBSVMCMPB-only captures are not treated
as direct RTKLIB input by mistake.
"""

from __future__ import annotations

import argparse
import collections
import dataclasses
import json
import struct
import zipfile
from pathlib import Path


SYNC = b"\xaa\x44\xb5"
HEADER_LEN = 24
CRC_LEN = 4
MAX_PAYLOAD_LEN = 1_048_576

MESSAGE_LABELS = {
    142: "ADRNAVB",
    509: "RTKSTATUSB",
    954: "STADOPB",
    1026: "PPPNAVB",
    2118: "BESTNAVB",
    2125: "RTCMSTATUSB",
}

COMMAND_PROFILE_OBSERVATION_CONFIG = {
    "um980-binary-multihz": "OBSVMCMPB",
    "experimental-rover-base-preparation": "OBSVMCMPB",
}


@dataclasses.dataclass(frozen=True)
class Um980SampleReport:
    path: Path
    status: str
    has_receiver_rx: bool
    receiver_rx_bytes: int
    correction_bytes: int
    configured_obsvmb: bool
    configured_obsvmcmpb: bool
    message_counts: dict[int, int]

    @property
    def direct_rtklib_ready(self) -> bool:
        return self.status == "direct-unicore-obsvmb"


def iter_unicore_frames(data: bytes):
    index = 0
    while True:
        start = data.find(SYNC, index)
        if start < 0 or start + HEADER_LEN > len(data):
            return
        msg_id = struct.unpack_from("<H", data, start + 4)[0]
        payload_len = struct.unpack_from("<H", data, start + 6)[0]
        frame_len = HEADER_LEN + payload_len + CRC_LEN
        if payload_len > MAX_PAYLOAD_LEN or start + frame_len > len(data):
            index = start + 1
            continue
        yield msg_id
        index = start + frame_len


def count_unicore_messages(data: bytes) -> dict[int, int]:
    return dict(collections.Counter(iter_unicore_frames(data)))


def classify_init_script(init_script: str, command_profile_id: str = "") -> tuple[bool, bool]:
    profile_hint = COMMAND_PROFILE_OBSERVATION_CONFIG.get(command_profile_id, "")
    normalized = f"{init_script}\n{profile_hint}".upper()
    configured_obsvmcmpb = "OBSVMCMPB" in normalized
    configured_obsvmb = "OBSVMB" in normalized and not configured_obsvmcmpb
    return configured_obsvmb, configured_obsvmcmpb


def classify_status(
    configured_obsvmb: bool,
    configured_obsvmcmpb: bool,
    receiver_rx_bytes: int,
    correction_bytes: int,
) -> str:
    if receiver_rx_bytes <= 0:
        return "missing-receiver-rx"
    if configured_obsvmb and correction_bytes > 0:
        return "direct-unicore-obsvmb"
    if configured_obsvmb:
        return "direct-unicore-obsvmb-missing-corrections"
    if configured_obsvmcmpb:
        return "converter-required-obsvmcmpb"
    return "no-direct-um980-observation-config"


def _zip_member_by_suffix(archive: zipfile.ZipFile, suffix: str) -> str | None:
    for name in archive.namelist():
        if name.rstrip("/").endswith(suffix):
            return name
    return None


def _read_zip_text(archive: zipfile.ZipFile, suffix: str) -> str:
    name = _zip_member_by_suffix(archive, suffix)
    if not name:
        return ""
    return archive.read(name).decode("utf-8", errors="replace")


def _read_zip_bytes(archive: zipfile.ZipFile, suffixes: tuple[str, ...]) -> bytes:
    for suffix in suffixes:
        name = _zip_member_by_suffix(archive, suffix)
        if name:
            return archive.read(name)
    return b""


def inspect_session(path: Path) -> Um980SampleReport:
    path = path.resolve()
    if path.is_dir():
        rx = (path / "receiver-rx.raw").read_bytes() if (path / "receiver-rx.raw").exists() else b""
        init_script = (path / "init-script.txt").read_text(encoding="utf-8", errors="replace") if (path / "init-script.txt").exists() else ""
        session_json = (path / "session.json").read_text(encoding="utf-8", errors="replace") if (path / "session.json").exists() else "{}"
        correction = b""
        for name in ("correction-input.raw", "correction-input.rtcm3"):
            candidate = path / name
            if candidate.exists():
                correction = candidate.read_bytes()
                break
    else:
        with zipfile.ZipFile(path) as archive:
            rx = _read_zip_bytes(archive, ("receiver-rx.raw",))
            init_script = _read_zip_text(archive, "init-script.txt")
            session_json = _read_zip_text(archive, "session.json")
            correction = _read_zip_bytes(archive, ("correction-input.raw", "correction-input.rtcm3"))

    try:
        command_profile_id = str(json.loads(session_json or "{}").get("commandProfileId", ""))
    except json.JSONDecodeError:
        command_profile_id = ""
    configured_obsvmb, configured_obsvmcmpb = classify_init_script(init_script, command_profile_id)
    status = classify_status(configured_obsvmb, configured_obsvmcmpb, len(rx), len(correction))
    return Um980SampleReport(
        path=path,
        status=status,
        has_receiver_rx=bool(rx),
        receiver_rx_bytes=len(rx),
        correction_bytes=len(correction),
        configured_obsvmb=configured_obsvmb,
        configured_obsvmcmpb=configured_obsvmcmpb,
        message_counts=count_unicore_messages(rx),
    )


def format_report(report: Um980SampleReport) -> str:
    interesting_counts = []
    for msg_id, count in sorted(report.message_counts.items()):
        label = MESSAGE_LABELS.get(msg_id, str(msg_id))
        interesting_counts.append(f"{label}={count}")
    counts = ", ".join(interesting_counts) if interesting_counts else "-"
    return (
        f"{report.path}: status={report.status}; "
        f"rx={report.receiver_rx_bytes}B; corrections={report.correction_bytes}B; "
        f"init_obsvmb={str(report.configured_obsvmb).lower()}; "
        f"init_obsvmcmpb={str(report.configured_obsvmcmpb).lower()}; "
        f"frames={counts}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sessions", nargs="+", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    failed = False
    for session in args.sessions:
        try:
            print(format_report(inspect_session(session)))
        except (OSError, zipfile.BadZipFile) as error:
            failed = True
            print(f"{session}: error={error}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
