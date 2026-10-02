# Security And Privacy Requirements

## Secrets

### SEC-NTRIP-TLS-001: Explicit Transport And Legacy Unsafe Migration

Status: Normative

Correction and source-upload profiles MUST select TLS with normal system trust
(the default) or explicit plaintext TCP. Both choices MUST work in Google Play
and sideload builds. Existing explicit choices MUST be preserved. Legacy unsafe
TLS and Custom-CA profiles MUST remain disabled until the user explicitly chooses
normal TLS or plaintext; they MUST NOT silently migrate to either. No local
acknowledgement may enable invalid TLS. The editor MUST preserve the migration
guard on unrelated saves, and sourcetable refresh MUST validate the displayed
transport rather than silently using an older stored policy.
Profiles predating explicit transport selection MUST also remain blocked until
the user chooses TLS or plaintext; their previous implicit plaintext value is
only a display hint and MUST NOT silently authorize a connection.

Verification:
- Automated: profile-store read/save/read and legacy migration tests.
- Manual: Android profile editor and recording/upload smoke tests after migration.

### SEC-NTRIP-TLS-002: Distribution And Service Intent Boundary

Status: Normative

Both build variants MUST accept explicitly selected plaintext and system-trusted
TLS policies and reject unsupported/unsafe TLS policies. Before constructing a
correction or upload request on recording start or update, the foreground
service MUST require correctly typed host, port, mountpoint, transport mode,
TLS verification and acknowledgement intent fields and validate the resulting
security policy. Missing, mistyped or unknown values MUST fail closed.

Verification:
- Automated: `ActiveRecordingConfigTest` against each variant's generated
  `BuildConfig`; `ServiceNtripIntentParserTest` and
  `RecordingForegroundServiceTest`.
- Build: both Google Play and sideload debug compile and unit-test tasks on a
  full Android host.

### SEC-NTRIP-TLS-003: Verified Endpoint And Redacted Transport Failures

Status: Normative

Correction download, sourcetable fetch, source upload and protocol retries MUST
use the same validated canonical endpoint and security policy. TLS MUST use
system trust and hostname verification, accept only TLS 1.2 or newer,
and complete its handshake before sending any NTRIP request, credential, GGA or
RTCM byte. A failed handshake MUST NOT fall back to plaintext. There MUST be no
trust-all or hostname-verification bypass path. Connection, handshake, stream and
caster-response failures exposed through status, diagnostics or session events
MUST NOT include passwords, Basic tokens, raw request frames, certificate bytes
or private-key material. These failures MUST remain advisory to receiver raw
capture.

Verification:
- Automated: `NtripTransportSecurityTest`, `NtripTlsSocketConnectorTest`,
  `NtripClientTest`, `NtripCasterUploadClientTest`, and
  `CasterUploadEventJsonTest`.
- Manual: real TLS correction and source-upload sessions with compatible
  casters; confirm receiver raw capture continues after TLS failure.

### SEC-SECRETS-001: Session Metadata Excludes Secrets

Status: Normative

`session.json` MUST NOT contain plaintext NTRIP passwords, tokens or raw
credentials. It may contain redacted metadata or secret references.

Verification:
- Automated: session metadata and settings-export tests.
- Review: all session metadata writers redact credentials.

### SEC-SECRETS-002: Committed Profile Owns Its Secret Binding

Status: Normative

The committed owning NTRIP profile's explicit secret binding MUST be the sole
runtime authority for that profile. Legacy/canonical aliases MAY be read as
migration inputs only and MUST NOT take precedence at runtime. Ordinary profile
JSON, profile IDs, logs, diagnostics and session metadata MUST NOT contain
password values. If password export is explicitly requested and consented, it
MUST traverse only committed, reachable profiles and MUST exclude staged or
orphan secret bindings. Existing explicit consent and sensitive-data warnings
remain required for that export exception.

Verification:
- Automated: secret binding precedence/collision, reachable-only export and
  profile/session/diagnostic redaction tests.
- Review: runtime credential lookup uses only the committed owner binding.
- Manual: credential collision migration and consented password export.

### SEC-SETTINGS-001: Plaintext Password Export Is Explicit

Status: Normative

Settings export MAY include plaintext NTRIP passwords only when the user
explicitly selects that option. The default export path MUST avoid plaintext
passwords.

Verification:
- Automated: settings export tests.
- Manual: export settings with and without password checkbox.

## Imports

### SEC-IMPORT-001: Imported Settings Are Validated

Status: Normative

Imported JSON settings MUST be structurally validated before use. Plaintext
password import MUST be explicit and user-visible.

Verification:
- Automated: settings import validation tests.
- Manual: Android "open JSON with RtkCollector" import flow.

### SEC-IMPORT-002: Settings Restore Is Complete And Does Not Transfer SAF Authority

Status: Normative

A confirmed settings restore MUST restore every profile family represented in
the validated backup, including RTKLIB and solution-policy profiles, so saved
settings-set references remain resolvable. If a historical backup omits a
profile-family array that was optional in that format, restore MUST retain the
currently installed profiles in that category and disclose that behavior in
the confirmation preview instead of silently clearing them. Android SAF tree
URI text MUST NOT be treated as transferable authority: an imported SAF storage
profile or settings-set storage override may retain a tree only when this
installation already holds persisted write permission. This check MUST use the
override's effective storage kind, including a kind inherited from its referenced
profile. Otherwise the imported URI MUST be stripped and the effective storage
selection MUST remain unusable until a locally granted folder is available
through the system picker.

Verification:
- Automated: complete profile-family restore and SAF permission-sanitisation
  tests.
