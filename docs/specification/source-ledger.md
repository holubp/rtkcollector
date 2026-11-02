# Specification Authority And Reconciliation Ledger

Canonical requirements live in docs/specification. User documentation and
Superpowers history explain intent but do not override a later explicit owner
decision. Code is implementation evidence, not permission to weaken a contract.

| Source | Authority and chronology | Reconciliation rule |
| --- | --- | --- |
| AGENTS.md and owner instructions | Current repository safety and approval rules | Preserve byte-exact raw recording, service ownership, truthful evidence and human-only approval. |
| 2026-11-02-rc12-owner-approved-replacement | Human:holubp requested "Update RC12 release with these changes" and selected option 2 after the immutable-tag conflict and both alternatives were explained | Authorizes only the one-time RC12 tag/APK replacement from d56d5d2 to verified d1313d1. Amends RELEASE-SIGN-001 narrowly; retains stable signing and all verification gates. Record old/new source and artifact identities. No other tag replacement or general waiver is approved. |
| 2026-11-02-stop-dialog-adjustments | Human:holubp explicitly requested centered Stop confirmation, visible colored confirmation/gray Cancel, and intact USB recovery/position-loss handling in both phases | Authorizes those exact additions to UI-STOP-001 and their narrow presentation-resource mappings. Existing resource paths remain classified; functional Stop styles/backgrounds/text states are implementation, not ungoverned branding. No timing/authorization change, new release or physical-validation claim is approved. |
| 2026-11-01-astra-stop-timer-repair-normal-gates | Human:holubp approved Astra's written repair with "Go ahead. Also resume goal" after clarifying normal fix/review/pre-push/CI, without an exception | Approves the narrow pure refresh-loop and design-document capability mappings implementing existing UI-STOP-001. Immutable timing, recording isolation and normal release gates remain unchanged; no diagnostic workflow waiver or host-GREEN claim is authorized. |
| 2026-11-01-stop-confirmation-approved-plan-release | Human:holubp approved option 1, then the written design including 1-60/1-120 bounds and lifecycle cancellation, then the checkpointable implementation plan with "Approved. Release new RC. Make it a goal." | Authorizes UI-STOP-001 and the narrowly corresponding capability mappings/implementation and next RC. This records approval of the translated approved contract, not a claim that the owner reviewed digest strings or passed physical-device tests. |
| September assurance work in the main workspace | Existing uncommitted checker, inventory and approval records | Preserve that workspace. Vendor tooling separately; reuse an approval only when its exact digest matches current text. |
| Owner decision 2026-09-23-specification-assurance-approval | Explicit approval of the then-current requirement inventory/capability map | Not approval of later changed text, new requirements or a changed capability inventory. Preserve matching records and retain previous records as history when superseded. |
| Later TLS and RC4 decisions | Both Google Play and sideload support explicitly selected plaintext and normal trusted TLS; no unsafe TLS or automatic downgrade | Current SEC-NTRIP-TLS requirements govern. Do not restore obsolete TLS-only build restrictions or invalid-certificate compatibility modes. |
| RC8/RC9 diagnostics and alarms plans | Later approved behavior and implemented changes | Keep essential logging, configurable alarms and recording isolation. Old diagnostic-disabled language does not disable minimal lifecycle evidence. |
| RC10 USB recovery plan and bugreports | Confirmed crash evidence and current recovery contract | Preserve Stop cancellation, per-attachment consent, stale-result rejection and epoch-fenced recovery. Private captures remain local and are not committed or treated as portable verification artifacts. |
| Current source and tests at e8c1c7e | Implemented RC10 with 1,355 local tests/193 suites and independent USB review | This establishes the recorded local scope only, not full specification approval, host CI or physical-device validation. |
| Current human direction to perform strict checking | Replaces the proposed release-specific waiver | Integrate the framework and resolve findings; do not silently release under the prior RC9 exception. |
| RC10 model 6 supporting-coverage correction | Approved with the RC10 baseline | Preserve runtime source coverage; bind actual documentation/process realization separately to receipts, inventory approval and exact semantic scope. |
| RC10 gap reconciliation | Existing behavior formalized and explicitly owner-approved | Runtime-command safety, supported rates, observed message rates, editor discard, UI throttling/expiry, upload prerequisites, baud ordering and RTCM station decoding are approved revisions, not inferred approvals from code. |
| 2026-10-30-rc10-specification-baseline-approval | Explicit human:holubp approval after the in-chat summary | Approve the named 49 revision digests and capability inventory; preserve 107 prior approvals. It is not a passing test or semantic attestation. |
| 2026-10-30-owner-external-pr-governance | Explicit subsequent owner instruction | GitHub protection/review applies to external PR authors, not owner-authored changes; retain all local, independent-audit, CI and release-verification gates. Do not claim universal protected enforcement. |
| 2026-11-01-p30pro-recording-safety-design-approval and plan approval | Human:holubp approved off-main owned startup, cancellation-safe raw finalization and bounded diagnostics, followed by RC11 after verification | Implement that approved contract without changing receiver-command delays, raw architecture or existing workflow behavior. Private P30 captures and historical inferred exception identity are not portable execution evidence. |
| 2026-11-01-rc11-approved-plan-no-further-approval | Subsequent explicit human:holubp direction: "Ensure that as a part of the goal, the next RC is actually released, without stopping for my approval(s)." | Apply this release-specific authorization to the three canonical requirements derived from the already approved design and their reconciled capability inventory. No further human approval checkpoint is required for that scope; this is not a claim that the owner inspected final digest strings, not authorization for unrelated requirement changes, and not a waiver of independent review, local/host tests, artifact verification or truthful pending device evidence. |

## Restored Contracts

PRODUCT-RX-001, UI-CONSOLE-001 and WF-REPLAY-001 existed in the historical
assurance draft but were absent from the release worktree. Their support-claim,
idle-console and replay-isolation intent is restored for explicit review rather
than silently dropping it from the inventory.

## Evidence And Approval Boundary

Mechanical metadata refresh is not a human approval or test execution. Pending
records remain pending. Unexecuted obligations remain not-run. Source audits,
CI execution, Android runtime checks, private field captures and published APK
verification are distinct evidence types and must not substitute for each other.

Local assurance requires current approvals, capability mappings, receipts,
matrix and exact semantic review. Owner-authored release follows the explicit
owner-governance exception above; it is not universal GitHub-protected compliance.
Main protection was absent at discovery; no setting has been changed. External
PR enforcement must be configured/verified before claiming it is active.
