# Supported Receivers

RtkCollector currently focuses on external USB receivers. Android's internal
phone GNSS is not a supported receiver input.

Support below means the app has repository support, profiles or design coverage
for the receiver family. It does not automatically mean every precise
positioning workflow has been field-tested.

## Current Focus

| Receiver | Status | Notes |
| --- | --- | --- |
| Unicore UM980 / N4 | Primary experimental target | Main device family for in-device RTK/PPP rover workflows, NTRIP-to-receiver correction routing, temporary-base preparation and fixed-base operation. |
| u-blox M8T | Primary experimental target | Main u-blox target for raw/timing recording and RTKLIB or external post-processing workflows. M8T is not treated as an internal RTK float/fix rover. |
| Generic NMEA + RTCM | Basic fallback | Raw capture, basic NMEA advisory parsing and RTCM injection concept. Advanced receiver configuration is not generic. |
| u-blox M8P | Experimental, not author-field-tested | May work in USB recording and NTRIP-to-receiver modes with correct profiles. Precise positioning is not currently supported or tested by the author. M8P-0 and M8P-2 capabilities differ. |
| u-blox ZED-F9P-class receivers | Experimental, not author-field-tested | May work in modes similar to M8T or M8P where profile commands and UBX messages are compatible. Precise positioning is not currently supported or tested by the author. |

## Practical Guidance

- Use UM980/N4 first if you want the best-covered rover, NTRIP and base
  workflows.
- Use M8T first if you want raw/timing recording for later RTKLIB or external
  post-processing.
- Treat M8P and F9P as community-validation targets. Do not assume published
  app behaviour is precise until a real device, real antenna setup and real
  correction workflow have been tested and documented.
- Receiver command profiles are intentionally explicit. If a user copies a
  built-in profile, the telemetry capability should stay enabled only when the
  copied commands still enable the corresponding receiver messages.
