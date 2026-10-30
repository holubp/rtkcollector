# Requirement Template

```markdown
### <ID>: <Short Requirement Name>

Status: Normative | Experimental | Future | Deprecated

Coverage kind: implementation_required

The app MUST/SHOULD/MAY <precise requirement>.

Rationale:
<Why this requirement exists.>

Applies to:
- <Workflow, receiver, UI area, session artifact or runtime component.>

Verification:
- Automated: <test name or planned test>.
- Manual: <field or device check>.
- Review: <code/design review criterion>.

Evidence obligations: EV-<ID>-AUTO, EV-<ID>-REVIEW

Provenance: <durable user decision or reviewed source reference>
```

Use an atomic observable requirement and concrete acceptance criteria. Evidence
obligations must resolve to configured receipts; a test filename is not proof
that its tests ran. Do not promote pending or unexecuted evidence to passing.

Coverage kind defaults to `implementation_required`. Only genuinely approved
documentation or process obligations may use `documentation` or `process`;
process coverage requires reviewed tooling/configuration, not prose alone.
Coverage kind participates in the approval digest. For retained obsolete
requirements, keep the ID, use `Status: Deprecated`, add `Superseded by:` with a
requirement ID or `decision:<durable-reference>`, and approve the tombstone digest.

