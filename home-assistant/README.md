# Home Assistant mobile trip ingestion

The versioned custom component mirrors the live `gmode_trip_recorder` integration and adds the authenticated endpoint used by the Android app:

```text
POST /api/gmode_trip_recorder/mobile/upload
Authorization: Bearer HOME_ASSISTANT_LONG_LIVED_ACCESS_TOKEN
```

The endpoint requires Home Assistant authentication, accepts protocol version 1, validates coordinates and telemetry ranges, limits requests to 500 points, and merges trips using deterministic server IDs. Stable client point IDs make retries idempotent. State writes use a temporary file followed by an atomic replacement.

Integration 1.3.0 also adds an authenticated two-way diagnostics channel:

```text
POST /api/gmode_trip_recorder/mobile/diagnostics
Authorization: Bearer HOME_ASSISTANT_LONG_LIVED_ACCESS_TOKEN
```

Every heartbeat updates `sensor.gmode_mobile_status` and `sensor.gmode_mobile_log`; the latter retains at most 100 deduplicated phone events. `sensor.gmode_mobile_control` exposes the current revisioned HA-to-phone payload. The `gmode_trip_recorder.set_mobile_control` action can publish a notice, app update URL/hash, bounded recording settings, or the safe `sync`/`rearm` commands. `gmode_trip_recorder.clear_mobile_logs` clears retained events without deleting the latest heartbeat. The app acknowledges commands by ID. It never silently installs an APK and does not accept arbitrary executable commands.

Extended point fields include:

- `pressure_hpa`
- `acceleration_rms_ms2`
- `acceleration_peak_ms2`
- `gyroscope_peak_rads`
- `bearing`
- `battery_percent`
- `is_charging`
- `network_type`
- `satellite_count`

Existing map consumers continue to use `lat`, `lon`, `altitude`, `accuracy`, `vertical_accuracy`, `speed`, and `at`.

Set `automatic_tracking: false` only when the Android app should be the sole trip recorder. Leave it `true` while comparing Home Assistant's tracker route against the phone app; records are labelled by source so the two paths remain distinguishable. The snapshot and map APIs remain available in either mode, and authenticated Android uploads continue normally. Historical trips remain in the state file.

Private entity IDs, LAN addresses, and route coordinates are configuration only; the component contains no household-specific defaults. Start from [`configuration.example.yaml`](configuration.example.yaml). `route_places` controls friendly route grouping, and `off_road_reference_place` optionally supplies a local area used by slow-trip auto-classification.

## Deploy

The live configuration is usually mapped to `Z:\`. Back up the live component, copy the versioned files, run HA Core's configuration check through Proxmox VM 100, and restart only after the check succeeds:

```powershell
.\scripts\deploy_gmode_trip_recorder.ps1 -ProxmoxHost PROXMOX_LAN_IP
```

The script does not create an access token. Create the phone's token from the Home Assistant user profile and save it only inside the Android app.

## Test

Run the ingestion unit tests without a Home Assistant development environment:

```powershell
python -m unittest discover -s home-assistant\tests -v
```

After deployment, unauthenticated probes for both mobile routes must return `401`, not `404`:

```powershell
$payload = '{"protocolVersion":1,"deviceId":"probe","trip":{"id":"probe","status":"active"},"points":[]}'
Invoke-WebRequest -Method Post -Uri 'http://HOME_ASSISTANT:8123/api/gmode_trip_recorder/mobile/upload' -ContentType 'application/json' -Body $payload -SkipHttpErrorCheck
Invoke-WebRequest -Method Post -Uri 'http://HOME_ASSISTANT:8123/api/gmode_trip_recorder/mobile/diagnostics' -ContentType 'application/json' -Body '{}' -SkipHttpErrorCheck
```
