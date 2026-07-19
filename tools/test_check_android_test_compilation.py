import os
import subprocess
import tempfile
import unittest
from pathlib import Path

import check_android_test_compilation as gate


class AndroidTestCompilationGateTest(unittest.TestCase):
    def test_ci_does_not_require_gradlew_executable_bit(self) -> None:
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github" / "workflows" / "android.yml").read_text(
            encoding="utf-8"
        )

        self.assertNotIn("run: ./gradlew", workflow)
        self.assertIn("run: sh gradlew assembleDebug --no-parallel", workflow)
        self.assertIn("python3 tools/check_android_test_compilation.py --mode standard", workflow)

    def test_ci_runs_tests_before_native_assembly(self) -> None:
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github" / "workflows" / "android.yml").read_text(
            encoding="utf-8"
        )

        test_step = workflow.index("- name: Compile and run Android tests")
        report_step = workflow.index("- name: Upload test reports")
        provision_step = workflow.index("- name: Provision pinned RTKLIB-EX source")
        assemble_step = workflow.index("- name: Assemble debug bootstrap")

        self.assertLess(test_step, report_step)
        self.assertLess(report_step, provision_step)
        self.assertLess(test_step, provision_step)
        self.assertLess(provision_step, assemble_step)
        self.assertIn("if: always()", workflow)
        self.assertIn("**/build/test-results/**/*.xml", workflow)
        self.assertIn("third_party/rtklib-ex/snapshot.json", workflow)
        self.assertIn("tools/update_rtklib_ex.py", workflow)
        self.assertIn("--metadata \"$RUNNER_TEMP/rtklib-ex-snapshot.json\"", workflow)

    def test_auto_mode_detects_termux(self) -> None:
        environment = {
            "PREFIX": "/data/data/com.termux/files/usr",
            "ANDROID_ROOT": "/system",
        }

        self.assertEqual(gate.resolve_mode("auto", environment), "termux")
        self.assertEqual(gate.resolve_mode("auto", {}), "standard")

    def test_standard_mode_compiles_both_android_test_source_sets(self) -> None:
        commands: list[list[str]] = []

        gate.run_gate(
            Path("/repo"),
            mode="standard",
            environment={},
            runner=lambda command, cwd: commands.append(command),
        )

        self.assertEqual(
            commands,
            [
                ["sh", "gradlew", "clean", "--no-parallel"],
                [
                    "sh",
                    "gradlew",
                    ":app:unitTestClasses",
                    ":app:androidTestClasses",
                    "test",
                    "--no-parallel",
                ],
            ],
        )

    def test_windows_standard_mode_uses_gradlew_bat(self) -> None:
        commands: list[list[str]] = []

        gate.run_gate(
            Path("C:/repo"),
            mode="standard",
            environment={"OS": "Windows_NT"},
            runner=lambda command, cwd: commands.append(command),
        )

        self.assertEqual(commands[0][0], "C:/repo/gradlew.bat")

    def test_repository_validation_requires_unit_test_sources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradlew").write_text("wrapper\n", encoding="utf-8")
            build_file = root / "app" / "build.gradle.kts"
            build_file.parent.mkdir(parents=True)
            build_file.write_text("plugins {}\n", encoding="utf-8")

            with self.assertRaisesRegex(gate.GateError, "misleading NO-SOURCE"):
                gate.validate_repository(root, {})

            test_file = root / "app" / "src" / "test" / "ExampleTest.kt"
            test_file.parent.mkdir(parents=True)
            test_file.write_text("class ExampleTest\n", encoding="utf-8")

            gate.validate_repository(root, {})

    def test_repository_validation_requires_exact_robolectric_exclusions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradlew").write_text("wrapper\n", encoding="utf-8")
            build_file = root / "app" / "build.gradle.kts"
            build_file.parent.mkdir(parents=True)
            build_file.write_text("plugins {}\n", encoding="utf-8")
            test_file = root / "app" / "src" / "test" / "RobolectricExampleTest.kt"
            test_file.parent.mkdir(parents=True)
            test_file.write_text(
                """package example

import org.robolectric.RobolectricTestRunner

class RobolectricExampleTest
""",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(gate.GateError, "Missing exclusions"):
                gate.validate_repository(root, {})

            build_file.write_text(
                """tasks.register<org.gradle.api.tasks.testing.Test>(\"termuxTestDebugUnitTest\") {
    filter {
        excludeTestsMatching(\"example.RobolectricExampleTest\")
    }
}
""",
                encoding="utf-8",
            )
            gate.validate_repository(root, {})

    def test_repository_validation_rejects_untracked_test_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            (root / "gradlew").write_text("wrapper\n", encoding="utf-8")
            build_file = root / "app" / "build.gradle.kts"
            build_file.parent.mkdir(parents=True)
            build_file.write_text("plugins {}\n", encoding="utf-8")
            test_file = root / "app" / "src" / "test" / "ExampleTest.kt"
            test_file.parent.mkdir(parents=True)
            test_file.write_text("class ExampleTest\n", encoding="utf-8")
            subprocess.run(
                ["git", "add", "gradlew", "app/build.gradle.kts"],
                cwd=root,
                check=True,
            )

            with self.assertRaisesRegex(gate.GateError, "not tracked by Git"):
                gate.validate_repository(root, {})

            subprocess.run(
                ["git", "add", "app/src/test/ExampleTest.kt"],
                cwd=root,
                check=True,
            )
            gate.validate_repository(root, {})

            ignored_fixture = root / "app" / "src" / "test" / "resources" / "local.fixture"
            ignored_fixture.parent.mkdir(parents=True)
            ignored_fixture.write_text("local only\n", encoding="utf-8")
            (root / ".gitignore").write_text("*.fixture\n", encoding="utf-8")
            with self.assertRaisesRegex(gate.GateError, "including ignored inputs"):
                gate.validate_repository(root, {})

    def test_repository_validation_requires_every_test_bearing_jvm_module(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "settings.gradle.kts").write_text(
                'include(":app")\ninclude(":core:alpha")\ninclude(":core:empty")\n',
                encoding="utf-8",
            )
            test_file = root / "core" / "alpha" / "src" / "test" / "AlphaTest.kt"
            test_file.parent.mkdir(parents=True)
            test_file.write_text("class AlphaTest\n", encoding="utf-8")

            with self.assertRaisesRegex(gate.GateError, "Missing tasks"):
                gate.validate_pure_jvm_test_tasks(root, configured_tasks=())

            gate.validate_pure_jvm_test_tasks(
                root,
                configured_tasks=(":core:alpha:test",),
            )

    def test_termux_mode_runs_feasible_unit_tests_without_running_aapt2(self) -> None:
        commands: list[list[str]] = []
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def runner(command: list[str], cwd: Path) -> None:
                commands.append(command)
                if ":app:compileDebugKotlin" in command:
                    source = gate.termux_r_jar_source(root)
                    source.parent.mkdir(parents=True, exist_ok=True)
                    source.write_bytes(b"generated-r-jar")

            gate.run_gate(
                root,
                mode="termux",
                environment={},
                runner=runner,
            )

            self.assertEqual(
                commands,
                [
                    ["sh", "gradlew", "clean", "--no-parallel"],
                    ["sh", "gradlew", ":app:compileDebugKotlin", "--no-parallel"],
                    [
                        "sh",
                        "gradlew",
                        ":app:termuxTestDebugUnitTest",
                        *gate.PURE_JVM_TEST_TASKS,
                        "-x",
                        ":app:processDebugResources",
                        "--no-parallel",
                    ],
                    [
                        "sh",
                        "gradlew",
                        ":app:unitTestClasses",
                        ":app:androidTestClasses",
                        "--dry-run",
                        "--no-parallel",
                    ],
                ],
            )
            self.assertEqual(
                gate.termux_r_jar_destination(root).read_bytes(),
                b"generated-r-jar",
            )

    def test_termux_mode_fails_if_production_compile_did_not_generate_r_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(gate.GateError, "generated debug R.jar"):
                gate.run_gate(
                    Path(directory),
                    mode="termux",
                    environment={},
                    runner=lambda command, cwd: None,
                )

    def test_command_failure_is_not_hidden(self) -> None:
        def failing_runner(command: list[str], cwd: Path) -> None:
            raise subprocess.CalledProcessError(1, command)

        with self.assertRaises(subprocess.CalledProcessError):
            gate.run_gate(
                Path("/repo"),
                mode="standard",
                environment={},
                runner=failing_runner,
            )

    def test_rejects_unknown_mode(self) -> None:
        with self.assertRaisesRegex(gate.GateError, "Unsupported mode"):
            gate.resolve_mode("remote", os.environ)


if __name__ == "__main__":
    unittest.main()
