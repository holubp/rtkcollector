import base64
import os
from pathlib import Path
import subprocess
import signal
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from sign_sideload_apk import SigningError, sign_apk


class SideloadSigningTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "input.apk"
        self.source.write_bytes(b"unchanged build artifact")
        self.output = self.root / "signed.apk"
        self.digest = "a" * 64
        self.environment = {
            "SIDELOAD_KEYSTORE_BASE64": base64.b64encode(b"test keystore").decode(),
            "SIDELOAD_STORE_PASSWORD": "private-store-password",
            "SIDELOAD_KEY_PASSWORD": "private-key-password",
            "SIDELOAD_KEY_ALIAS": "rtkcollector-sideload",
            "SIDELOAD_CERT_SHA256": self.digest,
        }
        self.commands = []

    def runner(self, command, **kwargs):
        self.commands.append(command)
        if command[1] == "sign":
            keystore = Path(command[command.index("--ks") + 1])
            self.assertEqual(keystore.read_bytes(), b"test keystore")
            self.assertEqual(keystore.stat().st_mode & 0o777, 0o600)
            self.assertNotIn("private-store-password", command)
            self.assertNotIn("private-key-password", command)
            self.assertIn("env:SIDELOAD_STORE_PASSWORD", command)
            self.assertIn("env:SIDELOAD_KEY_PASSWORD", command)
            Path(command[command.index("--out") + 1]).write_bytes(b"signed artifact")
            return subprocess.CompletedProcess(command, 0, "", "")
        return subprocess.CompletedProcess(
            command, 0, f"Signer #1 certificate SHA-256 digest: {self.digest}\n", "",
        )

    def test_verification_process_has_no_signing_secrets(self):
        def runner(command, **kwargs):
            if command[1] == "verify":
                for name in self.environment:
                    self.assertNotIn(name, kwargs["env"])
            return self.runner(command, **kwargs)

        with patch.dict(os.environ, self.environment), patch("sign_sideload_apk.run_tool", runner):
            sign_apk(self.source, self.output, Path("apksigner"))

    def test_workflow_re_signs_before_checksum_and_publication(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/release-debug-apk.yml").read_text()
        self.assertLess(workflow.index("Sign APK with stable"), workflow.index("Stage checksum"))
        self.assertLess(workflow.index("Stage checksum"), workflow.index("Attach APK"))
        self.assertIn("ref: ${{ github.sha }}", workflow)
        self.assertIn(".release-tooling/tools/sign_sideload_apk.py", workflow)
        self.assertIn('"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"', workflow)
        bootstrap = (Path(__file__).resolve().parents[1] / ".github/workflows/android.yml").read_text()
        self.assertIn('"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"', bootstrap)

    def sign(self):
        with patch.dict(os.environ, self.environment), patch("sign_sideload_apk.run_tool", self.runner):
            return sign_apk(self.source, self.output, Path("apksigner"))

    def test_signs_then_verifies_pinned_certificate_without_mutating_input(self):
        self.assertEqual(self.sign(), self.digest)
        self.assertEqual(self.source.read_bytes(), b"unchanged build artifact")
        self.assertEqual(self.output.read_bytes(), b"signed artifact")
        self.assertEqual([command[1] for command in self.commands], ["sign", "verify"])
        self.assertFalse(Path(self.commands[0][self.commands[0].index("--ks") + 1]).exists())

    def test_every_secret_and_certificate_pin_is_required(self):
        for name in self.environment:
            with self.subTest(name=name):
                environment = dict(self.environment, **{name: ""})
                with patch.dict(os.environ, environment), patch("sign_sideload_apk.run_tool") as runner:
                    with self.assertRaises(SigningError):
                        sign_apk(self.source, self.output, Path("apksigner"))
                    runner.assert_not_called()
                self.assertFalse(self.output.exists())

    def test_invalid_base64_is_rejected_without_disclosing_secret(self):
        self.environment["SIDELOAD_KEYSTORE_BASE64"] = "invalid!private-material"
        with self.assertRaises(SigningError) as raised:
            self.sign()
        self.assertNotIn("private-material", str(raised.exception))
        self.assertFalse(self.commands)

    def test_invalid_certificate_pin_is_rejected_before_signing(self):
        self.environment["SIDELOAD_CERT_SHA256"] = "not-a-certificate"
        with self.assertRaises(SigningError):
            self.sign()
        self.assertFalse(self.commands)

    def test_wrong_certificate_never_publishes_an_output(self):
        self.environment["SIDELOAD_CERT_SHA256"] = "b" * 64
        with self.assertRaises(SigningError):
            self.sign()
        self.assertFalse(self.output.exists())

    def test_failed_sign_or_verify_does_not_publish_or_disclose_command_output(self):
        for failed_step in ("sign", "verify"):
            with self.subTest(step=failed_step):
                def runner(command, **kwargs):
                    if command[1] == failed_step:
                        raise subprocess.CalledProcessError(
                            1, command, stderr="private-store-password",
                        )
                    return self.runner(command, **kwargs)

                with patch.dict(os.environ, self.environment), patch("sign_sideload_apk.run_tool", runner):
                    with self.assertRaises(SigningError) as raised:
                        sign_apk(self.source, self.output, Path("apksigner"))
                self.assertNotIn("private-store-password", str(raised.exception))
                self.assertFalse(self.output.exists())

    def test_multiple_signers_are_rejected(self):
        def runner(command, **kwargs):
            result = self.runner(command, **kwargs)
            if command[1] == "verify":
                result.stdout += f"Signer #2 certificate SHA-256 digest: {'b' * 64}\n"
            return result

        with patch.dict(os.environ, self.environment), patch("sign_sideload_apk.run_tool", runner):
            with self.assertRaises(SigningError):
                sign_apk(self.source, self.output, Path("apksigner"))
        self.assertFalse(self.output.exists())

    def test_existing_output_is_not_overwritten(self):
        self.output.write_bytes(b"existing")
        with self.assertRaises(SigningError):
            self.sign()
        self.assertEqual(self.output.read_bytes(), b"existing")

    @unittest.skipIf(os.name != "posix", "CI signer cancellation uses POSIX process groups")
    def test_sigterm_stops_signer_and_removes_decoded_keystore(self):
        self.cancellation_probe()

    @unittest.skipIf(os.name != "posix", "CI signer cancellation uses POSIX process groups")
    def test_sigterm_during_process_launch_cannot_orphan_signer(self):
        self.cancellation_probe(launch_boundary=True)

    def cancellation_probe(self, launch_boundary=False):
        fake_signer = self.root / "fake-apksigner"
        marker = self.root / "signer-marker"
        fake_signer.write_text(
            f"#!{sys.executable}\n"
            "import os, pathlib, sys, time\n"
            "key = sys.argv[sys.argv.index('--ks') + 1]\n"
            "marker = pathlib.Path(os.environ['SIGNER_MARKER'])\n"
            "staged = marker.with_suffix('.tmp')\n"
            "staged.write_text(key + '\\n' + str(os.getpid()))\n"
            "staged.replace(marker)\n"
            "while True: time.sleep(1)\n",
        )
        fake_signer.chmod(0o700)
        environment = dict(os.environ, **self.environment, SIGNER_MARKER=str(marker), TMPDIR=str(self.root))
        script = Path(__file__).with_name("sign_sideload_apk.py")
        if launch_boundary:
            bootstrap = self.root / "launch-boundary.py"
            bootstrap.write_text(
                "import os, pathlib, signal, sys, time\n"
                f"sys.path.insert(0, {str(script.parent)!r})\n"
                "import sign_sideload_apk as signing\n"
                "real_popen = signing.subprocess.Popen\n"
                "def launch(*args, **kwargs):\n"
                "    child = real_popen(*args, **kwargs)\n"
                "    deadline = time.monotonic() + 10\n"
                "    while not pathlib.Path(os.environ['SIGNER_MARKER']).exists() and time.monotonic() < deadline:\n"
                "        time.sleep(0.02)\n"
                "    os.kill(os.getpid(), signal.SIGTERM)\n"
                "    return child\n"
                "signing.subprocess.Popen = launch\n"
                "raise SystemExit(signing.main())\n",
            )
            script = bootstrap
        process = subprocess.Popen(
            [sys.executable, str(script), str(self.source), str(self.output), "--apksigner", str(fake_signer)],
            env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        )
        self.addCleanup(lambda: process.kill() if process.poll() is None else None)
        self.addCleanup(process.stdout.close)
        self.addCleanup(process.stderr.close)
        deadline = time.monotonic() + 10
        while not marker.exists() and process.poll() is None and time.monotonic() < deadline:
            time.sleep(0.02)
        self.assertTrue(marker.exists(), "signer did not reach the blocking probe")
        key_path, signer_pid = marker.read_text().splitlines()
        self.addCleanup(lambda: os.kill(int(signer_pid), signal.SIGKILL) if Path(f"/proc/{signer_pid}").exists() else None)
        if not launch_boundary:
            self.assertTrue(Path(key_path).exists())
            process.send_signal(signal.SIGTERM)
        stdout, stderr = process.communicate(timeout=10)
        self.assertNotEqual(process.returncode, 0)
        self.assertFalse(Path(key_path).exists())
        self.assertFalse(self.output.exists())
        with self.assertRaises(ProcessLookupError):
            os.kill(int(signer_pid), 0)
        self.assertNotIn("private-store-password", stdout + stderr)


if __name__ == "__main__":
    unittest.main()
