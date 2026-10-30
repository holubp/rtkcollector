# Supporting Requirement Coverage Design

Status: framework correction approved with the RC10 baseline under
`2026-10-30-rc10-specification-baseline-approval`; not a passing project audit.

## Defect

The v5 framework requires every active normative requirement to appear in a
runtime implementation mapping and rejects documentation/process paths in those
mappings. UI-DOCS-001 and PLAY-DISCLOSURE-001 cannot truthfully be fulfilled by
MainActivity merely to satisfy that invariant. RELEASE-SIGN-001 depends on signing
tooling, workflow and runbook content, not only Android application code.

## Contract

- A requirement defaults to `Coverage kind: implementation_required`. Explicit
  `documentation` and `process` kinds are part of its normalized, human-approved
  requirement digest. Unknown kinds fail closed.
- Runtime implementation mappings remain separate. Every runtime implementation
  file must still map to normative implementation-required requirements; no
  documentation/process declaration can make runtime code disappear from scope.
- Capabilities may declare reasoned `supporting_mappings` with pattern, exact
  requirement IDs and coverage kind. Documentation realization requires actual
  non-ignored documentation. Process realization requires reviewed configuration
  or tooling, with optional supporting operator documentation.
- Capability requirement IDs equal the union of their valid implementation and
  supporting mappings. Implementation-required IDs still need implementation
  mappings; documentation/process-only IDs need appropriate supporting coverage.
- Local, generated, test, vendor and asset paths cannot substitute for realization.
  Canonical requirements, matrix, approval/capability/evidence/semantic records
  and assurance control files cannot substantiate their own correctness, even
  through reclassification or overlapping patterns.
- Tests remain evidence, not implementation. Merely naming a mapping never
  issues an approval or passing receipt.
- Supporting paths and relationships enter inventory approval, exact review scope
  and per-requirement supporting digests. Receipts bind both implementation and
  supporting digests; supporting edits invalidate prior receipts.
- Supporting path/requirement pairs require exact semantic conclusions. Candidate
  and trusted-base mappings both participate in changed/deleted-path closure.
- Bump affected schemas and provide explicit migration of fixtures/records.
  Do not make old approvals, receipts or semantic attestations silently pass.
- Full and changed CI scopes may coexist in one semantic-review ledger (outer
  schema 1, individual context/attestation schema 5), or use the existing single
  schema 5 record. Every ledger member must be structurally valid. Selection
  requires exactly one matching mode/base/scope, followed by all existing exact
  metadata, authority and conclusion checks. No neighboring-scope fallback or
  duplicate scope is allowed. This avoids conflicting Android/full and PR/changed
  requirements without weakening either gate.

## Verification

Required regression cases: valid documentation/process coverage; rejected unknown
kinds; missing implementation for an implementation-required ID; still-unmapped
runtime source; forbidden local/generated/test/self-spec targets; wrong category;
supporting edit stales receipts/context; supporting deletion retains base scope;
missing supporting semantic conclusion; invalid human approval remains blocking;
old strict tests continue passing after deliberate schema migration.

The approved human baseline handoff includes coverage-kind changes and the
reconciled capability digest. The subsequent external-PR-only GitHub governance
decision is separately recorded; neither decision waives local or CI verification.
