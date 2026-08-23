# Gauges and Sensors

The app exposes 13 selectable instruments. Outer ticks, inner labels, zones, and the needle/progress calculation use the same scale specification so they stay aligned.

| Gauge | Source | Scale and behaviour |
| --- | --- | --- |
| Speed | GPS speed | Street 0-200, Snow 0-160, Water 0-100, Off road 0-120 km/h; 20 km/h major ticks, 10 minor. |
| Trip time | Active trip clock | 0-60 minute stopwatch dial; wraps each hour while text shows total h:mm. |
| Distance | GPS point path | Expands through 10/25/50/100/250/500 km and larger 500 km multiples. |
| GPS altitude | WGS84 GPS altitude | -100 m to next 1,000/2,000/5,000/10,000 m threshold. It is not map/terrain elevation. |
| Elevation gain | Positive filtered GPS altitude deltas | Expands through 100/250/500/1,000/2,000 m and larger 2,000 m multiples. |
| GPS course | GPS bearing or magnetic rotation vector | Full 360 degree compass, cardinal labels every 45 degrees, ticks every 5 degrees. GPS is preferred at 5 km/h or faster; magnetic heading otherwise. |
| 3D pitch + roll | S24 rotation-vector sensor plus saved zero | +/-45 degree roll/pitch scale, 15 degree labels, 5 degree ticks; dynamic 3D vehicle, attitude line/trail, course arcs, user caution/limit bezel. |
| Shock peak | Linear acceleration | 0-3 g, 0.5 g labels, 0.25 g minor ticks; 2-2.5 g caution, 2.5-3 g danger. This is a phone-sensor impact estimate, not certified vehicle instrumentation. |
| Phone battery | Android battery service | 0-100%; below 15 danger, 15-30 caution, 30-100 good. |
| GPS satellites | Android GNSS status | 0-30 used in fix; 0-4 danger, 4-8 caution, 8-30 good. |
| GPS accuracy | GPS horizontal uncertainty | Nonlinear quality scale 100+/50/25/10/5/0 m; smaller is better. |
| Coordinates | GPS position | Decimal latitude/longitude information pane; no artificial dial scale. |
| Station pressure | S24 barometer | 850-1,050 hPa, 50 hPa labels, 10 hPa minor ticks. It is station pressure, not sea-level corrected weather pressure. |

## Recorded telemetry

Each accepted GPS point can include timestamp, latitude, longitude, horizontal accuracy, altitude, vertical accuracy, speed, bearing, satellite count, pressure, acceleration RMS/peak, gyroscope peak, battery percentage, charging state, and network type. Unavailable hardware/readings stay blank; the app does not invent values.

## Attitude conventions

The back of the landscape phone faces forward. User calibration offsets are subtracted from pitch; roll is mirrored as `saved zero - sensor roll` so the vehicle and horizontal line move together in the driver's screen view. The warning state uses the larger absolute value of pitch or roll. This display is a situational aid, not a substitute for vehicle stability controls or safe driving judgment.

## Sensor availability

The Galaxy S24 normally provides rotation vector, linear acceleration/accelerometer, gyroscope, magnetometer support through rotation vector, barometer, fused GPS, and GNSS status. Android may withhold, batch, or lower the precision of readings based on permissions, battery policy, hardware state, or signal conditions.
