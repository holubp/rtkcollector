# Security And Privacy Requirements

## Secrets

### SEC-SECRETS-001: Session Metadata Excludes Secrets

Status: Normative

`session.json` MUST NOT contain plaintext NTRIP passwords, tokens or raw
credentials. It may contain redacted metadata or secret references.

Verification:
- Automated: session metadata and settings-export tests.
- Review: all session metadata writers redact credentials.

### SEC-SETTINGS-001: Plaintext Password Export Is Explicit

Status: Normative

Settings export MAY include plaintext NTRIP passwords only when the user
explicitly selects that option. The default export path MUST avoid plaintext
passwords.

Verification:
- Automated: settings export tests.
- Manual: export settings with and without password checkbox.

## Imports

### SEC-IMPORT-001: Imported Settings Are Validated

Status: Normative

Imported JSON settings MUST be structurally validated before use. Plaintext
password import MUST be explicit and user-visible.

Verification:
- Automated: settings import validation tests.
- Manual: Android "open JSON with RtkCollector" import flow.

