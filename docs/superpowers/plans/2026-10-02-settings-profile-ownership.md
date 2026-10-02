# Settings and profile ownership implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Use test-driven-development for reusable behaviour.

**Status:** Approved by user on 2026-10-02; implementation in progress.
**Goal:** One field owner, intact profile references, consistent settings-set
selection policies across Home, Menu and recording.
**Architecture:** A pure shared active-setup resolver validates a profile graph;
settings sets own defaults/policies and separate active selections. Legacy field
overlays become derived profiles through an idle, recoverable migration.
**Tech Stack:** Kotlin/JVM, Android/Compose, SharedPreferences, encrypted secret
store, repository specification checker and GitHub Android CI.
**Spec:** `docs/superpowers/specs/2026-10-02-settings-profile-ownership-design.md`.
**Baseline:** `b25cce2634b9c8476b9f25899180990eaa3e8b13`.

## Global constraints

- User approval on 2026-10-02 authorizes this plan, canonical-contract updates,
  compliance review and the requested RC6 release. This Task 1 checkpoint is
  documentation-only; it authorizes no code or test edits. Preserve unrelated
  work and local captures.
- Raw recording remains byte-exact and independent of configuration/advisory
  failures. No migration/profile-store work on capture or high-rate UI callbacks.
- No automatic caster substitution, TLS downgrade, credential disclosure or
  receiver command rewriting. Built-ins remain immutable and copyable.
- One writer per worktree. Controller owns contracts, migration and integration;
  bounded tests/UI/model implementation may use Luna with explicit acceptance
  criteria. Use one independent substantive final audit; Astra is requested for
  this cross-settings design review, not as the default implementation worker.
- Checkpoint each task with touched files, tests, findings and pending criteria
  in this ledger and `docs/superpowers/plan-status.md`. Do not label unexecuted
  hardware checks passing.

## Review focus

Review original user ownership rule, complete settings inventory, fixed source
vs fixed caster, every policy, shared edit semantics, inactive dependencies,
legacy overlays and secret bindings, import failure recovery, low-click UX and
Home/Start/live-update equivalence. No scope reduction to NTRIP alone.

## Checkpoints

### 1. Approved contracts and grounded test inventory

- [x] Confirm human approval of design; reconcile Astra findings.
- [x] Update `docs/specification/ui-requirements.md`, `workflows.md`, relevant
  security/runtime requirements, traceability and verification matrix. Map every
  design acceptance criterion to requirement ID and planned evidence.
- [x] Inventory production consumers and existing tests for `effective*`,
  `local*`, inline overrides, remembered selection, workflow activation,
  Home shortcuts and backup/migration. Inventory is reported at this checkpoint;
  implementation ownership stays with the controller.
- [ ] Add failing model tests for intact references, inactive applicability,
  source/caster mismatch and policy lifecycle before changing implementations.

Task 1 scope ruling: by explicit controller instruction, this checkpoint makes
no code or test edits. The planned failing model tests remain required before
their implementation in Tasks 2-4; this defers their timing without waiving
coverage.

Exit: approved contract and finite consumer/test map; no invented assurances.

### 2. Shared selection model and resolver

- [ ] Change `profile/ActiveSetupModels.kt`, `ActiveSetupResolver.kt`,
  `SettingsSetModels.kt`, `WorkflowActivationPolicy.kt`; introduce explicit
  presence/applicability and active selections separate from set defaults.
- [ ] Resolve fixed/default/choose/ask policies consistently; upload lock covers
  enable and selected profile together. Workflow activation remains explicit.
- [ ] Remove field merging from the new resolution path. Retain legacy decoding
  only for migration. Reject incompatible explicit solution-engine policies;
  preserve intentional AUTO arbitration.
- [ ] Tests: all policy transitions, failed Start/stop, Re-apply workflow,
  dormant choices, Off/None/not-chosen, modified state and unchanged defaults.

Exit: pure resolver contract passes tests; legacy data still readable.

Additional acceptance: Re-apply clears this set's choose-once memory;
LEAVE_INTACT retains workflow as activation baseline. Test set isolation.

### 3. Profile graph and NTRIP ownership

- [ ] Update `ui/NtripProfileResolution.kt`, `profile/ProfileCompatibility.kt`,
  `profile/ActiveRecordingConfig.kt` to consume shared results and complete
  profiles, not local field overlays.
