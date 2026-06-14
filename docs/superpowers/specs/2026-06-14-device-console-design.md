# Device Console Design

## Purpose

Add an idle-only device console for direct receiver interaction. The console is
for manual diagnostics and maintenance commands when no recording is active. It
must not share lifecycle with active recording and must not mutate recorded
session files.

The console is intentionally ASCII-command oriented for V1. It may display
mixed binary/text receiver output safely, but it will not provide arbitrary raw
binary send controls in this iteration.

## Scope

Included:

- A new final Settings menu group containing a `Device console` entry.
- Rename the existing `Command scripts` menu entry to `Init/shutdown scripts`.
- Idle-only USB connection using the selected USB/baud profile.
- Manual connect and disconnect actions.
- USB/baud profile selector in the console header.
- Line-ending selector in the console header, defaulting to `CRLF`.
- Init-script selector and a separate `Send init` action.
- Temporary rolling output buffer with default limit of 1 MB.
- Pause-output mode that keeps reading and buffering while freezing visible
  output updates.
- Multiline output view and multiline ASCII input view.
- Bottom actions: `Send`, `Clear input`, `Pause output`, `Copy output`, and
  `Clear output`.
- Disconnect on leaving the console screen.

Excluded from V1:

- Console use while recording is active.
- Recording console transcripts into session artifacts.
- Arbitrary hex/raw binary command send.
- Background console operation after leaving the screen.
- Receiver workflow start, NTRIP, or recording control from the console.

## Architecture

Use an app-layer console controller rather than letting Compose own USB
transport directly. The controller owns the short-lived idle USB transport,
reader thread, output buffer and send operations. Compose owns only user intent
and rendering.

Proposed components:

- `DeviceConsoleController`: opens/closes `AndroidUsbSerialTransport`, starts
  and stops the reader loop, exposes immutable state updates, and accepts send
  requests.
- `DeviceConsoleState`: connection status, selected USB profile label,
  line-ending selection, init-script selection, paused flag, buffer limit,
  output text, and last error.
- `DeviceConsoleScreen`: Compose screen with the console layout and action
  callbacks.
- Small pure helpers for line ending rendering and rolling-buffer trimming.

This keeps console USB ownership separate from `RecordingForegroundService`.
Recording remains the only owner of USB transport while a session is active.

## UI Layout

The console is reached from Settings as the last item in its own group.

Header:

- Back.
- Title: `Device console`.
- USB/baud profile selector.
- Line-ending selector: `CRLF`, `LF`, `CR`, `None`; default `CRLF`.
- Connect.
- Disconnect.

Second control row:

- Init-script selector.
- `Send init`.
- Buffer-size selector, default `1 MB`.

Main body:

- Output pane above input pane.
- Output pane is multiline, scrollable and read-only.
- Input pane is multiline and editable.

Bottom controls:

- `Send`.
- `Clear input`.
- `Pause output` / `Resume output`.
- `Copy output`.
- `Clear output`.

If recording is active, the screen must not open the USB device. It should show
a clear disabled state explaining that the console is available only after
recording stops.

## Data Flow

Connect flow:

1. User selects a USB/baud profile.
2. User taps `Connect`.
3. Controller checks that no recording is active.
4. Controller opens the selected USB device at the profile baud.
5. Controller starts the reader loop.
6. Incoming bytes are decoded for safe display and appended to the rolling
   buffer.

Send flow:

1. User enters ASCII text.
2. User selects a line ending.
3. User taps `Send`.
4. Controller writes the UTF-8/ASCII-compatible bytes plus selected line ending.

Init flow:

1. User selects an init script.
2. User taps `Send init`.
3. Controller sends the script text explicitly. Connect never sends init
   automatically.

Exit flow:

1. Back or navigation away closes the controller.
2. Controller stops the reader loop and closes the USB transport.

## Output Formatting

Receiver output can contain NMEA, UM980 ASCII, UBX, RTCM, or UM980 binary
frames. The console display must therefore be binary-safe:

- Printable ASCII is displayed as text.
- Common line endings are preserved.
- Non-printable bytes are represented compactly, for example `<AA>` or similar
  byte markers.
- Buffer trimming must operate on the encoded display buffer or byte buffer
  without corrupting app state.

The display is diagnostic only and must not be used as parser input for
recording.

## Error Handling

- Connect without a selected/available USB device shows a clear error.
- Missing Android USB permission uses the existing USB permission request path
  where practical.
- Open/read/write failures are shown in the console state and do not affect
  recording state.
- Disconnect is idempotent.
- Leaving the screen closes the USB transport even after errors.
- If recording starts from elsewhere while the console is open, the console
  should disconnect and report that recording owns the receiver.

## Safety Rules

- Console is disabled during active recording.
- Console does not write to `receiver-rx.raw`, `tx-to-receiver.raw`,
  `correction-input.raw`, session metadata or quality logs.
- Console does not run init scripts automatically on connect.
- Console does not call `SAVECONFIG` unless the user manually sends such a
  command or such a command is part of a selected script they explicitly send.
- Console does not change workflow/profile selection except through existing
  profile selectors.

## Testing

Pure unit tests:

- Line ending selection renders the expected bytes.
- Rolling output buffer trims to configured size.
- Binary-safe formatter preserves printable text and represents non-printable
  bytes.
- Console state transition helpers reject recording-active connection attempts.

Android/source verification:

- `:app:compileDebugKotlin` in the local Termux environment.
- Full APK build on Windows Android Studio or CI-capable host.

Manual hardware smoke test:

1. Stop recording.
2. Open Settings > Device console.
3. Select the known UM980 USB/baud profile.
4. Connect.
5. Send a harmless command such as `VERSION`.
6. Confirm output appears.
7. Pause output and confirm reading continues without blocking.
8. Disconnect and confirm the USB device can subsequently be used for normal
   recording.

