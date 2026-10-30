# RC10 Assurance Conflict Review

## Resolved By Existing Owner Decisions

- Current SEC-NTRIP-TLS requirements govern explicit plaintext and system-trusted
  TLS in both distribution variants. Historical TLS-only publication restrictions
  and unsafe-TLS proposals are not restored by importing assurance records.
- Essential runtime logging is always present independently of optional verbose
  diagnostics. Old disabled-diagnostics wording is not permission to suppress it.
- USB recovery and alarms use the current RC10/RC9 contracts, not the weaker
  September reconnect wording. Private bugreports establish discovery evidence,
  not a claim that the RC10 hardware fix has been field-validated.
- Canonical formal requirements remain separate from user workflow documentation.

## Approved Baseline

The 49 exact revised/new requirement digests and capability inventory were
explicitly approved by human:holubp under
`2026-10-30-rc10-specification-baseline-approval`. The 107 unchanged approvals
retain their prior references. No test result or project attestation is inferred
from that approval; a subsequent contract edit would need a new exact approval.

## Pending Evidence And Enforcement

- Android runtime/physical receiver/provider checks cannot be inferred from the
  Termux compile and JVM test results.
- The code-level USB review is not an exact full-project semantic attestation.
- The owner subsequently restricted GitHub protection to externally authored PRs
  under `2026-10-30-owner-external-pr-governance`. Owner-authored RC10 does not
  require a second GitHub reviewer or universal protection. External PRs still
  require trusted-base checks and owner review. No repository setting has been
  changed; technical enforcement must not be claimed until actually configured.

These items remain explicit limitations on the corresponding claims. The owner
exception applies only to GitHub governance, not tests or release verification.
Unexecuted evidence must not be recategorized as passed or not applicable.
