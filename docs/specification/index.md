# RtkCollector Formal Specification

This tree is the canonical formal specification for RtkCollector. It is written
for AI agents and contributors who need precise, testable requirements.

User-facing documentation remains outside this tree and should be friendly,
task-oriented and practical. Superpowers brainstorming, design and plan files
remain design history and traceability inputs.

## Normative Status

- `Normative`: required behaviour.
- `Experimental`: implemented or planned behaviour that is not yet a stable
  contract.
- `Future`: documented direction, not required for current implementation.
- `Deprecated`: retained only to explain old behaviour.
- `Informative`: explanation, not a normative implementation contract.

Implementation/evidence maturity belongs in the verification matrix, never in
the lifecycle Status field. Only Normative requirements appear in that matrix.

## Reading Order

1. `terminology.md`
2. `functional-requirements.md`
3. `workflows.md`
4. `receiver-behaviour.md`
5. `session-artifacts.md`
6. `android-runtime.md`
7. `security-privacy.md`
8. `ui-requirements.md`
9. `publication-readiness.md`
10. `verification-matrix.md`
11. `traceability.md`

## How To Change The App

Every non-trivial feature or fix should identify affected requirement IDs before
implementation. If no requirement exists, update this specification first or in
the same branch. Update `verification-matrix.md` and
`docs/superpowers/plan-status.md` when implementation status changes.

## Assurance Records

The approval policy, requirement approvals, capability map, evidence receipts and
semantic review bind decisions and verification to exact content digests.
Changing a requirement invalidates approval for its old digest; changing mapped
implementation invalidates the corresponding evidence. The verification matrix
is derived from receipts, not from a favorable completion claim in a plan.

The assurance record contract is model version 6, capability-map schema 3,
evidence-receipt schema 2, and semantic-review/context schema 5. A normative
requirement defaults to `Coverage kind: implementation_required`; use an explicit
`Coverage kind: documentation` or `Coverage kind: process` only when that
classification is itself part of the approved requirement contract. Unknown
coverage kinds fail closed.

Runtime `implementation_mappings` remain mandatory for every
`implementation_required` requirement and every runtime implementation file must
still map to normative implementation requirements. A supporting mapping has
`pattern`, exact `requirement_ids`, `coverage_kind` (`documentation` or `process`),
and a non-empty `reason`. Documentation coverage must resolve to actual,
non-ignored documentation. Process coverage may include supporting operator
documentation, but must also resolve to reviewed configuration or tooling;
prose alone is not process realization. Neither supporting coverage kind can
replace runtime implementation coverage.

Semantic context records `supporting_traceability` as path/requirement-ID pairs,
`supporting_digests` and `coverage_kinds` for every in-scope requirement ID. A
receipt carries `supporting_digest`, computed per requirement from its supporting
paths, bytes and mapping relationships. With no supporting paths, its canonical
empty-list value is `sha256:4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945`.
Changing a supporting file or mapping stales the associated receipt and semantic
context; regenerate them rather than copying old attestations forward.

Local/release checks require a full review, while trusted-base external-PR CI
requires the exact changed scope. They must not overwrite each other's evidence.
The semantic-review file may contain one schema 5 attestation or a schema 1
ledger with an `attestations` array of schema 5 records. A ledger must contain
exactly one record matching the requested mode, base commit and scope digest;
that record still needs every normal identity, digest and conclusion check.
Duplicate, malformed, stale or neighboring scopes are not substitutes. The
whole semantic-review file is excluded from its own content scope, so adding a
second reviewed scope does not recursively invalidate the first one.

Repository governance is owner-specific. Under the explicit decision
`2026-10-30-owner-external-pr-governance`, owner-authored changes do not need a
second GitHub reviewer or universal branch protection. External PR authors must
pass the trusted-base changed-scope check and owner review before merge. The
workflow tests the PR author's authenticated login, not the merging actor.
Local gates, independent audit, full release CI and stable signing remain
mandatory for owner work. No GitHub settings were changed by this decision;
do not describe unconfigured contributor protection as technically enforced.
The generic checker's protected-enforcement trust boundary describes universal
strict assurance, not a claim that this owner exception is equally protected.
GitHub's [bypass permissions](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository#granting-bypass-permissions-for-your-branch-or-tag-ruleset)
are actor-based, so an actor bypass alone does not implement an author-only rule.

Local, generated, test, vendor and asset paths cannot realize supporting
coverage. Canonical requirements, verification matrices, approvals, capability
maps, receipts, semantic records and assurance-control files cannot substantiate
their own correctness, even through reclassification or overlapping patterns.
Tests are evidence, not implementation. Mapping text alone grants no approval or
passing result. Human approval and verification results must remain pending until
actually supplied and validated.

Formal requirements and assurance mappings are contributor-facing control
artifacts. User-facing operational documentation remains distinct: it can be a
supporting realization only when it contains the actual required guidance, and
must not replace a formal requirement or assurance decision.

Run the full release gate with:

```sh
python3 tools/specification/spec_check.py --root . --full --summary-json
sh scripts/pre_push_check.sh
```

Use source-ledger.md and conflict-review.md for current reconciliation authority
and pending decisions. Keep local review contexts/checkpoints in the ignored
.spec-assurance directory. Pending approval, missing/stale evidence and missing
semantic audit are blockers to their respective claims, never implicit waivers.
