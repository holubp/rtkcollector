# NTRIP And Corrections

## Responsibilities

The NTRIP client manages caster connection, mountpoint selection,
authentication, optional GGA upload and RTCM byte stream receipt.

Experimental V1 implements an NTRIP client for receiving RTCM bytes and
transmitting them to the receiver serial input. The client sends NTRIP v2-style
HTTP requests by default and has a controlled v1 compatibility retry for caster
responses that indicate protocol-version incompatibility. Authentication and
authorization failures such as HTTP `401` and `403` are non-retryable field
errors, not reconnect loops. In code and artifacts the Android-to-receiver path
is called receiver TX. The app is a client only; it is not an NTRIP caster or
server.

Some strict casters reject generic application-style `User-Agent` values even
when credentials and mountpoint are correct. Default stream and sourcetable
requests must identify RtkCollector as an NTRIP client, for example
`User-Agent: NTRIP RtkCollector/0.1`, while still sending
`Ntrip-Version: Ntrip/2.0` for V2 requests.

## Caster And Mountpoint Profiles

V1 separates reusable caster settings from reusable mountpoint settings. A
caster profile stores host, port, username, protocol policy, cached sourcetable
mountpoint names and a secret reference for the password. A mountpoint profile
references a caster profile and stores the selected mountpoint plus expected
correction characteristics.

The UI can fetch a caster sourcetable with an NTRIP v2 root request and cache
the `STR` mountpoint names on the caster profile. The user may select one of
those cached names or type a mountpoint directly. Typed mountpoints remain valid
when sourcetable fetching is unavailable, blocked by credentials or unsupported
by the caster.

Fetching caster mountpoints must update the cached list only. The current
mountpoint text changes only when the user types a new value or explicitly
selects an item from the fetched list.

The last active NTRIP mountpoint profile may be remembered for convenience, but
NTRIP must only connect when the selected workflow uses NTRIP corrections.

## Reconnect State Machine

The client should move through explicit states:

```text
idle -> resolving -> connecting -> authenticating -> streaming
streaming -> reconnect-wait -> connecting
streaming -> stopped
any error -> reconnect-wait or stopped
```

Reconnects should use bounded backoff and clear user-visible state. Stop during
reconnect delay is intentional cancellation and must not surface an
`InterruptedException` as an app error.

Authentication and authorisation failures (`401` and `403`) are terminal for
the active NTRIP attempt and must not be retried indefinitely. Network failures
are degraded retry states. In both cases, receiver recording continues unless
USB or raw storage fails.

## Correction Routing

- RTCM bytes from NTRIP must be validated enough to avoid obvious framing
  mistakes once the RTCM extractor exists.
- Correction age must be monitored.
- Serial TX injection must use a queue separate from raw receiver RX capture.
- All correction bytes received from NTRIP are written to `correction-input.raw`
  and, when correction input recording is enabled, to the same-byte
  `correction-input.rtcm3` file for downstream tools that expect an RTCM3
  extension. `correction-input.raw` is canonical; the `.rtcm3` mirror is
  best-effort where storage permits.
- All correction bytes sent to the receiver are also written to
  `tx-to-receiver.raw` before serial TX.
- NTRIP must not block raw capture.

## Secrets

Caster passwords and other credentials must not be written to `session.json`,
logs, issue reports or test fixtures.

The UI may hold a runtime password long enough to build the NTRIP Basic
Authentication header. Session metadata stores redacted NTRIP metadata and
credential-presence flags only.

Only an explicit settings backup export may include plaintext credentials, and
only after user confirmation. That settings backup is separate from recording
session export and must be treated as secret material by the user.
