# Dashboard Reference Specification

The supplied automotive dashboard image remains the authoritative visual geometry for GMODE v2. Live content replaces only the display/control information that must respond to sensors, settings, installed apps, or trip state.

## Rendering contract

- Native design grid: `1280 x 592` (`2.1622:1`).
- Scale mode: one uniform `FIT_CENTER` transform. X and Y are never stretched independently.
- Extra screen area is filled with black/leather letterboxing.
- Touches are transformed back into the native grid before hit testing.
- Lossless reference pieces preserve the canopy, left/centre/right middle panels, footer, leather, seams, and bezel.
- The live scene covers the complete central aperture; no baked-in default mountain scene may show through.

## Primary geometry

| Region | Native coordinates |
| --- | --- |
| Top canopy | `x 70-1209`, `y 0-98` |
| Central gauge | centre `(640, 278)`, bezel radius about `212` |
| Left labels | `x 36-307` |
| Left icon panel | `x 307-428` |
| Right icon panel | `x 852-972` |
| Right labels | `x 972-1244` |
| Footer | `x 69-1210`, `y 466-592` |
| Previous/next arrows | near `(464, 517)` and `(816, 517)` |

## Radial side-icon centres

The icon panels remain rectangular and their touch targets do not move, but icon artwork follows the circular gauge arc:

| Slot | Centre |
| --- | --- |
| Left top | `(398, 155)` |
| Left middle | `(368, 276)` |
| Left bottom | `(398, 397)` |
| Right top | `(897, 155)` |
| Right middle | `(927, 276)` |
| Right bottom | `(897, 397)` |

Top/bottom icons are 15 px inward from the former grid; middle icons are 15 px outward. This creates an obvious radial relationship without compromising the reference label panels.

## Live central gauge

- Thirteen selectable gauge definitions share the same aperture.
- All selected instruments are reachable with wrapping footer navigation; there is no count limit.
- Outer decorative ticks are covered and rebuilt from the same `GaugeScaleSpec` as inner labels and needle/progress.
- Information faces such as Coordinates intentionally have no artificial dial ticks.

## 3D Attitude face

- Scene/vehicle pairs: Street/Truck, dirt Off road/SxS, Sand dunes/Sand rail, Snow/Snowmobile, Water/Mini jet boat.
- Camera begins high and behind, facing forward as the driver does.
- The phone back faces forward. Mirrored roll drives both vehicle chassis and the theme-coloured horizontal line in the same screen direction.
- The current line is full strength behind the vehicle. Earlier positions fade by age to visualize rotation history; opacity does not fade merely toward the line ends.
- Roll ticks occupy the left/right arcs. Current course and reciprocal course occupy the top/bottom arcs at the same radial hierarchy.
- GPS course is used at/above 5 km/h; live magnetic course is used when slower, with shortest-path north-wrap smoothing.
- Absolute pitch/roll at caution makes the full outer bezel solid orange. Limit makes it red with a pulsing glow.

## Live indicators and controls

The cleared source regions are redrawn from real state: clock, Wi-Fi, GPS/satellites, Bluetooth, network/Home Assistant, battery temperature, recording/trip type/time, charge/percentage, theme, settings, gauge title/value/subtitle, and all six labels/icons. Disabled/unavailable state is dimmed rather than simulated.

## Accessibility and interaction

- Large side panels retain their original touch regions even though icon artwork is radial.
- Footer arrows have descriptive content labels and wrap through the enabled gauges.
- The cockpit root publishes a changing content description with vehicle, trip, GPS, and Home Assistant state.
- Configuration remains accessible on a scrollable settings screen.