- [ ] Derive caster from source; model optional caster restriction separately.
  Validate IDs, never host/name guesses; fixed source cannot conflict with a
  restriction. Remove configured-caster/source fallback heuristics.
- [ ] Validate only workflow-applicable dependencies; preserve unresolved
  dormant choices without activating them. Validate receiver family/commands,
  baud routing, output/solution compatibility and fixed-base coordinate agreement.
- [ ] Resolve receiver identity once for driver/RTKLIB/UI; expose mismatches
  rather than deriving it from the filter. Make base-coordinate defaults and
  active selection use the same policy instead of global-ID precedence.
- [ ] Tests include same host/different accounts, duplicated mountpoint names,
  fixed caster/unlocked source, missing inactive refs, and TLS/plaintext unchanged.

Exit: graph resolution deterministic and no credentials routed by fallback.

Additional acceptance: map every legacy caster policy per design, retaining
unresolvable conflicts for review. Remove coordinate-reference edit protection
for user-owned records, confirm affected sets and reject MODE BASE disagreement
without rewriting commands or running coordinates.

### 4. Recoverable legacy migration and transfer

- [ ] Implement a focused pure migration planner alongside
  `SettingsBackupModels.kt`/`SettingsImportModels.kt`; add typed new-schema
  decoding and preserve format-1 import fixtures.
- [ ] Materialize each distinct effective legacy overlay as derived profiles,
  replacing active refs without rewriting defaults. Preserve ignored overlays
  as inactive recovery data. Unknown lineage becomes explicit Needs review.
- [ ] Integrate idle staged application in `ProfileStores.kt`,
  `NtripSecretStore.kt`, `SharedPreferencesTransactions.kt`: secrets staged before
  refs, idempotent restart, rollback retaining old graph. Handle incomplete
  credentials without silently discarding otherwise valid import data.
- [ ] Retain validated previous graph and durable phase record; allocate new
  secret bindings rather than overwrite live bindings. Test rollback failure
  and recovery blocking; do not treat the batch helper as cross-store atomicity.
- [ ] Make committed explicit owner secret binding authoritative everywhere;
  canonical/legacy aliases only migrate. Test old canonical password collision
  with a newly staged binding and reachable-only plaintext-password exports.
- [ ] Gate normal profile reads/default fallback/write-on-read behind recovery;
  serialize with edits and Start. Test process-visible commit failure, missing
  encryption key, unreadable credentials/storage and revoked SAF permissions.
- [ ] Tests: RC2-RC6 plaintext-password exports, alias/remap/collision handling,
  duplicate migration, failed secret/profile commits and interrupted restart,
  retained optional families, SAF reselection, no secrets in diagnostics or
  ordinary profile/session JSON (consented password export is an exception).
- [ ] New-schema round-trip defaults, policies, active choices and redacted
  recovery data; preserve opt-in plaintext-password transfer with consent.

Exit: every legacy customization retained or explicitly recoverable; failed
migration cannot publish a broken graph or overwrite good credentials.

### 5. Home and Menu consistency

- [ ] Update `ui/MainActivity.kt`, `DashboardModels.kt`, `ProfileListModels.kt`
  and focused UI helpers to use one resolved state for labels, locks, lists,
  validation and modified markers. Remove protected-set rewrites on profile save.
- [ ] Keep eight compact Home actions and folding. Caster restriction explains
  filtering, not source locking. Menu selection honours Home policy; management
  edits owning profiles explicitly and identifies affected sets.
- [ ] Keep direct mountpoint typing/mock toggle/rate: derive/reuse an explicit
  user profile and select it. Show identity; do not produce refresh-time clones.
- [ ] Re-apply restores defined active starting state, including workflow,
  without changing global preferences. Ask/choose prompts follow shared policy.
- [ ] UI tests: Home/Menu/Start equivalent values; same locks/accessibility;
  built-ins read-only; invalid selections highlighted; copy/derive semantics;
  modified marker; fixed/unlocked combinations; portrait/landscape folding.

Exit: no UI promises a different configuration from the one Start will use.

Additional acceptance: shortcuts honour locks; reuse matches complete content
and dependency identity. Shared edits immediately refresh idle preflight/Home.

### 6. Session integration and regression checks

