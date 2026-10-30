# RC10 Specification Assurance Reconciliation Plan

> **For agentic workers:** Use superpowers:executing-plans with isolated bounded
> Luna assignments and an independent substantive completion review. The owner
> explicitly authorized expanded parallelism on 2026-10-30; only the controller
> integrates changes into the release worktree.

**Goal:** Integrate and execute the existing strict assurance framework against
current RC10, repair real gaps without waivers, and release only after required
approvals, evidence, CI and stable APK verification are satisfied.

**Architecture:** Preserve the current release worktree and the main workspace's
uncommitted historical assurance work independently. Vendor existing checker and
tests, reconcile records against current canonical requirements and implementation,
and derive matrix state from truthful receipts. Never replace current behavior
with obsolete September transport/release requirements.

**Tech Stack:** Python standard-library checker/tests, Markdown/JSON contracts,
GitHub Actions, Gradle and stable-signed sideload APK.

**Spec:** AGENTS.md Specification-First Workflow; specification-assurance model 5
baseline and proposed model 6 supporting-coverage correction; current
docs/specification; explicit user instruction to perform
the checks rather than bypass them.

## Constraints And Authority

- Main workspace edits, captures and private settings remain untouched.
- RC9 tag/APK remain unchanged; RC10 application version/code stay 1.0-RC10/11.
- No weakening of controls, baseline waiver, fabricated approval, stale passing
  receipt or fabricated semantic attestation.
- Existing approvals survive only when exact requirement digests still match.
  Changed/new requirements and capability inventory require a fresh human decision.
- Unexecuted field/provider checks remain not-run; failing checks remain failed.
- Current explicit TLS/plaintext support in both variants supersedes September
  Play-TLS-only and unsafe-TLS proposals.
- Only the controller writes this worktree; reviewers are read-only.

## Checkpoints

### 1. Reconcile And Vendor

- [x] Confirm checker exists only as uncommitted main-workspace work.
- [x] Run it against RC10: 150 requirements, 708 blocking findings, 150 warnings;
  principally missing assurance records and stale declared evidence/status.
- [x] Compare requirement bodies and chronology; inventory new/changed contracts.
- [x] Vendor checker/handoff tools, focused tests and CI/CODEOWNERS definitions
  without importing unrelated application edits or obsolete requirements.
- [x] Run checker/tool tests before changing framework behavior.

### 2. Restore Truthful Assurance Records

- [x] Add provenance and explicit obligations to current requirements, preserving
  exact prior metadata only when applicable.
- [x] Reconcile capability classifications and exact implementation mappings.
- [x] Regenerate digest-bound receipts/matrix from actual evidence, retaining
  not-run where execution/review evidence does not exist.
- [x] Inspect checker findings and fix implementation defects with regression
  tests; do not resolve semantic defects through metadata alone.
- [x] Generate a human-readable approval handoff identifying exact changed/new
  requirements, capability digest, conflicts and remaining evidence limitations.

### 3. Approve And Audit

- [x] Obtain explicit human approval for final affected requirement digests and
  capability inventory; record authority and decision reference truthfully.
- [ ] Perform exact-scope independent semantic audit and address all findings.
- [ ] Validate strict full checker and checked-in pre-push gates.
- [x] Inspect trusted-base CI configuration and branch protection; record the
  owner's external-PR-only governance decision without changing GitHub settings.

### 4. Publish RC10

- [ ] Commit scoped changes, push reviewed source, execute full clean-host CI.
- [ ] Create RC10 tag/prerelease only after required checks pass.
- [ ] Build stable-signed APK through GitHub; independently verify version,
  package, checksum, signing certificate and provenance before release closure.
- [ ] Update release record, verification matrix and plan tracker with actual
  evidence and explicit remaining device validation; mark goal complete only
  after publication is confirmed.

## Current Checkpoint

