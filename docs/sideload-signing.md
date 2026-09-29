# Stable Sideload Signing

Android accepts an update only when the package name and signing identity match
the installed app, and its version code is not older. Windows Android Studio
debug builds reuse that machine's debug keystore. GitHub-hosted runners do not
provide a durable debug key: the earlier RC3 and RC4 APKs had different
certificates and therefore could not update one another.

## Release Contract

The GitHub sideload APK workflow signs the existing debug build with a dedicated
persistent sideload key. This does **not** turn it into a production/Play build.
It preserves the debug build's runtime behavior and uses a stable certificate
for future updates. The workflow has no fallback to ephemeral debug signing.
It verifies the resulting signature against a pinned public SHA-256 fingerprint
before uploading the APK, checksum and public signing-certificate receipt.

Source tags stay immutable. When rebuilding an older tag, the source comes from
that tag and signing tooling comes from the immutable workflow-dispatch commit.
Replacing an existing release asset changes its APK checksum, not its source
tag. Record the new checksum, certificate and signing-tooling commit in the
release notes; the public `.signing.txt` receipt records both source/tooling
commits and the certificate fingerprint.

The first installation with this new key requires uninstalling installations
signed with a Windows debug key or earlier ephemeral CI key. Export settings
and recordings to a safe location outside app-owned storage first; uninstalling
may delete app-owned sessions and preferences. Future APKs signed with the
stable key can update in place. Never regenerate the key to solve a CI failure.
Ordinary Windows Android Studio debug builds still use that machine's debug
key. To update a stable-key installation from a local APK, sign that APK with
the same dedicated keystore rather than the default Windows debug key.

## Owner Provisioning

The owner must decide how the key is generated and make an independently
recoverable backup **before publication**. Keep private key material and
passwords outside the repository, including ignored shared-storage folders.
Use protected storage, an encrypted offline backup, and a password manager.
GitHub secrets cannot be downloaded later and are not a recoverable backup.

Recommended Windows procedure (PowerShell, with the Java `keytool` on PATH):

```powershell
keytool -genkeypair -keystore "$env:USERPROFILE\RtkCollectorSigning\sideload.p12" -storetype PKCS12 -alias rtkcollector-sideload -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v -keystore "$env:USERPROFILE\RtkCollectorSigning\sideload.p12" -alias rtkcollector-sideload
```

Create the private directory first, use the interactive password prompts, and
record the certificate SHA-256 fingerprint. PKCS12 normally uses the same
password for the store and private key. Back up the keystore and recovery
information independently; verify the backup can be opened with `keytool`.
Do not send keystores or passwords in chat.

Configure these repository Actions secrets at **Settings > Secrets and
variables > Actions**, or using `gh secret set` with standard input:

| Name | Value |
| --- | --- |
| `SIDELOAD_KEYSTORE_BASE64` | Base64-encoded keystore bytes (base64 is not encryption) |
| `SIDELOAD_STORE_PASSWORD` | Keystore password |
| `SIDELOAD_KEY_PASSWORD` | Private key password |
| `SIDELOAD_KEY_ALIAS` | `rtkcollector-sideload`, or the alias actually created |

Configure the repository Actions **variable** `SIDELOAD_CERT_SHA256` with the
public certificate fingerprint as 64 hexadecimal digits, without colons. The
workflow requires all values and rejects a signature that does not match this
pin. Limit repository write access because approved workflows can use signing
secrets. Never print private values in shell tracing, logs or command arguments.
The helper cleans up on ordinary exits, SIGINT and SIGTERM, and stops its signer
process group. A forced SIGKILL or host failure cannot execute cleanup; release
signing must therefore remain on ephemeral CI runners, never cached or uploaded.

## Verify A Release

1. Run the repository tests in clean-host CI; do not rely on Termux APK assembly.
2. Dispatch `release-debug-apk.yml` for the approved existing prerelease tag.
3. Require a successful signing/verification step before release upload.
4. Download the APK, `.sha256` and `.signing.txt` assets independently.
5. Run `sha256sum -c <apk>.sha256` and Android SDK
   `apksigner verify --verbose --print-certs <apk>`.
6. Compare the certificate fingerprint to `SIDELOAD_CERT_SHA256` and the public
   signing receipt, then repeat in a separate workflow run to prove continuity.
7. On Android, verify a subsequent higher-version APK updates in place without
   losing settings or recordings. Never describe matching certificates alone
   as a completed device-update test.

## Google Play Is Separate

This change does not enroll a key with Play or change the Play upload key.
Whether to supply this same app-signing key to Play is an explicit owner
decision. If Play uses a different app-signing key, switching between sideload
and Play installs with the same package name requires a reinstall. If matching
distribution identities are desired, supply the existing key during initial
Play App Signing enrollment; use a separate upload key for Play submissions.
See [Android signing guidance](https://developer.android.com/studio/publish/app-signing)
and [GitHub Actions secrets](https://docs.github.com/en/actions/concepts/security/secrets).
