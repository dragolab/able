# Documentation system

## Source of truth

- The product specification owns agreed product behavior and functional requirement IDs.
- Use cases describe actor workflows and exceptions.
- User stories express user value and acceptance criteria.
- Non-functional requirements define cross-cutting quality constraints.
- The traceability matrix connects those sources to planned verification.
- UX and architecture documents explain presentation/implementation decisions without replacing requirements.
- The verification strategy defines evidence expected before release; it does not imply tests have been run.

## Requirement language

- **MUST:** required for v1 unless an Android/platform limitation is documented and approved.
- **SHOULD:** expected for v1; any omission needs a written reason.
- **MAY:** optional or future scope.
- **Accepted:** explicitly confirmed product decision.
- **Proposed:** recommended direction awaiting confirmation or feasibility evidence.
- **Open:** unresolved question; implementation must not assume a final answer.

## Change workflow

For each scope change:

1. Record the decision, rationale, and date in the product specification or ADR log.
2. Add/update stable requirement IDs and acceptance conditions.
3. Update affected use cases, user stories, NFRs, verification cases, and traceability.
4. Add a source link for platform/version-sensitive behavior.
5. Check both Italian and English flows and each supported form factor.

IDs are never reused. Deprecated requirements retain their ID and a reason. Document changes should be reviewed as one coherent change set.
