package ca.gmode.triprecorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
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
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val utvSide: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.utv_side) }
    private val utvFront: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.utv_front) }
    private val dialLandscape: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.dial_mountain_landscape) }
    private val dashboardLeather: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.dashboard_black_leather) }
    private val touchZones = linkedMapOf<CockpitAction, RectF>()
    private var palette = ca.gmode.triprecorder.settings.AppearanceSettings.PRESETS.first()
    private var state = CockpitState()
    private var contentScale = 1f
    private var contentOffsetX = 0f
    private var contentOffsetY = 0f
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
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, 0f, screenH, Color.BLACK, palette.background, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, screenW, screenH, paint)
        paint.shader = null
        drawDashboardTexture(canvas, screenW, screenH)

        contentScale = min(screenW / DESIGN_WIDTH, screenH / DESIGN_HEIGHT)
        contentOffsetX = (screenW - DESIGN_WIDTH * contentScale) / 2f
        contentOffsetY = (screenH - DESIGN_HEIGHT * contentScale) / 2f
        canvas.save()
        canvas.translate(contentOffsetX, contentOffsetY)
        canvas.scale(contentScale, contentScale)
        val w = DESIGN_WIDTH
        val h = DESIGN_HEIGHT
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        drawDashboardTexture(canvas, w, h)
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
        val heroY = h * 0.515f
        val heroRadius = min(w * 0.150f, h * 0.470f)
        drawHeroGauge(canvas, w * 0.356f, heroY, heroRadius, heroReadings[0], sideView = true)
        drawHeroGauge(canvas, w * 0.644f, heroY, heroRadius, heroReadings[1], sideView = false)
        canvas.restore()
    }

    private fun drawDashboardTexture(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.FILL
        paint.alpha = 155
        canvas.drawBitmap(dashboardLeather, null, RectF(0f, 0f, w, h), paint)
        paint.alpha = 255
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    private fun drawFrame(canvas: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.006f
        paint.color = Color.parseColor("#373737")
        canvas.drawRoundRect(RectF(w * 0.004f, h * 0.008f, w * 0.996f, h * 0.992f), h * 0.024f, h * 0.024f, paint)
        paint.strokeWidth = h * 0.0015f
        paint.color = Color.argb(145, 255, 255, 255)
        canvas.drawRoundRect(RectF(w * 0.008f, h * 0.012f, w * 0.992f, h * 0.988f), h * 0.020f, h * 0.020f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawHeader(canvas: Canvas, w: Float, h: Float) {
        drawText(canvas, state.time, w * 0.037f, h * 0.123f, h * 0.075f, Color.WHITE, Paint.Align.LEFT, true)
        drawText(canvas, "●  ${state.gpsLabel}", w * 0.110f, h * 0.115f, h * 0.035f, palette.accent, Paint.Align.LEFT, true)
        drawText(canvas, state.pendingLabel, w * 0.50f, h * 0.115f, h * 0.034f, palette.muted, Paint.Align.CENTER, true)
        drawText(canvas, state.homeAssistantLabel, w * 0.870f, h * 0.115f, h * 0.034f, palette.accent, Paint.Align.RIGHT, true)
        drawText(canvas, state.vehicleLabel.uppercase(), w * 0.970f, h * 0.123f, h * 0.038f, Color.WHITE, Paint.Align.RIGHT, true)
    }

    private fun drawSideControls(canvas: Canvas, w: Float, h: Float) {
        val labelsLeft = listOf(
            CockpitAction.START to if (state.recording) "RECORDING" else "START",
            CockpitAction.STOP to "STOP",
            CockpitAction.AUTO to if (state.automaticArmed) "AUTO ARMED" else "AUTO",
            CockpitAction.SETTINGS to "SETTINGS",
        )
        val labelsRight = listOf(
            CockpitAction.SYNC to "SYNC",
            CockpitAction.TRIP_TYPE to state.tripTypeLabel,
            CockpitAction.THEME to "THEME",
            CockpitAction.HOME_ASSISTANT to "HA LINK",
        )
        val top = h * 0.181f
        val height = h * 0.190f
        val gap = h * 0.017f
        labelsLeft.forEachIndexed { index, pair ->
            drawControl(canvas, RectF(w * 0.009f, top + index * (height + gap), w * 0.174f, top + index * (height + gap) + height), pair.first, pair.second)
        }
        labelsRight.forEachIndexed { index, pair ->
            drawControl(canvas, RectF(w * 0.827f, top + index * (height + gap), w * 0.991f, top + index * (height + gap) + height), pair.first, pair.second)
        }
    }

    private fun drawControl(canvas: Canvas, rect: RectF, action: CockpitAction, label: String) {
        touchZones[action] = rect
        val leftSide = action == CockpitAction.START || action == CockpitAction.STOP || action == CockpitAction.AUTO || action == CockpitAction.SETTINGS
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(Color.parseColor("#282828"), palette.panel, Color.parseColor("#060606")),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, rect.height() * 0.12f, rect.height() * 0.12f, paint)
        paint.shader = null
        paint.shader = BitmapShader(dashboardLeather, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.alpha = 38
        canvas.drawRoundRect(rect, rect.height() * 0.12f, rect.height() * 0.12f, paint)
        paint.alpha = 255
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = rect.height() * 0.040f
        paint.color = Color.parseColor("#080808")
        canvas.drawRoundRect(rect, rect.height() * 0.12f, rect.height() * 0.12f, paint)
        paint.strokeWidth = rect.height() * 0.012f
        paint.color = if (action == CockpitAction.START && state.recording) Color.parseColor("#35D06F") else Color.parseColor("#4A4A4A")
        canvas.drawRoundRect(
            RectF(rect.left + rect.height() * 0.07f, rect.top + rect.height() * 0.07f, rect.right - rect.height() * 0.07f, rect.bottom - rect.height() * 0.07f),
            rect.height() * 0.08f,
            rect.height() * 0.08f,
            paint,
        )
        val accentX = if (leftSide) rect.right - rect.width() * 0.055f else rect.left + rect.width() * 0.055f
        paint.color = palette.accent
        paint.strokeWidth = rect.height() * 0.045f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(accentX, rect.top + rect.height() * 0.18f, accentX, rect.bottom - rect.height() * 0.18f, paint)
        paint.style = Paint.Style.FILL
        drawControlIcon(canvas, action, rect.left + rect.width() * 0.25f, rect.centerY(), rect.height() * 0.23f)
        drawText(canvas, label, rect.left + rect.width() * 0.42f, rect.centerY() + rect.height() * 0.075f, rect.height() * 0.18f, Color.WHITE, Paint.Align.LEFT, true)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawControlIcon(canvas: Canvas, action: CockpitAction, cx: Float, cy: Float, size: Float) {
        paint.shader = null
        paint.color = Color.WHITE
        paint.strokeWidth = size * 0.16f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        val path = Path()
        when (action) {
            CockpitAction.START -> {
                paint.style = Paint.Style.FILL
                path.moveTo(cx - size * 0.46f, cy - size * 0.62f)
                path.lineTo(cx + size * 0.65f, cy)
                path.lineTo(cx - size * 0.46f, cy + size * 0.62f)
                path.close()
                canvas.drawPath(path, paint)
            }
            CockpitAction.STOP -> {
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(cx - size * 0.53f, cy - size * 0.53f, cx + size * 0.53f, cy + size * 0.53f), size * 0.12f, size * 0.12f, paint)
            }
            CockpitAction.AUTO -> {
                canvas.drawCircle(cx, cy, size * 0.67f, paint)
                drawText(canvas, "A", cx, cy + size * 0.34f, size * 0.86f, Color.WHITE, Paint.Align.CENTER, true)
            }
            CockpitAction.SETTINGS -> {
                canvas.drawCircle(cx, cy, size * 0.48f, paint)
                canvas.drawCircle(cx, cy, size * 0.14f, paint)
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    canvas.drawLine(
                        cx + cos(angle).toFloat() * size * 0.60f,
                        cy + sin(angle).toFloat() * size * 0.60f,
                        cx + cos(angle).toFloat() * size * 0.82f,
                        cy + sin(angle).toFloat() * size * 0.82f,
                        paint,
                    )
                }
            }
            CockpitAction.SYNC -> {
                canvas.drawArc(RectF(cx - size * 0.62f, cy - size * 0.62f, cx + size * 0.62f, cy + size * 0.62f), 35f, 280f, false, paint)
                paint.style = Paint.Style.FILL
                path.moveTo(cx + size * 0.64f, cy - size * 0.28f)
                path.lineTo(cx + size * 0.72f, cy + size * 0.32f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.04f)
                path.close()
                canvas.drawPath(path, paint)
            }
            CockpitAction.TRIP_TYPE -> {
                path.moveTo(cx - size * 0.68f, cy + size * 0.60f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.12f)
                path.lineTo(cx - size * 0.34f, cy - size * 0.15f)
                path.lineTo(cx + size * 0.62f, cy - size * 0.65f)
                canvas.drawPath(path, paint)
            }
            CockpitAction.THEME -> {
                canvas.drawCircle(cx, cy, size * 0.38f, paint)
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    canvas.drawLine(
                        cx + cos(angle).toFloat() * size * 0.56f,
                        cy + sin(angle).toFloat() * size * 0.56f,
                        cx + cos(angle).toFloat() * size * 0.82f,
                        cy + sin(angle).toFloat() * size * 0.82f,
                        paint,
                    )
                }
            }
            CockpitAction.HOME_ASSISTANT -> {
                path.moveTo(cx - size * 0.68f, cy - size * 0.02f)
                path.lineTo(cx, cy - size * 0.68f)
                path.lineTo(cx + size * 0.68f, cy - size * 0.02f)
                path.moveTo(cx - size * 0.48f, cy - size * 0.10f)
                path.lineTo(cx - size * 0.48f, cy + size * 0.65f)
                path.lineTo(cx + size * 0.48f, cy + size * 0.65f)
                path.lineTo(cx + size * 0.48f, cy - size * 0.10f)
                canvas.drawPath(path, paint)
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawHeroGauge(canvas: Canvas, cx: Float, cy: Float, radius: Float, reading: CockpitReading, sideView: Boolean) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, radius, intArrayOf(palette.dialCenter, palette.dialMiddle, Color.BLACK), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

        paint.color = Color.parseColor("#050505")
        canvas.drawCircle(cx, cy, radius * 0.985f, paint)
        paint.style = Paint.Style.STROKE
        paint.shader = LinearGradient(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            intArrayOf(Color.parseColor("#050505"), Color.parseColor("#353535"), Color.parseColor("#030303")),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.strokeWidth = radius * 0.105f
        canvas.drawCircle(cx, cy, radius * 0.94f, paint)
        paint.shader = null
        paint.color = Color.parseColor("#747474")
        paint.strokeWidth = radius * 0.006f
        canvas.drawCircle(cx, cy, radius * 0.992f, paint)
        paint.color = Color.parseColor("#0A0A0A")
        paint.strokeWidth = radius * 0.030f
        canvas.drawCircle(cx, cy, radius * 0.865f, paint)
        paint.color = Color.parseColor("#696969")
        paint.strokeWidth = radius * 0.006f
        canvas.drawCircle(cx, cy, radius * 0.846f, paint)
        paint.color = palette.accent
        paint.strokeWidth = radius * 0.010f
        canvas.drawCircle(cx, cy, radius * 0.825f, paint)
        for (tick in 0 until 60) {
            val angle = Math.toRadians(tick * 6.0)
            val outer = radius * 0.825f
            val inner = radius * if (tick % 5 == 0) 0.715f else 0.770f
            paint.color = if (tick in 46..54) palette.accent else Color.WHITE
            paint.strokeWidth = radius * if (tick % 5 == 0) 0.018f else 0.006f
            canvas.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                paint,
            )
        }
        drawTerrain(canvas, cx, cy + radius * 0.04f, radius)
        paint.style = Paint.Style.FILL
        drawText(canvas, reading.title.uppercase(), cx, cy - radius * 0.67f, radius * 0.095f, Color.WHITE, Paint.Align.CENTER, false)
        canvas.save()
        canvas.clipCircle(cx, cy + radius * 0.02f, radius * 0.75f)
        val tilt = reading.angleDegrees?.coerceIn(-45.0, 45.0)?.toFloat() ?: 0f
        canvas.rotate(tilt * 0.55f, cx, cy)
        if (state.vehicleId == "atv_utv") {
            drawUtvBitmap(canvas, cx, cy + radius * 0.08f, radius, sideView)
        } else {
            drawVehicle(canvas, cx, cy + radius * 0.02f, radius * 0.48f, sideView)
        }
        canvas.restore()
        drawAngleLabels(canvas, cx, cy, radius)
        drawText(canvas, reading.value, cx, cy + radius * 0.58f, radius * 0.18f, palette.accent, Paint.Align.CENTER, true)
        if (reading.title.equals("PITCH", ignoreCase = true) || reading.title.equals("ROLL", ignoreCase = true)) {
            drawCalibrationScale(canvas, cx, cy + radius * 0.72f, radius)
        } else {
            drawText(canvas, reading.unit, cx, cy + radius * 0.74f, radius * 0.07f, palette.muted, Paint.Align.CENTER, false)
        }
    }

    private fun drawAngleLabels(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val labels = listOf(
            45 to 225.0, 45 to 315.0,
            15 to 195.0, 15 to 345.0,
            0 to 180.0, 0 to 0.0,
            -15 to 165.0, -15 to 15.0,
            -45 to 135.0, -45 to 45.0,
        )
        labels.forEach { (value, degrees) ->
            val angle = Math.toRadians(degrees)
            drawText(
                canvas,
                "${value}°",
                cx + cos(angle).toFloat() * radius * 0.635f,
                cy + sin(angle).toFloat() * radius * 0.635f + radius * 0.020f,
                radius * 0.056f,
                Color.WHITE,
                Paint.Align.CENTER,
                false,
            )
        }
    }

    private fun drawTerrain(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val inner = radius * 0.75f
        canvas.save()
        canvas.clipCircle(cx, cy, inner)
        val destination = RectF(cx - inner * 1.5f, cy - inner, cx + inner * 1.5f, cy + inner)
        canvas.drawBitmap(dialLandscape, null, destination, bitmapPaint)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            cx,
            cy - inner,
            cx,
            cy + inner,
            intArrayOf(Color.argb(90, 0, 0, 0), Color.argb(55, 0, 0, 0), Color.argb(170, 0, 0, 0)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, inner, paint)
        paint.shader = null
        canvas.restore()
    }

    private fun drawCalibrationScale(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        repeat(13) { index ->
            val x = cx + (index - 6) * radius * 0.045f
            val height = radius * if (index == 6) 0.075f else if (index % 3 == 0) 0.052f else 0.032f
            paint.color = if (index == 6) palette.accent else Color.WHITE
            paint.strokeWidth = radius * if (index == 6) 0.012f else 0.006f
            canvas.drawLine(x, cy - height / 2f, x, cy + height / 2f, paint)
        }
        paint.style = Paint.Style.FILL
        drawText(canvas, "0°", cx, cy + radius * 0.10f, radius * 0.045f, Color.WHITE, Paint.Align.CENTER, false)
    }

    private fun drawUtvBitmap(canvas: Canvas, cx: Float, cy: Float, radius: Float, sideView: Boolean) {
        val bitmap = if (sideView) utvSide else utvFront
        val targetWidth = radius * if (sideView) 1.35f else 1.10f
        val targetHeight = targetWidth * bitmap.height / bitmap.width
        val verticalOffset = if (sideView) radius * 0.04f else radius * 0.02f
        val target = RectF(
            cx - targetWidth / 2f,
            cy - targetHeight / 2f + verticalOffset,
            cx + targetWidth / 2f,
            cy + targetHeight / 2f + verticalOffset,
        )
        canvas.drawBitmap(bitmap, null, target, bitmapPaint)
    }

    private fun Canvas.clipCircle(cx: Float, cy: Float, radius: Float) {
        val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        clipPath(clip)
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
            val designX = (event.x - contentOffsetX) / contentScale
            val designY = (event.y - contentOffsetY) / contentScale
            touchZones.entries.firstOrNull { it.value.contains(designX, designY) }?.let {
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

    companion object {
        private const val DESIGN_WIDTH = 1280f
        private const val DESIGN_HEIGHT = 408f
    }
}
