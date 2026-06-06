# NTRIP And Corrections

## Responsibilities

The NTRIP client manages caster connection, mountpoint selection,
authentication, optional GGA upload and RTCM byte stream receipt.

## Reconnect State Machine

The client should move through explicit states:

```text
idle -> resolving -> connecting -> authenticating -> streaming
streaming -> reconnect-wait -> connecting
streaming -> stopped
any error -> reconnect-wait or stopped
```

Reconnects should use bounded backoff and clear user-visible state.

## Correction Routing

- RTCM bytes from NTRIP must be validated enough to avoid obvious framing
  mistakes once the RTCM extractor exists.
- Correction age must be monitored.
- Serial TX injection must use a queue separate from raw receiver RX capture.
- All correction bytes sent to the receiver must be recorded in
  `tx-to-receiver.raw` or a dedicated corrections sidecar.
- NTRIP must not block raw capture.

## Secrets

Caster passwords and other credentials must not be written to `session.json`,
logs, issue reports or test fixtures.
