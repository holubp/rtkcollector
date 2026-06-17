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

## Reading Order

1. `terminology.md`
2. `functional-requirements.md`
3. `workflows.md`
4. `receiver-behaviour.md`
5. `session-artifacts.md`
6. `android-runtime.md`
7. `security-privacy.md`
8. `ui-requirements.md`
9. `verification-matrix.md`
10. `traceability.md`

## How To Change The App

Every non-trivial feature or fix should identify affected requirement IDs before
implementation. If no requirement exists, update this specification first or in
the same branch. Update `verification-matrix.md` and
`docs/superpowers/plan-status.md` when implementation status changes.

