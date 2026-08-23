# Architecture and Data Flow

## Local-first path

1. `TrackingService` runs as a location foreground service while recording.
2. Fused location and `SensorCollector` produce GPS plus phone telemetry.
3. `RecordingRepository` writes the trip and points to Room before any network action.
4. `SyncScheduler` queues WorkManager with a network constraint.
5. `UploadWorker` sends a dirty trip in batches of up to 500 points.
6. Home Assistant acknowledges stable point IDs; only those rows are marked synchronized.
7. Failed/repeated work uses exponential retry and does not block recording or export.

## Automatic recording

`AutoRecordingManager` combines an Android home geofence with `HomeWifiReader`/network callbacks. Wi-Fi departure begins the configurable confirmation window; GPS distance plus reported uncertainty prevents a router outage inside the home zone from starting a trip. Boot and package-replaced receivers restore a previously enabled configuration. Automatic recording is user-controlled and disabled by default.

## Security boundaries

- Room, normal preferences, home location/SSID, and synchronization status are app-private.
- Backup/data extraction is disabled.
- The Home Assistant token is encrypted with AES-GCM using an Android Keystore key.
- Export uses Android's Storage Access Framework, so the user explicitly chooses each destination.
- There are no GMODE backend, ad, analytics, account, or crash-reporting dependencies.

## Dashboard

`LandscapeCockpitView` draws on a 1280 x 592 source grid and applies one uniform fit transform. Reference artwork pieces preserve the physical dashboard frame while live scenes, procedural 3D vehicles, scale ticks, status indicators, labels, and configurable controls are rendered over it. Touches are transformed back to source coordinates. Sensor/UI state is refreshed independently from the one-second trip-summary update so magnetic heading and attitude can remain smooth.

## Release variants

- `debug`: development/test signing.
- `sideload`: release-optimized code using the existing debug signature to update v1 GitHub installs.
- `release`: minified/shrunk Play upload bundle signed only when an ignored production upload configuration is present.

Google Play App Signing must own the distribution key. The locally protected key is the upload key, not a reason to disable Play App Signing.
