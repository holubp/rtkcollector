# Security And Privacy Requirements

## Secrets

### SEC-SECRETS-001: Session Metadata Excludes Secrets

Status: Normative

`session.json` MUST NOT contain plaintext NTRIP passwords, tokens or raw
credentials. It may contain redacted metadata or secret references.

Verification:
- Automated: session metadata and settings-export tests.
- Review: all session metadata writers redact credentials.

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

## Diagnostics

### SEC-DIAGNOSTICS-001: Diagnostics Are Opt-In And Redacted

Status: Normative

Runtime diagnostics and performance monitoring MUST be disabled by default.
When enabled, diagnostic records MUST redact NTRIP passwords, authorization
headers, tokens and credential-like fields before writing or sharing.

Verification:
- Automated: diagnostics redaction and disabled-state tests.
- Review: diagnostic call sites use guarded construction for hot-path records.
