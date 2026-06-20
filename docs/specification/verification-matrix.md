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
| `WF-RTKLIB-001` | Automated + review | RTKLIB input route tests; native bridge tests; V1 code review | In progress | Routing/API foundation, bounded worker and JNI/native bridge are implemented. Native `.so` build still needs validation on a host with a working Android NDK. |
| `WF-SOLUTION-001` | Automated + review | Best-solution selector tests; recording tick policy tests; session metadata tests | Passing | Screen and mock-location source policies can be selected separately. |
| `RX-UM980-001` | Automated + manual | UM980 parser tests; binary session replay | Needs review | Mixed-stream parser coverage should keep expanding. |
| `RX-UM980-RTK-001` | Automated + manual | BESTNAV/ADRNAV/RTKSTATUS/RTCMSTATUS tests; field session | Needs review | Real receiver/caster validation remains important. |
| `RX-UM980-PPP-001` | Automated + manual | PPPNAV parser/dashboard tests; debug sessions | Passing | Local tests cover label mapping; field regression should continue. |
| `RX-UM980-PROFILES-001` | Automated + review | Profile store migration/protected-profile tests | Passing | Built-in profiles are protected and copyable. |
| `RX-UM980-RTKLIB-001` | Automated + review | RTKLIB input route tests; UM980 sample-readiness tooling | In progress | OBSVMB is direct. OBSVMCMPB is routed through the named `rtkcollector-obsvmcmp-shim`; existing local samples do not contain OBSVMB/OBSVMCMPB frames, so replay validation remains open. |
| `RX-UBLOX-M8T-001` | Automated + hardware | u-blox parser/capability tests; M8T device test | Not field-tested | First implementation exists. |
| `RX-UBLOX-RTKLIB-001` | Automated + hardware | RTKLIB input route tests; M8T/M8P replay test later | Needs field validation | UBX RAWX/SFRBX route is modelled as direct RTKLIB-EX input. |
| `SESSION-FILES-001` | Automated + manual | Session writer tests; completed sessions | Needs review | Keep artifacts separate by source and purpose. |
| `SESSION-META-001` | Automated + review | Session metadata tests; session format review | Passing | Metadata includes workflow and solution-policy context and must exclude secrets. |
| `SESSION-NMEA-001` | Automated + sample replay | NMEA exporter/re-exporter tests; high-rate samples | Needs review | Verify sub-second UTC preservation. |
| `SESSION-RTCM-001` | Automated + manual | Correction-input raw/RTCM3 tests; downstream replay | Needs review | Ensure output works with external pipelines. |
| `SESSION-RTKLIB-001` | Automated + manual | RTKLIB output writer tests; RTKLIB-enabled session later | In progress | NMEA/POS artifact model and native output bridge exist; host native build and field/replay solution validation remain open. |
| `ANDROID-SERVICE-001` | Review + manual | Service ownership review; background recording smoke test | Needs review | Screen-off and background testing remain required. |
| `ANDROID-CAPTURE-001` | Review + manual | Capture code review; background/minimised session | Needs review | UI/parser failures must remain advisory. |
| `ANDROID-USB-001` | Automated + device | USB decision tests; Huawei P30 Pro smoke test | Needs field retest | Vendor Android edge case. |
| `ANDROID-USB-002` | Automated + manual | Reconnect policy tests; disconnect/reconnect smoke test | Needs review | Must preserve existing session artifacts. |
| `ANDROID-STORAGE-001` | Manual + review | SAF folder-picker storage profile and service start review | Needs provider testing | Verify persisted write permission on common Android document providers. |
| `ANDROID-MOCK-001` | Automated + manual | Mock-location mapper tests; mock altitude smoke test | Needs review | `Location.altitude` must use ellipsoidal height. |
| `ANDROID-MOCK-002` | Review | Android API limitation review; user-doc wording | Passing | Satellite counts may be extras; full `GnssStatus` injection is not promised. |
| `ANDROID-MOCK-003` | Automated + manual | Mock publish tick/dashboard tests; mock provider field run | Needs review | Monitor status, last publish interval, selectable fixed rate and effective rate. |
| `SEC-SECRETS-001` | Automated + review | Session/settings export tests | Needs review | Include plaintext password export option. |
| `SEC-SETTINGS-001` | Automated + manual | Settings export tests; export UI review | Needs review | Plaintext passwords must be opt-in. |
| `SEC-IMPORT-001` | Automated + manual | Settings import validation tests; Android JSON open flow | Needs review | Reject malformed or unsafe imports. |
| `UI-DASH-001` | Manual + review | Phone/tablet portrait/landscape screenshots | Needs review | Visual fidelity and nav-bar checks. |
| `UI-DASH-002` | Manual + review | Live UM980 session screenshots/video | Needs review | No jumping cards. |
| `UI-DASH-003` | Manual + review | Phone portrait dashboard screenshot | Needs review | Live-monitoring cards should switch to single column when needed. |
| `UI-DASH-004` | Automated + manual | Dashboard state/action tests; dashboard chip smoke test | Needs review | Mock GPS chip is top-level; no placeholder Mark action. |
| `UI-BASE-001` | Automated + manual | Dashboard state tests; temporary-base dashboard review | Needs review | Keep controls compact and explicit. |
| `UI-SETUP-001` | Automated + manual | Active setup resolver tests; settings hub review | In progress | Active settings set is visible in the settings hub. Main-menu refinement remains under review. |
| `UI-SETUP-002` | Automated + manual | Active setup resolver tests; settings-set editor review | In progress | Policy model and JSON persistence exist; compact editor polish remains. |
| `UI-PROFILE-002` | Automated + manual | Profile compatibility tests; profile editor smoke test | In progress | Compatibility model and controlled RTKLIB/solution references exist; grouping/reorder UI remains open. |
| `UI-PROFILE-001` | Automated + manual | Protected-profile tests; profile editor smoke test | Needs review | Built-ins are view-only and copyable. |
| `UI-KEYBOARD-001` | Manual + review | Hardware keyboard command-editor smoke test | Needs field retest | Arrow keys stay inside native multiline editor; Tab/Shift+Tab traverse fields. |
| `UI-STORAGE-001` | Manual + review | Storage profile folder-picker smoke test | Needs provider testing | Tree URI is picker-selected and display-only. |
| `UI-DOCS-001` | Review | Docs review | Passing | Formal specs and user docs are separate. |
