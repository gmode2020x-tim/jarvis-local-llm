package ca.gmode.triprecorder

import kotlin.math.ceil

enum class GaugeFaceStyle {
    ANALOG,
    STOPWATCH,
    ATTITUDE_PITCH,
    ATTITUDE_ROLL,
    COURSE,
    QUALITY,
    INFO,
}

enum class GaugeZoneRole { DANGER, CAUTION, GOOD }

data class GaugeTick(val value: Double, val label: String)

data class GaugeZone(
    val startValue: Double,
    val endValue: Double,
    val role: GaugeZoneRole,
)

data class GaugeScaleSpec(
    val gaugeId: String,
    val faceStyle: GaugeFaceStyle,
    val minimum: Double,
    val maximum: Double,
    val majorTicks: List<GaugeTick>,
    val minorStep: Double? = null,
    val zones: List<GaugeZone> = emptyList(),
    val startAngle: Float = 135f,
    val sweepAngle: Float = 270f,
) {
    fun progress(value: Double?): Double? = value?.let {
        if (maximum == minimum) 0.0 else ((it - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0)
    }
}

object GaugeScaleCatalog {
    fun forGauge(gaugeId: String, value: Double?, tripType: String): GaugeScaleSpec = when (gaugeId) {
        "speed" -> analog(gaugeId, 0.0, speedMaximum(tripType), 20.0, 10.0)
        "trip_time" -> analog(gaugeId, 0.0, 60.0, 10.0, 5.0, GaugeFaceStyle.STOPWATCH)
        "distance" -> {
            val maximum = nextThreshold(value ?: 0.0, listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0))
            analog(gaugeId, 0.0, maximum, maximum / 5.0, maximum / 10.0)
        }
        "altitude" -> {
            val maximum = nextThreshold(value ?: 0.0, listOf(1_000.0, 2_000.0, 5_000.0, 10_000.0))
            val step = when {
                maximum <= 1_000.0 -> 200.0
                maximum <= 2_000.0 -> 500.0
                else -> 1_000.0
            }
            GaugeScaleSpec(gaugeId, GaugeFaceStyle.ANALOG, -100.0, maximum, ticks(-100.0, maximum, step), step / 2.0)
        }
        "elevation_gain" -> {
            val maximum = nextThreshold(value ?: 0.0, listOf(100.0, 250.0, 500.0, 1_000.0, 2_000.0))
            analog(gaugeId, 0.0, maximum, maximum / 5.0, maximum / 10.0)
        }
        "compass" -> GaugeScaleSpec(
            gaugeId,
            GaugeFaceStyle.COURSE,
            0.0,
            360.0,
            listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW").mapIndexed { index, label -> GaugeTick(index * 45.0, label) },
            minorStep = 5.0,
            startAngle = -90f,
            sweepAngle = 360f,
        )
        "pitch" -> attitude(gaugeId, GaugeFaceStyle.ATTITUDE_PITCH)
        "roll" -> attitude(gaugeId, GaugeFaceStyle.ATTITUDE_ROLL)
        "g_force" -> analog(
            gaugeId, 0.0, 3.0, 0.5, 0.25,
            zones = listOf(GaugeZone(2.0, 2.5, GaugeZoneRole.CAUTION), GaugeZone(2.5, 3.0, GaugeZoneRole.DANGER)),
        )
        "battery" -> analog(
            gaugeId, 0.0, 100.0, 20.0, 10.0,
            zones = listOf(
                GaugeZone(0.0, 15.0, GaugeZoneRole.DANGER),
                GaugeZone(15.0, 30.0, GaugeZoneRole.CAUTION),
                GaugeZone(30.0, 100.0, GaugeZoneRole.GOOD),
            ),
        )
        "gps_satellites" -> analog(
            gaugeId, 0.0, 30.0, 5.0, 1.0,
            zones = listOf(
                GaugeZone(0.0, 4.0, GaugeZoneRole.DANGER),
                GaugeZone(4.0, 8.0, GaugeZoneRole.CAUTION),
                GaugeZone(8.0, 30.0, GaugeZoneRole.GOOD),
            ),
        )
        "gps_accuracy" -> GaugeScaleSpec(
            gaugeId,
            GaugeFaceStyle.QUALITY,
            0.0,
            5.0,
            listOf("100+", "50", "25", "10", "5", "0").mapIndexed { index, label -> GaugeTick(index.toDouble(), label) },
            zones = listOf(
                GaugeZone(0.0, 2.0, GaugeZoneRole.DANGER),
                GaugeZone(2.0, 3.0, GaugeZoneRole.CAUTION),
                GaugeZone(3.0, 5.0, GaugeZoneRole.GOOD),
            ),
        )
        "coordinates" -> GaugeScaleSpec(gaugeId, GaugeFaceStyle.INFO, 0.0, 1.0, emptyList())
        "pressure" -> analog(gaugeId, 850.0, 1_050.0, 50.0, 10.0)
        else -> analog(gaugeId, 0.0, 100.0, 20.0, 10.0)
    }

    fun progress(gaugeId: String, value: Double?, tripType: String): Double? = when (gaugeId) {
        "gps_accuracy" -> accuracyProgress(value)
        "trip_time" -> value?.let { (it % 60.0) / 60.0 }
        else -> forGauge(gaugeId, value, tripType).progress(value)
    }

    fun accuracyProgress(meters: Double?): Double? {
        if (meters == null) return null
        val anchors = listOf(100.0, 50.0, 25.0, 10.0, 5.0, 0.0)
        val clamped = meters.coerceIn(0.0, 100.0)
        val segment = (0 until anchors.lastIndex).firstOrNull { clamped <= anchors[it] && clamped >= anchors[it + 1] } ?: 0
        val high = anchors[segment]
        val low = anchors[segment + 1]
        val fraction = if (high == low) 0.0 else (high - clamped) / (high - low)
        return ((segment + fraction) / anchors.lastIndex).coerceIn(0.0, 1.0)
    }

    private fun speedMaximum(tripType: String): Double = when (tripType.trim().lowercase().replace('-', '_').replace(' ', '_')) {
        "street" -> 200.0
        "snow" -> 160.0
        "water" -> 100.0
        else -> 120.0
    }

    private fun attitude(gaugeId: String, face: GaugeFaceStyle) = GaugeScaleSpec(
        gaugeId,
        face,
        -45.0,
        45.0,
        ticks(-45.0, 45.0, 15.0),
        minorStep = 5.0,
        zones = listOf(
            GaugeZone(-45.0, -30.0, GaugeZoneRole.CAUTION),
            GaugeZone(30.0, 45.0, GaugeZoneRole.CAUTION),
        ),
        startAngle = 225f,
        sweepAngle = 90f,
    )

    private fun analog(
        gaugeId: String,
        minimum: Double,
        maximum: Double,
        majorStep: Double,
        minorStep: Double,
        face: GaugeFaceStyle = GaugeFaceStyle.ANALOG,
        zones: List<GaugeZone> = emptyList(),
    ) = GaugeScaleSpec(gaugeId, face, minimum, maximum, ticks(minimum, maximum, majorStep), minorStep, zones)

    private fun ticks(minimum: Double, maximum: Double, step: Double): List<GaugeTick> {
        val count = ceil((maximum - minimum) / step).toInt()
        return (0..count).map { index ->
            val value = (minimum + index * step).coerceAtMost(maximum)
            GaugeTick(value, formatTick(value))
        }.distinctBy { it.value }
    }

    private fun nextThreshold(value: Double, thresholds: List<Double>): Double =
        thresholds.firstOrNull { value <= it } ?: (ceil(value / thresholds.last()) * thresholds.last()).coerceAtLeast(thresholds.last())

    private fun formatTick(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
