# Persistent Receiver Writes And Baud Persistence Design

## Goal

Fix persistent receiver configuration writing so it works both while recording
and as a maintenance action before recording. Add an explicit way to write the
selected USB/baud profile target baud as the UM980 persistent receiver baud.

This design is for UM980/N4 V1 behaviour. It must not change normal recording
startup into a persistent receiver mutation.

## Current Problem

The current `Write init config persistently to device` action is blocked while
recording. That is inconvenient and sometimes wrong: while recording, the app
already owns the working USB connection, the receiver may already be at the
target runtime baud, and opening a second connection can fail or target the
wrong line state.

The current action also only belongs to command profiles. There is no clear USB
and baud profile action for writing `CONFIG COM1 <target baud>` and
`SAVECONFIG` as the receiver's saved default line speed.

## Non-Goals

- Do not add `SAVECONFIG` to normal recording start.
- Do not persist receiver configuration silently.
- Do not support persistent writes for non-UM980 receivers in this iteration.
- Do not add firmware reset, factory reset, flash erase or broad unsafe command
  support.
- Do not bypass USB permission checks.
- Do not mutate `receiver-rx.raw`; all app-to-receiver bytes remain separate.

## User-Facing Actions

### Command Profile Persistent Write

The command profile editor keeps a warned action:

`Write init config persistently to device`

The action sends the currently edited visible `Init script` field. In current
models that field is stored as `runtimeScript`; the legacy `initScript` model
field is not the source of truth for this action. The app then appends exactly
one final `SAVECONFIG`.

The warning must state that the action writes receiver non-volatile memory and
can affect future sessions and other tools until the receiver is reconfigured.

### USB/Baud Profile Persistent Baud Write

The USB/baud profile editor adds a warned action:

`Write target baud persistently to device`

The action sends:

```text
CONFIG COM1 <target receiver and host baud>
CONFIG COM2 <target receiver and host baud>
CONFIG COM3 <target receiver and host baud>
SAVECONFIG
```

`<target receiver and host baud>` is the selected USB/baud profile's target baud
(`serialBaud` in current models). It must be one of the app's supported baud
values.

The warning must explicitly mention:

- the current initial receiver baud used to connect;
- the target baud being written;
- that a power cycle or future app connection may need the target baud after the
  write succeeds;
- that the write is UM980-specific.

## Execution Modes

### Recording Active

When recording is active, persistent writes must use the foreground recording
service's already-open receiver connection.

Rules:

- Do not ask the Activity to open a second USB connection.
- Send commands through the service's app-to-receiver TX path.
- Record all transmitted bytes in `tx-to-receiver.raw`.
- Emit an event or quality/status message saying a persistent write was
  requested, started, succeeded or failed.
- Keep raw receiver capture independent. Parser/NTRIP/UI failures must not
  block RX recording.
- If the active recording connection is unavailable or not connected, fail with
  a user-visible error rather than silently opening a second connection.

For a command profile persistent write, the service sends validated profile
commands followed by `SAVECONFIG`, then requires an explicit receiver
`response: OK` acknowledgement.

For a USB/baud persistent write, the service sends `CONFIG COM1 <target baud>`,
waits briefly, reconfigures the host bridge to the target baud if needed, checks
that receiver communication continues, then sends `CONFIG COM2 <target baud>`,
`CONFIG COM3 <target baud>` and `SAVECONFIG` at the target baud. Success requires
an explicit receiver `response: OK` acknowledgement for `SAVECONFIG`.

### Not Recording

When recording is not active, persistent writes use a temporary maintenance USB
connection.

Rules:

- Resolve the selected USB/baud profile.
- Select the configured USB device, or fail clearly if no matching receiver is
  connected.
- Require Android USB permission.
- Open the maintenance connection at the profile's initial receiver baud
  (`profileBaud` in current models).
- Verify that the connection is live before writing permanent configuration.
- Only after the live check succeeds, send the persistent command sequence.
- Close the maintenance connection after success or failure.

The live check must prefer a benign UM980 query:

```text
VERSION
```

or the binary equivalent where already available. A response containing plausible
UM980/Unicore output is sufficient. If the receiver is already streaming, recent
valid UM980/NMEA bytes may also satisfy the check.

If the live check fails, the app must not send `CONFIG COM1`, `SAVECONFIG` or
any other persistent command.

## Baud Persistence Sequence

