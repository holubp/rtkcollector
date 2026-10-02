# Settings and profile ownership

Status: Proposed; pending human approval. No application behaviour is changed by
this document. Baseline: `b25cce2634b9c8476b9f25899180990eaa3e8b13`.

## Contract

A profile owns its fields. A reference selects that profile intact; it cannot
override the profile's endpoint, credentials, commands or other fields. Settings
sets are orchestration objects: they own defaults, applicability and selection
policies, not shadow copies of fields owned by referenced profiles.

Allowing a selection to change means replacing a whole profile reference, not
unlocking arbitrary nested fields. Editing the owning profile remains explicit;
built-ins must be copied. Locking a reference locks its identity, not a historical
snapshot of its contents. Shared-profile edits affect its users on their next
Start, with affected sets visible in the editor. An idle selected setup refreshes
after an explicit profile edit and shows the content its next Start will use;
no separate reactivation is required. Running sessions retain
their validated snapshot except for explicit supported live-source changes.
The existing live Mock GPS enable/rate control is another explicit permitted
update; neither exception permits general profile edits to mutate a session.

## Ownership inventory

| Owner | Owned values | References and constraints |
| --- | --- | --- |
| Settings set | Workflow default/activation policy; receiver capability identity; default profile selections and their policies; upload enabled state | Selects complete profiles. Does not own host/password/scripts/output/URI overlays. |
| Init/shutdown profile | Receiver family, init/shutdown commands, explicit telemetry configuration | Family must match receiver capabilities and workflow. No automatic command rewriting. |
| USB/baud profile | USB matching, initial/target baud and bridge settings | Commands and device capability must be compatible; retain documented baud-switch ordering. |
| Correction caster profile | Host, port, account, secret binding, NTRIP version, transport | Mountpoint references this caster intact. TLS or plaintext remains explicit; no guessing or downgrade. |
| Correction mountpoint profile | Caster reference, mountpoint, GGA policy and source metadata | Cannot override the caster. A different caster means a different source profile. |
| Source-upload profile | Host, port, mountpoint, credentials, protocol, transport, retries and safety | Independent of correction download. A settings set explicitly enables/disables upload and selects this whole profile. |
| Recording-output profile | Recorded/exported outputs and mock-output enable/rate | Solution source belongs to solution policy; no hidden output field overrides. |
| RTKLIB profile | Processing and input-routing configuration | Only required when workflow uses RTKLIB; availability cannot silently change the workflow. |
| Solution policy profile | Screen and mock engine selection | Explicit engine policies require compatible workflow; AUTO selection remains intentional arbitration, not configuration rewriting. |
| Storage profile | Destination type, SAF binding and reselection state | Device-local permissions remain external prerequisites. No inline URI replacement. |
| Accepted base-coordinate profile | Accepted position, height semantics and provenance | Explicit handoff materializes MODE BASE in chosen init profile; validate agreement before Start. |
| Application preferences | Theme/layout/cards, device filter, folding, diagnostics | Independent of settings-set activation and Re-apply. Device filter is not a physical receiver selection. |
| Session/runtime | Resolved immutable configuration, live measurements, averaging, operational connection state | Not defaults or profile content. Persist redacted provenance, never credentials. |

Receiver capability identity remains an explicit set-level selection, not
something inferred from the Device filter or silently replaced by a command
profile. Validate it against command family once; pass the same validated
identity to driver, RTKLIB routing and UI. Expose incompatible choices clearly.
Base coordinates likewise resolve from the set default and explicit active
selection under its policy; a separately remembered global selection must not
silently supersede the set default.

App-global ownership does not assert persistence that the code lacks: current
layout/unit/theme controls use UI saved state, whereas folding/filter/diagnostic
preferences have separate durable stores. Preserve existing persistence in this
scope; any durability change requires a separately approved requirement.

`enabledByDefault` on an upload profile is a creation suggestion only, not a
second runtime authority. Upload selection policy governs the pair `(enabled,
profile reference)` so a fixed upload cannot be partially changed. Off preserves
the remembered profile without connecting. None, not chosen, disabled and not
applicable are distinct states; an inactive optional reference does not block
Start merely because it is absent or stale. Enabling it requires validation.

## NTRIP dependency rule

The effective correction caster is derived from the selected mountpoint's caster
reference. A settings set may constrain selectable mountpoints to a caster ID.
This is a restriction, not an endpoint override. A fixed mountpoint already fixes
its caster; contradictory independent restrictions are invalid.

