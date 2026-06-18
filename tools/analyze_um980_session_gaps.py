#!/usr/bin/env python3
"""Analyse UM980 binary receiver-time gaps in an RtkCollector session ZIP."""

from __future__ import annotations

import argparse
import collections
import struct
import zipfile


SYNC = b"\xaa\x44\xb5"
HEADER_LEN = 24
CRC_LEN = 4
LABELS = {
    142: "ADRNAVB",
    509: "RTKSTATUSB",
    954: "STADOPB",
    1026: "PPPNAVB",
    2118: "BESTNAVB",
}


def iter_frames(data: bytes):
    index = 0
    while True:
        start = data.find(SYNC, index)
        if start < 0 or start + HEADER_LEN > len(data):
            return
        msg_id = struct.unpack_from("<H", data, start + 4)[0]
        payload_len = struct.unpack_from("<H", data, start + 6)[0]
        week = struct.unpack_from("<H", data, start + 10)[0]
        tow_ms = struct.unpack_from("<I", data, start + 12)[0]
        frame_len = HEADER_LEN + payload_len + CRC_LEN
        if payload_len > 4096 or start + frame_len > len(data):
            index = start + 1
            continue
        yield msg_id, week * 604_800_000 + tow_ms, start
        index = start + frame_len


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("session_zip")
    parser.add_argument("--gap-ms", type=int, default=250)
    args = parser.parse_args()

    with zipfile.ZipFile(args.session_zip) as archive:
        data = archive.read("receiver-rx.raw")

    by_id = collections.defaultdict(list)
    for msg_id, receiver_ms, byte_offset in iter_frames(data):
        by_id[msg_id].append((receiver_ms, byte_offset))

    for msg_id, samples in sorted(by_id.items()):
        label = LABELS.get(msg_id, str(msg_id))
        if len(samples) < 2:
            continue
        gaps = [
            (samples[index + 1][0] - samples[index][0], samples[index], samples[index + 1])
            for index in range(len(samples) - 1)
        ]
        large = [gap for gap in gaps if gap[0] > args.gap_ms]
        print(
            f"{label}: count={len(samples)} max_gap_ms={max(g[0] for g in gaps)} "
            f"gaps>{args.gap_ms}ms={len(large)}",
        )
        for gap_ms, previous, following in large[:10]:
            print(
                f"  gap_ms={gap_ms} receiver_ms={previous[0]}->{following[0]} "
                f"byte={previous[1]}->{following[1]}",
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
