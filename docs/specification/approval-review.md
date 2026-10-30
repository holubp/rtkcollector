# RC10 Requirement And Capability Approval Review

Status: baseline approved by human:holubp; independent audit and clean-host CI remain pending.

Owner approval was explicitly given after the in-chat summary of these exact
revisions and inventory. Decision: `2026-10-30-rc10-specification-baseline-approval`,
recorded at `2026-10-30T18:06:55Z`. This document records that decision; its mere
presence did not grant approval. Unexecuted evidence is not declared passed.

Unchanged approved revisions: 107. Newly approved revisions: 49.
Approved revised-requirement inventory digest: `sha256:c78b2b81ebec170bae05e107cc3f8e697524a43a8321491f4ddfa2783a0f49b2`.
Approved capability inventory digest: `sha256:9675e61473ccc89b160f7cbe775d550d13b36cc962987594a03b318fc8ad2aa2`.
Approval-policy digest: `sha256:7f86c567dc580c62a27ba00d8c956af4a3ca946e0fb95f91aa2452af41988c48`.

## What Changed

- Reconcile approved alarms, diagnostics, USB recovery, transport and settings behavior
  into the canonical baseline without resurrecting obsolete TLS-only/unsafe-TLS rules.
- Formalize existing uncovered behavior: safe runtime commands, supported UM980
  periods, broadcast throttling, transient errors, base-output preflight, RTCM station
  decoding, measured message rates and unsaved-editor decisions.
- Correct documentation/process-only coverage: UI-DOCS-001 and PLAY-DISCLOSURE-001
  are documentation; RELEASE-SIGN-001 is process. Other requirements retain runtime
  coverage. The proposed model 6 framework preserves source coverage and approval
  gates and includes supporting content/relationships in receipts and exact review.
- Capability inventory covers 236 runtime source files explicitly, plus actual
  operator documentation and signing tooling/configuration. No wildcard runtime
  mappings or local/private captures substitute for coverage.

## Approved Revisions

