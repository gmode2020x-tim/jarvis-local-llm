package ca.gmode.triprecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import ca.gmode.triprecorder.settings.DashboardPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CockpitGaugeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var palette = ca.gmode.triprecorder.settings.AppearanceSettings.PRESETS.first()
    private var reading = CockpitReading("Speed", "--", "km/h", gaugeId = "speed")
    private var tripType = "off_road"

    init {
        minimumHeight = dp(154)
        contentDescription = "Speed gauge waiting for telemetry"
    }

    fun setPalette(value: DashboardPalette) {
        palette = value
        invalidate()
    }

    fun setReading(value: CockpitReading, tripType: String) {
        reading = value
        this.tripType = tripType
        contentDescription = listOf(value.title, value.value, value.unit, value.subtitle).filter { it.isNotBlank() }.joinToString(" ")
        invalidate()
    }

    fun setReading(title: String, value: String, unit: String, progress: Double?, subtitle: String = "") {
        setReading(CockpitReading(title, value, unit, progress, subtitle, gaugeId = title.lowercase().replace(' ', '_')), tripType)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize(dp(154), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height * 0.55f
        val radius = min(width * 0.34f, height * 0.34f)
        val spec = GaugeScaleCatalog.forGauge(reading.gaugeId, reading.numericValue, tripType)

        if (spec.faceStyle != GaugeFaceStyle.INFO) drawScale(canvas, spec, centerX, centerY, radius)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = palette.muted
        paint.textSize = dp(10).toFloat()
        canvas.drawText(reading.title.uppercase(), centerX, dp(19).toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = dp(if (reading.value.length > 14) 14 else if (reading.value.length > 9) 18 else 28).toFloat()
        canvas.drawText(reading.value, centerX, centerY + dp(4), paint)
        paint.color = palette.muted
        paint.textSize = dp(9).toFloat()
        canvas.drawText(reading.unit, centerX, centerY + dp(19), paint)
        paint.color = palette.accent
        paint.textSize = dp(8).toFloat()
        canvas.drawText(reading.subtitle.uppercase(), centerX, height - dp(10).toFloat(), paint)
    }

    private fun drawScale(canvas: Canvas, spec: GaugeScaleSpec, cx: Float, cy: Float, radius: Float) {
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = dp(4).toFloat()
        paint.color = palette.outline
        canvas.drawArc(oval, spec.startAngle, spec.sweepAngle, false, paint)
        spec.zones.forEach { zone ->
            val start = spec.startAngle + spec.progress(zone.startValue)!!.toFloat() * spec.sweepAngle
            val sweep = (spec.progress(zone.endValue)!! - spec.progress(zone.startValue)!!).toFloat() * spec.sweepAngle
            paint.color = zoneColor(zone.role)
            canvas.drawArc(oval, start, sweep, false, paint)
        }
        spec.minorStep?.takeIf { it > 0.0 }?.let { minor ->
            var value = spec.minimum
            while (value <= spec.maximum + minor / 10.0) {
                val angle = spec.startAngle + spec.progress(value)!!.toFloat() * spec.sweepAngle
                drawTick(canvas, cx, cy, radius, angle, radius * 0.07f, dp(1).toFloat(), palette.muted)
                value += minor
            }
        }
        spec.majorTicks.forEach { tick ->
            val angle = spec.startAngle + spec.progress(tick.value)!!.toFloat() * spec.sweepAngle
            drawTick(canvas, cx, cy, radius, angle, radius * 0.14f, dp(2).toFloat(), Color.WHITE)
            val radians = Math.toRadians(angle.toDouble())
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = dp(7).toFloat()
            paint.color = Color.LTGRAY
            canvas.drawText(tick.label, cx + cos(radians).toFloat() * radius * 1.28f, cy + sin(radians).toFloat() * radius * 1.28f + dp(3), paint)
        }
        reading.progress?.let { progress ->
            val angle = spec.startAngle + progress.coerceIn(0.0, 1.0).toFloat() * spec.sweepAngle
            val radians = Math.toRadians(angle.toDouble())
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(2).toFloat()
            paint.color = palette.accent
            canvas.drawLine(cx, cy, cx + cos(radians).toFloat() * radius * 0.76f, cy + sin(radians).toFloat() * radius * 0.76f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, dp(3).toFloat(), paint)
        }
    }

    private fun drawTick(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, length: Float, width: Float, color: Int) {
        val radians = Math.toRadians(angle.toDouble())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        canvas.drawLine(
            cx + cos(radians).toFloat() * (radius - length),
            cy + sin(radians).toFloat() * (radius - length),
            cx + cos(radians).toFloat() * radius,
            cy + sin(radians).toFloat() * radius,
            paint,
        )
    }

    private fun zoneColor(role: GaugeZoneRole): Int = when (role) {
        GaugeZoneRole.DANGER -> Color.parseColor("#E5091B")
        GaugeZoneRole.CAUTION -> Color.parseColor("#FF9D00")
        GaugeZoneRole.GOOD -> Color.parseColor("#20B94B")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