An unlocked mountpoint with a fixed caster shows only sources belonging to that
caster and explains the restriction. Two accounts on the same host are distinct
profiles. A mismatch is a visible configuration problem, never substitution or
fallback to another caster with the same mountpoint name. Selecting a source
never rewrites the defaults of a settings set or any owning profile.

Caster management edits the caster itself. Mountpoint management edits its own
reference explicitly. Such edits must not rewrite all referencing settings sets,
especially protected built-ins. Save validates dependencies and reports affected
sets; an invalid set remains highlighted until repaired, without losing choices.

## Uniform policies and user experience

New settings sets have a source-selection policy plus an optional fixed caster
restriction, not an independent active-caster selection policy. Source creation
may suggest a caster but saves an explicit source-to-caster reference. Changing
a restriction retains an incompatible source visibly until repaired; never
silently clear/substitute it. Source selection needs no prior caster selection.

Legacy caster-policy mapping: LOCKED with a valid caster becomes a fixed
restriction. DEFAULT_OVERRIDABLE cannot override a source's caster; retain its
former default only as a source-creation suggestion/recovery value. Fixed-null,
CHOOSE_ONCE_REMEMBER and ASK_EVERY_TIME caster policies need operator review
when applicable: explicitly choose unrestricted/fixed restriction and source
policy before changing that active route. Do not silently strengthen, drop or combine policies. A conflicting
fixed source also needs review. Dormant conflicts resolve before route enabling.

Remove reference-induced edit protection for user-owned coordinate profiles;
show affected sets and confirm shared edits. A coordinate edit never edits MODE
BASE automatically: affected idle sets show disagreement and Start rejects it
until explicit handoff/profile editing repairs it. Running coordinates remain
snapshotted; built-in/protected owning records remain protected.

Use one pure resolver for Home, Menu, Start and supported live updates. It returns
effective selections, provenance, applicability, locks and validation problems.
No separate UI-only or Start-only resolution/fallback logic is allowed.

- Default/overridable: use set default until explicitly replaced in active setup.
- Fixed: select default; disable the corresponding selection with a lock symbol.
- Choose once/remember: initially unchosen; an explicit valid operator choice
  is remembered until changed or reset, including across app restart. A failed
  Start does not erase this remembered choice.
- Ask every time: require selection for each recording; clear its transient
  selection after stop or failed Start, without changing set defaults.

Store remembered choices keyed by settings-set identity; never leak one set's
choices into another. Per-run ask choices cannot survive into a new recording
after restart/recovery. Applicability gates prompts: an inactive upload does not
ask for an upload profile; an explicit Off/None choice counts where allowed.
Conflicting fixed constraints fail validation rather than forcibly selecting
another dependency. The current explicit user choice takes precedence over an
older remembered value when policy permits replacement.

Workflow activation intent (including leave current unchanged) remains distinct
from selection policy. Re-apply reruns activation and resets active selections
to the set's defined starting state, including workflow; it does not reset global
preferences or edit shared profiles. Modified `+` denotes differences in active
setup from the applied set, not merely the presence of ignored override objects.
Re-apply clears this set's choose-once memory and active replacements. Workflow
LEAVE_INTACT deliberately retains the current workflow as the activation baseline,
not an unexplained modification. Other sets' defaults/choices remain unchanged.

Keep the eight compact Home actions, folding and forced expansion for actionable
configuration problems. Menu active selectors follow the same locks as Home;
profile-library management stays separate. A fixed-caster restriction must not
lock an otherwise changeable mountpoint. Unsupported/unused options are shown
with applicability rather than misleading operational errors.

Convenient mountpoint typing and mock toggle/rate changes remain accessible.
They create/reuse an explicit derived user profile and select it, rather than
silently changing an existing referenced profile or overlaying its fields.
Expose the resulting profile identity and modified state. Protected defaults
remain unchanged. Never derive profiles repeatedly from refresh callbacks.
Shortcuts obey selection locks, including mock output controls. Reuse requires
complete matching content and dependency identity, not a matching name. Never
implicitly edit a previously shared derived profile.

Live source/mock changes are restricted patches against the service-owned
running snapshot, not fresh resolution of library/default profiles. Validate
session identity/revision; delayed patches cannot reach a new recording after
stop/restart. Derive live mock changes from running output content, excluding
unrelated pending edits. Show pending and acknowledged states separately. Commit
active selection only after acknowledgement; failure retains running state and
offers retry/discard. A saved derived library profile may remain visibly
unselected without claiming live success. A retained configuration does not
imply the old network stream is still connected: show actual degraded/failed
connection state separately. Re-apply/library edits affect next
Start, not this patch authority.

## Observed gaps to remove

