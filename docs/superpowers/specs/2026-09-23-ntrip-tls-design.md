# NTRIP TLS Transport Design

## Purpose

Add secure NTRIP transport for rover correction intake and base caster source
upload. Google Play variants must not send NTRIP credentials or VRS GGA over
plaintext TCP. Sideload variants retain explicit compatibility with conventional
plaintext NTRIP casters.

## Scope

This design covers NTRIP correction download, sourcetable fetch, NTRIP v1/v2
source upload, profile transport selection, distribution-specific validation,
diagnostics and tests. It does not change NTRIP request syntax, receiver
command protocols, or the authoritative raw recording path.

## Transport Model

Each NTRIP correction and caster-upload profile has an explicit transport mode:

- `TLS`: TCP followed by a TLS handshake before any NTRIP request, credential,
  GGA or RTCM byte is sent.
- `PLAINTEXT`: existing direct TCP behavior.

TLS is the default for newly created profiles. Existing profiles migrate to
explicit `PLAINTEXT` to preserve their behavior in sideload distributions.

TLS verification is explicit per profile:

- `System trust` is the default and uses the platform default
  `SSLSocketFactory`, certificate trust store, hostname verification and SNI.
- `Unsafe: accept any certificate / ignore hostname` is available only in
  sideload variants. It is off by default, requires an explicit danger
  confirmation, is visibly marked as unsafe in the active setup, and has no
  silent fallback from either safe mode.

Custom CA certificates are not supported in this release. A private caster
must use a certificate trusted by the platform or use explicitly accepted
unsafe TLS in a sideload build.

Google Play variants allow only `System trust`. They reject plaintext and
unsafe verification modes before a socket is opened, even if the mode arrived
through imported, inherited or stale profile data.

## Mandatory Security Boundary

Every NTRIP connection is constructed from one validated
`NtripEndpointSecurityPolicy`. It contains a canonical endpoint, transport,
TLS verification selection, distribution capability and local unsafe
acknowledgement. Correction download, sourcetable fetch, V1/V2 source upload,
reconnects and V2-to-V1 compatibility retries retain that same policy. No API
may open an unqualified `connect(host, port)` connection.

Legal profile states are:

| Transport | Verification | Sideload capability | Acknowledgement | Result |
| --- | --- | --- | --- | --- |
| TLS | System trust | either | not relevant | allowed |
| TLS | Unsafe | true | true | allowed |
| TLS | Unsafe | false or missing | either | rejected |
| Plaintext | System trust marker | true | not relevant | allowed |
| Plaintext | any other verification | either | either | rejected |
| Plaintext | System trust marker | false | either | rejected |

Unsafe acknowledgement is local consent, not portable configuration. Clear it
on import, migration, copy/derivation, endpoint change, transport change and
verification change. A user must acknowledge the final unsafe configuration on
the current device.

An imported or migrated Custom-CA profile is disabled: remove certificate
bytes, preserve non-secret endpoint metadata, and require the user to choose
system trust or sideload unsafe TLS with a fresh acknowledgement.

Endpoints are parsed before policy validation. A host is exactly one DNS name,
IPv4 literal or bracketed IPv6 literal; reject whitespace, controls, URL
syntax, `@`, path/query/fragment delimiters, embedded ports, malformed IP
literals and invalid IDN. Normalize DNS with IDNA. Generate the `Host` header
only from the parsed endpoint; send SNI only for DNS names.

System-trust TLS permits TLS 1.2 or newer only, enables HTTPS endpoint
identification, and rejects a weaker negotiated protocol. Unsafe TLS may omit
hostname/certificate verification only after policy validation; it does not
permit plaintext fallback.

NTRIP request framing remains unchanged inside the selected socket:

- correction download retains its NTRIP client request syntax;
- NTRIP v1 source upload retains classic `SOURCE` syntax;
- NTRIP v2 source upload retains HTTP `POST` and chunked RTCM framing.

## Distribution Boundary

Add a `distribution` flavor dimension with `googlePlay` and `sideload`
flavors. Google Play build variants reject active plaintext NTRIP correction or
source-upload profiles during validated workflow/start preflight, before a
socket is opened. Sideload variants permit either transport mode.

The restriction is enforced by domain/start validation using generated build
configuration, not by hiding a UI control. Imported, inherited, stale and
programmatically selected plaintext profiles therefore cannot bypass it.
The core policy constructor and service ingress repeat the enforcement; UI
preflight is advisory and cannot be the sole control.

Google Play release packaging and documentation must refer to the
`googlePlayRelease` AAB. Sideload release artifacts use `sideloadRelease` and
are never represented as Play-submittable artifacts.

## User Experience

Profile editors present a transport selector with `TLS` as the default and a
TLS verification selector. The endpoint’s configured port remains
user-controlled. A plaintext selection or unsafe verification mode
that is active under a Google Play variant is visibly incompatible and yields a
clear preflight error explaining that it is available only in sideload builds.
No credential field is cleared when transport mode, verification mode or build
distribution changes.

TLS connection, certificate, hostname and handshake failures report a distinct
NTRIP connection failure without exposing passwords, authorization headers or
private-key material. Tests must prove diagnostics and session events contain
no password, Basic token, raw request frame, certificate bytes or private-key
material.

## Reliability And Recording

Transport establishment, handshake, read/write errors and validation failures
remain advisory. They may degrade NTRIP state and retry according to existing
policy, but MUST NOT stop or alter authoritative receiver RX recording while
receiver transport and storage remain functional. Raw receiver bytes, app TX,
correction sidecars and upload audit artifacts remain separate.

## Verification

- Unit-test TLS connector setup and ensure no NTRIP bytes are written before a
  successful handshake.
- Test normal platform trust/hostname rejection with a local test endpoint and
  sideload-only unsafe-mode confirmation.
- Test canonical endpoint rejection and `Host`/SNI rendering for DNS, IPv4 and
  bracketed IPv6 literals.
- Test TLS 1.2-or-newer selection and that every V2-to-V1 retry retains the
  same TLS policy.
- Regression-test correction client and source-upload v1/v2 framing over an
  injected TLS-capable socket.
- Test Google Play variant preflight rejects plaintext and unsafe
  correction/upload profiles before connector invocation; test sideload permits
  them only after the required confirmation.
- Test profile migration/defaults and UI model labels.
- Use checked-in test-only TLS certificates with explicit DNS/IP SANs and a
  deterministic local TLS server; production profiles never receive test trust
  material.
- Build and inspect `googlePlayRelease`; manually validate TLS correction
  intake and TLS source upload against compatible caster endpoints.
- Keep `PLAY-DATA-001` blocked until the Google Play variant verification has
  passed; then update its evidence and publication documentation.

## Formal Requirement Changes

Update or add formal requirements for explicit NTRIP transport selection, TLS
validation, Google Play plaintext rejection, sideload compatibility, redacted
TLS failures and variant-specific publication verification. Update the
capability map, evidence receipts and verification matrix in the same change.
