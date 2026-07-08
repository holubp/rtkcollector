# RtkCollector Privacy Policy

Effective date: 2026-06-14

RtkCollector is an Android GNSS receiver companion for byte-exact receiver
recording, NTRIP correction intake, receiver control and temporary-base
preparation. The app is designed to operate locally on the user's Android
device. It does not include advertising, analytics SDKs, crash-reporting SDKs,
maps, shapefiles, GIS editing or field-feature collection.

## Data Processed By The App

RtkCollector can process and store:

- precise GNSS positions received from an attached receiver;
- receiver raw byte streams and derived solution logs;
- receiver identifiers, USB VID/PID, product names and serial/baud settings;
- NTRIP caster host, port, mountpoint, username and secret references;
- NTRIP passwords stored locally through Android Keystore-backed storage;
- user-created receiver command scripts, storage profiles and settings sets;
- session files, ZIP archives and settings backup JSON files selected by the user.

## Local Storage

Recording sessions are stored in the configured app-private folder or Android
Storage Access Framework location. Session metadata must not contain NTRIP
passwords or tokens. Receiver RX, app TX to receiver, correction input, events,
quality logs and derived solution files are stored as separate artifacts.

NTRIP passwords are stored locally and encrypted with Android Keystore-backed
AES-GCM where Android provides the required Keystore services. Android backup is
disabled for the app.

## Network Transfers

When the user enables NTRIP, RtkCollector connects to the configured caster and
mountpoint. The app sends NTRIP credentials to that caster when credentials are
configured. If GGA upload is enabled, the app may send the receiver-derived
rover position to the selected caster for NTRIP/VRS operation.

Many NTRIP casters use ordinary TCP. In that mode credentials, source-upload
authentication and optional GGA data are not protected by TLS. Do not treat
NTRIP data as encrypted in transit unless a future release explicitly implements
and uses TLS transport for the selected caster endpoint.

RtkCollector does not operate an app backend service for sessions, credentials
or telemetry.

## Sharing And Export

Users may explicitly share session ZIPs, session files or settings backup JSON
files through Android's share sheet. Settings backup export can optionally
include plaintext NTRIP passwords. This option is off by default and should be
used only when transferring settings to a trusted device.

Temporary share files are written to app cache and should be removed by the app
after sharing on a best-effort basis. Recipients selected in Android's share
sheet are outside RtkCollector's control.

## Deletion And Retention

Users control retention by deleting sessions, archives and settings inside the
app or through Android storage tools. Removing the app removes app-private
storage on normal Android installations. Files stored in user-selected external
locations remain under the user's control.

## Permissions

RtkCollector uses:

- USB host access to communicate with GNSS receivers;
- Internet access for NTRIP caster connections;
- foreground service and wake lock access to keep recording active while the
  screen is off;
- notification permission on Android versions that require it for foreground
  service notifications.

The app does not request Android location permission for receiver-derived GNSS
positions from an external USB receiver.

## Contact

Report privacy or security concerns through the repository security process in
`SECURITY.md`.
