# GMODE Trip Recorder 2.0

GMODE Trip Recorder is an offline-first Android GPS and telemetry recorder designed for a landscape-mounted phone. It records to a local Room database first, keeps working without Home Assistant, exports standard trip files, and retries authenticated Home Assistant uploads when connectivity returns.

The v2 cockpit combines a scene-matched procedural 3D vehicle, live pitch and roll, GPS or magnetic course, warning limits, configurable gauges, six app-launch buttons, and live phone/network indicators. The 1280 x 592 design scales uniformly on different displays so the gauge stays circular and the dashboard geometry remains unchanged.

![GMODE cockpit](play-store/screenshots/01-attitude-dashboard.png)

## Highlights

- Manual or opt-in automatic trip recording with Street, Off road, Snow, and Water classifications.
- Hybrid home detection using the saved GPS zone and optional current Wi-Fi SSID.
- Local recording of GPS, accuracy, altitude, vertical accuracy, speed, bearing, GNSS satellites, barometer, motion summaries, battery, charging state, and network type.
- Five scene-matched 3D vehicles: Truck, SxS, Sand rail, Snowmobile, and Mini jet boat.
- Thirteen selectable gauges with logical, sensor-specific scales and unlimited arrow navigation.
- Six editable side buttons that can run GMODE actions or launch installed Android apps.
- Reference Red plus five preset themes and a custom RGB accent.
- Stationary pitch/roll zero calibration and adjustable caution/limit thresholds.
- GPX, KML, GeoJSON, and full-telemetry CSV export through Android's document picker.
- Durable Home Assistant uploads in idempotent 500-point batches with network-constrained retry.
- No advertising, analytics, account system, or GMODE cloud service.

## Documentation

- [User guide](docs/USER_GUIDE.md)
- [Complete settings reference](docs/SETTINGS_REFERENCE.md)
- [Gauges and sensors](docs/GAUGES_AND_SENSORS.md)
- [Home Assistant setup](docs/HOME_ASSISTANT_SETUP.md)
- [Architecture and data flow](docs/ARCHITECTURE.md)
- [Privacy policy](PRIVACY_POLICY.md)
- [Google Play listing copy](docs/PLAY_STORE_LISTING.md)
- [Google Play submission checklist](docs/PLAY_STORE_SUBMISSION.md)
- [Dashboard geometry reference](DASHBOARD_REFERENCE.md)
- [Changelog](CHANGELOG.md)
- [Security policy](SECURITY.md)

## Install

The GitHub release has two deliberately different artifacts:

- `GMODE-Trip-Recorder-v2.0.0-sideload.apk` is signed with the existing sideload/debug lineage so it can update the previous GitHub APK without erasing local trips or settings.
- `GMODE-Trip-Recorder-v2.0.0-play.aab` is signed with the private upload key and is intended only for Google Play Console. Google Play App Signing supplies the distribution signature.

On the S24, download the sideload APK, open it, approve installation from the browser/file app if prompted, and choose **Update**. Do not uninstall an older version unless its app-private trips and settings are no longer needed.

## First run

1. Press **Start** and review the prominent location disclosure, then allow precise location. Notifications are requested separately when recording begins.
2. In **Settings > System > S24 battery settings**, make the app unrestricted and remove it from Samsung sleeping/deep-sleeping app lists.
3. Record manually, or configure the automatic home zone as described in the [user guide](docs/USER_GUIDE.md).
4. To sync, install the companion Home Assistant integration and enter the server URL plus a Home Assistant long-lived access token.
5. Use **Privacy + data use** at any time to review the in-app summary and open the full policy.

## Build requirements

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Android Gradle Plugin 8.13.2 and Gradle 8.13 (declared by the project)
- `local.properties` with `sdk.dir=...`, or Android Studio with the SDK configured

Debug verification:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug connectedDebugAndroidTest assembleDebug
```

Create the upgrade-compatible sideload APK:

```powershell
.\gradlew.bat assembleSideload
```

Create the Play upload bundle after copying `keystore.properties.example` to the ignored `keystore.properties` and supplying an upload keystore:

```powershell
.\gradlew.bat bundleRelease
```

Never commit `keystore.properties`, a `.jks` file, a Home Assistant token, or generated private trip data.

## Home Assistant upload contract

`UploadWorker` sends `POST /api/gmode_trip_recorder/mobile/upload` with the user's bearer token. Each request contains one trip and up to 500 points. The integration returns acknowledged point IDs; the phone marks only those IDs synchronized. Stable IDs make retries safe. Local rows are retained after upload so the phone remains a complete export source.

## Supported Android versions

The app supports Android 10 (API 29) and newer, targets Android 16/API 36 for Google Play, and is optimized for landscape phone use. The primary hardware target is Samsung Galaxy S24.
