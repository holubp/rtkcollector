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

## Correction Routing

- RTCM bytes from NTRIP must be validated enough to avoid obvious framing
  mistakes once the RTCM extractor exists.
- Correction age must be monitored.
- Serial TX injection must use a queue separate from raw receiver RX capture.
- All correction bytes received from NTRIP are written to `correction-input.raw`.
- All correction bytes sent to the receiver are also written to
  `tx-to-receiver.raw` before serial TX.
- NTRIP must not block raw capture.

## Secrets

Caster passwords and other credentials must not be written to `session.json`,
logs, issue reports or test fixtures.

The UI may hold a runtime password long enough to build the NTRIP Basic
Authentication header. Session metadata stores redacted NTRIP metadata and
credential-presence flags only.
