package ca.gmode.triprecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import ca.gmode.triprecorder.settings.DashboardPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class CockpitReading(
    val title: String,
    val value: String,
    val unit: String,
    val progress: Double? = null,
    val subtitle: String = "",
    val angleDegrees: Double? = null,
)

data class CockpitState(
    val time: String = "--:--",
    val vehicleId: String = "atv_utv",
    val vehicleLabel: String = "ATV / UTV",
    val tripTypeLabel: String = "OFF ROAD",
    val recording: Boolean = false,
    val automaticArmed: Boolean = false,
    val gpsLabel: String = "GPS STANDBY",
    val pendingLabel: String = "0 PENDING",
    val homeAssistantLabel: String = "HA SETUP",
    val tripLabel: String = "READY TO RECORD",
    val readings: List<CockpitReading> = emptyList(),
)

enum class CockpitAction {
    START,
    STOP,
    AUTO,
    SETTINGS,
    SYNC,
    TRIP_TYPE,
    THEME,
    HOME_ASSISTANT,
}

class LandscapeCockpitView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val touchZones = linkedMapOf<CockpitAction, RectF>()
    private var palette = ca.gmode.triprecorder.settings.AppearanceSettings.PRESETS.first()
    private var state = CockpitState()
    var onAction: ((CockpitAction) -> Unit)? = null

    init {
        isFocusable = true
        isClickable = true
        keepScreenOn = true
        contentDescription = "GMODE vehicle telemetry cockpit"
    }

    fun setPalette(value: DashboardPalette) {
        palette = value
        invalidate()
    }

    fun setState(value: CockpitState) {
        state = value
        contentDescription = "${value.vehicleLabel} cockpit, ${value.tripLabel}, ${value.gpsLabel}, ${value.homeAssistantLabel}"
        invalidate()
    }

    internal fun activeGaugeTitles(): List<String> = state.readings.map { it.title }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        touchZones.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.BLACK, palette.background, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        drawFrame(canvas, w, h)
        drawHeader(canvas, w, h)
        drawSideControls(canvas, w, h)

        val heroReadings = state.readings.take(2).let { values ->
            when (values.size) {
                0 -> listOf(
                    CockpitReading("PITCH", "--", "degrees"),
                    CockpitReading("ROLL", "--", "degrees"),
                )
                1 -> values + CockpitReading("ROLL", "--", "degrees")
                else -> values
            }
        }
        val heroY = h * 0.50f
        val heroRadius = min(w * 0.15f, h * 0.35f)
        drawHeroGauge(canvas, w * 0.35f, heroY, heroRadius, heroReadings[0], sideView = true)
        drawHeroGauge(canvas, w * 0.65f, heroY, heroRadius, heroReadings[1], sideView = false)
        drawText(canvas, state.tripLabel, w * 0.50f, h * 0.955f, h * 0.026f, palette.muted, Paint.Align.CENTER, true)
    }

    private fun drawFrame(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.004f
        paint.color = palette.outline
        canvas.drawRoundRect(RectF(w * 0.004f, h * 0.008f, w * 0.996f, h * 0.986f), h * 0.025f, h * 0.025f, paint)
        paint.strokeWidth = h * 0.0015f
        paint.color = Color.argb(150, 255, 255, 255)
        paint.style = Paint.Style.FILL
    }

    private fun drawHeader(canvas: Canvas, w: Float, h: Float) {
        drawText(canvas, state.time, w * 0.035f, h * 0.065f, h * 0.047f, Color.WHITE, Paint.Align.LEFT, true)
        drawText(canvas, "●  ${state.gpsLabel}", w * 0.19f, h * 0.058f, h * 0.024f, palette.accent, Paint.Align.LEFT, true)
        drawText(canvas, state.pendingLabel, w * 0.50f, h * 0.058f, h * 0.023f, palette.muted, Paint.Align.CENTER, true)
        drawText(canvas, state.homeAssistantLabel, w * 0.81f, h * 0.058f, h * 0.023f, palette.accent, Paint.Align.RIGHT, true)
        drawText(canvas, state.vehicleLabel.uppercase(), w * 0.965f, h * 0.065f, h * 0.027f, Color.WHITE, Paint.Align.RIGHT, true)
    }

    private fun drawSideControls(canvas: Canvas, w: Float, h: Float) {
        val labelsLeft = listOf(
            CockpitAction.START to if (state.recording) "RECORDING" else "START TRIP",
            CockpitAction.STOP to "STOP + SYNC",
            CockpitAction.AUTO to if (state.automaticArmed) "AUTO ARMED" else "AUTO RECORD",
            CockpitAction.SETTINGS to "SETTINGS",
        )
        val labelsRight = listOf(
            CockpitAction.SYNC to "SYNC NOW",
            CockpitAction.TRIP_TYPE to state.tripTypeLabel,
            CockpitAction.THEME to "COLOR / THEME",
            CockpitAction.HOME_ASSISTANT to "HOME ASSISTANT",
        )
        val top = h * 0.14f
        val height = h * 0.15f
        val gap = h * 0.025f
        labelsLeft.forEachIndexed { index, pair ->
            drawControl(canvas, RectF(w * 0.012f, top + index * (height + gap), w * 0.177f, top + index * (height + gap) + height), pair.first, pair.second)
        }
        labelsRight.forEachIndexed { index, pair ->
            drawControl(canvas, RectF(w * 0.823f, top + index * (height + gap), w * 0.988f, top + index * (height + gap) + height), pair.first, pair.second)
        }
    }

    private fun drawControl(canvas: Canvas, rect: RectF, action: CockpitAction, label: String) {
        touchZones[action] = rect
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, palette.panel, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, rect.height() * 0.12f, rect.height() * 0.12f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = rect.height() * 0.035f
        paint.color = if (action == CockpitAction.START && state.recording) Color.parseColor("#35D06F") else palette.outline
        canvas.drawRoundRect(rect, rect.height() * 0.12f, rect.height() * 0.12f, paint)
        paint.color = palette.accent
        paint.strokeWidth = rect.height() * 0.035f
        canvas.drawLine(rect.left + rect.width() * 0.06f, rect.top + rect.height() * 0.17f, rect.left + rect.width() * 0.06f, rect.bottom - rect.height() * 0.17f, paint)
        paint.style = Paint.Style.FILL
        drawText(canvas, label, rect.centerX() + rect.width() * 0.04f, rect.centerY() + rect.height() * 0.09f, rect.height() * 0.25f, Color.WHITE, Paint.Align.CENTER, true)
    }

    private fun drawHeroGauge(canvas: Canvas, cx: Float, cy: Float, radius: Float, reading: CockpitReading, sideView: Boolean) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, radius, intArrayOf(palette.dialCenter, palette.dialMiddle, Color.BLACK), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = palette.outline
        paint.strokeWidth = radius * 0.065f
        canvas.drawCircle(cx, cy, radius * 0.94f, paint)
        paint.color = palette.accent
        paint.strokeWidth = radius * 0.022f
        canvas.drawArc(RectF(cx - radius * 0.87f, cy - radius * 0.87f, cx + radius * 0.87f, cy + radius * 0.87f), 205f, 130f, false, paint)
        for (tick in 0..24) {
            val angle = Math.toRadians((135.0 + tick * 11.25))
            val outer = radius * 0.88f
            val inner = radius * if (tick % 4 == 0) 0.74f else 0.80f
            paint.color = if (tick % 6 == 0) palette.accent else Color.WHITE
            paint.strokeWidth = radius * if (tick % 4 == 0) 0.025f else 0.012f
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                paint,
            )
        }
        paint.style = Paint.Style.FILL
        drawText(canvas, reading.title.uppercase(), cx, cy - radius * 0.70f, radius * 0.14f, Color.WHITE, Paint.Align.CENTER, true)
        drawHorizon(canvas, cx, cy + radius * 0.05f, radius * 0.66f)
        canvas.save()
        val tilt = reading.angleDegrees?.coerceIn(-45.0, 45.0)?.toFloat() ?: 0f
        canvas.rotate(tilt * 0.55f, cx, cy)
        drawVehicle(canvas, cx, cy + radius * 0.02f, radius * 0.48f, sideView)
        canvas.restore()
        drawText(canvas, reading.value, cx, cy + radius * 0.66f, radius * 0.24f, palette.accent, Paint.Align.CENTER, true)
        drawText(canvas, reading.unit, cx, cy + radius * 0.83f, radius * 0.09f, palette.muted, Paint.Align.CENTER, false)
    }

    private fun drawHorizon(canvas: Canvas, cx: Float, cy: Float, width: Float) {
        val path = Path().apply {
            moveTo(cx - width, cy + width * 0.22f)
            lineTo(cx - width * 0.55f, cy - width * 0.08f)
            lineTo(cx - width * 0.25f, cy + width * 0.08f)
            lineTo(cx + width * 0.10f, cy - width * 0.20f)
            lineTo(cx + width * 0.42f, cy + width * 0.02f)
            lineTo(cx + width, cy - width * 0.12f)
            lineTo(cx + width, cy + width * 0.35f)
            lineTo(cx - width, cy + width * 0.35f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(115, Color.red(palette.accent), Color.green(palette.accent), Color.blue(palette.accent))
        canvas.drawPath(path, paint)
    }

    private fun drawVehicle(canvas: Canvas, cx: Float, cy: Float, size: Float, sideView: Boolean) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = size * 0.055f
        paint.color = Color.WHITE
        val path = Path()
        when (state.vehicleId) {
            "boat" -> {
                path.moveTo(cx - size, cy)
                path.lineTo(cx + size, cy)
                path.lineTo(cx + size * 0.55f, cy + size * 0.42f)
                path.lineTo(cx - size * 0.55f, cy + size * 0.42f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.drawLine(cx, cy, cx, cy - size * 0.72f, paint)
                canvas.drawLine(cx, cy - size * 0.68f, cx + size * 0.45f, cy - size * 0.20f, paint)
            }
            "motorcycle" -> {
                canvas.drawCircle(cx - size * 0.62f, cy + size * 0.25f, size * 0.30f, paint)
                canvas.drawCircle(cx + size * 0.62f, cy + size * 0.25f, size * 0.30f, paint)
                path.moveTo(cx - size * 0.62f, cy + size * 0.25f)
                path.lineTo(cx, cy - size * 0.12f)
                path.lineTo(cx + size * 0.62f, cy + size * 0.25f)
                path.lineTo(cx - size * 0.05f, cy + size * 0.25f)
                path.close()
                canvas.drawPath(path, paint)
            }
            "snowmobile" -> {
                canvas.drawOval(RectF(cx - size * 0.75f, cy + size * 0.12f, cx + size * 0.55f, cy + size * 0.50f), paint)
                path.moveTo(cx - size * 0.55f, cy + size * 0.10f)
                path.lineTo(cx - size * 0.10f, cy - size * 0.48f)
                path.lineTo(cx + size * 0.48f, cy - size * 0.25f)
                path.lineTo(cx + size * 0.78f, cy + size * 0.18f)
                canvas.drawPath(path, paint)
            }
            else -> {
                if (sideView) {
                    canvas.drawCircle(cx - size * 0.62f, cy + size * 0.32f, size * 0.28f, paint)
                    canvas.drawCircle(cx + size * 0.62f, cy + size * 0.32f, size * 0.28f, paint)
                    path.moveTo(cx - size * 0.95f, cy + size * 0.20f)
                    path.lineTo(cx - size * 0.70f, cy - size * 0.20f)
                    path.lineTo(cx - size * 0.25f, cy - size * 0.25f)
                    path.lineTo(cx - size * 0.08f, cy - size * 0.72f)
                    path.lineTo(cx + size * 0.48f, cy - size * 0.68f)
                    path.lineTo(cx + size * 0.75f, cy - size * 0.12f)
                    path.lineTo(cx + size, cy + size * 0.12f)
                    path.close()
                } else {
                    canvas.drawCircle(cx - size * 0.62f, cy + size * 0.28f, size * 0.27f, paint)
                    canvas.drawCircle(cx + size * 0.62f, cy + size * 0.28f, size * 0.27f, paint)
                    path.moveTo(cx - size * 0.88f, cy + size * 0.12f)
                    path.lineTo(cx - size * 0.56f, cy - size * 0.65f)
                    path.lineTo(cx + size * 0.56f, cy - size * 0.65f)
                    path.lineTo(cx + size * 0.88f, cy + size * 0.12f)
                    path.close()
                    path.moveTo(cx, cy - size * 0.63f)
                    path.lineTo(cx, cy + size * 0.10f)
                }
                canvas.drawPath(path, paint)
            }
        }
        paint.color = palette.accent
        paint.strokeWidth = size * 0.035f
        canvas.drawLine(cx - size * 0.48f, cy, cx + size * 0.48f, cy, paint)
    }

    private fun drawText(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, bold: Boolean) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        paint.textAlign = align
        paint.textSize = size
        paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        canvas.drawText(value, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            touchZones.entries.firstOrNull { it.value.contains(event.x, event.y) }?.let {
                onAction?.invoke(it.key)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
