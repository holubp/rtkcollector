#!/usr/bin/env python3
"""Checkout or update a pinned local RTKLIB-EX source tree.

The script intentionally requires an explicit ref. RtkCollector must not track
RTKLIB-EX HEAD implicitly because native GNSS solver behaviour must be
reviewable and reproducible.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


DEFAULT_UPSTREAM_URL = "https://github.com/rtklibexplorer/RTKLIB.git"
DEFAULT_DESTINATION = Path("third_party/rtklib-ex/upstream")
DEFAULT_METADATA = Path("third_party/rtklib-ex/snapshot.json")
PINNED_COMMIT_RE = re.compile(r"^[0-9a-fA-F]{40}$")


class RtklibSnapshotError(RuntimeError):
    pass


@dataclass(frozen=True)
class SnapshotMetadata:
    upstream_url: str
    requested_ref: str
    resolved_commit: str
    updated_utc: str

    def to_json(self) -> str:
        return json.dumps(
            {
                "upstreamUrl": self.upstream_url,
                "requestedRef": self.requested_ref,
                "resolvedCommit": self.resolved_commit,
                "updatedUtc": self.updated_utc,
            },
            indent=2,
            sort_keys=True,
        ) + "\n"


def is_pinned_commit(ref: str) -> bool:
    return bool(PINNED_COMMIT_RE.fullmatch(ref.strip()))


def validate_ref(ref: str, allow_non_commit_ref: bool) -> None:
    if not ref.strip():
        raise RtklibSnapshotError("RTKLIB-EX ref must not be empty")
    if not allow_non_commit_ref and not is_pinned_commit(ref):
        raise RtklibSnapshotError(
            "RTKLIB-EX ref must be a full 40-character commit hash. "
            "Use --allow-non-commit-ref only for temporary local investigation.",
        )


def run_git(args: list[str], cwd: Path | None = None, dry_run: bool = False) -> str:
    command = ["git"]
    if cwd is not None:
        command.extend(["-c", f"safe.directory={cwd.resolve()}"])
    command.extend(args)
    if dry_run:
        location = f" (cwd={cwd})" if cwd else ""
        print("+ " + " ".join(command) + location)
        return ""
    completed = subprocess.run(
        command,
        cwd=cwd,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout.strip()


def checkout_is_dirty(destination: Path) -> bool:
    if not (destination / ".git").exists():
        return False
    return bool(run_git(["status", "--porcelain"], cwd=destination))


def update_checkout(
    upstream_url: str,
    ref: str,
    destination: Path,
    metadata_path: Path,
    allow_dirty: bool = False,
    allow_non_commit_ref: bool = False,
    dry_run: bool = False,
) -> SnapshotMetadata:
    validate_ref(ref, allow_non_commit_ref)
    destination = destination.resolve()
    metadata_path = metadata_path.resolve()

    if destination.exists() and not (destination / ".git").exists() and any(destination.iterdir()):
        raise RtklibSnapshotError(f"{destination} exists but is not an empty git checkout")

    if (destination / ".git").exists():
        if not allow_dirty and checkout_is_dirty(destination):
            raise RtklibSnapshotError(f"{destination} has local changes; commit, clean, or pass --allow-dirty")
        run_git(["remote", "set-url", "origin", upstream_url], cwd=destination, dry_run=dry_run)
        run_git(["fetch", "--tags", "origin"], cwd=destination, dry_run=dry_run)
    else:
        destination.parent.mkdir(parents=True, exist_ok=True)
        run_git(["clone", upstream_url, str(destination)], dry_run=dry_run)

    run_git(["checkout", "--detach", ref], cwd=destination, dry_run=dry_run)
    resolved = ref.lower() if dry_run and is_pinned_commit(ref) else run_git(["rev-parse", "HEAD"], cwd=destination, dry_run=dry_run)
    if dry_run and not resolved:
        resolved = ref

    metadata = SnapshotMetadata(
        upstream_url=upstream_url,
        requested_ref=ref,
        resolved_commit=resolved,
        updated_utc=datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    )
    if dry_run:
        print(f"+ write {metadata_path}")
    else:
        metadata_path.parent.mkdir(parents=True, exist_ok=True)
        metadata_path.write_text(metadata.to_json(), encoding="utf-8")
    return metadata


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-url", default=DEFAULT_UPSTREAM_URL)
    parser.add_argument("--ref", required=True, help="Pinned RTKLIB-EX commit hash")
    parser.add_argument("--destination", type=Path, default=DEFAULT_DESTINATION)
    parser.add_argument("--metadata", type=Path, default=DEFAULT_METADATA)
    parser.add_argument("--allow-dirty", action="store_true")
    parser.add_argument(
        "--allow-non-commit-ref",
        action="store_true",
        help="Allow branch/tag refs for temporary investigation only; do not commit this state.",
    )
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        metadata = update_checkout(
            upstream_url=args.upstream_url,
            ref=args.ref,
            destination=args.destination,
            metadata_path=args.metadata,
            allow_dirty=args.allow_dirty,
            allow_non_commit_ref=args.allow_non_commit_ref,
            dry_run=args.dry_run,
        )
    except (RtklibSnapshotError, subprocess.CalledProcessError) as error:
        print(f"error: {error}")
        return 1

    print(metadata.to_json(), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
