# USB Access Permission Robustness Design

## Purpose

Improve RtkCollector's USB receiver access flow so older or vendor-modified
Android devices, including Huawei P30 Pro class devices, get a clear and
recoverable path from connected receiver to verified USB access.

The goal is not to implement USB recording differently. The goal is to make the
permission and access gate more explicit:

- request USB permission automatically when Start is pressed and permission is
  missing;
- never create a recording session until USB access has been verified;
- do not treat `UsbManager.hasPermission(device)` as sufficient proof that the
  device can be opened;
- explain stale or inconsistent Android USB permission states clearly.

## Current Behaviour

The app has two relevant USB permission paths:

- The dashboard USB access action calls `UsbManager.requestPermission(...)` when
  `hasPermission(device)` is false.
- Start currently refuses to start when `hasPermission(device)` is false, and
  the service checks `hasPermission(device)` again before opening the USB
  transport.

Recent field-hardening changes already made USB open safer by opening the
transport before session writers are created. That prevents empty successful
sessions when USB open fails. However, the permission UX is still fragile:

- Start does not request permission itself.
- If Android reports `hasPermission(device) == true`, the app does not request
  permission again.
- Some vendor Android builds may report permission as granted while
  `openDevice(...)` or interface claiming still fails.

## Desired User Flow

### Start With No Matching Device Connected

When Start is pressed and the selected USB profile does not match any connected
device, the app shows a USB error:

> Selected USB receiver is not connected.

No recording session is created.

### Start With Device Connected But Permission Missing

When Start is pressed and `UsbManager.hasPermission(device)` is false:

1. The app calls `UsbManager.requestPermission(device, pendingIntent)`.
2. No recording session is created.
3. The user sees a clear message:

> USB permission requested. Approve the Android permission dialog, then press
> Start again.

When the permission broadcast returns:

- if granted, dashboard state should show that permission is available;
- if denied, dashboard state should show that USB permission was denied.

The app will not auto-start recording immediately after the permission result in
V1. Requiring Start again avoids surprising receiver initialisation, NTRIP
connection or command transmission immediately after a system dialog.

### Start With Permission Reported Granted

When Start is pressed and `hasPermission(device)` is true, the app still treats
USB access as unverified until it has successfully opened and claimed the USB
interface.

Before any session writer is opened, the service verifies:

- selected USB device exists;
- Android reports USB permission;
- the device exposes a usable serial endpoint;
- the transport can be opened and the interface claimed;
- storage preflight passes.

If the USB open/claim succeeds, recording may proceed.

If Android reports permission granted but USB open/claim fails, recording must
not start and no session directory should be created. The user-facing error must
distinguish this from a simple missing permission:

> Android reports USB permission, but the receiver could not be opened. Reconnect
> the receiver, close other serial apps, then retry USB access.

This message should also cover stale permission state, another app holding the
device, vendor USB stack issues and a disconnected/re-enumerated receiver.

## Dashboard And UI Requirements

- Keep the existing USB access button.
- Start may invoke the same permission request path automatically when
  permission is missing.
- The USB access button should remain useful for manual recovery and for testing
  access before recording.
- If `hasPermission(device)` is true but access has not yet been verified, UI
  wording should avoid claiming the device is ready. Prefer:

> USB permission reported granted; access will be verified on Start.

- If a verification/open failure occurs, show it as a USB category error on the
  dashboard.

## Manifest And Default-App Handling

RtkCollector should remain registered for USB device attach intents so Android
can offer it when a supported receiver is connected.

The `usb_device_filter.xml` should cover known receiver USB bridge IDs that are
expected for V1 hardware testing. The current known UM980 FTDI bridge is:

- VID `0x0403`, PID `0x6015`.

Future IDs should be added only when observed or documented. Runtime USB access
verification remains mandatory even if the app is launched by a USB attach
intent, because launch/default-app handling does not guarantee successful
permission or interface claiming.

## Error Handling Rules

- Missing connected receiver: USB fatal/pre-start error.
- Permission missing: request permission, no session creation, user presses
  Start again after approving.
- Permission denied: USB error, no session creation.
- Permission reported granted but open fails: USB error, no session creation,
  advise reconnect/close other apps/retry.
- USB disconnect during recording: recording remains alive where possible,
  raw recording becomes inactive, NTRIP may continue/reconnect according to the
  selected workflow, and USB reconnect should retry only when permission and
  device identity still match.

## Architecture Rules

- Activity/UI may request permission and display state.
- Foreground service remains the owner of recording and must verify USB access
  before opening session writers.
- No permission or access-check logic may write into `receiver-rx.raw`.
- Permission failure, stale permission or open failure must not start parser,
  NTRIP, receiver command or recording session work.

## Testing Strategy

Unit-level tests should cover:

- Start-intent builder requests permission instead of returning a recording
  intent when permission is missing.
- Permission request message includes "press Start again".
- Unknown/stale access states produce USB-category errors.
- USB access verification is required before session writer opening.

Manual tests should cover:

- Fresh install, connect UM980, press Start, approve permission, press Start
  again.
- Deny permission and confirm no recording session is created.
- Disconnect/reconnect receiver and confirm permission/access status refreshes.
- Huawei P30 Pro or similar older/vendor Android device:
  - USB access button path;
  - Start-triggered permission request path;
  - reported-granted-but-open-fails message if reproducible.

## Out Of Scope

- Automatic recording start immediately after permission grant.
- Replacing Android's USB permission model.
- Persistent permission beyond what Android grants for the device and app.
- USB serial driver rewrites beyond access verification and error reporting.
