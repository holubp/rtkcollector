#!/usr/bin/env python3
"""Compile Android test source sets and run feasible tests before code is pushed.

The Termux mode deliberately bypasses only Android resource processing, whose
host binary cannot run in the supported aarch64 Termux environment. It still
compiles every app JVM unit-test source and runs the non-Robolectric app tests,
so missing test dependencies, stale test calls and ordinary test regressions
remain blocking failures. Clean CI runs the complete suite, including the three
Robolectric classes that need a host-compatible Android runtime artifact.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from collections.abc import Callable, Mapping, Sequence
from pathlib import Path


class GateError(RuntimeError):
    """Raised when the test-compilation gate cannot perform a valid check."""


CommandRunner = Callable[[list[str], Path], None]

PURE_JVM_TEST_TASKS = (
    ":core:capture:test",
    ":core:correction:test",
    ":core:quality:test",
    ":core:rtklib:test",
    ":core:session:test",
    ":core:solution:test",
    ":core:workflow:test",
    ":receiver:api:test",
    ":receiver:generic-nmea-rtcm:test",
    ":receiver:ublox-m8:test",
    ":receiver:unicore-n4:test",
)


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

    discovered_robolectric_tests = discover_robolectric_tests(test_root)
    configured_exclusions = configured_termux_test_exclusions(
        root / "app" / "build.gradle.kts",
    )
    if discovered_robolectric_tests != configured_exclusions:
        missing = sorted(discovered_robolectric_tests - configured_exclusions)
        stale = sorted(configured_exclusions - discovered_robolectric_tests)
        raise GateError(
            "Termux Robolectric exclusions do not match the test sources. "
            f"Missing exclusions: {missing or 'none'}; stale exclusions: {stale or 'none'}.",
        )

    validate_pure_jvm_test_tasks(root)
    validate_test_inputs_are_tracked(root)


def validate_pure_jvm_test_tasks(
    root: Path,
    configured_tasks: Sequence[str] = PURE_JVM_TEST_TASKS,
) -> None:
    """Keep the constrained-host task list aligned with test-bearing modules."""

    settings_file = root / "settings.gradle.kts"
    if not settings_file.is_file():
        return
    project_paths = re.findall(
        r'include\(\s*"(:[^"]+)"\s*\)',
        settings_file.read_text(encoding="utf-8"),
    )
    expected = {
        f"{project_path}:test"
        for project_path in project_paths
        if project_path != ":app" and module_has_test_sources(root, project_path)
    }
    configured = set(configured_tasks)
    if configured != expected:
        missing = sorted(expected - configured)
        stale = sorted(configured - expected)
        raise GateError(
            "Termux pure-JVM test tasks do not match test-bearing Gradle modules. "
            f"Missing tasks: {missing or 'none'}; stale tasks: {stale or 'none'}.",
        )


def module_has_test_sources(root: Path, project_path: str) -> bool:
    """Return whether a Gradle project contains Kotlin or Java JVM tests."""

    module_root = root.joinpath(*project_path.strip(":").split(":"))
    test_root = module_root / "src" / "test"
    return any(
        path.is_file() and path.suffix.lower() in {".kt", ".java"}
        for path in test_root.rglob("*")
    )


def validate_test_inputs_are_tracked(root: Path) -> None:
    """Reject local-only test inputs that would disappear in a clean checkout."""

    if not (root / ".git").exists():
        return

    local_only: set[str] = set()
    for arguments in (
        ("--others", "--exclude-standard"),
        ("--others", "--ignored", "--exclude-standard"),
    ):
        result = subprocess.run(
            ["git", "ls-files", *arguments, "-z"],
            cwd=root,
            check=True,
            capture_output=True,
        )
        for item in result.stdout.split(b"\0"):
            if not item:
                continue
            relative = Path(item.decode("utf-8"))
            if is_test_input(relative):
                local_only.add(relative.as_posix())
    if local_only:
        formatted = "\n  - ".join(sorted(local_only))
        raise GateError(
            "Unit-test inputs are present but not tracked by Git, including ignored "
            "inputs; a clean checkout "
            f"would be incomplete:\n  - {formatted}",
        )


def is_test_input(relative: Path) -> bool:
    """Return whether a repository path is required by the test gate or suites."""

    parts = relative.parts
    in_test_source_set = any(
        parts[index : index + 2] in (("src", "test"), ("src", "androidTest"))
        for index in range(len(parts) - 1)
    )
    if in_test_source_set or (parts and parts[0] == "testdata"):
        return True
    return relative.as_posix() in {
        ".github/workflows/android.yml",
        "scripts/pre_push_check.sh",
        "tools/check_android_test_compilation.py",
        "tools/test_check_android_test_compilation.py",
    }


def discover_robolectric_tests(test_root: Path) -> set[str]:
    """Return fully qualified tests that use ``RobolectricTestRunner``."""

    discovered: set[str] = set()
    for source in test_root.rglob("*.kt"):
        text = source.read_text(encoding="utf-8")
        if "RobolectricTestRunner" not in text:
            continue
        package = re.search(r"^package\s+([\w.]+)\s*$", text, re.MULTILINE)
        test_class = re.search(r"^class\s+(\w+)", text, re.MULTILINE)
        if package is None or test_class is None:
            raise GateError(f"Cannot identify Robolectric test class in {source}.")
        discovered.add(f"{package.group(1)}.{test_class.group(1)}")
    return discovered


def configured_termux_test_exclusions(build_file: Path) -> set[str]:
    """Return exact classes excluded from the constrained Termux test task."""

    text = build_file.read_text(encoding="utf-8")
    task_start = text.find('tasks.register<org.gradle.api.tasks.testing.Test>("termuxTestDebugUnitTest")')
    if task_start < 0:
        return set()
    task_text = text[task_start:]
    return set(re.findall(r'excludeTestsMatching\("([\w.]+)"\)', task_text))


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
    """Compile Android test sources and execute the strongest valid test set."""

    environment = os.environ if environment is None else environment
    resolved_mode = resolve_mode(mode, environment)
    gradle = gradle_prefix(root, environment)
    runner(gradle + ["clean", "--no-parallel"], root)

    if resolved_mode == "standard":
        runner(
            gradle
            + [
                ":app:unitTestClasses",
                ":app:androidTestClasses",
                "test",
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
            ":app:termuxTestDebugUnitTest",
            *PURE_JVM_TEST_TASKS,
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