RC10 application fixes are committed at e8c1c7e; clean local gate passed 1,355
tests/193 suites and Sol/high source review closed USB/muted-alarm findings.
These are not full specification-framework approval or host/device evidence.
The owner authorized assurance reconciliation, then explicitly approved the
baseline and an owner-authored GitHub-governance exception; runtime tests,
independent audit, clean-host CI and artifact verification are not waived.

Reconciliation now has 160 requirements/156 normative and 313 scoped receipt
records. 107 exact original approvals remain valid; 49 revisions were explicitly
approved by human:holubp under `2026-10-30-rc10-specification-baseline-approval`.
Old unsupported Passing assertions were removed from the receipt-derived matrix;
the existing Play policy block is retained and is not a sideload-release block.
Local tooling verification: 166 unittest cases pass (139 assurance cases); three
existing pytest cases passed after the framework corrections. The lifecycle
adapter was verified RED/GREEN for normative-only matrix
coverage. Existing signing/audio/manifest checks remain intact in pre-push.

The candidate capability inventory covers 236 implementation files using 291
exact-path mappings and nine documentation/process supporting mappings. No
runtime wildcard bindings remain. The exact inventory was human-approved with
the baseline; any inventory edit requires a new exact approval.
Kuhn's bounded inventory/data work used verified gpt-6-luna/medium; Archimedes's
independent mapping review uses verified gpt-6.1-sol/high because false mappings
could conceal security or recording-integrity gaps.

The pre-approval snapshot had 51 blocking findings: 49 pending human approvals,
one pending inventory approval and one stale/missing exact project attestation.
The 50 approval findings are now addressed by the explicit owner decision, not
machine-generated permission. Exact project review and clean-host CI remain.

GitHub main protection GET returned 404/unprotected. Subsequent owner decision
`2026-10-30-owner-external-pr-governance` requires protection/review only for
external PR authors; owner work need not wait for a second GitHub reviewer or
universal protection. The workflow uses PR author, not merge actor. No repository
setting was changed or externally enforced protection claimed. The approved
handoff and governance authority are in docs/specification/approval-review.md.

## Independent Mapping Audit Findings

Archimedes reviewed all 141 original binding records for responsibility. It did
not attest full compliance. Findings require real fixes, not metadata approval:

- Console could open during STOPPING because UI running=false precedes resource
  release. Fix uses service-owned session registry at connect/send and a separate
  lifecycle-derived receiverBusy dashboard flag. New console tests were RED;
  Android DTO mapping test compiles locally and needs CI execution.
- Generic RTKLIB profiles allowed M8T/M8P frequency counts 2/3. The receiver-aware
  start guard now rejects those while preserving UM980 multi-frequency settings.
  The initial regression called resolve instead of validateForStart; corrected
  oracle was rerun RED without the guard before restoring it for GREEN.
- Owned RTKLIB output close could skip POS after an NMEA failure. All independent
  flush/close attempts now execute and aggregate failures. The owned-stream test
  was RED; production callback outputs are non-owning, so no production data loss
  was inferred from the latent defect.
- Unused legacy SessionShareState contradicted active-session sharing policy.
  It now fails closed even when its legacy partial-snapshot flag is true. No
  reachable production share bypass was claimed; actual export registry checks
  remain authoritative.
- The Play checklist's stale RC4 candidate label is corrected to RC10.

The v5 framework cannot truthfully bind documentation/process-only requirements
without pretending they are runtime implementation. A generic supporting-mapping
extension is needed, with explicit digest-bound coverage kinds, supporting path
digests/receipts/context/conclusions, source coverage preserved, forbidden local/
generated/test/self-specification realization, and candidate/base deletion closure.
This extension is implemented, its focused Sol/high findings are closed and
the owner approved the baseline. Source behaviors now have approved explicit
applicable contracts rather than unrelated mappings: baud ordering,
persistent command risk, output rates, UI throttling/error expiry, base upload
prerequisites, reference-station decoding, unsaved editing and observed message
rates. The machine inventory and human approval handoff are refreshed.

