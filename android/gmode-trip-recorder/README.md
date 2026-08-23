# GMODE Trip Recorder for Android

Private Android application for offline-first GPS and Samsung phone telemetry. Recording never depends on Home Assistant connectivity: points are written to Room first and synchronized in authenticated, idempotent batches whenever a network becomes available.

## Current capabilities

- Full-screen landscape automotive cockpit rebuilt from lossless pieces cut directly from the authoritative 1280×592 reference image. Its original canopy, leather panels, seams, gauge artwork, footer and proportions are preserved instead of approximated with canvas shapes. The live trip-type scene now covers the complete gauge aperture so the reference image's baked-in mountain/UTV scene cannot show through. Live clock, gauge text, editable side controls, and corner indicators are drawn over the source artwork. The grid scales uniformly and is symmetrically letterboxed when necessary, so every phone preserves the same geometry and circular gauge.
- Live corner indicators report Wi-Fi/internet validation, GPS fix and satellite count, Bluetooth state, Home Assistant availability, queued uploads, phone battery temperature/charge/percentage, recording state, trip type, and elapsed trip time. Theme and settings icons remain functional controls.
- All three left and three right dashboard buttons are user-configurable. Each has editable text, a choice of dashboard icons or the target app's real icon, and can open any launchable app installed on the phone or run a built-in GMODE action. New installs default to Spotify, Navi/Maps, and Camera on the left, with Trip Type, Start, and Stop on the right. Spotify, Maps, and Camera resolve to their installed phone apps and use safe phone-function fallbacks when unavailable. Older untouched factory layouts migrate automatically while customized buttons are preserved.
- User-selectable Reference Red, GMODE Orange, Electric Blue, Trail Green, Water Cyan, and Snow White dashboard themes, with an optional custom `#RRGGBB` accent color. Reference Red is the first-install default.
- Four independent vehicle libraries automatically switch with the trip type and remember the user's choice for each category. Street includes Car, Truck/4x4, Street motorcycle, and a playful Clown car; Off Road includes Dirt bike, SxS, Quad ATV, Three-wheeler, Truck/4x4, Sand rail, Trophy truck, and a funny extreme Unicycle; Snow includes Snowmobile, Snow bike, Snowcat, and Tracked SxS; Water includes Boat, Sea-Doo, Hovercraft, Kayak, and Mini jet boat. All 20 profiles have dedicated side, front, and rear artwork. Off Road can use either the clear mountain-free dirt scene or the selectable Sand dunes scene, Water uses uninterrupted open water with no mountain shoreline, and Street and Snow use their matching scenes. Enable and order any number of the 14 gauges, then use the dashboard footer arrows to cycle through every selected instrument.
- Automatic vehicle perspective assumes the S24 is mounted in landscape with the back of the phone facing forward: pitch or pitch-dominant motion shows the side view, while roll or roll-dominant motion shows the rear view. The user can instead lock the gauge to Side, Front, or Rear at any time.
- Stationary pitch/roll level calibration works without starting a trip. It waits for the phone mount to settle, samples the S24 orientation sensors for two seconds, rejects the attempt if acceleration or rotation is detected, and saves the level-ground offsets.
- Fourteen real S24 telemetry gauges now use sensor-specific instrument faces instead of sharing an arbitrary degree scale. Speed ranges follow the trip type; distance and elevation gain expand through logical trip ranges; GPS Course uses a 360° compass rose; pitch and mirrored roll use centred ±45° scales with 5°/15° ticks and 1:1 vehicle movement; Shock peak uses 0–3 g; battery and satellites use red/amber/green operating bands; GPS accuracy uses a non-linear 100+/50/25/10/5/0 m quality scale; coordinates use a clean position pane; and Station pressure uses 850–1050 hPa. Trip time is a 60-minute stopwatch, while GPS altitude is explicitly identified as WGS84. Pitch, roll, Shock peak, Station pressure, and battery stay live while the foreground dashboard is open, even before a trip starts. Unavailable readings remain visibly blank instead of being simulated.

- Explicit Start Trip / Stop Trip workflow with Street, Off road, Snow, and Water classifications.
- Opt-in hybrid automatic recording: choose the currently connected home Wi-Fi or enter its SSID, then use Wi-Fi departure plus the saved GPS home zone to confirm that the phone has actually left home. A Wi-Fi/router interruption while GPS remains inside the home radius does not start a trip, and the GPS geofence remains the fallback when Wi-Fi status is unavailable.
- Adjustable home radius (100–5,000 m), Wi-Fi departure confirmation delay (1–30 min), return delay (1–120 min), GPS interval (2–300 sec), minimum movement (1–500 m), and automatic trip type.
- Foreground high-accuracy GPS recording using the user-selected interval and movement threshold (defaults: 5 seconds or 5 metres).
- GPS accuracy, altitude, vertical accuracy, speed, bearing, and satellites used in the fix.
- Samsung S24 barometer, linear-acceleration, and gyroscope summaries attached to each GPS point.
- Battery percentage, charging state, and Wi-Fi/cellular/offline state.
- App-private Room database that remains authoritative while disconnected.
- User-selected export of any locally retained trip through Android's standard Save dialog. GPX supports navigation/trail apps, timestamped KML supports Google Earth, GeoJSON supports map/GIS tools, and CSV preserves every recorded telemetry field for spreadsheets.
- Home Assistant upload through WorkManager with a network constraint, exponential retry, 500-point batches, and stable point IDs.
- Home Assistant token encrypted with an AES-GCM key held by Android Keystore.
- Active-trip distance, duration, speed, accuracy, point count, and pending-sync count.