- [ ] Route `buildStartRecordingIntent`, `buildNtripUpdateIntent` and service
  consumers through validated shared snapshots. Save redacted profile provenance.
- [ ] Running sessions do not change on library edits/Re-apply. Preserve explicit
  permitted live source switching and averaging across sources, with atomic
  handoff/clear failure state and byte-exact capture unaffected.
- [ ] Preserve UI-DASH-004 live mock enable/rate updates through an explicit
  validated patch to the running advisory output, not general profile mutation.
- [ ] Test start rejection before I/O, profile edits during recording, live
  source mismatch/failure, corrected-source reconnect, stop writer ownership,
  base handoff/cancel, upload Off and workflow activation.
- [ ] Retire remaining production inline/local overlay escape paths; audit
  legacy adapters so old imports do not re-enable field merging.

Exit: Home/Start/service agree; no capture/data-loss regressions in tests.

Additional acceptance: live patches use running snapshot and session ID/revision,
not library defaults. Test pending/acknowledged state, delayed commands after
restart, unrelated edits, and saved derivation plus failed patch: failed patches
leave the derived profile unselected and capture intact.

### 7. Documentation, full gates and independent completion audit

- [ ] Update operator workflows/settings/NTRIP docs with ownership, fixed
  selection vs shared content, caster restrictions, Re-apply and migration.
- [ ] Search repository for stale override/fallback/policy semantics. Reconcile
  every affected requirement with actual implementation and valid evidence.
- [ ] Run targeted JVM tests, `git diff --check`, then repository-prescribed
  `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK sh scripts/pre_push_check.sh`.
  No parallel Gradle compiles; full native assembly/Robolectric run on GitHub CI.
- [ ] Independent Sol-high audit of complete delta, original scope and evidence.
  Fix findings and recheck impacted criteria; do not waive failures for green.
- [ ] Manual Windows/Android: upgrade with legacy configs; copy built-ins; all
  policy/lock combinations; source switch; edit shared profile; failed/retried
  migration; SAF reselect; mock shortcuts; fixed-base handoff; no raw data loss.
- [ ] Commit/push only intended approved implementation after mandatory gates;
  record CI and manual gaps truthfully. No release without separate instruction.

Exit: verified automated criteria; manual evidence explicitly pending/passing,
never inferred from CI. Human approval remains required for normative changes.

## Review and execution ledger

| Date | Scope | Status | Evidence / next action |
| --- | --- | --- | --- |
| 2026-10-02 | Current-source ownership and resolution analysis | Reviewed locally | Baseline above; observed field overlays, source fallback, eager inactive validation and policy integration gaps. |
| 2026-10-02 | Proposed design and checkpointable plan | Reviewed; pending human approval | No product implementation or canonical requirement modification. |
| 2026-10-02 | Independent design review | Nine findings resolved in proposal | Requested/accepted route `gpt-6-astra`, high; runtime metadata unavailable. Full findings and focused re-review completed on agent `01a0fbec-c799-7953-99af-f7a0cd8ba337`; no blockers for human design approval, no implementation verification claimed. |
| 2026-10-02 | Documentation-only pre-push gate | Passed | `git diff --check`; `ANDROID_HOME=/storage/3830-3863/Termux/AndroidSDK sh scripts/pre_push_check.sh`: gate tests, specification/signing checks, app production/test compilation, feasible JVM tests and IDE alias dry-runs pass. No native APK assembly attempted. |
| 2026-10-02 | Approved settings/profile ownership; Task 1 docs checkpoint | In progress | User approval authorizes implementation, compliance review and RC6 release. Canonical contracts and traceability updated in this worktree. No code/tests changed; grounded consumer/test inventory reported to controller. New requirements remain `Needs review` until implementation and evidence are independently checked. |

Review corrections: (1) explicit committed secret binding authority;
(2) recovery before every store read/publication; (3) live patches against
service snapshot/session identity; (4) bounded migration availability guarantee;
(5) all legacy caster-policy mappings; (6) identity-only coordinate locks with
command mismatch rejection; (7) lock-aware derived shortcut reuse/acknowledgement;
(8) idle shared edits and Re-apply/LEAVE_INTACT boundaries; (9) consented password
transfer exception and reachable-only export. These are proposed requirements,
not claims that the current app already meets them.
