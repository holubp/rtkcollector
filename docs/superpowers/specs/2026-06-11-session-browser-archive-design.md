# Session Browser, Share ZIP, Archive And Restore Design

## Goal

Replace the current one-item Sessions screen with a storage-backed session
browser that lists current and completed recordings, supports multi-selection,
temporary share ZIPs, permanent archive ZIPs, restore from archive and safe
deletion.

## Scope

The screen must list sessions from the currently configured storage location,
sorted newest first. It must distinguish:

- current session;
- other normal recordings;
- archived recordings.

The feature must work for app-private filesystem storage in V1. SAF storage must
be represented by the same model and UI, but SAF traversal and mutation may be
implemented behind a document-tree adapter so it can be completed without
rewriting the UI.

## User Behaviour

The Sessions menu shows grouped lists:

1. Current session, if one is active or known from service state.
2. Recordings, meaning normal session folders discovered in configured storage.
3. Archived recordings, meaning archive ZIP files discovered in configured
   storage.

Each group supports selection. The user can select:

- current session;
- all normal recordings;
- all archived recordings;
- all sessions and archives;
- none.

Active recordings cannot be deleted, archived or shared as ZIP. Completed
recordings can be selected for sharing, archiving or deletion. Archives can be
selected for sharing, restoring or deletion.

## Share ZIP

Share ZIP is temporary. For every selected completed normal recording, the app
creates one ZIP file in app cache, launches Android sharing, then schedules the
temporary ZIP for cleanup. Sharing never deletes or modifies recording
originals.

Android does not reliably notify when the receiver app has finished reading a
shared file, so cleanup is best-effort:

- remove old temporary share ZIPs when the Sessions screen opens;
- remove newly created temporary share ZIPs after a delay when safe;
- tolerate cleanup failure and retry later.

## Archive

Archive is permanent compression. For every selected completed normal recording,
the app creates a maximum-compression ZIP in configured storage. It verifies ZIP
contents before deleting originals. Only after successful verification may the
original session folder be removed.

Archived recordings are shown in the Archived recordings group. A normal session
and its archive should not both be shown as the same logical recording after a
successful archive.

## Restore

Restore from archive extracts the archive ZIP back to a normal session folder,
verifies required contents, then removes the ZIP. If restore fails, the archive
ZIP must remain.

For V1 verification, required contents are:

- `session.json`, when present in the archive;
- `receiver-rx.raw`, when present in the archive;
- at least one regular file.

The restore operation must not overwrite an existing session folder. If the
target folder already exists, create a unique restored folder name.

## Deletion

Delete is destructive and must require confirmation. It supports multiple
selection. Active/current recording deletion is forbidden. Deleting a normal
recording removes its session folder. Deleting an archived recording removes the
archive ZIP. Partial failures are reported per item.

## Architecture

Add a session-browser model layer separate from Compose:

- discovery model for normal and archived session entries;
- grouping and latest-first sorting;
- selection model;
- operation planning for share, archive, restore and delete.

Filesystem storage uses `java.nio.file.Path` and existing `SessionZipExporter`
where possible. SAF storage should use the same public model through a future
document-tree implementation.

The UI stays thin: it renders grouped entries, selection actions, progress and
confirmation dialogs. It must not mutate `receiver-rx.raw` except by deleting a
whole completed session after confirmation.

## Testing

Pure/unit tests must cover:

- latest-first sorting;
- current, recording and archive grouping;
- select-current, select-recordings, select-archived and select-all;
- active session cannot be deleted, archived or shared;
- one share ZIP plan is produced per selected recording;
- archive creates a ZIP, verifies it and removes originals;
- restore extracts a ZIP and removes the archive only after verification;
- delete plans exclude active sessions.

Filesystem tests must use temporary directories and must not depend on Android
storage APIs. SAF behaviour can be covered by fake document-tree tests when the
adapter is implemented.

## Non-Goals

- No map, GIS or shapefile features.
- No background upload or cloud sync.
- No modification of raw receiver bytes.
- No combined multi-session ZIP; multi-export creates one ZIP per selected
  session.