Runtime patches invalidate previous implementation digests/review context.
The fresh clean Android test-compilation/feasible-JVM gate passed 1,365 tests
across 193 suites, zero failures/errors/skips, for the current runtime delta.
Host-only Robolectric tests remain compile-checked here, not executed.
The all-app/core regression completed GREEN in 32 seconds before the later M8
variant correction. The M8P-0/M8P-2 omission found by independent review is now
fixed: targeted start-validation tests were RED (32 tests, one failure) and then
GREEN (32 tests, 57 seconds). Exact baseline approval is recorded; the full
semantic audit remains pending. No RC10 publication
or checker pass has occurred.

The reference-station boundary tests were RED (nine tests, four failures) before
the fix; the correction-module suite then passed and the clean local gate includes
it. Incomplete 1005/1006 and inconsistent frame metadata are rejected while valid
decoding, separate antenna height and the existing unknown-CRC API are preserved.

Model 6 regressions reproduced symlink support, dropped base mapping scope,
candidate-local reads, shared-path closure and alternate control-path defects.
All are corrected and independently rechecked. Candidate-local contents never
substitute for trusted-base transition blobs. Shared realization relationships
close to a fixed point. Scoped review closure is not the full-project attestation.

## Expanded Parallel Execution Checkpoint

- Controller: model 6 checker integration, canonical requirements, record refresh
  and source-local verification. No concurrent Gradle compiles.
- Luna/medium mapping worker: responsibility mappings in an isolated worktree;
  first pass removed false bindings and exposed 32 uncovered source files.
- Luna/high coverage-test worker: ordinary supporting coverage and deleted-base
  regressions, converted to standard-library unittest for mandatory gates.
- Luna/high handoff worker: schema 5 metadata and exact supporting-pair closure;
  22 isolated tests passed, integrated into the controller for joint verification.
- Luna/medium runtime worker: M8 variant two-file patch, integrated and verified.
- Sol/high independent runtime reviewer: console/cleanup/shared-state findings
  closed at source level; M8 variant correction identified. Full audit pending.
- Luna/medium release preflight: stable-secret names/certificate pin present;
  latest successful remote CI belongs to RC9-era main, not this candidate.
- Luna/medium contract inventory: six narrow existing-behavior contract gaps and
  a missing baud-order clause; canonical revisions now have owner approval.
- Luna/medium documentation worker: model 6 examples and contributor guidance,
  integrated without approval or passing-result fabrication.
- Luna/high adversarial worker: found symlink realization and removed-mapping
  scope-closure gaps; focused regressions integrated and green. Sol's additional
  privacy/fixed-point/path-spelling findings were reproduced and closed too.
- Luna/high reference-position worker: bounded parser hardening integrated;
  malformed-input regressions verified RED/GREEN. Independent source review of
  this last runtime delta closed with no material findings; valid decoding and
  advisory isolation stay unchanged.
- Luna/high CI-scope regression worker: sixteen tests prove full and changed exact
  attestations can coexist in one ledger without borrowing scopes. Five expected
  failures were observed against the pre-ledger checker; malformed archived
  member regressions were also RED before shape validation. The isolated patch
  is integrated and the whole tooling suite is green. Independent CI-flow and
  member-shape reviews closed with no remaining material findings in that delta.

The configured thread cap is now 20. More workers are used only for independent
deliverables, not shared-file edits or duplicate reviews. Weekly allowance
telemetry is unavailable; no speed tier, account or billing pool was changed.
Runtime turn metadata verified the eight newly spawned Luna assignments used
their requested medium/high efforts. No mismatched routing was observed.

## Approval Checkpoint And Next Safe Action

