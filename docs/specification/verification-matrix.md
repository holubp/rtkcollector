# Verification Matrix

| Requirement ID | Verification type | Evidence | Status | Notes |
| --- | --- | --- | --- | --- |
| `ARCH-RAW-001` | Automated + manual | Session writer tests; replay comparison | Needs review | Confirm current tests cover byte-for-byte replay. |
| `ARCH-RAW-002` | Automated + manual | Parser-failure isolation tests; malformed stream session | Needs review | Confirm parser exceptions cannot stop raw capture. |
| `ARCH-TX-001` | Automated + review | TX artifact tests; serial write path review | Needs review | Include correction injection and command scripts. |
| `ARCH-CORR-001` | Automated + manual | NTRIP runtime tests; rover+NTRIP session | Needs review | Verify `correction-input.raw` is usable RTCM3. |
| `PRODUCT-NONGOAL-001` | Review | Dependency and UI review | Passing | No maps/GIS/shapefile scope is allowed. |
| `WF-ROVER-001` | Automated + manual | Workflow validator tests; plain rover smoke test | Needs review | Confirm no NTRIP connection opens. |
| `WF-ROVER-NTRIP-001` | Automated + manual | NTRIP tests; EUREF/CORS field session | Needs review | Confirm RTK float/fix with correction recording. |
| `WF-TEMPBASE-001` | Automated + manual | Base averaging/acceptance tests; temporary-base field test | In progress | Implementation exists, needs complete workflow review and field validation. |
| `WF-TEMPBASE-HEIGHT-001` | Automated + review | Dashboard candidate tests; UM980 fixed-base command review | Passing | Current candidate requires ellipsoidal height and avoids altitude fallback. |
| `WF-FIXEDBASE-001` | Automated + manual | Workflow validator; fixed-base coordinate display/start test | In progress | Accepted-coordinate persistence and display remain under review. |
| `WF-FIXEDBASE-002` | Automated + review | Active recording config tests | Passing | Fixed-base start rejects `MODE ROVER` command profiles. |
| `WF-RTKLIB-001` | Review | V1 code review | Passing | RTKLIB remains future/V2. |
| `RX-UM980-001` | Automated + manual | UM980 parser tests; binary session replay | Needs review | Mixed-stream parser coverage should keep expanding. |
| `RX-UM980-RTK-001` | Automated + manual | BESTNAV/ADRNAV/RTKSTATUS/RTCMSTATUS tests; field session | Needs review | Real receiver/caster validation remains important. |
| `RX-UM980-PPP-001` | Automated + manual | PPPNAV parser/dashboard tests; debug sessions | Passing | Local tests cover label mapping; field regression should continue. |
| `RX-UM980-PROFILES-001` | Automated + review | Profile store migration/protected-profile tests | Passing | Built-in profiles are protected and copyable. |
| `RX-UBLOX-M8T-001` | Automated + hardware | u-blox parser/capability tests; M8T device test | Not field-tested | First implementation exists. |
| `SESSION-FILES-001` | Automated + manual | Session writer tests; completed sessions | Needs review | Keep artifacts separate by source and purpose. |
| `SESSION-META-001` | Automated + review | Session metadata tests; session format review | Needs review | Must exclude secrets. |
| `SESSION-NMEA-001` | Automated + sample replay | NMEA exporter/re-exporter tests; high-rate samples | Needs review | Verify sub-second UTC preservation. |
| `SESSION-RTCM-001` | Automated + manual | Correction-input raw/RTCM3 tests; downstream replay | Needs review | Ensure output works with external pipelines. |
| `ANDROID-SERVICE-001` | Review + manual | Service ownership review; background recording smoke test | Needs review | Screen-off and background testing remain required. |
| `ANDROID-CAPTURE-001` | Review + manual | Capture code review; background/minimised session | Needs review | UI/parser failures must remain advisory. |
| `ANDROID-USB-001` | Automated + device | USB decision tests; Huawei P30 Pro smoke test | Needs field retest | Vendor Android edge case. |
| `ANDROID-USB-002` | Automated + manual | Reconnect policy tests; disconnect/reconnect smoke test | Needs review | Must preserve existing session artifacts. |
| `SEC-SECRETS-001` | Automated + review | Session/settings export tests | Needs review | Include plaintext password export option. |
| `SEC-SETTINGS-001` | Automated + manual | Settings export tests; export UI review | Needs review | Plaintext passwords must be opt-in. |
| `SEC-IMPORT-001` | Automated + manual | Settings import validation tests; Android JSON open flow | Needs review | Reject malformed or unsafe imports. |
| `UI-DASH-001` | Manual + review | Phone/tablet portrait/landscape screenshots | Needs review | Visual fidelity and nav-bar checks. |
| `UI-DASH-002` | Manual + review | Live UM980 session screenshots/video | Needs review | No jumping cards. |
| `UI-BASE-001` | Automated + manual | Dashboard state tests; temporary-base dashboard review | Needs review | Keep controls compact and explicit. |
| `UI-DOCS-001` | Review | Docs review | Passing | Formal specs and user docs are separate. |

