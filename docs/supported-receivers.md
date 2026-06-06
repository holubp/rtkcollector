# Supported Receivers

## Bootstrap Targets

| Receiver | Status | Notes |
| --- | --- | --- |
| Generic NMEA + RTCM | Skeleton | Raw capture, basic NMEA advisory parsing and RTCM injection concept. |
| Unicore UM980 / N4 | Skeleton | Primary high-rate target; no risky commands hard-coded yet. |
| u-blox M8P | Skeleton | Internal RTK target; M8P-0 and M8P-2 capabilities differ. |
| u-blox M8T | Skeleton | Raw/timing/post-processing target, not an internal RTK rover. |

Support status means repository design and driver boundaries exist. It does not
yet mean field-tested Android operation.