The prescribed pre-push gate was executed and correctly stopped at full assurance:
49 missing human requirement approvals, one missing inventory approval and one
stale/missing full-project attestation. Diff, gate-self-tests, signing, sound,
manifest and assurance-tool tests passed before that stopping point. The separate
clean Android source/test-compilation gate passed for the current runtime delta.
No check was bypassed; no candidate CI, tag, release or APK publication occurred.

The owner subsequently approved the exact baseline and clarified external-only
GitHub governance. Those decisions are recorded explicitly; the earlier automatic
continuations were not treated as approval. The next action is to regenerate the
final review scope and complete the independent project audit, then rerun all
gates and execute GitHub CI/signing. This owner release cannot be described as
universal GitHub-protected assurance.

The external-PR workflow condition now uses `pull_request.user.login != 'holubp'`,
not the merging actor. Its focused static contract test was RED before the change
and GREEN afterward; the 166-case tooling suite is green. Actual GitHub event
execution/protection configuration is not proved by that static test.
Any approved revision must be re-digested; do not copy a stale approval forward.

## Post-Approval Audit Repair Checkpoint

The independent Sol/high audit completed the frozen 713-path/736-pair scope.
It withheld attestation: six direct findings require repair (bounded NTRIP
metadata/chunks, optional RTKLIB snapshot/status isolation, bounded cancellation,
session-scoped averaging, validated/bounded protocol text, and postpublication
backup cleanup). Peer regrading additionally confirmed u-blox position/time,
signal mapping and split-sync defects, import presence/transaction semantics,
native handle/exception boundaries, and missing inference disclosure. Layered
helper-only allegations were not accepted as end-to-end defects.

Prepared workers edited isolated scratch copies only. Integration has now begun:
NTRIP/u-blox regression tests are installed first and their targeted clean Gradle
RED run is in progress. No new release, tag or push has occurred. Optional
performance-sampler hardening is not included: its alleged live escape was
disproved by the actual service containment boundary.

Live GitHub checks returned no rulesets and an unprotected main branch. No
remote settings were changed; external-author workflow checks are not claimed
as mandatory branch enforcement. The owner's exemption does not waive local,
independent audit, exact-source CI or signing verification.

Integration evidence so far: NTRIP RED 7/122; u-blox RED 12/55; replay RED
1/79; unknown-CRC regression RED; native safety RED 6/21 then GREEN 21/21.
The checker suite remains GREEN 139/139. Integrated production app compilation
is GREEN after correcting a Long/Int literal and a cross-module smart cast that
the standalone compiler harnesses missed. Current app test sources compile too;
the integrated test run is still executing. A separate actual-bytecode probe
reproduced and then rejected all eight invalid u-blox gnssFixOK combinations.

Ruling: retain encrypted recovery staging before its reference, independently of
subsequent imported runtime-password persistence. The independent reviewer found
this compatible with SEC-IMPORT-003/006; prerequisite failure must retain the
prior graph and report rejection, not pretend a partial restore succeeded.

The same reviewer confirmed initial synchronous native RTKLIB startup still
violates ANDROID-CAPTURE-001/ARCH-RAW-002. A focused follow-up is in progress;
cached snapshot/status isolation alone does not close that acceptance criterion.
Strict complete/checksummed text admission and bounded UM980 pending-state fixes
are prepared separately and awaiting integration. No new normative ID is needed
for session-scoped averaging: existing workflow/UI requirements plus the explicit
AGENTS recording-session rule govern it. Approval digests are unchanged.

The integrated app/changed-module run is now GREEN: 1,232 tests across 162
suites, zero failures/errors/skips. Strict UM980 admission was then integrated;
its Gradle suite is GREEN 114/114 across 16 suites, and all five service-source
regressions pass. Strict C++ syntax checking also passed against the exact pinned
RTKLIB header, not merely an unverified local pipeline checkout.

The reviewer confirmed a further downstream ownership defect: failed native
server startup can leave startup buffers/decoders allocated. An incremental
adapter cleanup/fault-test repair remains in progress, separately from Kotlin
startup isolation. Normal successful native stop must not be double-cleaned.

