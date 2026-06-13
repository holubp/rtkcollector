# Receiver Profiles

## Generic NMEA + RTCM

- Can record the raw stream.
- Can parse basic NMEA fix state.
- Can feed RTCM corrections into the receiver if serial TX is available.
- Cannot configure advanced receiver modes.

## Unicore UM980 / N4

- Primary high-rate target.
- Supports custom command scripts.
- Supports rover configuration.
- Supports NTRIP RTCM injection.
- Should support base and fixed-base configuration later.
- May expose receiver PPP solution/status on supported firmware or correction
  services; this must be modelled separately from the normal device solution.
- Should monitor NMEA and UM980-native solution messages where available.
- Should record mixed UM980/NMEA/RTCM streams byte-exactly.

The bootstrap driver declares capabilities and placeholders only. It does not
hard-code operational UM980 commands.

The V1 COM1 binary monitoring profile should include `BESTNAVB`, `ADRNAVB`,
`PPPNAVB`, `RTKSTATUSB`, `RTCMSTATUSB ONCHANGED`, `OBSVMCMPB` and `STADOPB`.
Android is the NTRIP client; UM980 internal NTRIP-client commands are not part
of the V1 profile. Normal recording start commands are runtime commands only:
writing `SAVECONFIG` requires an explicit warned user action from the command
profile editor.

## u-blox M8P

- Internal single-frequency RTK receiver.
- M8P-0 is rover-oriented.
- M8P-2 is base and rover capable.
- Supports UBX configuration.
- Supports RTCM3 input for rover use.
- Supports RTCM3 output for base use on M8P-2.
- Supports UBX RAWX/SFRBX recording for post-processing and diagnostics.
- Should not be modelled as a receiver PPP solution source unless a specific
  profile proves that capability.

## u-blox M8T

- Raw, timing and post-processing receiver.
- Supports UBX RAWX/SFRBX recording.
- Supports survey-in and fixed-position timing concepts.
- Should not be treated as an internal RTK float/fix rover like M8P.
- Should not be treated as a receiver PPP solution source.
- Useful for static/base recording and post-processing workflows.

V1 practical support starts with M8T raw/timing recording. Built-in profiles
enable UBX `RXM-RAWX`, `RXM-SFRBX`, `TIM-TM2` and, where selected, `NAV-PVT`
for live dashboard and mock-location output. M8T is not treated as an internal
RTK float/fix rover.
