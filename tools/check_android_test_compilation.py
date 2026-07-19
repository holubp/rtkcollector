#!/usr/bin/env python3
"""Compile Android test source sets before code is pushed.

The Termux mode deliberately bypasses only Android resource processing, whose
host binary cannot run in the supported aarch64 Termux environment. It still
compiles production Kotlin and the app JVM unit-test sources, so missing test
dependencies and stale test calls remain blocking failures.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path


class GateError(RuntimeError):
    """Raised when the test-compilation gate cannot perform a valid check."""


CommandRunner = Callable[[list[str], Path], None]


def resolve_mode(requested: str, environment: Mapping[str, str]) -> str:
    """Resolve ``auto`` to the full-host or constrained Termux check."""

    if requested not in {"auto", "standard", "termux"}:
        raise GateError(f"Unsupported mode: {requested}")
    if requested != "auto":
        return requested

    prefix = environment.get("PREFIX", "")
    if environment.get("TERMUX_VERSION") or "com.termux" in prefix:
        return "termux"
    return "standard"


def termux_r_jar_source(root: Path) -> Path:
    """Return the R.jar generated without invoking Android resource linking."""

    return (
        root
        / "app/build/intermediates/compile_r_class_jar/debug/generateDebugRFile/R.jar"
    )


def termux_r_jar_destination(root: Path) -> Path:
    """Return the R.jar path expected by app unit-test compilation."""

    return (
        root
        / "app/build/intermediates/compile_and_runtime_r_class_jar/debug/"
        "processDebugResources/R.jar"
    )


def seed_termux_unit_test_r_jar(root: Path) -> Path:
    """Seed AGP's unit-test classpath from the production compile R.jar."""

    source = termux_r_jar_source(root)
    if not source.is_file():
        raise GateError(
            "Production Kotlin compilation did not create the generated debug R.jar "
            f"at {source}. The Termux unit-test compile cannot be trusted.",
        )
    destination = termux_r_jar_destination(root)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    return destination


def is_windows_environment(environment: Mapping[str, str]) -> bool:
    """Return whether commands should use the Windows Gradle wrapper."""

    return environment.get("OS", "").lower() == "windows_nt"


def gradle_prefix(root: Path, environment: Mapping[str, str]) -> list[str]:
    """Return the platform-appropriate Gradle wrapper invocation."""

    if is_windows_environment(environment):
        return [str(root / "gradlew.bat")]
    return ["sh", "gradlew"]


def validate_repository(root: Path, environment: Mapping[str, str]) -> None:
    """Reject an incomplete checkout that could produce a false green task."""

    wrapper_name = "gradlew.bat" if is_windows_environment(environment) else "gradlew"
    required_files = (root / wrapper_name, root / "app" / "build.gradle.kts")
    for required in required_files:
        if not required.is_file():
            raise GateError(f"Required Android build file is missing: {required}")

    test_root = root / "app" / "src" / "test"
    test_sources = (
        path
        for path in test_root.rglob("*")
        if path.is_file() and path.suffix.lower() in {".kt", ".java"}
    )
    if next(test_sources, None) is None:
        raise GateError(
            "No Kotlin or Java app unit-test sources were found under "
            f"{test_root}; Gradle could otherwise report a misleading NO-SOURCE success.",
        )


def subprocess_runner(command: list[str], cwd: Path) -> None:
    """Run one gate command and preserve its non-zero exit status."""

    print(f"+ {' '.join(command)}", flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def run_gate(
    root: Path,
    *,
    mode: str = "auto",
    environment: Mapping[str, str] | None = None,
    runner: CommandRunner = subprocess_runner,
) -> str:
    """Compile Android test sources using the strongest valid host strategy."""

    environment = os.environ if environment is None else environment
    resolved_mode = resolve_mode(mode, environment)
    gradle = gradle_prefix(root, environment)

    if resolved_mode == "standard":
        runner(
            gradle
            + [
                ":app:unitTestClasses",
                ":app:androidTestClasses",
                "--no-parallel",
            ],
            root,
        )
        return resolved_mode

    runner(gradle + [":app:compileDebugKotlin", "--no-parallel"], root)
    seeded_jar = seed_termux_unit_test_r_jar(root)
    print(f"Seeded Termux unit-test R.jar: {seeded_jar}", flush=True)
    runner(
        gradle
        + [
            ":app:unitTestClasses",
            "-x",
            ":app:processDebugResources",
            "--no-parallel",
        ],
        root,
    )
    runner(
        gradle
        + [
            ":app:unitTestClasses",
            ":app:androidTestClasses",
            "--dry-run",
            "--no-parallel",
        ],
        root,
    )
    return resolved_mode


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compile Android test source sets as a pre-push quality gate.",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Repository root (defaults to the parent of tools/).",
    )
    parser.add_argument(
        "--mode",
        choices=("auto", "standard", "termux"),
        default="auto",
        help="Host strategy; auto detects Termux.",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    root = args.root.resolve()
    try:
        validate_repository(root, os.environ)
    except GateError as error:
        print(f"ERROR: Android test-compilation gate is not configured: {error}", file=sys.stderr)
        return 2
    try:
        resolved = run_gate(root, mode=args.mode)
    except (GateError, subprocess.CalledProcessError) as error:
        print(f"ERROR: Android test-compilation gate failed: {error}", file=sys.stderr)
        return 1
    print(f"Android test-compilation gate passed ({resolved} mode).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
