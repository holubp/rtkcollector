# Android JSON Settings Import Design

## Goal

Register RtkCollector as an Android handler for JSON settings backup files so a
user can open a backup from WhatsApp, Files, email or another app and import it
through a safe confirmation flow.

## Non-Goals

- Do not change the settings backup JSON format.
- Do not add selective merge/import in this iteration.
- Do not auto-import settings from an external intent.
- Do not import arbitrary JSON files that are not RtkCollector settings backups.
- Do not weaken existing password redaction rules for session metadata.

## Existing Context

The Compose `MainActivity` already supports manual settings import through an
`ActivityResultContracts.OpenDocument` launcher. Manual import reads a URI,
parses it with `SettingsBackupFile.fromJson(JSONObject(text))`, writes imported
profiles through `ProfileStores`, restores selected workflow/settings references,
and restores plaintext NTRIP passwords only when the backup explicitly contains
them.

This feature should reuse the same import application logic after explicit user
confirmation. Android intent handling should only create a pending import
candidate.

## Android Entry Points

`app/src/main/AndroidManifest.xml` should register `.ui.MainActivity` for JSON
files with:

- `ACTION_VIEW` with `CATEGORY_DEFAULT` and `CATEGORY_BROWSABLE`.
- `ACTION_SEND` with `CATEGORY_DEFAULT`.
- MIME types: `application/json`, `text/json`, and `text/plain`.
- Schemes suitable for shared files: `content` and `file` for `ACTION_VIEW`.

The implementation should ignore launcher and USB attach intents. It should
extract only a single URI from:

- `intent.data` for `ACTION_VIEW`.
- `Intent.EXTRA_STREAM` for `ACTION_SEND`.

`ACTION_SEND_MULTIPLE` is out of scope for the first pass. If received, it may be
ignored or rejected with a clear unsupported-import message.

## Pending Import States

The app should model external JSON import as explicit UI state:

- `None`: no pending external import.
- `Ready`: URI read and parsed as a structurally valid RtkCollector settings
  backup, with an import summary.
- `Error`: URI read or validation failed, with a user-visible error message.

The app must not call the existing import writer while only constructing these
states.

## Confirmation UI

When a valid backup URI is received, show an import confirmation screen or modal
route with:

- Title: `Import settings backup`.
- Source display text: URI string or display name if cheaply available.
- Counts for command profiles, USB/baud profiles, NTRIP caster profiles, NTRIP
  mountpoint profiles, recording output profiles, storage profiles and settings
  sets.
- Selected settings set, selected workflow and last active NTRIP mountpoint when
  present.
- A warning when the backup contains plaintext NTRIP passwords:
  `This backup contains plaintext NTRIP passwords. Import only if you trust the source.`

Buttons:

- `Import`: applies the existing settings import logic, refreshes profile UI,
  clears pending import state, returns to the main screen and shows a success
  toast.
- `Cancel`: clears pending import state and returns to the previous/main screen
  without modifying settings.

When validation fails, show an error state with a concise explanation and a
`Dismiss` action. Invalid imports must not modify any settings or secrets.

## Structural Validation

External JSON must be validated before showing `Import` as an available action.
Validation should be stricter than simply checking that a file parses as JSON.

Required checks:

- File text must be valid JSON object text.
- `formatVersion` must equal `SettingsBackupFile.CURRENT_FORMAT_VERSION`.
- Required top-level arrays must exist and be JSON arrays:
  - `commandProfiles`
  - `usbBaudProfiles`
  - `ntripCasterProfiles`
  - `ntripMountpointProfiles`
  - `recordingPolicyProfiles`
  - `storageProfiles`
  - `settingsSets`
- Profile objects must parse through existing model `fromJson` functions.
- Settings sets must pass existing `RecordingSettingsSet.validate` rules.
- Optional selected IDs may be absent or null. If present, they should only be
  restored when they point to an imported object that exists.
- Plaintext password entries, when present, must be a JSON object mapping secret
  IDs to strings. Non-string password values should reject the import.

