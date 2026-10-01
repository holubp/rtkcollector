# Stable Sideload Signing Checkpoint

Scope: user-requested re-release of the RC4 GitHub APK with a stable sideload
signing key. Preserve `v1.0-RC4` source tag and debug build behavior. A single
reinstall is expected; never overwrite a key or imply old signatures can update.
Google Play key enrollment is not authorized by this request.

- [x] Diagnose signing mismatch using RC3/RC4 certificate fingerprints.
- [x] Add fail-closed, pinned-certificate signing helper and focused red/green tests.
- [x] Review workflow, docs and helper; pass full clean-host CI and push tooling.
- [x] Owner selects generation/backup arrangement; generate or obtain key outside Git.
- [x] Owner confirms independent key backup outside GitHub before publication.
- [x] Configure GitHub secrets and public fingerprint pin.
- [x] Build/re-release RC4; independently verify downloaded checksum and certificate.
- [x] Verify the same certificate across a second CI run; record release evidence.
- [ ] Owner tests in-place update with a higher-version stable-key APK.

Evidence: twelve helper tests pass (2026-09-29), including process-level SIGTERM
cleanup and cancellation during subprocess launch after independent review
findings. At the 2026-09-29 checkpoint, no permanent keystore or secrets had
been created and no stable-key APK had been published. Key backup/provisioning
was pending owner input. Build tests run in GitHub CI per the owner's instruction.
Routing: bounded changes inline; independent `gpt-6-sol` high review requested
for secret/signing integrity. No Gradle builds run locally. Usage delta unknown.

Independent fix audit passes for `726bd33`: both steady-state and subprocess
launch cancellation probes terminate/reap the signer and remove decoded key
material. CI `36526339395` passed the repository gate, both distribution checks
and APK assembly, but the signing exercise failed because `sdkmanager` was not
on PATH. Both workflows now use its explicit Android SDK path. Rerun
`36526770060` passed for `20d3a94`, including full repository/variant gates,
APK assembly and two real signing/verification passes with a disposable key.
The owner confirmed an independent key backup outside GitHub on 2026-10-01.
Backup restore was not witnessed by the agent; the owner should verify the
saved keystore can be opened with its recorded credentials.
The four repository Actions secrets and public certificate pin were present;
secret values were not read. Clean-host CI `36527196955` passed for `bb0fade`.
RC4 release runs `36850174366` and `36850525062` both passed. Independently
downloaded APKs from both runs verified with `sha256sum -c` and `apksigner`;
both had APK SHA-256
`e453ff4468027c166f3ec8c5d15f28a7bd277adb70a0c8d9fa287badc7363851`
and one signer with certificate SHA-256
`d8047f43b2748198ae69ff773be13d3aa11c8ac36159dd4e6768a3ac2db3e329`.
The final public receipt records source `e6dd75fb` and tooling `bb0fade`.
An in-place update using a future higher-version stable-key APK remains an
owner device test; older differently signed installations need one reinstall.