Routing checkpoint: explicit new workers and the continuing reviewer were
verified as requested. Resuming the closed original native worker unexpectedly
inherited Sol/medium rather than high. It was stopped before follow-up code was
written, its exact-pinned-source investigation was preserved, and a fresh
explicit Sol/high worker was runtime-verified for the native ownership repair.
No speed tier or billing pool was changed; weekly allowance telemetry is unknown.

The protocol repair review source-qualified its other fixes and found PR-001:
u-blox protocol health incorrectly used wall-clock time for the monotonic stall
monitor. A focused source regression was RED (one failure/five passing), then
GREEN (six passing) after changing only the health call to elapsedRealtime;
telemetry timestamps retain wall-clock time. Affected independent re-review is
pending. Startup isolation now also passes the 33-test Gradle RTKLIB suite and
integrated Android production compilation. Early startup input must next use the
existing bounded queues so initial ephemerides are not discarded unnecessarily.

The native failure-path harness exposed ignored decoder initialization errors,
worker startup/Stop resurrection and early worker allocation cleanup gaps in the
exact pinned dependency. Focused repair is authorized in the existing CMake
generation path plus bridge/tests; no new source file or inventory mapping is
needed. Generated adaptation must reject pin drift and normalize CRLF so normal
Windows Android Studio builds remain supported. This is not a waiver or a
modification of the ignored upstream checkout.

## Final Runtime Freeze

All runtime repairs are integrated. Independent focused reports now close the
import/UI, protocol (including PR-001), postpublication cleanup, recording
isolation/averaging and native ownership/CMake findings. NR-001 was reproduced
with three failing Gradle regressions, then repaired in the existing native
bridge/worker boundary; its 39-test Gradle suite is GREEN and the affected
independent review is source-closed. Deliberate Stop is not reclassified as an
unexpected native failure. The earlier bounded-startup queue increment is also
integrated and preserves FIFO and explicit discard counts.

The final native build/source suite is GREEN 30/30 with no skips, including the
actual generated-C fault/ordering harness and the new CI execution-order check.
CI installs the locally tested pytest pin in an isolated temporary environment,
requires CMake/Clang and explicit pinned-source inputs, and runs native tests
after provisioning and before assembly. Final bridge syntax validation passes
with warnings as errors against the exact pinned header. Windows CRLF pin
normalization is tested without claiming a Windows Android Studio execution.

No normative text or capability-inventory mapping changed during these repairs;
the owner's approvals remain bound to the same approved digests. Pending receipt
bindings were refreshed without creating passing evidence. Runtime writers are
paused for the final full-context reconciliation. Outstanding release work is
the exact full attestation, full pre-push gate, commit/push, exact-source host CI,
stable APK signing/publication and artifact verification. Android/device/SAF and
vendor-specific manual checks remain explicit, not fabricated as completed.

## Publication Verification Checkpoint

Runtime tree `279294be6647c283a04841427538105b1b3f569a` is committed and pushed.
Its full independent attestation covers 713 paths and 736 requirement/path pairs
with no unresolved source findings. The full repository pre-push gate passed:
1,444 tests across 193 suites, zero failures/errors/skips in executed tests,
strict specification assurance and all JVM-test source compilation. The three
Robolectric classes remain compile-only locally and execute on the host.

Host validation is tracked by GitHub Actions run `41137476006`. The final
published tag, host results, stable-key build and independent APK certificate,
checksum, package/version and ABI checks must be recorded in the RC10 GitHub
release. Those external records, not this source-preparation checkpoint, establish
publication. Existing manual/device/provider obligations and the separate Play
policy blocker retain their honest maturity; this is not a field-test claim.

The follow-up tracker changes implementation status to implemented/not
field-tested. It changes no runtime, tests, build configuration, requirements,
approvals or capability mappings. Its refreshed exact-scope review and pre-push
gate remain required before the documentation commit is pushed.
