package ca.gmode.triprecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import ca.gmode.triprecorder.settings.AppearanceSettings
import ca.gmode.triprecorder.settings.DashboardPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Automotive-style live trip instrument drawn without bitmap assets. */
class DashboardGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val needlePath = Path()
    private var dialGradient: RadialGradient? = null
    private var palette: DashboardPalette = AppearanceSettings.PRESETS.first()

    private var isRecording = false
    private var speedKph = 0
    private var distanceKm = 0.0
    private var duration = "0:00"
    private var accuracyMeters: Int? = null
    private var tripTitle = "READY"

    fun setPalette(palette: DashboardPalette) {
        this.palette = palette
        rebuildGradient()
        invalidate()
    }

    fun setTelemetry(
        recording: Boolean,
        title: String,
        speedKph: Int,
        distanceKm: Double,
        duration: String,
        accuracyMeters: Int?,
    ) {
        isRecording = recording
        tripTitle = title.uppercase().take(22)
        this.speedKph = speedKph.coerceAtLeast(0)
        this.distanceKm = distanceKm.coerceAtLeast(0.0)
        this.duration = duration
        this.accuracyMeters = accuracyMeters
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (width * 0.92f).roundToInt()
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.43f
        val activeColor = if (isRecording) palette.accent else palette.muted

        paint.style = Paint.Style.FILL
        paint.shader = dialGradient
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(12f)
        paint.color = palette.outline
        canvas.drawCircle(cx, cy, radius - dp(5f), paint)
        paint.strokeWidth = dp(2f)
        paint.color = activeColor
        canvas.drawCircle(cx, cy, radius - dp(13f), paint)

        bounds.set(cx - radius + dp(24f), cy - radius + dp(24f), cx + radius - dp(24f), cy + radius - dp(24f))
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = dp(11f)
        paint.color = if (isRecording) palette.activeSurface else palette.inactiveSurface
        canvas.drawArc(bounds, 135f, 270f, false, paint)
        paint.color = activeColor
        val sweep = (speedKph.coerceAtMost(MAX_SPEED).toFloat() / MAX_SPEED) * 270f
        canvas.drawArc(bounds, 135f, sweep, false, paint)

        drawTicks(canvas, cx, cy, radius - dp(26f), activeColor)
        drawNeedle(canvas, cx, cy, radius - dp(48f), activeColor)

        drawCentered(canvas, tripTitle, cx, cy - radius * 0.42f, dp(11f), Color.LTGRAY, true)
        drawCentered(canvas, speedKph.toString(), cx, cy + dp(13f), dp(58f), Color.WHITE, true)
        drawCentered(canvas, "KM/H", cx, cy + dp(38f), dp(11f), activeColor, true)

        val metricY = cy + radius * 0.55f
        drawMetric(canvas, cx - radius * 0.46f, metricY, "DISTANCE", "%.2f km".format(distanceKm), activeColor)
        drawMetric(canvas, cx, metricY, "DURATION", duration, activeColor)
        drawMetric(
            canvas,
            cx + radius * 0.46f,
            metricY,
            "GPS",
            accuracyMeters?.let { "±$it m" } ?: "--",
            activeColor,
        )

        paint.style = Paint.Style.FILL
        paint.color = if (isRecording) palette.accent else palette.muted
        canvas.drawCircle(cx, cy + radius * 0.84f, dp(4f), paint)
        drawCentered(
            canvas,
            if (isRecording) "RECORDING" else "STANDBY",
            cx + dp(42f),
            cy + radius * 0.87f,
            dp(10f),
            Color.LTGRAY,
            true,
        )
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        for (index in 0..30) {
            val degrees = 135f + index * 9f
            val radians = Math.toRadians(degrees.toDouble())
            val major = index % 5 == 0
            val outer = radius
            val inner = radius - dp(if (major) 16f else 8f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(if (major) 2.6f else 1.2f)
            paint.color = if (major) Color.WHITE else color
            canvas.drawLine(
                cx + cos(radians).toFloat() * inner,
                cy + sin(radians).toFloat() * inner,
                cx + cos(radians).toFloat() * outer,
                cy + sin(radians).toFloat() * outer,
                paint,
            )
        }
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, length: Float, color: Int) {
        val degrees = 135f + (speedKph.coerceAtMost(MAX_SPEED).toFloat() / MAX_SPEED) * 270f
        val radians = Math.toRadians(degrees.toDouble())
        val tipX = cx + cos(radians).toFloat() * length
        val tipY = cy + sin(radians).toFloat() * length
        val perpendicular = radians + Math.PI / 2
        needlePath.apply {
            reset()
            moveTo(
                cx + cos(perpendicular).toFloat() * dp(4f),
                cy + sin(perpendicular).toFloat() * dp(4f),
            )
            lineTo(tipX, tipY)
            lineTo(
                cx - cos(perpendicular).toFloat() * dp(4f),
                cy - sin(perpendicular).toFloat() * dp(4f),
            )
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(needlePath, paint)
        paint.color = palette.panel
        canvas.drawCircle(cx, cy, dp(10f), paint)
        paint.color = color
        canvas.drawCircle(cx, cy, dp(4f), paint)
    }

    private fun drawMetric(canvas: Canvas, x: Float, y: Float, label: String, value: String, accent: Int) {
        drawCentered(canvas, label, x, y, dp(8f), accent, true)
        drawCentered(canvas, value, x, y + dp(17f), dp(12f), Color.WHITE, true)
    }

    private fun drawCentered(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean,
    ) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.color = color
        paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        canvas.drawText(value, x, baseline, paint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildGradient()
    }

    private fun rebuildGradient() {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.43f
        dialGradient = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(palette.dialCenter, palette.dialMiddle, palette.background),
            floatArrayOf(0f, 0.74f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private companion object {
        const val MAX_SPEED = 160
    }
}
