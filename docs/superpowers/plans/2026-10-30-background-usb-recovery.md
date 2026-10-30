# Background USB Recovery Fix Plan

> **For agentic workers:** Use superpowers:executing-plans; controller writes,
> one focused read-only gpt-6.1-sol/high reviewer checks Android lifecycle policy.

**Goal:** Recover an existing recording after background USB reattachment,
without false alarm recovery, stale consent, process-killing Stop cancellation
or losing existing session files.
**Spec:** ANDROID-USB-001/002/003; ANDROID-ALARM-004/005.

## Evidence And Boundaries

Local runtime ZIP aff3c39b has 128 essential records and no fatal stack. Last run
has 98 read failures 09:46:15-09:47:53 UTC, Stop USER 09:47:54, UI attach 09:48:01.
It does not prove the actual USB permission state or the historical crash cause.
New USBdisconnect2 bugreport confirms the historical crash at local 11:36:43.183:
uncaught InterruptedException on rtkcollector-capture at retry sleep line 1431;
Android records process death at 11:36:43.380. Android opened its USB resolver
at 11:36:28.063. Noncrashing default-handler phone shows five detach/error
bursts ending within 0.2-1.2s of reattachment. This does not prove resumed RX.
Source confirms: no service-owned re-consent; platform detach reads app-only
`usbDevice` instead of Android's `device`; Stop interrupts retry sleep outside
the capture thread's try/finally. Reproduce/fix those defects, not the LED state.

## Contract And Design

- Preserve raw/session writers and init/TX admission; no receiver-profile changes.
- Catch expected capture interruption at the worker boundary; resume unexpected
  active interruption, never silently leave running=true with no worker.
- Separate platform USB extraction from app Start payload extraction.
- Service owns consent tickets keyed by generation, device and attachment/request
  nonce, with distinct package-targeted mutable PendingIntent identities.
- Request once per attachment, register first, invalidate on Stop/detach/destroy.
  Denial keeps degraded recording intent with explicit guidance, not dialog spam.
- Keep enumeration-based paced retries independent of Activity/attach delivery.
  Hard read failure enters recovery, not repeated reads on a dead handle.
- Consent callback never opens/re-inits or starts recording on main; actual RX
  recovery clears connection loss. Unresolved position/protocol alarms remain.
- Record deduplicated absent/consent/open/init/recovery transitions in essential
  logs so a future export distinguishes permission, enumeration and open errors.

## Checkpoints

- [x] Reproduce Stop interruption and active-interruption handling RED/GREEN;
  preserve byte-exact existing file content.
- [x] Test consent deduplication, denial, stale generation/attachment, Stop and
  platform extras/PI identity; wire service-only recovery and state diagnostics.
- [ ] Review full scoped delta, targeted/full local gates and host test compilation.
- [ ] Host CI/device smoke checks after push-gate approval; do not reuse RC9-only
  strict-checker exception or move existing RC9 tag/APK.

Routing metadata verified gpt-6.1-sol/high for reviewer Hooke. Reviewer confirms
permission/event-key gaps and no additional proven normal-event FGS crash path.
System permission UI while locked/backgrounded remains vendor/device validation.

## RC10 Release Ledger

- Scope: USB recovery/Stop crash fix, previously committed muted-alarm warning,
  version 1.0-RC10 (code 11), full CI and stable-signed GitHub prerelease.
- No changes to existing RC9 assets/tag or unrelated ownership-review drafts.
- Strict specification checker absent. RC10-specific exception requested;
  approval pending. No push/publication before the release gate is resolved.
- Cancellation regression: focused JVM suite green; new consent tests initially
  compile RED against the missing coordinator. Full integration gates pending.
- Consent coordinator deduplicates prompts, retires pending callbacks after
  external grants and rejects old session/device/attachment tickets. Platform
  event extraction and distinct URI PendingIntent/filter helpers are integrated.
- First independent audit found P1 old-read/detach publication and P2 accepted
  consent callback repaint races. P1 now uses a short epoch publication fence;
  P2 makes polling the only recovery UI-status publisher. New fence-ordering and
  service callback/queued request tests added; targeted GREEN and re-review pending.
- First full pre-push run intentionally went RED when the newly added
  external-grant test referenced not-yet-implemented accessAvailable(). This is
  not a passing release gate; a clean full re-run remains required.
- Final local pre-push gate PASSED: 1,355 tests/193 suites, zero failures/errors/
  skips, clean production/test compilation, pure receiver/core suites, IDE task
  alias dry runs and specification/signing/audio/manifest tooling checks.
  Receipt: ignored sdd/2026-10-29-recording-alarms/rc10-final-prepush.log.
- Sol/high independent audit and affected-path re-reviews closed all findings.
  The private nested recorder test lookup compilation defect was fixed using
  method reflection and verified by the final gate. No source blocker remains.
- Host Robolectric tests are compile-checked only here; CI, APK signing/artifact
  inspection and GitHub publication await owner RC10 gate-exception approval.
