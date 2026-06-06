# Receiver Driver API

Receiver drivers describe capabilities, optional command builders and advisory
parsers for receiver-specific byte streams. A driver must never be required for
safe recording. Unknown bytes remain recordable bytes.

Workflow validation uses receiver capability data before driver command builders
are called. See [Workflows](workflows.md) for the higher-level workflow model.
Capability data should distinguish normal in-device solution output from
receiver PPP solution output where a receiver supports PPP.

## Conceptual Kotlin Contract

```kotlin
interface GnssReceiverDriver {
    val id: String
    val displayName: String
    val capabilities: ReceiverCapabilities

    fun identify(sample: ByteArray): ReceiverIdentification?
    fun buildInitCommands(profile: ReceiverProfile): List<ReceiverCommand>
    fun buildRoverCommands(config: RoverConfig): List<ReceiverCommand>
    fun buildBaseCommands(config: BaseConfig): List<ReceiverCommand>
    fun buildFixedBaseCommands(position: BasePosition): List<ReceiverCommand>

    fun parseSolution(input: ByteArray): List<SolutionEvent>
    fun parseQuality(input: ByteArray): List<QualityEvent>
    fun extractRtcmFrames(input: ByteArray): List<RtcmFrame>
}
```

## Driver Rules

- `identify` may return `null`; recording must still be possible.
- Command builders return explicit byte commands and must not write to transport
  directly.
- Commands sent to a receiver must be recorded in `tx-to-receiver.raw` or a
  dedicated corrections sidecar.
- Parser output is advisory and may be dropped if it fails.
- Receiver PPP solution parsing/logging, where supported, is separate from the
  normal device solution stream.
- Drivers must not inject timestamps or markers into `receiver-rx.raw`.
- Risky receiver commands must be documented and tested before use.