- Manual: import on a second device requires selecting its SAF folder before
  recording.

### SEC-IMPORT-003: Profile Graph Restore Is One Validated Store Transaction

Status: Normative

After validation and confirmation, profile families, settings sets and
selection references MUST be written synchronously as one profile-store
transaction. The profile-store transaction MUST either commit the complete
validated graph or report failure without exposing a partially written graph.
Because Android may expose editor changes in process even when `commit()`
returns `false`, failure handling MUST synchronously restore the exact prior
values and value types for every touched profile-store key before reporting the
failure. If that rollback also fails, the app MUST report the uncertain store
state explicitly. The same rollback rule applies to a batch secret-store
commit, without including secret values in errors or logs.
Imported plaintext password persistence is a subsequent secret-store
transaction. The profile transaction MUST already leave imported credential
bindings isolated from local secrets, so interruption before that secret write
leaves the imported profiles disarmed. A secret-store failure MUST be disclosed
separately and MUST NOT be represented as a successful credential restore. The
user-facing result MUST distinguish a failed secret write whose rollback was
confirmed from a failed rollback whose persisted state is uncertain, without
including secret values in either message.

Verification:
- Automated: `SettingsImportModelsTest` validation tests and
  `SharedPreferencesTransactionsTest` commit-failure rollback tests.
- Review: `ProfileStores.replaceImportedSettings` synchronous editor transaction.
- Manual: interrupt or fail profile-store and Android Keystore operations and
  confirm distinct outcome messages.

### SEC-IMPORT-004: Imported Credential Bindings Cannot Reuse Local Secrets

Status: Normative

Before an imported profile graph can resolve credentials, represented NTRIP
profiles and explicit secret references MUST be re-keyed to fresh,
collision-resistant IDs in that graph. Only plaintext passwords explicitly
carried by the backup may be copied to those fresh IDs. Pre-existing local
secrets MUST NOT be cleared or silently rebound. If an optional profile family
is genuinely omitted because it is absent from a historical backup, its
retained local profiles and credentials MUST remain unchanged and MUST be
reported as retained. An imported endpoint or username override over such a
retained profile MUST receive a fresh, disarmed binding unless the backup
explicitly carries that override's password.

Verification:
- Automated: `SettingsImportModelsTest`; profile-store and secret-store
  import call-site review.
- Manual: import colliding IDs with and without passwords and verify the
  imported graph does not inherit local credentials; omit an optional family
  and verify its retained credentials remain.

### SEC-IMPORT-005: Present Optional Family Fields Are Strictly Typed

Status: Normative

An optional profile-family field is a historical omission only when its JSON key
is absent. If the key is present, it MUST contain a valid array of profiles;
malformed or non-array values MUST reject the entire import before persistence.
An empty present array is valid and MUST represent an intentional empty family,
not a request to retain the local family.

Verification:
- Automated: `SettingsImportModelsTest` present-family malformed-field tests.
- Manual: import backups with omitted, empty and malformed optional-family
  fields and confirm their distinct outcomes.

### SEC-IMPORT-006: Legacy Profile Migration Is Staged And Recoverable

Status: Normative

Migration of legacy settings-set field overlays into explicit derived profiles
and active references MUST run only while idle. It MUST be idempotent and use a
staged graph transaction that retains a validated prior graph and a durable
migration phase before publishing reachable profile references. New secret
entries MUST be staged before references become reachable; migration MUST NOT
overwrite live secret bindings, and orphan cleanup MUST wait until commit is
confirmed. Recovery MUST run before ordinary profile-store reads, default
fallbacks or write-on-read migration, and MUST be serialized with configuration
writes and recording Start.

For supported persistence failures or process interruption, recovery MUST leave
one consistent committed old or new graph. If rollback/recovery persistence is
uncertain, the new graph MUST be blocked from use, recovery MUST remain
available, and the app MUST report uncertainty without claiming success. Lost
encryption keys, unreadable storage or revoked SAF permissions are unavailable
prerequisites, not graph-migration success; retain evidence and block only
affected operations. Distinguish absent passwords from present but undecryptable
entries. Do not rewrite unrelated stores.

Migration MUST preserve explicit TLS/plaintext, credentials, outputs and
operator customizations. Dormant values ignored by a fixed policy MUST remain
recoverable but MUST NOT become active. Uncertain source lineage or conflicting
legacy policies MUST be retained for explicit operator review, never guessed.
Continue format-1 RC2-RC6 imports and introduce a newer backup schema when
persisted semantics change; exports MUST declare their format and MUST NOT
imply old releases understand new semantics.

Verification:
- Automated: repeated/idempotent migration, format-1 RC2-RC6 imports, new-schema
  round trips, partial secret/profile failures, interruption/recovery, collision,
  missing optional families, and reachable-only export tests.
- Review: every normal profile-store read/default fallback is gated on recovery;
  staged references are not visible before commit.
- Manual: upgrade/import with legacy customizations, missing credentials,
  storage/Keystore failure and SAF reselection.

## Diagnostics

### SEC-DIAGNOSTICS-001: Diagnostics Are Opt-In And Redacted

Status: Normative

Runtime diagnostics and performance monitoring MUST be disabled by default.
When enabled, diagnostic records MUST redact NTRIP passwords, authorization
headers, tokens and credential-like fields before writing or sharing. TLS and
caster failure paths MUST not pass untrusted response text or exception content
that could contain raw requests, certificate or private-key material into
diagnostics or session events.

Verification:
- Automated: diagnostics redaction and disabled-state tests.
- Review: diagnostic call sites use guarded construction for hot-path records.
