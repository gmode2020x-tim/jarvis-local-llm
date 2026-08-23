# Google Play Submission Checklist

This document separates completed repository work from manual Play Console actions. It does not claim that Google has reviewed or published the app.

## Build and signing

- [x] Package: `ca.gmode.triprecorder`
- [x] Version name/code: `2.0.0` / `20000`
- [x] Minimum SDK: 29 (Android 10)
- [x] Compile/target SDK: 36 (Android 16)
- [x] Release minification and resource shrinking enabled
- [x] Dedicated ignored upload-keystore support
- [x] Public upload certificate exported as `play-store/gmode-upload-certificate.pem`
- [ ] In Play Console, enroll in mandatory Play App Signing and register the upload certificate
- [ ] Upload `GMODE-Trip-Recorder-v2.0.0-play.aab` to an internal test track first

The GitHub sideload APK intentionally has the previous debug signature for in-place v1 upgrades. Never upload the sideload APK to Play.

## Store presence

- [x] Listing copy and URLs prepared in [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md)
- [x] 512 x 512 app icon
- [x] 1024 x 500 feature graphic
- [x] At least four real 1920 x 1080 landscape app screenshots
- [x] Public privacy policy and in-app privacy access
- [ ] Enter contact email and any optional website in Play Console

## App content declarations

- **Ads:** No.
- **App access:** All primary app functions are available without an account. Home Assistant sync is optional and requires the user's own server/token.
- **Target audience:** Adults/general utility; not designed for children.
- **News:** No.
- **Government:** No.
- **Health:** No.
- **Financial features:** None.
- **Content rating:** Utility/navigation app; no user-generated content, gambling, violence, or sexual content.

## Data safety draft

Review these answers against the exact current Play Console wording before submission:

- Data collected/shared with a GMODE developer service: **No**. There is no GMODE server, advertising, analytics, or account backend.
- Precise location, device/sensor telemetry, trip names/types, and a random device ID are processed and stored on-device.
- At the user's direction, trip/location/telemetry is transmitted to the user's own Home Assistant server or exported destination. Declare this consistently with Play's current definition of collection and user-initiated transfer.
- Data is not sold and is not used for advertising, profiling, credit, or eligibility.
- Data is encrypted in transit only when the user configures HTTPS/VPN; the app also permits trusted-LAN HTTP. Do not claim universal encryption in transit.
- Users can delete phone-held data by clearing app storage/uninstalling and must separately delete Home Assistant/exported copies.

## Background location declaration

Core feature wording:

> GMODE Trip Recorder uses background precise location only when the user enables automatic trip recording. It detects departure from and return to the saved home area and records the route while the app is closed or not in use. Automatic recording is disabled by default and can be turned off in app settings.

Google recommends a video of 30 seconds or less. It must show:

1. Opening GMODE settings.
2. The automatic recording section and disabled-by-default switch.
3. Capturing home location/Wi-Fi and enabling automatic recording.
4. The prominent **Location data** disclosure before the permission request/settings handoff.
5. Android **Allow all the time** location selection.
6. Returning to the app and the armed automatic-departure status.
7. The app's visible result when an automatic trip is activated while the app is not in use (for example, the recording notification followed by the active trip dashboard).

Use an unlisted, stable video URL without exposing a real home coordinate, SSID, or token. Complete the Play Console background location declaration and link the public privacy policy.

## Foreground service declaration

Because the app targets Android 14 or newer and declares a `location` foreground service, complete **App content > Foreground service permissions** in Play Console.

- Function: user-started continuous trip recording with an accurate, persistent recording notification and an in-app Stop control.
- Why immediate: deferring start would omit the beginning of the route the user explicitly asked to record.
- Impact if interrupted: route points and telemetry would be missing until Android restarts the service; locally saved points remain safe.
- Video: show pressing Start, granting the prompted permissions, the persistent recording notification, the live timer, and pressing Stop.

## Upcoming precise-location declaration

Google announced a separate minimum-scope declaration for `ACCESS_FINE_LOCATION`, expected to appear in Play Console in November 2026 with enforcement in 2027. GMODE's justification is continuous live route measurement: coarse location or a one-time location button cannot provide the frequent, accurate route points, home-boundary decisions, speed/bearing, or trip-distance calculations that the user requests. Re-check the live Console wording before the enforcement date.

## Testing tracks

1. Internal test: install from Play, verify clean install, permissions, manual trip, background departure, export, and HA sync.
2. Closed test if required by the account: meet Google's current tester/duration rules shown in Play Console.
3. Production: use staged rollout, monitor Android vitals, and retain v2.0.0 artifacts/checksums.

## Reviewer notes draft

GMODE Trip Recorder works without login. Press START for a manual trip. Background location is used only by the optional START WHEN I LEAVE HOME feature under Settings > Automatic recording. The feature requires a saved current location and Android Allow all the time permission. Home Assistant sync is optional and cannot be tested without a reviewer-owned server/token; recording and all local dashboard/export functions remain usable without it.

## Release notes

New v2 cockpit with scene-matched 3D vehicles, combined pitch/roll attitude, radial course, configurable stability alerts, 13 logical gauges, editable installed-app controls, automatic GPS + Wi-Fi home departure recording, four trip export formats, and offline Home Assistant catch-up.
