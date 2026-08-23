# Complete Settings Reference

## Trip and export

| Control | Function |
| --- | --- |
| Trip name | Optional title for the next manual trip; blank produces an automatic title. |
| Trip type | Street, Off road, Snow, or Water. Selects speed scale, scene, and 3D vehicle. |
| Start trip | Creates a local trip and starts the foreground location service. |
| Stop trip | Finishes the active trip and queues synchronization. |
| Exported trip | Any locally retained active or completed trip. |
| Export format | GPX, KML, GeoJSON, or CSV. |

## Automatic recording

| Setting | Default | Accepted range/values | Effect |
| --- | ---: | --- | --- |
| Start when I leave home | Off | On/off | Enables background GPS/geofence and Wi-Fi departure monitoring. |
| Home location | Not set | Current precise fix | Centre of the home zone; required for automatic recording. |
| Home Wi-Fi | Not set | Current/typed SSID, up to 64 chars | Optional hybrid departure signal; never uploaded/exported. |
| Home radius | 250 m | 100-5,000 m | GPS distance that counts as home, with location uncertainty considered. |
| Wi-Fi departure delay | 2 min | 1-30 min | Confirmation delay after leaving the chosen SSID. |
| Return delay | 5 min | 1-120 min | Required dwell inside home before an automatic trip stops. |
| GPS interval | 5 sec | 2-300 sec | Requested location update interval. Shorter uses more battery. |
| Minimum movement | 5 m | 1-500 m | Requested minimum displacement between location updates. |
| Automatic trip type | Street | Street/Off road/Snow/Water | Type assigned to automatically created trips. |

Android may batch low-power background/geofence delivery. Force-stopping the app disables automatic events until it is opened again.

## Appearance

| Setting | Values | Effect |
| --- | --- | --- |
| Theme preset | Reference Red, GMODE Orange, Electric Blue, Trail Green, Water Cyan, Snow White | Changes accent and supporting dashboard palette. |
| Custom accent | Optional `#RRGGBB` | Replaces the preset accent and derives the active surface. |
| Save + Apply Theme | - | Saves preset plus custom accent. |
| Use Preset Color | - | Clears custom accent and applies the preset's colour. |

The sun icon cycles presets directly from the dashboard.

## Side buttons

Each of six slots has:

| Field | Rules |
| --- | --- |
| Text | Required after normalization; repeated spaces collapse; maximum 18 characters. |
| Icon | Target app icon, Radio, Navigation, Music, Phone, Internet/globe, Apps grid, Start/play, Stop, Sync, Home, or Settings. |
| Opens | Any discovered launchable app or one built-in GMODE/phone action. |

Factory defaults: Left top Spotify, Left middle Navi/Maps, Left bottom Camera, Right top Trip type, Right middle Start, Right bottom Stop. Preferred real app icons are used when the matching app is installed.

## Cockpit layout

| Setting | Default | Accepted values | Effect |
| --- | ---: | --- | --- |
| Off road scene | Dirt | Dirt/rock or Sand dunes | Chooses SxS/dirt or Sand rail/sand. |
| 3D camera | Chase | Chase, Free orbit, Locked high rear | Controls touch orbit and return behaviour. |
| Caution start | 15 degrees | 5-40 degrees | Orange status/bezel when absolute pitch or roll reaches this value. |
| Limit start | 30 degrees | At least caution + 5, max 60 degrees | Red pulsing status/bezel threshold. |
| Gauge switches | Attitude, Speed, Course | At least one of 13 | Enables gauge faces. |
| Up/down arrows | Default order | Any order | Sets footer-navigation order without a count limit. |
| Calibrate zero | 0/0 until set | Stationary sample | Saves phone-mount pitch and roll offsets. |

## System

| Setting/control | Function |
| --- | --- |
| Home Assistant URL | Complete `http://` or `https://` base URL, without an API path. LAN HTTP is allowed by the app; prefer HTTPS/VPN on untrusted networks. |
| Long-lived access token | Home Assistant user token; encrypted using Android Keystore. A blank field preserves an already saved token. |
| Save connection | Validates/saves URL and nonblank replacement token, then queues sync. |
| Sync now | Queues network-constrained WorkManager upload. |
| S24 battery settings | Opens Android battery optimization settings for unrestricted operation. |
| Privacy + data use | Shows collected data, destinations, security, and the full-policy link. |

## Permissions

| Permission | When requested | Required for |
| --- | --- | --- |
| Precise/approximate location | After prominent disclosure when a location feature is first used | Route recording, home capture, Wi-Fi SSID access. Precise location is required by the app. |
| Notifications | When starting a trip on Android 13+ | Visible foreground-service recording status. Recording still starts if the prompt is declined. |
| Allow all the time location | Through Android app settings only after enabling automatic recording | Departure/return detection while the app is closed/not in use. |
| Nearby devices/Bluetooth | When the Bluetooth indicator is used on Android 12+ | Live Bluetooth state only. |