The validation result should be structured enough for tests:

- `valid`
- summary counts
- `containsPlaintextPasswords`
- `errorMessage` for failures

## Security Requirements

External import is a trust boundary. The app should treat JSON from WhatsApp or
another app as untrusted until validation succeeds and the user confirms.

Security rules:

- No import is performed automatically from an external intent.
- The confirmation screen must warn when plaintext passwords are present.
- The app should reject unsupported backup versions rather than trying to
  partially interpret them.
- The app should not log plaintext passwords.
- The app should not show plaintext passwords in the confirmation summary.
- The app should not persist imported plaintext passwords until the user taps
  `Import`.
- The app should cap the amount of text read from an external URI before parsing.
  A reasonable first limit is 2 MiB; larger files should fail with a clear
  `Settings backup is too large` message.
- The app should not grant new URI permissions beyond the temporary read access
  already granted by the sending app.
- Failed imports must leave existing profiles and NTRIP secrets unchanged.

## Data Flow

1. Android sends `ACTION_VIEW` or `ACTION_SEND` JSON intent to `.ui.MainActivity`.
2. `MainActivity.onCreate` or `onNewIntent` stores the latest intent.
3. Compose state observes the pending intent and calls a small importer reader.
4. The reader extracts a URI and reads at most the configured byte limit.
5. The parser validates the JSON as `SettingsBackupFile`.
6. The UI shows either a confirmation summary or a validation error.
7. User taps `Import`.
8. Existing import application logic writes profiles/settings/secrets.
9. Profile UI refreshes and the app returns to the main screen.

## Suggested Components

- `SettingsImportIntentReader`
  - Pure or Android-light helper for extracting a single URI from `Intent`.
  - Tests cover `ACTION_VIEW`, `ACTION_SEND`, unrelated actions and missing URI.

- `SettingsImportValidator`
  - Reads/parses a JSON string into a validated pending import model.
  - Produces summary counts and password warning flag.
  - Tests cover valid backup, invalid JSON, wrong version, missing arrays,
    malformed password object and oversized input handling.

- `PendingSettingsImport`
  - UI state model with `None`, `Ready` and `Error`.

- `SettingsImportConfirmationScreen`
  - Compose screen/modal route displaying summary, warnings and Import/Cancel
    actions.

## Error Handling

User-facing error examples:

- `Settings backup could not be read.`
- `Settings backup is too large.`
- `Unsupported settings backup format version.`
- `Settings backup is missing commandProfiles.`
- `Settings backup contains invalid NTRIP password data.`
- `This JSON file is not a RtkCollector settings backup.`

Implementation should preserve the original exception for developer diagnostics
where useful, but the UI should show concise user-readable text.

## Testing

Minimum tests:

- `ACTION_VIEW` JSON URI is extracted.
- `ACTION_SEND` stream URI is extracted.
- Launcher and USB attach intents produce no import candidate.
- Valid backup JSON produces summary counts and password warning state.
- Valid backup JSON without passwords reports no password warning.
- Invalid JSON produces an error result.
- Missing required arrays produce an error result.
- Unsupported format version produces an error result.
- Plaintext password values that are not strings reject the import.
- Oversized input rejects before parsing.
- Confirmed import uses the existing import application path; cancelled import
  makes no changes.

Manifest coverage should be added where practical, either through an XML
inspection test or an Android manifest test, to ensure JSON view/send filters do
not regress.

## Compatibility Notes

WhatsApp and file managers commonly send `content://` URIs with transient read
permission. RtkCollector should read the file while handling the intent and
should not assume long-term access to the URI. Import confirmation should keep
the parsed backup model in memory rather than relying on reopening the URI much
later.

If the app is already recording when a settings JSON is opened, the confirmation
screen may still be shown, but importing should either be disabled or require
the same safeguards as existing settings changes. The first implementation
should reject importing while recording with a clear message:
`Stop recording before importing settings.`