For both active-recording and maintenance paths, baud persistence follows this
logical sequence:

1. Confirm target baud is one of the supported values.
2. Confirm the selected receiver/device is connected and writable.
3. Send `CONFIG COM1 <target baud>` at the currently working baud.
4. Pause briefly for the receiver line-speed change.
5. Reconfigure the host USB serial bridge to the target baud when the target
   differs from the current host baud.
6. Confirm receiver communication at the target baud.
7. Send `CONFIG COM2 <target baud>` and `CONFIG COM3 <target baud>` at the
   target baud.
8. Send `SAVECONFIG` at the target baud.
9. Report success only after `SAVECONFIG` returns an explicit receiver
   `response: OK`; otherwise report the write as failed.

The app must not claim that the receiver definitely committed flash/NVM unless
the receiver provides an explicit acknowledgement that is parsed. In V1,
persistent-write success requires a verified connection, successful writes and
an explicit `SAVECONFIG` OK acknowledgement.

## Command Safety

The persistent command builder must:

- strip blank lines and comments;
- reject unsafe commands such as reset, factory reset, shell metacharacters,
  flash erase or unrelated save variants;
- permit `SAVECONFIG` only as the final app-appended command;
- remove user-supplied `SAVECONFIG` from the body and append exactly one final
  `SAVECONFIG`;
- validate `CONFIG COM1 <baud>` for the USB/baud persistent action.

The existing runtime-command safety validator must be reused where it already
matches this behaviour. If it currently rejects `SAVECONFIG`, the persistent
builder remains responsible for appending it after validating the body.

## Recording And Session Artifacts

When the active service path is used:

- app-to-receiver persistent commands are written to `tx-to-receiver.raw`;
- user-visible events are written to `events.jsonl` or equivalent service state;
- `receiver-rx.raw` remains byte-exact receiver output only;
- no app-injected markers are written into raw RX.

When the maintenance path is used outside recording, there is no recording
session. The app must still report the action result with a toast/dialog and
must not create an empty recording session.

## Error Handling

User-visible errors must distinguish:

- no selected USB/baud profile;
- selected USB receiver not connected;
- USB permission missing;
- maintenance connection could not be opened at initial receiver baud;
- live receiver check failed;
- persistent write already in progress;
- active recording service has no writable receiver connection;
- target baud is unsupported;
- host baud switch failed;
- write failed while sending `CONFIG COM1` or `SAVECONFIG`.

For active recording, a failed persistent write must not stop recording unless
the underlying receiver transport itself has failed.

## UI Placement

Command profile editor:

- keep the existing persistent write action;
- update warning copy to say it can run through the active recording connection
  when recording is active;
- make clear that the current edited command text is what will be sent.

USB/baud profile editor:

- add `Write target baud persistently to device`;
- show initial baud, target baud and selected USB device in the warning;
- require confirmation before sending anything.

The dashboard Start button must remain disabled or guarded while a persistent
write is already in progress.

## Tests

Add or update tests for:

- persistent command builder appends exactly one final `SAVECONFIG`;
- user-supplied `SAVECONFIG` inside the command body does not create duplicates;
- unsafe commands are rejected before persistent write;
- baud-persistence command builder emits `CONFIG COM1 <target baud>`,
  `CONFIG COM2 <target baud>`, `CONFIG COM3 <target baud>` and `SAVECONFIG`;
- unsupported baud values are rejected;
- active-recording decision chooses service TX path;
- not-recording decision chooses maintenance connection path;
- maintenance path refuses to send persistent commands when live check fails;
- UI action visibility and warning text for USB/baud persistent write.

Hardware/manual validation:

- With UM980 connected and not recording, write target baud persistently from
  the USB/baud profile editor and confirm reconnect after power cycle using the
  target baud.
- While recording, write a benign persistent command/profile through the service
  path and confirm recording continues and `tx-to-receiver.raw` contains the
  transmitted commands.
- Confirm normal recording start still does not send `SAVECONFIG`.

## Compatibility Notes

Older Android/vendor USB behaviour is handled by requiring explicit USB
permission and checking that the selected configured USB device is connected.
The maintenance path must not assume that `hasPermission()` alone means the
connection can be opened; it must attempt the open and verify receiver
communication before writing permanent configuration.

The active-recording path is preferred whenever recording is already active
because it avoids competing USB ownership and uses the known working connection.
