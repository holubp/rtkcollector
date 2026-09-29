# Stable Sideload Signing Checkpoint

Scope: user-requested re-release of the RC4 GitHub APK with a stable sideload
signing key. Preserve `v1.0-RC4` source tag and debug build behavior. A single
reinstall is expected; never overwrite a key or imply old signatures can update.
Google Play key enrollment is not authorized by this request.

- [x] Diagnose signing mismatch using RC3/RC4 certificate fingerprints.
- [x] Add fail-closed, pinned-certificate signing helper and focused red/green tests.
- [ ] Review workflow, docs and helper; pass full clean-host CI and push tooling.
- [ ] Owner selects generation/backup arrangement; generate or obtain key outside Git.
- [ ] Owner verifies independently recoverable backup before publication.
- [ ] Configure GitHub secrets and public fingerprint pin.
- [ ] Build/re-release RC4; independently verify downloaded checksum and certificate.
- [ ] Verify the same certificate across a second CI run; record release evidence.
- [ ] Owner tests in-place update with a higher-version stable-key APK.

Evidence: twelve helper tests pass (2026-09-29), including process-level SIGTERM
cleanup and cancellation during subprocess launch after independent review
findings. No permanent keystore or secrets were
created. No stable-key APK has been published. Key backup/provisioning is pending
owner input. Build tests will run in GitHub CI per the owner's instruction.
Routing: bounded changes inline; independent `gpt-6-sol` high review requested
for secret/signing integrity. No Gradle builds run locally. Usage delta unknown.