- `SettingsSetModels.kt` stores inline command, baud, caster, source, upload,
  output and storage overrides; `ActiveRecordingConfig.kt` merges these with
  owning profiles and local field arguments.
- `NtripProfileResolution.kt` contains source/caster fallback heuristics;
  `MainActivity.kt` mountpoint saves can rewrite caster defaults in referencing
  sets, even though the mountpoint already owns the relationship.
- Home/Start do not consume `ActiveSetupResolver` consistently; non-workflow
  choose/ask policies are not implemented end to end.
- Re-apply clears override objects but can leave separately stored workflow
  selection unchanged. Raw override presence also drives misleading `+` state.
- Start eagerly validates some inactive NTRIP/RTKLIB/solution references.
  RTKLIB-only solution policies can be silently coerced to device-internal.
- Shared mutable profile edits and fixed-reference semantics need explicit
  presentation; fixing a selection must not imply freezing profile content.
- Receiver ID and command family currently influence different runtime paths;
  an unlocked base-coordinate default can be bypassed by global selected ID.

## Migration and compatibility

Preserve explicit TLS/plaintext, credentials, outputs and operator customizations.
Convert legacy effective field overlays into explicit derived user profiles and
active selection references, retaining original defaults for Re-apply. Convert
caster/source dependencies together; retain source metadata only when its
identity is still valid. Conflicting or unknowable source lineage needs review;
do not guess an endpoint or send credentials elsewhere.

Dormant overrides ignored by a fixed policy remain recoverable, but must not
become active during migration. Missing optional passwords remain a clearly
incomplete configuration, not grounds to discard a whole backup. Preserve
legacy secret aliases and migrate to canonical encrypted owner bindings without
passwords in profile JSON, IDs, logs or session metadata.
Existing explicitly consented plaintext-password backup is the sole JSON
transfer exception; preserve consent/warnings and treat it as sensitive. Normal
profile serialization and recovery provenance never contain passwords.

Migration runs only while idle and uses an idempotent staged graph transaction.
Stage new secret entries first, publish reachable profile references last, and
retain the old graph until commit succeeds. Failure or process interruption must
preserve a consistent old or new committed graph for supported persistence
failures. Lost encryption keys, unreadable storage or revoked permissions are
not recoverable by graph migration: retain evidence, identify the unavailable
prerequisite and block only affected operations. Distinguish absent passwords
from entries that exist but cannot be decrypted. Avoid unrelated store rewrites;
use the existing rollback batch helper.
The helper alone does not make multiple stores transactional or survive a
failed rollback: retain a validated previous graph and a durable migration
phase record before publication. On uncertain commit/recovery, block use of the
new graph, keep recovery available and report the failure; never claim recovery
succeeded if storage cannot persist it. Never overwrite a live secret binding
while staging; allocate new bindings and defer orphan cleanup until confirmed.
The committed profile's explicit owner secret binding becomes authoritative
across runtime, editors, export and import; canonical/legacy IDs are migration
inputs only, not a runtime precedence that can select an old password. Password
export, only when explicitly requested, traverses committed reachable profiles;
it must not enumerate staged/orphan secrets.

Recovery must run before any normal profile-store read, default fallback or
write-on-read migration. Serialize it with configuration writers and Start.
Keep an unresolved graph from being concealed by default profiles or from
being reused in-process after `commit(false)` changed visible preferences.
SAF imports still require valid persisted local permissions or reselection.

Introduce an explicit newer backup schema if the persisted structure changes;
continue importing format-1 RC2-RC6 backups. Export must declare its format and
not imply old releases can consume new semantics. Test repeated migration,
partial failures, missing families, collisions and large credential inventories.
New-schema round trips preserve defaults, policies, separate active selections
and redacted recovery data. Exports exclude staging/orphan bindings. Test
format-1 imports both with and without consented passwords.

## Approval and assurance

This proposal changes contracts, not just UI polish. After human approval, update
canonical requirements and traceability before implementation: UI-SETUP-001/002,
UI-PROFILE-001/002, WF-FIXEDBASE-003/004, WF-BUILTIN-SETTINGS-001,
WF-PROFILE-FILTER-001, UI-STORAGE-001, UI-DASH-004, security and applicable runtime requirements.
Add explicit ownership/dependency/migration requirements where not covered.
Preserve capture independence, source-upload safety, keyboard editing, explicit
telemetry and the existing temporary-to-fixed-base confirmation workflow.
Changed digests invalidate approvals/evidence; no copied passing attestations.

Implementation plan: `../plans/2026-10-02-settings-profile-ownership.md`.
