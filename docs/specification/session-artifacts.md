# Session Artifact Requirements

## Session Files

### SESSION-FILES-001: Required Artifact Separation

Status: Normative

Sessions MUST distinguish receiver RX, app TX to receiver, correction input,
events, quality logs, generated solution exports and metadata as separate
artifacts.

Verification:
- Automated: session writer and artifact model tests.
- Manual: completed session contains expected files for selected recording
  outputs.

### SESSION-META-001: Session Metadata Records Workflow Context

Status: Normative

`session.json` SHOULD record workflow, receiver, correction-source, artifact,
solution-policy and validation context without embedding secret values. When a
solution policy profile is active, metadata SHOULD include the selected profile
identifier plus the screen and mock-location source policies.

Verification:
- Automated: session metadata writer tests.
- Review: metadata fields align with `docs/session-format.md`.

## Generated Exports

### SESSION-NMEA-001: Generated NMEA Preserves Sub-Second UTC

Status: Normative

Generated `receiver-solution.nmea` MUST preserve sub-second UTC where source
telemetry provides it. It MUST NOT copy binary/noise fragments that merely look
like dollar-prefixed NMEA lines. Regeneration of NMEA for completed sessions
MUST update only `receiver-solution.nmea` from the receiver/in-device solution.
It MUST NOT overwrite RTKLIB real-time or postprocessed NMEA artifacts.

Verification:
- Automated: UM980 NMEA exporter/re-exporter tests; receiver-family NMEA
  regeneration tests; source-aware NMEA sharing tests.
- Manual: compare generated NMEA against known high-rate recording.

### SESSION-RTCM-001: NTRIP RTCM Is Exportable

Status: Normative

When NTRIP correction recording is enabled, the session MUST retain correction
input bytes in a form usable by downstream post-processing tools.

Verification:
- Automated: correction-input raw/RTCM3 extraction tests.
- Manual: replay correction stream through the downstream pipeline.

## Archive And Restore

### SESSION-ARCHIVE-001: Destructive Session Actions Reject Active Recording

Status: Normative

Archive and delete operations MUST reject the session currently owned by the
recording service at the operation boundary. They MUST NOT rely only on cached
Activity or dashboard state. The active session MUST remain protected across
Activity recreation while recording continues.

Verification:
- Automated: active-session registry tests and archive/delete entry-point
  tests for filesystem and SAF locations.
- Manual: recreate the Activity during recording and confirm the current
  session cannot be archived or deleted.

### SESSION-ARCHIVE-004: Destructive Operations Lease The Session Atomically

Status: Normative

Archive and delete MUST acquire an atomic destructive-operation lease that
checks the session is inactive while acquiring the lease. Recording activation
MUST perform the corresponding lease check under the same registry
synchronisation. A destructive operation MUST retain its lease through the
destructive boundary, so activation cannot race between an initial inactive
check and source deletion.

Verification:
- Automated: `ActiveRecordingSessionRegistryTest` activation-at-delete-boundary
  test and filesystem/SAF destructive-operation tests.
- Review: all filesystem and SAF archive/delete entry points acquire the lease
  before destructive work.

### SESSION-SHARE-001: Temporary Shares Reject Active Sessions At Execution

Status: Normative

Temporary ZIP and copied NMEA share exports MUST recheck the selected session
against the service-owned active-session registry inside the export operation,
immediately before opening source artifacts. They MUST NOT rely only on cached
browser capabilities or Activity state, and MUST reject rather than expose a
partial snapshot of an active recording.
Failed single-file or multi-file share preparation MUST remove temporary and
already-created cache outputs from that operation before reporting failure.

Verification:
- Automated: filesystem share tests and SAF share entry-point source tests.
- Manual: queue a share while recording starts and confirm no partial share is
  produced.

### SESSION-ARCHIVE-002: Source Deletion Requires Content Verification

Status: Normative

Archiving MUST delete the source session only after the destination archive has
been reopened and verified against the source file set and contents. Restoring
MUST delete the source archive only after every restored file has been reopened
and verified against archive contents. Any verification or provider failure
MUST retain the authoritative source and may remove only newly created partial
output.

Verification:
- Automated: archive corruption, truncated-copy and restore verification tests.
- Manual: SAF provider failure test retains the original session or archive.

### SESSION-ARCHIVE-003: Archive Import Is Bounded And Path-Safe

Status: Normative

Session ZIP inspection and restore MUST reject unsafe or duplicate paths and
MUST enforce explicit limits for entry count, path depth, per-entry expanded
bytes, cumulative expanded bytes and expansion ratio. SAF restore MUST preserve
safe nested paths or reject them before writing; it MUST NOT flatten different
entries onto the same basename.

Verification:
- Automated: traversal, duplicate-entry, excessive-depth, entry-count,
  expanded-size and compression-ratio tests.
- Review: filesystem and SAF restore use the same bounded archive policy.

### SESSION-NMEA-002: SAF NMEA Replacement Is Rollback-Safe

Status: Normative

Regenerating `receiver-solution.nmea` through SAF MUST retain the previous valid
export until a non-empty replacement has been fully written and verified. A
provider rename, read, write or verification failure MUST surface an error and
MUST NOT report successful replacement after deleting the previous export.

Verification:
- Automated: replacement-state/helper tests and SAF action source review.
- Manual: provider without rename support preserves the old export on fallback
  failure.

### SESSION-RTKLIB-001: RTKLIB Outputs Are Separate Advisory Artifacts

Status: Future

When RTKLIB-EX real-time processing is enabled, RTKLIB-derived user-facing
solution output MUST be stored separately from receiver-derived solution output.
The required RTKLIB artifacts are `rtklib-solution.nmea` and
`rtklib-solution.pos`. Optional RTKLIB engine diagnostics MAY be stored in
`rtklib-status.jsonl`. These artifacts MUST NOT replace or modify
`receiver-rx.raw`, `correction-input.raw`, `receiver-solution.nmea` or
`receiver-solution.jsonl`. Completed-session RTKLIB postprocessing MAY create
additional artifacts such as `rtklib-postprocessed-forward.nmea` and
`rtklib-postprocessed-combined.nmea`. When completed-session postprocessing
converts RTCM3 correction input to base-station RINEX before calling RTKLIB
`postpos()`, it MUST use the converted base RINEX header as the reference
position source. If native real-time RTKLIB text output is unavailable and the
app synthesizes a minimal GGA sentence from a solution snapshot, it MUST NOT
write ellipsoidal height into the NMEA GGA mean-sea-level altitude field or
claim a zero geoid separation. These artifacts MUST be shared only when they
already exist and MUST NOT be implied by the receiver NMEA regeneration action.

Verification:
- Automated: workflow artifact validation and RTKLIB output writer tests.
- Manual: RTKLIB-enabled session contains RTKLIB NMEA/POS artifacts while
  receiver raw and receiver solution artifacts remain separate.
