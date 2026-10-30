### CORE-FLOW-001: Stable title

Status: Normative

Coverage kind: implementation_required

The system MUST perform one atomic, observable behavior.

Verification:
- Define concrete acceptance criteria.

Evidence obligations: EV-CORE-FLOW-001-AUTO

Provenance: <durable decision/source reference>

Coverage kind defaults to `implementation_required` when omitted. For an
approved documentation-only or process obligation, set exactly
`Coverage kind: documentation` or `Coverage kind: process`; this field is part
of the approved requirement digest. Do not reclassify a runtime behavior to avoid
implementation coverage. Process coverage requires actual reviewed tooling or
configuration, not prose alone.

For a retained obsolete requirement, use `Status: Deprecated`, keep the same
ID, add `Superseded by: CURRENT-REQUIREMENT-ID` or
`Superseded by: decision:<durable-ref>`, and obtain a current approval for the
tombstone digest. Do not add deprecated requirements to the verification
matrix. Use `Status: Informative` for explanatory material that must remain
visible but is not a contract obligation.