Automatic recording is disabled by default. When enabled, the app registers a low-power Android home geofence and a Wi-Fi network callback. Leaving the selected home Wi-Fi starts the configurable confirmation delay; GPS must then place the phone beyond the home radius plus the reported GPS uncertainty. A confirmed GPS geofence exit can start immediately when the phone is no longer on home Wi-Fi. The monitors are restored after reboot or an app upgrade. Android cannot deliver automatic departures while the user has force-stopped the app; reopen it once to re-arm. Pending uploads remain durable across normal process shutdowns, lost cellular service, and reboots through WorkManager.

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
5. For manual recording, choose a trip type and press **Start trip**. The persistent notification confirms recording.
6. For automatic recording, stand at home, press **Use current location**, then press **Use current Wi-Fi**. To switch networks first, press **Choose Wi-Fi in Android**; the selected connected network is captured when the Android panel closes. You can also type a known SSID directly. Enable **Start when I leave home**, adjust the distance/time fields, and press **Save auto settings**.
7. When Android opens the app settings, choose **Permissions > Location > Allow all the time**. Return to the app and press **Save auto settings** again. The status must say **Armed** before relying on automatic departure recording.
8. Under **Appearance**, select a dashboard theme. Optionally enter a custom six-digit color such as `#D946EF`, then press **Save + Apply Theme**.
9. Under **Left + right dashboard buttons**, edit each button's text, choose an icon, and select either a GMODE action or an installed app under **Opens**. Press **Save + Apply Side Buttons**.
10. Under **Cockpit layout**, choose a vehicle for every trip type, select the Off Road dirt or Sand dunes scene, and enable at least one gauge from the catalog with no upper limit. Arrange the gauges in the desired arrow-navigation order. Select **Automatic — phone sensors** for side during pitch and rear during roll, or lock a fixed view. Mount the phone in landscape with its back facing forward. To set level, park on flat ground, stop completely, leave the phone in its normal mount, and press **Calibrate Pitch + Roll Zero**.
11. Under **Export recorded trip**, select a locally retained trip and GPX, KML, GeoJSON, or CSV. Press **Export trip file**, then choose a phone folder or cloud drive in Android's Save dialog.

Geofence delivery is optimized for battery life, so an exit or return can be reported a short time after crossing the boundary. A 250 m home radius, 2 minute Wi-Fi departure delay, and 5 minute return delay are the defaults and are a practical starting point for driving.

For a local installation, use `http://HOME_ASSISTANT_LAN_IP:8123`. HTTP is permitted for LAN installations; use HTTPS or a trusted VPN when connecting across an untrusted network because an HTTP bearer token is not protected in transit.

## Build

Requirements:

- JDK 17
- Android SDK platform 35 and build tools 35.0.0
- `local.properties` containing the local SDK path, or Android Studio configured for the project

Build and test:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug connectedDebugAndroidTest assembleRelease
```

`connectedDebugAndroidTest` uses a running emulator/device and protects the Room parent/child upsert behavior that keeps recorded points durable. `tools/mock_ha_server.mjs` provides a local authenticated upload target for end-to-end synchronization tests without writing test trips into the production Home Assistant instance.

This private sideload build is signed with this workstation's Android debug signing key. Back up `%USERPROFILE%\.android\debug.keystore` before relying on in-place upgrades. A public release should use a dedicated, backed-up production keystore.

## Synchronization contract

`UploadWorker` sends `POST /api/gmode_trip_recorder/mobile/upload` with a Home Assistant bearer token. Each request contains one trip and up to 500 points. Home Assistant acknowledges point IDs, and the app marks only those IDs synchronized. Retrying a request is safe.

Locally saved rows are not deleted after upload, so the phone retains a complete trip history for recovery and file export. Retention and deletion controls remain planned follow-ups.

## Privacy

The app has no analytics, advertising, crash-reporting SDK, user account, or third-party telemetry service. Location and sensor data remain on the phone and the configured Home Assistant instance. The selected home Wi-Fi name remains in app-private settings and is used only for departure detection; it is not included in exported trips or Home Assistant point uploads. The token is never written to logs or source files.
