#!/usr/bin/env python3
import argparse
import collections
import zipfile


def summarize_pos_lines(lines):
    quality = collections.Counter()
    satellites = collections.Counter()
    for line in lines:
        line = line.strip()
        if not line or line.startswith("%"):
            continue
        parts = line.split()
        if len(parts) < 7:
            continue
        quality[parts[5]] += 1
        satellites[parts[6]] += 1
    return quality, satellites


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("session_zip")
    args = parser.parse_args()
    with zipfile.ZipFile(args.session_zip) as archive:
        text = archive.read("rtklib-solution.pos").decode("ascii", errors="replace")
    quality, satellites = summarize_pos_lines(text.splitlines())
    print("quality", " ".join(f"{key}:{quality[key]}" for key in sorted(quality)))
    print(
        "satellites",
        " ".join(
            f"{key}:{satellites[key]}"
            for key in sorted(satellites, key=lambda value: int(value) if value.isdigit() else value)
        ),
    )


if __name__ == "__main__":
    main()
