#!/usr/bin/env python3
"""Sign a sideload APK using CI secrets and enforce its public certificate pin."""

import argparse
import base64
import binascii
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile


class SigningError(Exception):
    """A signing prerequisite or certificate verification failed."""

_cancel_requested = False


def check_cancelled() -> None:
    """Defer signal cancellation to a boundary with owned cleanup resources."""
    if _cancel_requested:
        raise SigningError("APK signing cancelled")


def run_tool(command: list[str], *, env: dict[str, str]) -> subprocess.CompletedProcess:
    """Run a signing tool and reap its process group if the caller is cancelled."""
    check_cancelled()
    process = subprocess.Popen(
        command, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, start_new_session=os.name == "posix",
    )
    try:
        while True:
            check_cancelled()
            try:
                stdout, stderr = process.communicate(timeout=0.2)
                break
            except subprocess.TimeoutExpired:
                continue
        check_cancelled()
    except BaseException:
        if os.name == "posix":
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        else:
            process.kill()
        process.communicate()
        raise
    if process.returncode:
        raise subprocess.CalledProcessError(process.returncode, command, stdout, stderr)
    return subprocess.CompletedProcess(command, process.returncode, stdout, stderr)


def sign_apk(source: Path, output: Path, apksigner: Path) -> str:
    """Publish output only after signing and verifying the pinned certificate.

    The input stays unchanged. Keystore material is decoded inside a private
    temporary directory and removed on ordinary exit/catchable cancellation; tool diagnostics are not
    echoed because they may contain private signing information.
    """
    names = (
        "SIDELOAD_KEYSTORE_BASE64", "SIDELOAD_STORE_PASSWORD",
        "SIDELOAD_KEY_PASSWORD", "SIDELOAD_KEY_ALIAS", "SIDELOAD_CERT_SHA256",
    )
    values = {name: os.environ.get(name, "") for name in names}
    missing = [name for name, value in values.items() if not value.strip()]
    if missing:
        raise SigningError("Missing stable signing configuration: " + ", ".join(missing))
    expected = values["SIDELOAD_CERT_SHA256"].strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SigningError("SIDELOAD_CERT_SHA256 must be a 64-digit SHA-256 fingerprint")
    try:
        keystore_bytes = base64.b64decode(
            "".join(values["SIDELOAD_KEYSTORE_BASE64"].split()), validate=True,
        )
    except (binascii.Error, ValueError):
        raise SigningError("Invalid base64 signing keystore") from None
    if not keystore_bytes:
        raise SigningError("Signing keystore is empty")
    if not source.is_file() or output.exists() or source.resolve() == output.resolve():
        raise SigningError("Require an existing input APK and a distinct, unused output path")
    output.parent.mkdir(parents=True, exist_ok=True)
    environment = dict(os.environ)
    # Remove the encoded key from subprocess environments; passwords use env:
    # references rather than command arguments or loggable properties files.
    environment.pop("SIDELOAD_KEYSTORE_BASE64", None)
    with tempfile.TemporaryDirectory(prefix="rtkcollector-signing-") as key_directory:
        keystore = Path(key_directory) / "signing.p12"
        with keystore.open("xb") as stream:
            os.chmod(keystore, 0o600)
            stream.write(keystore_bytes)
        with tempfile.TemporaryDirectory(prefix=".signed-apk-", dir=output.parent) as staging:
            staged = Path(staging) / "signed.apk"
            sign = [
                str(apksigner), "sign", "--ks", str(keystore),
                "--ks-key-alias", values["SIDELOAD_KEY_ALIAS"],
                "--ks-pass", "env:SIDELOAD_STORE_PASSWORD",
                "--key-pass", "env:SIDELOAD_KEY_PASSWORD",
                "--out", str(staged), str(source),
            ]
            verify = [str(apksigner), "verify", "--verbose", "--print-certs", str(staged)]
            try:
                run_tool(sign, env=environment)
                verification_environment = {
                    name: value for name, value in environment.items()
                    if name not in names
                }
                result = run_tool(verify, env=verification_environment)
            except (subprocess.CalledProcessError, OSError):
                raise SigningError("APK signing or signature verification failed; check signing configuration") from None
            digests = re.findall(
                r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
                result.stdout, re.MULTILINE,
            )
            if len(digests) != 1 or digests[0].lower() != expected:
                raise SigningError("APK signing certificate does not match the pinned stable identity")
            check_cancelled()
            os.replace(staged, output)
    return expected


def main() -> int:
    global _cancel_requested
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--apksigner", required=True, type=Path)
    args = parser.parse_args()
    _cancel_requested = False
    def cancelled(signum, frame):
        global _cancel_requested
        _cancel_requested = True

    previous = {sig: signal.signal(sig, cancelled) for sig in (signal.SIGTERM, signal.SIGINT)}
    try:
        digest = sign_apk(args.source, args.output, args.apksigner)
        check_cancelled()
    except SigningError as exception:
        parser.exit(1, f"Stable signing failed: {exception}\n")
    finally:
        for sig, handler in previous.items():
            signal.signal(sig, handler)
    print(f"Signing certificate SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
