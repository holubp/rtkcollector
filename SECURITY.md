# Security Policy

RtkCollector is in bootstrap status and should not yet be treated as production
field software.

## Reporting

Please report security issues privately to the repository owner rather than
opening a public issue. Include:

- affected version or commit;
- device and Android version;
- receiver model and transport type;
- clear reproduction steps;
- expected and observed behaviour.

## Sensitive Data

NTRIP passwords, caster credentials and private mountpoint credentials must not
be committed to the repository or written into exported session metadata.
Session files may contain precise locations and receiver identifiers; handle
them as sensitive operational data.
