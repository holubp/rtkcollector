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
        self.assertIn("run: sh gradlew clean", workflow)
        self.assertIn("run: sh gradlew assembleDebug --no-parallel", workflow)
        self.assertIn("run: sh gradlew test --no-parallel", workflow)

    def test_ci_runs_tests_before_native_assembly(self) -> None:
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github" / "workflows" / "android.yml").read_text(
            encoding="utf-8"
        )

        test_step = workflow.index("- name: Run tests")
        provision_step = workflow.index("- name: Provision pinned RTKLIB-EX source")
        assemble_step = workflow.index("- name: Assemble debug bootstrap")

        self.assertLess(test_step, provision_step)
        self.assertLess(provision_step, assemble_step)
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
            [[
                "sh",
                "gradlew",
                ":app:unitTestClasses",
                ":app:androidTestClasses",
                "--no-parallel",
            ]],
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

    def test_termux_mode_compiles_unit_tests_without_running_aapt2(self) -> None:
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
                    ["sh", "gradlew", ":app:compileDebugKotlin", "--no-parallel"],
                    [
                        "sh",
                        "gradlew",
                        ":app:unitTestClasses",
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
