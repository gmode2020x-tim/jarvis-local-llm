# Security

## Supported version

Security fixes are applied to the current 2.x release line.

## Reporting

Open a GitHub issue for non-sensitive problems. Do not paste a Home Assistant token, private URL, precise route, home coordinates, signing material, or exported telemetry into a public issue. For a sensitive report, first open a minimal issue asking for a private contact channel.

## Security design

- A Home Assistant access token is encrypted using AES-GCM and a non-exportable Android Keystore key.
- The app database, preferences, and automatic-home configuration are app-private and excluded from Android backup/data extraction.
- Components that receive geofence, Wi-Fi, boot, or service events are not exported.
- Home Assistant uploads require bearer authentication and acknowledge stable point IDs for idempotent retry.
- No analytics, ads, third-party crash reporting, or GMODE cloud endpoint is included.
- Production upload keys and `keystore.properties` are ignored by Git. Release signing material must be backed up privately.

## Operator responsibilities

Use HTTPS or a trusted VPN for Home Assistant traffic outside a trusted LAN. Rotate a Home Assistant token if a phone, backup, or server is compromised. Keep Google Play Console access protected with multi-factor authentication. Verify release checksums before distributing a sideload APK.
