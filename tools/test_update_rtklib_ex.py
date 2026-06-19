from pathlib import Path

import pytest

from update_rtklib_ex import (
    RtklibSnapshotError,
    SnapshotMetadata,
    is_pinned_commit,
    update_checkout,
    validate_ref,
)


def test_is_pinned_commit_accepts_full_hash_only():
    assert is_pinned_commit("0123456789abcdef0123456789abcdef01234567")
    assert not is_pinned_commit("main")
    assert not is_pinned_commit("012345")


def test_validate_ref_rejects_unpinned_ref_by_default():
    with pytest.raises(RtklibSnapshotError):
        validate_ref("main", allow_non_commit_ref=False)


def test_snapshot_metadata_uses_stable_json_names():
    metadata = SnapshotMetadata(
        upstream_url="https://example.invalid/rtklib.git",
        requested_ref="0" * 40,
        resolved_commit="1" * 40,
        updated_utc="2026-06-19T00:00:00Z",
    )

    text = metadata.to_json()

    assert '"upstreamUrl": "https://example.invalid/rtklib.git"' in text
    assert '"requestedRef": "0000000000000000000000000000000000000000"' in text
    assert text.endswith("\n")


def test_update_checkout_refuses_non_git_non_empty_destination(tmp_path: Path):
    destination = tmp_path / "upstream"
    destination.mkdir()
    (destination / "file.txt").write_text("local file", encoding="utf-8")

    with pytest.raises(RtklibSnapshotError):
        update_checkout(
            upstream_url="https://example.invalid/rtklib.git",
            ref="0" * 40,
            destination=destination,
            metadata_path=tmp_path / "snapshot.json",
            dry_run=True,
        )


def test_update_checkout_writes_metadata_for_existing_checkout(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    destination = tmp_path / "upstream"
    metadata_path = tmp_path / "snapshot.json"
    (destination / ".git").mkdir(parents=True)
    calls = []

    def fake_run_git(args, cwd=None, dry_run=False):
        calls.append((args, cwd, dry_run))
        if args == ["status", "--porcelain"]:
            return ""
        if args == ["rev-parse", "HEAD"]:
            return "1" * 40
        return ""

    monkeypatch.setattr("update_rtklib_ex.run_git", fake_run_git)

    metadata = update_checkout(
        upstream_url="https://example.invalid/rtklib.git",
        ref="0" * 40,
        destination=destination,
        metadata_path=metadata_path,
    )

    assert metadata.resolved_commit == "1" * 40
    assert '"resolvedCommit": "1111111111111111111111111111111111111111"' in metadata_path.read_text(encoding="utf-8")
    assert (["fetch", "--tags", "origin"], destination.resolve(), False) in calls


def test_run_git_scopes_safe_directory_for_nested_checkout(monkeypatch: pytest.MonkeyPatch, tmp_path: Path):
    captured = {}

    class Completed:
        stdout = "ok\n"

    def fake_run(command, cwd=None, check=False, text=False, stdout=None, stderr=None):
        captured["command"] = command
        captured["cwd"] = cwd
        captured["check"] = check
        return Completed()

    monkeypatch.setattr("update_rtklib_ex.subprocess.run", fake_run)

    result = __import__("update_rtklib_ex").run_git(["status", "--short"], cwd=tmp_path)

    assert result == "ok"
    assert captured["command"][:3] == ["git", "-c", f"safe.directory={tmp_path.resolve()}"]
