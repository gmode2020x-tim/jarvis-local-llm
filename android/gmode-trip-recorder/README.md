# GMODE Trip Recorder for Android

Private Android application for offline-first GPS and Samsung phone telemetry. Recording never depends on Home Assistant connectivity: points are written to Room first and synchronized in authenticated, idempotent batches whenever a network becomes available.

## Current capabilities

- Automotive dashboard inspired by Tim's reference: orange-on-black instrument styling, live central speed gauge, glanceable GPS/queue/Home Assistant indicators, and large driving-friendly controls.

- Explicit Start Trip / Stop Trip workflow with Street, Off road, Snow, and Water classifications.
- Foreground high-accuracy GPS recording every 5 seconds or 5 metres.
- GPS accuracy, altitude, vertical accuracy, speed, bearing, and satellites used in the fix.
- Samsung S24 barometer, linear-acceleration, and gyroscope summaries attached to each GPS point.
- Battery percentage, charging state, and Wi-Fi/cellular/offline state.
- App-private Room database that remains authoritative while disconnected.
- Home Assistant upload through WorkManager with a network constraint, exponential retry, 500-point batches, and stable point IDs.
- Home Assistant token encrypted with an AES-GCM key held by Android Keystore.
- Active-trip distance, duration, speed, accuracy, point count, and pending-sync count.

The app deliberately does not request `ACCESS_BACKGROUND_LOCATION`. A trip must be started while the app is visible, after which Android's visible location foreground service keeps recording with an ongoing notification. Android cannot restart location collection after the user force-stops the app; reopen it and start/resume recording. Pending uploads remain durable across normal process shutdowns and reboots through WorkManager.

## Install on the Samsung S24

The current private APK is produced at:

```text
app/build/outputs/apk/release/app-release.apk
```

Transfer it to the phone, open it, and allow installation from the file-sharing app when Android prompts. Alternatively, with USB debugging enabled:

```powershell
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

On first use:

1. Allow precise location and notifications.
2. Open **Battery settings** from the app. Under Samsung's app battery settings, set GMODE Trip Recorder to **Unrestricted** and ensure it is not listed as a sleeping or deep-sleeping app.
3. In Home Assistant, open your profile, create a Long-Lived Access Token, and copy it.
4. In the app, enter the Home Assistant URL and token, then choose **Save connection**.
5. Choose a trip type and press **Start trip**. The persistent notification confirms recording.

For a local installation, use `http://HOME_ASSISTANT_LAN_IP:8123`. HTTP is permitted for LAN installations; use HTTPS or a trusted VPN when connecting across an untrusted network because an HTTP bearer token is not protected in transit.

## Build

Requirements:

- JDK 17
- Android SDK platform 35 and build tools 35.0.0
- `local.properties` containing the local SDK path, or Android Studio configured for the project

Build and test:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

Release 1 is a private sideload build signed with this workstation's Android debug signing key. Back up `%USERPROFILE%\.android\debug.keystore` before relying on in-place upgrades. A public release should use a dedicated, backed-up production keystore.

## Synchronization contract

`UploadWorker` sends `POST /api/gmode_trip_recorder/mobile/upload` with a Home Assistant bearer token. Each request contains one trip and up to 500 points. Home Assistant acknowledges point IDs, and the app marks only those IDs synchronized. Retrying a request is safe.

Locally saved rows are not deleted after upload. Release 1 therefore retains a complete phone-side trip history for recovery. Database export and retention controls are planned follow-ups.

## Privacy

The app has no analytics, advertising, crash-reporting SDK, user account, or third-party telemetry service. Location and sensor data remain on the phone and the configured Home Assistant instance. The token is never written to logs or source files.
