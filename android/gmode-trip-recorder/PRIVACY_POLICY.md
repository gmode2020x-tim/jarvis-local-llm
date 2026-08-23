# GMODE Trip Recorder Privacy Policy

Effective date: August 23, 2026

GMODE Trip Recorder is a local-first GPS and vehicle telemetry application. It has no advertising, analytics, account system, crash-reporting SDK, or GMODE-operated cloud service. It does not sell personal data.

## Data the app handles

When the user starts a trip or enables automatic trip recording, the app may process:

- precise location, route points, timestamps, GPS accuracy, altitude, vertical accuracy, speed, bearing, and satellite count;
- phone motion/orientation summaries from the rotation vector, accelerometer/linear acceleration, and gyroscope;
- barometric pressure when the device provides a pressure sensor;
- battery percentage, charging state, and Wi-Fi/cellular/offline network type;
- the home latitude/longitude and optional home Wi-Fi network name selected by the user;
- trip names, trip types, app preferences, and a randomly generated device identifier;
- a Home Assistant URL and access token supplied by the user.

## Background location

Automatic trip recording is optional and disabled by default. If enabled, GMODE collects precise location in the background to detect when the phone leaves or returns to the saved home area and to record the route, even when the app is closed or not in use. Android's **Allow all the time** location setting is required for that feature. Manual foreground recording does not require automatic recording to be enabled.

## Storage and sharing

Trip data is written first to an app-private Room database on the device. It remains there until Android app data is cleared or the app is uninstalled. The app does not send data to GMODE or an advertising/analytics provider.

Data leaves the device only when the user:

1. configures a Home Assistant server, in which case recorded trip points and telemetry are sent to that server; or
2. exports a trip, in which case Android saves GPX, KML, GeoJSON, or CSV data to a destination explicitly chosen by the user.

The saved home Wi-Fi name is used only for local departure detection and is not included in trip exports or point uploads. The Home Assistant access token is encrypted with an AES-GCM key stored in Android Keystore. An HTTP Home Assistant URL does not encrypt the token in transit; HTTPS or a trusted VPN is recommended outside a trusted LAN.

## Retention and deletion

The phone retains locally recorded trips after successful synchronization so they remain available for export. To erase all app-held data, use Android **Settings > Apps > GMODE Trip Recorder > Storage > Clear data**, or uninstall the app. Data already sent to Home Assistant or exported to another destination must be deleted from that destination separately.

## Permissions

- **Precise/approximate location:** route recording, home-zone detection, and Android Wi-Fi SSID access.
- **Background location:** optional automatic departure/return recording.
- **Notifications:** persistent foreground-service status while a trip is recording.
- **Nearby devices/Bluetooth:** displays live Bluetooth status when the user enables the indicator permission.
- **Network access and network/Wi-Fi state:** Home Assistant synchronization and hybrid home detection.
- **Foreground service, wake lock, and boot completed:** durable recording and re-arming the user's automatic recording configuration after reboot/update.

## Children

The app is a vehicle/trip utility and is not designed for children. It does not knowingly collect information through an account or GMODE service.

## Changes and contact

Policy changes will be published in this repository and identified by a new effective date. Questions or security reports can be opened through the repository's public issue tracker without including location data, access tokens, or other secrets:

https://github.com/gmode2020x-tim/jarvis-local-llm/issues
