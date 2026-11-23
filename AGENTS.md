# Able project instructions

## Before implementation

1. Read `README.md`, `docs/product/ABLE_PRODUCT_SPECIFICATION.md`, and the relevant requirements, UX, architecture, and verification documents.
2. Treat decisions marked accepted as product constraints. Treat `MUST` requirements as v1 scope; do not silently downgrade them.
3. If Android behavior, permission policy, API availability, or Play eligibility is uncertain or time-sensitive, verify against current primary Android documentation and record the source and date.

## Requirements discipline

- Keep requirement IDs stable. Add IDs rather than renumbering existing requirements.
- Every new user-visible feature needs a use case, at least one user story, acceptance criteria, a traceability entry, and unsupported/error states.
- Distinguish confirmed decisions, proposals, and unresolved questions.
- Update the product specification, user stories, use cases, traceability matrix, and verification strategy together when scope changes.
- Do not mark acceptance criteria as passed without evidence from the named verification method/device.

## Platform and safety constraints

- Cloud sync is out of scope for v1. Do not add telemetry or account requirements without a product decision.
- Do not request powerful permissions at first launch in bulk. Request them in the context of the feature that needs them.
- Battery profiles may automatically run only user-reviewed and pre-authorized actions. Freezing or disabling apps is always manual.
- Do not claim battery savings or app-level drain causality without sufficient measurements.
- Use Android-supported user consent flows for files, shortcuts, permissions, and destructive operations. Verify state after privileged changes; report failure honestly.
- Keep private reference notes under `private-references/`; that directory is intentionally excluded from Git and public documentation packages.

## Documentation and repository conventions

- Write documentation in Markdown, UTF-8, LF line endings, and English identifiers. User-facing examples and terminology should preserve the agreed Italian/English product behavior.
- Keep product decisions in `docs/architecture/decisions.md` and implementation alternatives explicitly marked as proposed or open.
- Never add a license, public remote, secrets, signing keys, private notes, or release binaries without an explicit project decision.
- Keep the root limited to project entry points and repository configuration; put product documents under `docs/`.