| Requirement | Coverage kind | Exact proposed digest |
| --- | --- | --- |
| [ANDROID-ALARM-001: Trustworthy Loss Detection](android-runtime.md) | implementation_required | `sha256:c72d3d5d49c8320ed7e13df19d594d7438c49d7395e7f71879517bc83603ba98` |
| [ANDROID-ALARM-002: Independent Persistent Configuration](android-runtime.md) | implementation_required | `sha256:fae89de68bec2ff6bdaa71cf30fe53d4dd4c1e787cdc24d5846c9fd798f8001f` |
| [ANDROID-ALARM-003: Distinct And Private Outputs](android-runtime.md) | implementation_required | `sha256:c5af775d0cd6bd4b8942c96e7efe172675999b54d5a9a7877c2e9b8d8739cca6` |
| [ANDROID-ALARM-004: Recovery And Stop Semantics](android-runtime.md) | implementation_required | `sha256:74f08834d16b9ec43f4b99b774545b95ca1d66a479a15523b1819f9bfa90fd15` |
| [ANDROID-ALARM-005: Alarm Isolation](android-runtime.md) | implementation_required | `sha256:1115818e78f93d03b88995805ac716a42833023206f38a04c20248e1da14d3dd` |
| [ANDROID-ALARM-006: Explicit Playback Lifetime](android-runtime.md) | implementation_required | `sha256:cdd51f85803a777b3d8c0b88b6568c409a9f074b2aad4c11bd434db8b335d784` |
| [ANDROID-ALARM-007: Opt-In Bluetooth Routing And Speaker Fallback](android-runtime.md) | implementation_required | `sha256:7bffa837551d27d2c2ed2fae15593d51e0ff453193293d2c128753752a044b4d` |
| [ANDROID-ALARM-008: Do Not Disturb Advisory](android-runtime.md) | implementation_required | `sha256:294f8d4c1b791146197b87e1003b889ae7c930fa8971e1366779aea8c2bcb1ff` |
| [ANDROID-ALARM-009: Muted Alarm Audio Advisory](android-runtime.md) | implementation_required | `sha256:b8a9bfa41e4788672739dc0284ff997883856c8680cdee9fa0e0fa10eccb3e78` |
| [ANDROID-CONFIG-001: Running Configuration Uses A Service-Owned Snapshot](android-runtime.md) | implementation_required | `sha256:4b48e0fc77aed93bd97057798b989a5f8f447236b7614498a952b509dce23b66` |
| [ANDROID-DIAGNOSTICS-001: Diagnostics Do Not Block Recording](android-runtime.md) | implementation_required | `sha256:1d40c315f82bfd0b31dc9b28ddec60bf691bd61f64af8f13fb8a9bd010056054` |
| [ANDROID-DIAGNOSTICS-002: Persistent Levels And Bounded Optional Logs](android-runtime.md) | implementation_required | `sha256:1a9b2017729f9ecc8dec04706f8d3b12c12bde1297fb8525c78ea3dde797e6ed` |
| [ANDROID-DIAGNOSTICS-003: Stop Evidence And Honest Recovery](android-runtime.md) | implementation_required | `sha256:b26a4d241e0c4221769efd1def600d325edbf9eda6e9c1c9507c92673c83ea83` |
| [ANDROID-DIAGNOSTICS-004: Logging Disclosure And Privacy](android-runtime.md) | implementation_required | `sha256:ac5f5e4d53e1703538721f505c600ac726c24d92244ed5fb921b8c754d87f88a` |
| [ANDROID-STATE-002: Routine Dashboard Delivery Is Bounded](android-runtime.md) | implementation_required | `sha256:22cccc52db9192e4efb6524b657053fe69a06661437d195c2004f97a32414616` |
| [ANDROID-USB-002: USB Reconnect Attempts Do Not Stop Recording Session State](android-runtime.md) | implementation_required | `sha256:9a47a1e9106dd06346913b88c5f653e3b61e433bf2ce049541d0d1eede09d16d` |
| [ARCH-WRITER-002: Finalisation Waits For Writer-Using Workers](functional-requirements.md) | implementation_required | `sha256:2bdef8e5e0f784fca0f8e81a0b8e0564bd940e8b71a2f03c4eea544bd45b5a12` |
| [CORR-REFERENCE-001: RTCM Reference Positions Are Decoded From Valid Frames](functional-requirements.md) | implementation_required | `sha256:79fc1e81a0cf93561ccdcd191aa3e914b140fbf06d0ef8493f07b7e9194ed77e` |
| [CORR-UPLOAD-001: Caster Upload Uses RTCM3 Frames Only](functional-requirements.md) | implementation_required | `sha256:6f6e415ad376574dd3924c132f4f564b5935380d79f0d7a4c11f7d0a802c4dba` |
| [CORR-UPLOAD-010: UM980 Base Upload Requires Position And Observation Output](functional-requirements.md) | implementation_required | `sha256:a36ca48e18d77a2c5dc5b1feb8dc5ad19ca80f919384609701ac564a2742c76e` |
| [PLAY-DATA-001: Data Safety Claims Must Be Truthful Per Build](publication-readiness.md) | implementation_required | `sha256:f86e0890b50a3e4f62a0d2d911a3a5b62f52e756374129bdb0d1d20ac33acb8b` |
| [PLAY-DISCLOSURE-001: Privacy Policy And Play Checklist Are Release Sources](publication-readiness.md) | documentation | `sha256:9f5cbf9f685926786e4af20bd37c033348ddcb965f7f42a16d50dee0ef70029c` |
| [PLAY-LICENSE-001: Third-Party Licence Documentation Matches Release Dependencies](publication-readiness.md) | implementation_required | `sha256:c42a2d3e563dd81343cae69b4bbac65ae0c9f420ad886be4321bd0e5e8e1ccb0` |
| [RELEASE-SIGN-001: Stable Sideload Update Identity](publication-readiness.md) | process | `sha256:8d7bc421ab2f5ab00d73490e6d2c4c66bbcf3bb7dfc81e2aa091589f7a2a73b5` |
| [RX-MONITOR-001: Displayed Message Rates Measure Received Telemetry](receiver-behaviour.md) | implementation_required | `sha256:256a70fef317a578a7f3e97e2c9716f6efee6205d8ab2200edecc4cdc70baf25` |
| [RX-UM980-COMMANDS-001: Runtime Commands Are Nonpersistent And Single-Line](receiver-behaviour.md) | implementation_required | `sha256:c09aa6678eff3c5c86473397bdc28e6c5946b1c2de8b01b32848215e342d76f9` |
| [RX-UM980-FREQUENCY-001: Unsupported Continuous Output Rates Are Reported](receiver-behaviour.md) | implementation_required | `sha256:979507ce439efbfb9edfd18e8656b3feaa9d3c3a54382b488df1797a3a29716e` |
| [SEC-DIAGNOSTICS-001: Diagnostics Are Opt-In And Redacted](security-privacy.md) | implementation_required | `sha256:d108fab59b92ac711aa3351ef90606d2bbb76d2e34ce80c8bb21dfe13026adb8` |
| [SEC-IMPORT-005: Present Optional Family Fields Are Strictly Typed](security-privacy.md) | implementation_required | `sha256:cf459a15daff4592f262dd31b7b2efa5200fcc32516c127e277072bda11b3a32` |
| [SEC-IMPORT-006: Legacy Profile Migration Is Staged And Recoverable](security-privacy.md) | implementation_required | `sha256:c1eb5c31b8cdb17baefea27ea1259c9a39230f4aecb6e431f84fd4732573efcb` |
| [SEC-NTRIP-TLS-001: Explicit Transport And Legacy Unsafe Migration](security-privacy.md) | implementation_required | `sha256:ccb88787ec18a6215cb4d8780e98054b4dcc16d505a43b6b9e65c170640e2094` |
| [SEC-NTRIP-TLS-002: Distribution And Service Intent Boundary](security-privacy.md) | implementation_required | `sha256:a197ab944e0f2250ded50563428a16b8cfeedfd5da7bae56cc0b2cd91f5a7517` |
| [SEC-NTRIP-TLS-003: Verified Endpoint And Redacted Transport Failures](security-privacy.md) | implementation_required | `sha256:863a96abe70c7115d3fd122bc884898640b6014e6525ae5573902c1a8495c989` |
| [SEC-SECRETS-002: Committed Profile Owns Its Secret Binding](security-privacy.md) | implementation_required | `sha256:9f22b94a537d2918fb7c9a92e2ac0d1ddbb4bbf92a3bb7963aa470b4e82154f2` |
| [UI-CONSOLE-001: Device Console Is Idle-Only And Session-Isolated](ui-requirements.md) | implementation_required | `sha256:1003b199ff994cbd0a9c857166a021d3d8f0f51476311604fd9700cc41bdd892` |
| [UI-DASH-006: Recording Reliability Warnings Stay In The Alert Area](ui-requirements.md) | implementation_required | `sha256:a82c427fbfac116a5b1a847f16ca87ea7d8c9ae753321a871efbee8292976dd0` |
| [UI-DASH-007: Transient Error Presentation Does Not Alter Recording](ui-requirements.md) | implementation_required | `sha256:86d6af5b5716428c16a0066169feee6256ece72b031c19160b809d87b33b75fc` |
| [UI-DOCS-001: User Documentation Is Separate From Formal Specs](ui-requirements.md) | documentation | `sha256:cfa3ef3a12d5bb34b186ac8159f1dc6ff0ecb43988c58be4fad04ac0c07fcdf4` |
| [UI-NTRIP-001: Transport Metadata Does Not Masquerade As Status](ui-requirements.md) | implementation_required | `sha256:806de4433ecc20ede3f15495e9bf06a9062c20ba277a1ff63867d452d4fd1d24` |
| [UI-PROFILE-002: Profile Activation Is Separate From Editing](ui-requirements.md) | implementation_required | `sha256:3c51c32e8602d687b153db76a30d7a09139a0aa77be3d2e864f34efba2add8e7` |
| [UI-PROFILE-003: Profile References Preserve Owner Content](ui-requirements.md) | implementation_required | `sha256:1bd286c70e5da4d5409653c51e7a7c3d078c0304c18de6348e2be9c227e09ad1` |
| [UI-SETUP-002: Settings Set Options Have Explicit Application Policy](ui-requirements.md) | implementation_required | `sha256:a392a8a0bdf3b4edf5fce3bf4df5aa4b8ca89ed55acc68f7a0cd565def3e0f91` |
| [UI-SETUP-003: Dashboard Setup Strip Keeps Essential Selectors](ui-requirements.md) | implementation_required | `sha256:c0828630be76ccd2cda2b219bf9446b07ce0a5ea49583284ed21c5419b4e3fe9` |
| [UI-SETUP-008: Dashboard Setup Selectors Are Foldable And Self-Revealing](ui-requirements.md) | implementation_required | `sha256:00b99fcff6bbdcb71907d694938054637961b0cb49f7b0eafc1c9095cd759588` |
| [UI-SETUP-009: Active Setup Policies Resolve Consistently](ui-requirements.md) | implementation_required | `sha256:6528b4783f684d187ffd00cf66b33f16e6b01dd43c29117abc5d03541e3e44ed` |
| [UI-STORAGE-001: SAF Storage Uses Android Folder Picker](ui-requirements.md) | implementation_required | `sha256:5bccc007bbb0964f9eee55ea5e4afc003a804d9eed3d197bf79cb7d6e2c9568d` |
| [WF-BUILTIN-SETTINGS-001: Built-In Rover Settings Require Explicit NTRIP Selection](workflows.md) | implementation_required | `sha256:dc0da7ea3df4bcf5bcef8ca7b785c3fc945035de770ac5e4a88d893be8944ebf` |
| [WF-SETTINGS-DEPENDENCY-001: Correction Source Owns Its Caster Reference](workflows.md) | implementation_required | `sha256:933106011575aac30d80b8c2598f8f498988407a8b8cd42a310cfe43adf53ba8` |
| [WF-SETTINGS-OWNERSHIP-001: Settings Sets Own Defaults And Selection Policy](workflows.md) | implementation_required | `sha256:42f545a9ae6c8999d534c68fe4bdc8d9985d34295f139a8b0d5a7bd1c8f2c0c5` |

