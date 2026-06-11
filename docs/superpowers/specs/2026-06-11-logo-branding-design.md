# RtkCollector Logo Branding Design

## Goal

Integrate the user-provided RtkCollector logo into the Android app without
changing the GNSS recording workflow or adding decorative clutter to the field
dashboard.

The source logo lives in `logos/` and is a wide 1354 x 527 wordmark with a
strong satellite/pin symbol on the left. The app needs derived assets for
Android launcher surfaces and compact rectangular uses.

## Asset Strategy

The implementation will derive three asset classes from the provided logo:

1. Full wordmark asset.
   Used only where there is enough horizontal space, such as about/settings
   surfaces or documentation. It should not consume space on the compact live
   monitoring dashboard.

2. Square launcher/adaptive icon asset.
   Cropped from the left satellite/pin symbol, not from the full wordmark. This
   is the primary Android launcher identity and may also be used for compact app
   surfaces.

3. Rectangular badge asset.
   A compact rectangular version based on the full mark, suitable for share,
   export, documentation, splash-like surfaces and places where the original
   very-wide logo is awkward.

The square icon and rectangular badge should be generated from the checked-in
source logo so future updates are reproducible.

## Android Integration

Android launcher resources should use standard app icon conventions:

- adaptive icon foreground/background resources where supported;
- density-appropriate raster fallbacks if the build setup needs them;
- manifest `android:icon` and `android:roundIcon` pointing to the new icon
  resources.

The app must remain buildable on normal Android Studio/CI environments. Termux
native Android packaging limitations are not treated as source failures.

## UI Usage

The live field dashboard remains data-first. Branding should be restrained:

- launcher icon: always uses the square symbol asset;
- app/header branding: optional compact symbol plus wordmark, only where it
  does not reduce monitoring clarity;
- settings/about/share/export surfaces: may use the rectangular badge;
- notification surfaces: use the square icon where Android permits app icons.

No map, GIS, shapefile or cartographic branding should be introduced.

## Implementation Boundaries

This task does not change:

- recording workflows;
- USB, NTRIP or receiver command behaviour;
- session file formats;
- telemetry parsing;
- dashboard data semantics.

It only adds brand assets and wires Android icon metadata.

## Validation

Implementation should verify:

- generated assets exist in predictable app resource paths;
- manifest references valid resources;
- launcher/icon resources compile in the feasible local or CI environment;
- no local-only visual companion or sample files are committed;
- original user-provided logo sources remain available for regeneration.
