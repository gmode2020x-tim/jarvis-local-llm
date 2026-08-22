# Dashboard reference specification

The supplied dashboard image is the authoritative visual layout and artwork source for the Android cockpit.

## Source-art rendering

- The app uses lossless PNG pieces cut directly from the supplied image rather than recreating its surfaces with approximate vector drawing.
- The five pieces are the top canopy, left middle, centre middle, right middle, and footer.
- Reassembling the pieces on the 1280 × 592 design grid is pixel-identical to the cleaned source master.
- The clock, gauge name/value, footer readout, and red corner indicators are cleared from the source artwork and redrawn from live application state.
- The original visible controls use invisible touch regions mapped to trip-recorder actions; the artwork itself is not distorted or reflowed.

## Source geometry

- Native reference canvas: `1280 × 592` pixels (`2.1622:1`).
- Rendering rule: draw on the native 1280 × 592 coordinate grid, then scale uniformly with `FIT_CENTER`.
- Never independently stretch the X and Y axes. Remaining pixels on a mismatched display use the same black leather texture as the dashboard.
- Touch coordinates are transformed back into the 1280 × 592 grid before hit testing.

## Measured regions

| Region | Reference coordinates |
| --- | --- |
| Top canopy | `x 70–1209`, `y 0–98` |
| Central gauge | centre approximately `(640, 278)`, outer radius approximately `212` |
| Left label column | `x 36–307`, rows beginning near `y 96`, `214`, and `337` |
| Left icon column | `x 307–428` |
| Right icon column | `x 852–972` |
| Right label column | `x 972–1244` |
| Footer | `x 69–1210`, `y 466–592` |
| Footer centre controls | arrows near `x 464` and `x 816`; title centred at `x 640` |

## Visual hierarchy

1. Near-black outer canvas.
2. Fine black automotive-leather grain.
3. Thin graphite frame and stitched/seamed panel dividers.
4. Curved top canopy with red status symbols and centred white time.
5. Three raised control rows per side. Each row has a wide label panel and a narrow icon panel next to the gauge.
6. One circular gauge above all adjoining panels.
7. Curved footer with trip status, gauge navigation arrows, and utility controls.

## Gauge construction

- One active gauge at a time; configured gauges are selected with the footer arrows.
- Layered black/graphite bezel with narrow silver highlights.
- White major/minor ticks with restrained red accent ticks.
- Photographic blue-sky mountain and rocky-ground interior.
- Vehicle artwork centred over the terrain.
- White gauge title and scale labels; large red live value.
- Zero/calibration scale at the bottom of pitch and roll gauges.

## Functional mapping

The reference artwork and its visible labels are retained unchanged. Their touch regions are mapped to trip-recorder operations as follows:

- `RADIO`: start recording.
- `NAVI`: cycle trip type.
- `MUSIC`: automatic-recording settings.
- `PHONE`: stop recording.
- `INTERNET`: synchronize queued data.
- `APPS`: Home Assistant settings.
- Footer centre arrows: previous/next configured gauge.
- Footer utilities: theme and settings.

## Live corner indicators

- Top left: validated Wi-Fi, GPS/satellite fix, and Bluetooth state. Tapping Bluetooth requests Nearby Devices permission when required, then opens Bluetooth settings.
- Top right: validated internet, Home Assistant state, battery temperature, and queued upload count.
- Bottom left: GPS state, recording/standby state, trip-type code, and elapsed trip time.
- Bottom right: battery percentage/charging state, theme control, and settings control.
- Active indicators use the selected theme accent. Inactive indicators use a deliberately dimmed red; unavailable Bluetooth state shows `?` rather than pretending to be off.

## Responsive acceptance criteria

- The gauge remains circular on every device.
- The relative coordinates above do not reflow or reorder.
- All controls remain inside the fitted reference canvas.
- Letterboxing is symmetrical and visually continuous with the leather dashboard.
- Tap targets follow the rendered controls after scaling and centring.