## Evidence And Release Boundary

Approval accepts the contract and inventory, not a claim that every device/provider
check passed. The receipt-derived matrix retains unexecuted evidence as Needs review
and the existing Google Play data-policy review as Blocked; that Play publication
block does not prohibit a GitHub sideload prerelease. Source-local test results,
exact independent semantic review, clean-host CI and stable APK verification remain
separate. No RC10 tag/release/APK has been published.

## Repository Enforcement Decision

Owner decision `2026-10-30-owner-external-pr-governance` supersedes the earlier
proposal to require universal GitHub protection and a second GitHub reviewer:

- Work authored by `holubp`, including the owner-authorized RC10 release, may
  proceed without GitHub-level review/protection. Requirements approval, local
  checks, independent audit, clean-host CI and signing verification remain gates.
- Externally authored PRs require the trusted-base changed-scope check and owner
  review before merge. Author classification uses the authenticated PR author,
  not the merging actor or forgeable commit-author text.
- The workflow keeps external-author checking and full release/scheduled checks.
  Full and changed attestations remain separate exact records, with no fallback.
- The initial approved baseline must be installed before a trusted-base workflow
  can rely on its checker. No candidate-owned checker fallback is permitted.
- No GitHub repository settings were changed. Actual required-check protection
  for contributors must be configured and verified before claiming it is enforced.
  Its absence is not an owner-authored RC10 release blocker under this decision.

This is an explicit owner-governance exception to universal protected enforcement,
not a claim that an unprotected repository provides equivalent technical protection.
GitHub bypass is actor-based, so granting an owner bypass alone does not express
an author-only policy; the workflow separately checks the PR author.

Read-only GitHub inspection currently shows successful `build`, `Analyze (python)`,
`Analyze (actions)` and `Analyze (c-cpp)` checks on main. Those results are not RC10
candidate evidence. No `changed` baseline check has been established there yet.
