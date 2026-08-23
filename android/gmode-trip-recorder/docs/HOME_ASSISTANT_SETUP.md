# Home Assistant Setup

## Install the integration

Copy this repository's `home-assistant/custom_components/gmode_trip_recorder` folder to Home Assistant:

```text
/config/custom_components/gmode_trip_recorder
```

Restart Home Assistant, then add/configure the GMODE Trip Recorder integration according to the component README. Confirm Home Assistant is reachable from the phone before entering credentials.

## Create a token

In Home Assistant, open the user's profile and create a **Long-Lived Access Token**. Copy it immediately; Home Assistant does not show it again. Treat it like a password.

## Connect the app

1. In GMODE settings open **System > Home Assistant connection**.
2. Enter the base URL, for example `http://192.168.1.10:8123` on a trusted LAN or an HTTPS/VPN URL remotely.
3. Paste the long-lived token and press **Save connection**.
4. Press **Sync now**.
5. Read the status below the buttons. **Up to date** means no unsynchronized local points remain.

## API contract

The app sends authenticated JSON to:

```text
POST /api/gmode_trip_recorder/mobile/upload
Authorization: Bearer HOME_ASSISTANT_TOKEN
```

One request carries one trip and at most 500 points. Stable trip/point IDs and the `acknowledgedPointIds` response make retries idempotent. The phone retains acknowledged data locally.

## Troubleshooting

| Status/problem | Check |
| --- | --- |
| Setup required | Save both a complete URL and token. Blank token input keeps an existing saved token. |
| Waiting for connection | Verify phone network/VPN, HA reachability, DNS/IP, port 8123, and TLS certificate. |
| HTTP 401/403 | Create a new token for an authorized HA user and save it. |
| HTTP 404 | Confirm the custom integration is installed/restarted and the mobile upload route exists. |
| HTTP 5xx/408/429 | WorkManager retries automatically; inspect Home Assistant logs/resources. |
| Points remain pending | Keep Android network enabled, make the app battery-unrestricted, press Sync now, and check the detailed status. |
| LAN hostname fails | Try the HA LAN IP. Some Android/DNS networks do not resolve `.local` names reliably. |

Never publish the token in screenshots, logs, source code, or support issues. Rotate it if exposed.
