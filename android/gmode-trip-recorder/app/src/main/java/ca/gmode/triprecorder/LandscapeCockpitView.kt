package ca.gmode.triprecorder

import android.content.Context
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import ca.gmode.triprecorder.settings.DashboardPalette
import ca.gmode.triprecorder.settings.SideButtonConfig
import ca.gmode.triprecorder.settings.SideButtonSettings
import ca.gmode.triprecorder.settings.SideButtonSlot
import ca.gmode.triprecorder.settings.DashboardSettings
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
    val wifiConnected: Boolean = false,
    val networkConnected: Boolean = false,
    val bluetoothEnabled: Boolean? = null,
    val gpsReady: Boolean = false,
    val satelliteCount: Int? = null,
    val pendingCount: Int = 0,
    val homeAssistantConnected: Boolean = false,
    val batteryPercent: Int? = null,
    val batteryCharging: Boolean = false,
    val batteryTemperatureC: Double? = null,
    val tripDurationLabel: String = "0:00",
    val readings: List<CockpitReading> = emptyList(),
    val sideButtons: List<SideButtonConfig> = SideButtonSettings.DEFAULTS.values.toList(),
    val vehicleViewModeId: String = DashboardSettings.DEFAULT_VIEW_MODE_ID,
    val pitchDegrees: Double? = null,
    val rollDegrees: Double? = null,
)

data class CornerIndicatorSnapshot(
    val wifiConnected: Boolean,
    val networkConnected: Boolean,
    val bluetoothEnabled: Boolean?,
    val gpsReady: Boolean,
    val pendingCount: Int,
    val batteryPercent: Int?,
    val batteryCharging: Boolean,
    val recording: Boolean,
    val tripDurationLabel: String,
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
    BLUETOOTH,
    SIDE_LEFT_TOP,
    SIDE_LEFT_MIDDLE,
    SIDE_LEFT_BOTTOM,
    SIDE_RIGHT_TOP,
    SIDE_RIGHT_MIDDLE,
    SIDE_RIGHT_BOTTOM,
}

class LandscapeCockpitView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val utvSide: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.utv_side) }
    private val utvFront: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.utv_front) }
    private val dialBackgroundCache = mutableMapOf<Int, Bitmap>()
    private val dashboardLeather: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.dashboard_black_leather) }
    private val referenceTop: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.reference_dashboard_top) }
    private val referenceMiddleLeft: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.reference_dashboard_middle_left) }
    private val referenceMiddleCenter: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.reference_dashboard_middle_center) }
    private val referenceMiddleRight: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.reference_dashboard_middle_right) }
    private val referenceFooter: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.reference_dashboard_footer) }
    private val touchZones = linkedMapOf<CockpitAction, RectF>()
    private val activityIconCache = mutableMapOf<String, Bitmap?>()
    private val vehicleArtworkCache = mutableMapOf<String, VehicleArtwork>()
    private var palette = ca.gmode.triprecorder.settings.AppearanceSettings.PRESETS.first()
    private var state = CockpitState()
    private var contentScale = 1f
    private var contentOffsetX = 0f
    private var contentOffsetY = 0f
    private var selectedGaugeIndex = 0
    private val previousGaugeZone = RectF()
    private val nextGaugeZone = RectF()
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
        if (value.readings.isNotEmpty()) selectedGaugeIndex %= value.readings.size else selectedGaugeIndex = 0
        contentDescription = "${value.vehicleLabel} cockpit, ${value.tripLabel}, ${value.gpsLabel}, ${value.homeAssistantLabel}"
        invalidate()
    }

    internal fun activeGaugeTitles(): List<String> = state.readings.map { it.title }

    internal fun activeSideButtons(): List<SideButtonConfig> = state.sideButtons

    internal fun activeBackgroundResourceId(): Int = tripTypeBackgroundResourceId(state.tripTypeLabel)

    internal fun activeVehicleId(): String = state.vehicleId

    internal fun cornerIndicatorSnapshot(): CornerIndicatorSnapshot = CornerIndicatorSnapshot(
        wifiConnected = state.wifiConnected,
        networkConnected = state.networkConnected,
        bluetoothEnabled = state.bluetoothEnabled,
        gpsReady = state.gpsReady,
        pendingCount = state.pendingCount,
        batteryPercent = state.batteryPercent,
        batteryCharging = state.batteryCharging,
        recording = state.recording,
        tripDurationLabel = state.tripDurationLabel,
    )

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
        drawReferenceArtwork(canvas)
        configureReferenceTouchZones()
        val reading = state.readings.getOrNull(selectedGaugeIndex) ?: CockpitReading("PITCH", "--", "degrees")
        drawLiveReferenceContent(canvas, reading)
        canvas.restore()
    }

    private fun drawReferenceArtwork(canvas: Canvas) {
        bitmapPaint.isFilterBitmap = true
        canvas.drawBitmap(referenceTop, null, RectF(0f, 0f, 1280f, 98f), bitmapPaint)
        canvas.drawBitmap(referenceMiddleLeft, null, RectF(0f, 98f, 428f, 466f), bitmapPaint)
        canvas.drawBitmap(referenceMiddleCenter, null, RectF(428f, 98f, 852f, 466f), bitmapPaint)
        canvas.drawBitmap(referenceMiddleRight, null, RectF(852f, 98f, 1280f, 466f), bitmapPaint)
        canvas.drawBitmap(referenceFooter, null, RectF(0f, 466f, 1280f, 592f), bitmapPaint)
    }

    private fun configureReferenceTouchZones() {
        touchZones[CockpitAction.SIDE_LEFT_TOP] = RectF(36f, 98f, 428f, 212f)
        touchZones[CockpitAction.SIDE_LEFT_MIDDLE] = RectF(36f, 216f, 428f, 336f)
        touchZones[CockpitAction.SIDE_LEFT_BOTTOM] = RectF(36f, 338f, 428f, 464f)
        touchZones[CockpitAction.SIDE_RIGHT_TOP] = RectF(852f, 98f, 1244f, 212f)
        touchZones[CockpitAction.SIDE_RIGHT_MIDDLE] = RectF(852f, 216f, 1244f, 336f)
        touchZones[CockpitAction.SIDE_RIGHT_BOTTOM] = RectF(852f, 338f, 1244f, 464f)
        touchZones[CockpitAction.THEME] = RectF(996f, 468f, 1074f, 586f)
        touchZones[CockpitAction.SETTINGS] = RectF(1074f, 468f, 1164f, 586f)
        touchZones[CockpitAction.BLUETOOTH] = RectF(282f, 16f, 343f, 92f)
        previousGaugeZone.set(410f, 468f, 530f, 590f)
        nextGaugeZone.set(750f, 468f, 870f, 590f)
    }

    private fun drawLiveReferenceContent(canvas: Canvas, reading: CockpitReading) {
        drawDynamicGaugeScene(canvas, reading)
        drawReferenceText(canvas, state.time, 640f, 55f, 44f, Color.WHITE, true)
        drawReferenceText(canvas, reading.title.uppercase(), 640f, 136f, 18f, Color.WHITE, true)
        drawReferenceText(canvas, reading.value, 640f, 402f, 38f, palette.accent, true)
        drawReferenceText(canvas, reading.title, 640f, 536f, 24f, Color.WHITE, true)
        val detail = if (reading.subtitle.isNotBlank()) reading.subtitle else state.tripLabel
        drawReferenceText(canvas, detail, 640f, 565f, 18f, palette.accent, true)
        drawConfiguredSideButtons(canvas)
        drawLiveCornerIndicators(canvas)
    }

    private data class VehicleArtwork(val bitmap: Bitmap, val contentBounds: Rect)

    private fun drawDynamicGaugeScene(canvas: Canvas, reading: CockpitReading) {
        val cx = 640f
        val cy = 278f
        canvas.save()
        // Cover the complete inner aperture. The source dashboard center contains its original
        // mountain/UTV scene, so a smaller clip allows that baked-in image to show around the
        // live trip-type background.
        canvas.clipCircle(cx, cy, 174f)
        canvas.drawBitmap(activeBackgroundBitmap(), null, RectF(347f, 40f, 932f, 430f), bitmapPaint)

        val viewId = DashboardSettings.resolveVehicleView(
            modeId = state.vehicleViewModeId,
            gaugeTitle = reading.title,
            pitchDegrees = state.pitchDegrees,
            rollDegrees = state.rollDegrees,
        )
        val resourceId = vehicleResourceId(state.vehicleId, viewId)
        val cacheKey = "${state.vehicleId}:$viewId"
        val artwork = vehicleArtworkCache.getOrPut(cacheKey) {
            val bitmap = BitmapFactory.decodeResource(resources, resourceId)
            VehicleArtwork(bitmap, findOpaqueBounds(bitmap))
        }
        val bounds = artwork.contentBounds
        val maxWidth = if (viewId == "side") 262f else 200f
        val maxHeight = if (viewId == "side") 148f else 202f
        val scale = min(maxWidth / bounds.width().coerceAtLeast(1), maxHeight / bounds.height().coerceAtLeast(1))
        val targetWidth = bounds.width() * scale
        val targetHeight = bounds.height() * scale
        val targetCy = if (viewId == "side") 294f else 286f
        val target = RectF(
            cx - targetWidth / 2f,
            targetCy - targetHeight / 2f,
            cx + targetWidth / 2f,
            targetCy + targetHeight / 2f,
        )
        val tilt = reading.angleDegrees?.coerceIn(-45.0, 45.0)?.toFloat() ?: 0f
        if (reading.title.equals("pitch", ignoreCase = true) || reading.title.equals("roll", ignoreCase = true)) {
            canvas.rotate(tilt * 0.55f, cx, targetCy)
        }
        canvas.drawBitmap(artwork.bitmap, bounds, target, bitmapPaint)
        canvas.restore()
        drawReferenceGaugeMarks(canvas)
    }

    private fun drawReferenceGaugeMarks(canvas: Canvas) {
        listOf(
            Triple("45°", 565f, 148f), Triple("45°", 715f, 148f),
            Triple("15°", 505f, 205f), Triple("15°", 775f, 205f),
            Triple("0°", 480f, 281f), Triple("0°", 800f, 281f),
            Triple("-15°", 507f, 347f), Triple("-15°", 773f, 347f),
            Triple("-45°", 559f, 397f), Triple("-45°", 721f, 397f),
        ).forEach { (label, x, y) ->
            drawReferenceText(canvas, label, x, y, 14f, Color.WHITE, true)
        }

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        repeat(13) { index ->
            val x = 640f + (index - 6) * 5f
            val height = if (index == 6) 11f else if (index % 3 == 0) 8f else 5f
            paint.color = if (index == 6) palette.accent else Color.WHITE
            paint.strokeWidth = if (index == 6) 2f else 1f
            canvas.drawLine(x, 431f - height / 2f, x, 431f + height / 2f, paint)
        }
        paint.style = Paint.Style.FILL
        drawReferenceText(canvas, "0°", 640f, 450f, 12f, Color.WHITE, true)
    }

    private fun tripTypeBackgroundResourceId(tripTypeLabel: String): Int = when (
        tripTypeLabel.trim().lowercase().replace('-', '_').replace(' ', '_')
    ) {
        "street" -> R.drawable.dial_street_landscape
        "snow" -> R.drawable.dial_snow_landscape
        "water" -> R.drawable.dial_water_landscape
        else -> R.drawable.dial_offroad_landscape
    }

    private fun activeBackgroundBitmap(): Bitmap {
        val resourceId = activeBackgroundResourceId()
        return dialBackgroundCache.getOrPut(resourceId) {
            BitmapFactory.decodeResource(resources, resourceId)
        }
    }

    private fun findOpaqueBounds(bitmap: Bitmap): Rect {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, color ->
            if (Color.alpha(color) > 24) {
                val x = index % bitmap.width
                val y = index / bitmap.width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right >= left && bottom >= top) Rect(left, top, right + 1, bottom + 1) else Rect(0, 0, bitmap.width, bitmap.height)
    }

    internal fun vehicleResourceId(vehicleId: String, viewId: String): Int = when (vehicleId) {
        "dirt_bike" -> when (viewId) {
            "front" -> R.drawable.vehicle_dirt_bike_front
            "rear" -> R.drawable.vehicle_dirt_bike_rear
            else -> R.drawable.vehicle_dirt_bike_side
        }
        "quad" -> when (viewId) {
            "front" -> R.drawable.vehicle_quad_front
            "rear" -> R.drawable.vehicle_quad_rear
            else -> R.drawable.vehicle_quad_side
        }
        "snowmobile" -> when (viewId) {
            "front" -> R.drawable.vehicle_snowmobile_front
            "rear" -> R.drawable.vehicle_snowmobile_rear
            else -> R.drawable.vehicle_snowmobile_side
        }
        "three_wheeler" -> when (viewId) {
            "front" -> R.drawable.vehicle_three_wheeler_front
            "rear" -> R.drawable.vehicle_three_wheeler_rear
            else -> R.drawable.vehicle_three_wheeler_side
        }
        "truck" -> when (viewId) {
            "front" -> R.drawable.vehicle_truck_front
            "rear" -> R.drawable.vehicle_truck_rear
            else -> R.drawable.vehicle_truck_side
        }
        "car" -> when (viewId) {
            "front" -> R.drawable.vehicle_car_front
            "rear" -> R.drawable.vehicle_car_rear
            else -> R.drawable.vehicle_car_side
        }
        "street_motorcycle" -> when (viewId) {
            "front" -> R.drawable.vehicle_street_motorcycle_front
            "rear" -> R.drawable.vehicle_street_motorcycle_rear
            else -> R.drawable.vehicle_street_motorcycle_side
        }
        "clown_car" -> when (viewId) {
            "front" -> R.drawable.vehicle_clown_car_front
            "rear" -> R.drawable.vehicle_clown_car_rear
            else -> R.drawable.vehicle_clown_car_side
        }
        "snow_bike" -> when (viewId) {
            "front" -> R.drawable.vehicle_snow_bike_front
            "rear" -> R.drawable.vehicle_snow_bike_rear
            else -> R.drawable.vehicle_snow_bike_side
        }
        "snowcat" -> when (viewId) {
            "front" -> R.drawable.vehicle_snowcat_front
            "rear" -> R.drawable.vehicle_snowcat_rear
            else -> R.drawable.vehicle_snowcat_side
        }
        "tracked_utv" -> when (viewId) {
            "front" -> R.drawable.vehicle_tracked_utv_front
            "rear" -> R.drawable.vehicle_tracked_utv_rear
            else -> R.drawable.vehicle_tracked_utv_side
        }
        "boat" -> when (viewId) {
            "front" -> R.drawable.vehicle_boat_front
            "rear" -> R.drawable.vehicle_boat_rear
            else -> R.drawable.vehicle_boat_side
        }
        "seadoo" -> when (viewId) {
            "front" -> R.drawable.vehicle_seadoo_front
            "rear" -> R.drawable.vehicle_seadoo_rear
            else -> R.drawable.vehicle_seadoo_side
        }
        "hovercraft" -> when (viewId) {
            "front" -> R.drawable.vehicle_hovercraft_front
            "rear" -> R.drawable.vehicle_hovercraft_rear
            else -> R.drawable.vehicle_hovercraft_side
        }
        "kayak" -> when (viewId) {
            "front" -> R.drawable.vehicle_kayak_front
            "rear" -> R.drawable.vehicle_kayak_rear
            else -> R.drawable.vehicle_kayak_side
        }
        else -> when (viewId) {
            "front" -> R.drawable.vehicle_sxs_front
            "rear" -> R.drawable.vehicle_sxs_rear
            else -> R.drawable.vehicle_sxs_side
        }
    }

    private fun drawConfiguredSideButtons(canvas: Canvas) {
        val bySlot = state.sideButtons.associateBy { it.slot }
        val rows = listOf(
            Triple(SideButtonSlot.LEFT_TOP, 155f, true),
            Triple(SideButtonSlot.LEFT_MIDDLE, 276f, true),
            Triple(SideButtonSlot.LEFT_BOTTOM, 397f, true),
            Triple(SideButtonSlot.RIGHT_TOP, 155f, false),
            Triple(SideButtonSlot.RIGHT_MIDDLE, 276f, false),
            Triple(SideButtonSlot.RIGHT_BOTTOM, 397f, false),
        )
        rows.forEach { (slot, y, leftSide) ->
            val config = bySlot[slot] ?: SideButtonSettings.DEFAULTS.getValue(slot)
            val labelX = if (leftSide) 170f else 1110f
            val iconX = if (leftSide) 383f else 912f
            drawFittedReferenceText(canvas, config.label.uppercase(), labelX, y + 9f, 178f)
            drawSideButtonIcon(canvas, config, iconX, y, 30f)
        }
    }

    private fun drawFittedReferenceText(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float) {
        paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
        paint.textSize = 24f
        while (paint.textSize > 15f && paint.measureText(value) > maxWidth) paint.textSize -= 1f
        drawReferenceText(canvas, value, x, y, paint.textSize, Color.WHITE, true)
    }

    private fun drawSideButtonIcon(canvas: Canvas, config: SideButtonConfig, cx: Float, cy: Float, size: Float) {
        if (config.iconId == "app" && drawTargetAppIcon(canvas, config.target, cx, cy, size * 1.85f)) return
        val iconId = if (config.iconId == "app") {
            when (config.target) {
                SideButtonSettings.ACTION_OPEN_MUSIC -> "music"
                SideButtonSettings.ACTION_OPEN_NAVIGATION -> "navigation"
                SideButtonSettings.ACTION_OPEN_CAMERA -> "camera"
                else -> "apps"
            }
        } else {
            config.iconId
        }

        paint.shader = null
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val path = Path()
        when (iconId) {
            "radio" -> {
                canvas.drawRoundRect(RectF(cx - size, cy - size * .55f, cx + size, cy + size * .65f), 4f, 4f, paint)
                canvas.drawCircle(cx + size * .45f, cy + size * .05f, size * .28f, paint)
                canvas.drawLine(cx - size * .7f, cy - size * .7f, cx + size * .65f, cy - size * 1.15f, paint)
                canvas.drawLine(cx - size * .65f, cy - size * .15f, cx - size * .12f, cy - size * .15f, paint)
            }
            "navigation" -> {
                path.moveTo(cx, cy - size)
                path.lineTo(cx + size * .62f, cy + size)
                path.lineTo(cx, cy + size * .62f)
                path.lineTo(cx - size * .62f, cy + size)
                path.close()
                canvas.drawPath(path, paint)
            }
            "music" -> {
                canvas.drawLine(cx - size * .15f, cy - size * .75f, cx + size * .72f, cy - size, paint)
                canvas.drawLine(cx - size * .15f, cy - size * .75f, cx - size * .15f, cy + size * .55f, paint)
                canvas.drawLine(cx + size * .72f, cy - size, cx + size * .72f, cy + size * .3f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawOval(RectF(cx - size * .85f, cy + size * .35f, cx - size * .08f, cy + size), paint)
                canvas.drawOval(RectF(cx + size * .02f, cy + size * .1f, cx + size * .8f, cy + size * .75f), paint)
            }
            "camera" -> {
                canvas.drawRoundRect(RectF(cx - size, cy - size * .65f, cx + size, cy + size * .72f), 7f, 7f, paint)
                canvas.drawCircle(cx, cy + size * .03f, size * .46f, paint)
                path.moveTo(cx - size * .62f, cy - size * .65f)
                path.lineTo(cx - size * .38f, cy - size)
                path.lineTo(cx + size * .25f, cy - size)
                path.lineTo(cx + size * .48f, cy - size * .65f)
                canvas.drawPath(path, paint)
            }
            "phone" -> {
                paint.strokeWidth = size * .28f
                path.moveTo(cx - size * .58f, cy - size * .62f)
                path.cubicTo(cx - size * .85f, cy - size * .08f, cx + size * .18f, cy + size * .85f, cx + size * .65f, cy + size * .55f)
                canvas.drawPath(path, paint)
                paint.strokeWidth = size * .36f
                canvas.drawLine(cx - size * .68f, cy - size * .78f, cx - size * .40f, cy - size * .50f, paint)
                canvas.drawLine(cx + size * .48f, cy + size * .37f, cx + size * .78f, cy + size * .66f, paint)
            }
            "internet" -> drawGlobeStatus(canvas, cx, cy, size * .85f, Color.WHITE)
            "apps" -> {
                paint.style = Paint.Style.FILL
                for (row in -1..1) for (column in -1..1) {
                    val x = cx + column * size * .72f
                    val y = cy + row * size * .72f
                    canvas.drawRoundRect(RectF(x - size * .18f, y - size * .18f, x + size * .18f, y + size * .18f), 3f, 3f, paint)
                }
            }
            "play" -> drawControlIcon(canvas, CockpitAction.START, cx, cy, size)
            "stop" -> drawControlIcon(canvas, CockpitAction.STOP, cx, cy, size)
            "sync" -> drawControlIcon(canvas, CockpitAction.SYNC, cx, cy, size)
            "home" -> drawControlIcon(canvas, CockpitAction.HOME_ASSISTANT, cx, cy, size)
            "settings" -> drawControlIcon(canvas, CockpitAction.SETTINGS, cx, cy, size)
            else -> drawControlIcon(canvas, CockpitAction.SETTINGS, cx, cy, size)
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawTargetAppIcon(canvas: Canvas, target: String, cx: Float, cy: Float, size: Float): Boolean {
        if (!target.startsWith(SideButtonSettings.APP_PREFIX)) return false
        val flattened = target.removePrefix(SideButtonSettings.APP_PREFIX)
        val bitmap = activityIconCache.getOrPut(flattened) {
            val component = ComponentName.unflattenFromString(flattened) ?: return@getOrPut null
            runCatching { context.packageManager.getActivityIcon(component).toBitmap() }.getOrNull()
        } ?: return false
        canvas.drawBitmap(bitmap, null, RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f), bitmapPaint)
        return true
    }

    private fun Drawable.toBitmap(): Bitmap {
        val targetWidth = intrinsicWidth.takeIf { it > 0 } ?: 96
        val targetHeight = intrinsicHeight.takeIf { it > 0 } ?: 96
        return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val iconCanvas = Canvas(bitmap)
            setBounds(0, 0, iconCanvas.width, iconCanvas.height)
            draw(iconCanvas)
        }
    }

    private fun drawLiveCornerIndicators(canvas: Canvas) {
        val active = palette.accent
        val wifiColor = indicatorColor(state.wifiConnected)
        val gpsColor = indicatorColor(state.gpsReady)
        val bluetoothColor = indicatorColor(state.bluetoothEnabled)
        val networkColor = indicatorColor(state.networkConnected)
        val homeAssistantColor = indicatorColor(state.homeAssistantConnected)

        drawWifiStatus(canvas, 162f, 54f, 22f, wifiColor)
        drawSatelliteStatus(canvas, 238f, 54f, 20f, gpsColor)
        drawReferenceText(canvas, state.satelliteCount?.toString() ?: "NO", 238f, 83f, 10f, gpsColor, true)
        drawBluetoothStatus(canvas, 313f, 54f, 21f, bluetoothColor)
        if (state.bluetoothEnabled == null) drawReferenceText(canvas, "?", 313f, 85f, 10f, bluetoothColor, true)

        drawGlobeStatus(canvas, 954f, 54f, 21f, networkColor)
        drawReferenceText(canvas, "HA", 954f, 84f, 10f, homeAssistantColor, true)
        drawThermometerStatus(canvas, 1028f, 53f, 20f, active)
        val temperature = state.batteryTemperatureC?.let { "${it.toInt()}°" } ?: "--°"
        drawReferenceText(canvas, temperature, 1070f, 61f, 16f, active, true)
        drawReferenceText(canvas, state.pendingCount.toString(), 1132f, 61f, 16f, active, true)
        drawReferenceText(canvas, "Q", 1132f, 83f, 9f, active, true)

        drawSatelliteStatus(canvas, 149f, 521f, 15f, gpsColor)
        drawReferenceText(canvas, if (state.recording) "REC" else "STBY", 181f, 511f, 12f, indicatorColor(state.recording), true)
        drawTripTypeStatus(canvas, 230f, 523f, active)
        drawReferenceText(canvas, state.tripDurationLabel, 294f, 535f, 23f, active, false)

        drawBatteryStatus(canvas, 966f, 522f, state.batteryPercent, state.batteryCharging)
        drawSunStatus(canvas, 1041f, 522f, active)
        drawGearStatus(canvas, 1115f, 522f, active)
    }

    private fun indicatorColor(enabled: Boolean?): Int = when (enabled) {
        true -> palette.accent
        false -> Color.rgb(72, 8, 13)
        null -> Color.rgb(115, 18, 24)
    }

    private fun drawTripTypeStatus(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = color
        canvas.drawRoundRect(RectF(cx - 17f, cy - 21f, cx + 17f, cy + 21f), 2f, 2f, paint)
        val code = when (state.tripTypeLabel.lowercase()) {
            "street" -> "S"
            "snow" -> "N"
            "water" -> "W"
            else -> "O"
        }
        drawReferenceText(canvas, code, cx, cy + 11f, 28f, color, true)
    }

    private fun drawBatteryStatus(canvas: Canvas, cx: Float, cy: Float, percentage: Int?, charging: Boolean) {
        val color = indicatorColor(percentage != null)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = color
        canvas.drawRoundRect(RectF(cx - 12f, cy - 22f, cx + 12f, cy + 21f), 3f, 3f, paint)
        canvas.drawRect(cx - 5f, cy - 27f, cx + 5f, cy - 22f, paint)
        percentage?.coerceIn(0, 100)?.let { value ->
            paint.style = Paint.Style.FILL
            val fillTop = cy + 18f - 37f * (value / 100f)
            canvas.drawRect(cx - 8f, fillTop, cx + 8f, cy + 17f, paint)
        }
        if (charging) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = Color.WHITE
            val bolt = Path().apply {
                moveTo(cx + 2f, cy - 14f)
                lineTo(cx - 6f, cy + 1f)
                lineTo(cx, cy + 1f)
                lineTo(cx - 2f, cy + 14f)
                lineTo(cx + 7f, cy - 3f)
                lineTo(cx + 1f, cy - 3f)
            }
            canvas.drawPath(bolt, paint)
        }
        drawReferenceText(canvas, percentage?.let { "$it%" } ?: "--", cx, cy + 41f, 11f, color, true)
    }

    private fun drawSunStatus(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        canvas.drawCircle(cx, cy, 13f, paint)
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            canvas.drawLine(
                cx + cos(angle).toFloat() * 19f,
                cy + sin(angle).toFloat() * 19f,
                cx + cos(angle).toFloat() * 27f,
                cy + sin(angle).toFloat() * 27f,
                paint,
            )
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawGearStatus(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        canvas.drawCircle(cx, cy, 15f, paint)
        canvas.drawCircle(cx, cy, 4f, paint)
        repeat(8) { index ->
            val angle = Math.toRadians(index * 45.0)
            canvas.drawLine(
                cx + cos(angle).toFloat() * 18f,
                cy + sin(angle).toFloat() * 18f,
                cx + cos(angle).toFloat() * 27f,
                cy + sin(angle).toFloat() * 27f,
                paint,
            )
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawReferenceText(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        paint.typeface = android.graphics.Typeface.create(
            "sans-serif-condensed",
            if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
        )
        canvas.drawText(value, x, y, paint)
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
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, 100f, Color.parseColor("#202020"), Color.parseColor("#050505"), Shader.TileMode.CLAMP)
        val leftWing = Path().apply {
            moveTo(70f, 94f)
            lineTo(145f, 0f)
            lineTo(438f, 0f)
            lineTo(355f, 94f)
            close()
        }
        val rightWing = Path().apply {
            moveTo(925f, 94f)
            lineTo(842f, 0f)
            lineTo(1136f, 0f)
            lineTo(1210f, 94f)
            close()
        }
        val centreHood = Path().apply {
            moveTo(350f, 94f)
            cubicTo(405f, 12f, 485f, -14f, 640f, -14f)
            cubicTo(795f, -14f, 875f, 12f, 930f, 94f)
            close()
        }
        canvas.drawPath(leftWing, paint)
        canvas.drawPath(rightWing, paint)
        canvas.drawPath(centreHood, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#4A4A4A")
        canvas.drawPath(leftWing, paint)
        canvas.drawPath(rightWing, paint)
        canvas.drawPath(centreHood, paint)
        canvas.drawLine(58f, 96f, 1222f, 96f, paint)
        paint.style = Paint.Style.FILL

        drawText(canvas, state.time, 640f, 56f, 44f, Color.WHITE, Paint.Align.CENTER, true)
        drawText(canvas, "−", 462f, 70f, 43f, Color.parseColor("#777777"), Paint.Align.CENTER, true)
        drawText(canvas, "+", 818f, 70f, 43f, Color.parseColor("#777777"), Paint.Align.CENTER, true)
        drawWifiStatus(canvas, 162f, 54f, 23f)
        drawSatelliteStatus(canvas, 238f, 54f, 22f)
        drawBluetoothStatus(canvas, 313f, 54f, 23f)
        drawGlobeStatus(canvas, 954f, 54f, 23f)
        drawThermometerStatus(canvas, 1028f, 54f, 23f)
        val pendingCount = state.pendingLabel.substringBefore(' ').toIntOrNull() ?: 0
        drawText(
            canvas,
            if (pendingCount == 0) "− − −" else pendingCount.toString(),
            1101f,
            60f,
            17f,
            palette.accent,
            Paint.Align.CENTER,
            true,
        )
    }

    private fun drawWifiStatus(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int = palette.accent) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 3f
        paint.color = color
        for (radius in floatArrayOf(size, size * 0.68f, size * 0.36f)) {
            canvas.drawArc(RectF(cx - radius, cy - radius * 0.70f, cx + radius, cy + radius * 1.30f), 220f, 100f, false, paint)
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + size * 0.65f, 2.7f, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawSatelliteStatus(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int = palette.accent) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx, cy - size, cx, cy + size * 0.70f, paint)
        canvas.drawLine(cx - size * 0.52f, cy - size * 0.22f, cx + size * 0.52f, cy + size * 0.22f, paint)
        canvas.drawLine(cx - size * 0.66f, cy + size * 0.44f, cx + size * 0.66f, cy - size * 0.44f, paint)
        canvas.drawArc(RectF(cx - size, cy - size * 0.10f, cx - size * 0.12f, cy + size * 0.96f), 245f, 80f, false, paint)
        canvas.drawArc(RectF(cx + size * 0.12f, cy - size * 0.96f, cx + size, cy + size * 0.10f), 65f, 80f, false, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 3.2f, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawBluetoothStatus(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int = palette.accent) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        val path = Path().apply {
            moveTo(cx, cy - size)
            lineTo(cx + size * 0.55f, cy - size * 0.42f)
            lineTo(cx - size * 0.42f, cy + size * 0.46f)
            lineTo(cx + size * 0.55f, cy + size)
            lineTo(cx, cy + size)
            close()
            moveTo(cx, cy - size)
            lineTo(cx, cy + size)
            moveTo(cx - size * 0.55f, cy - size * 0.55f)
            lineTo(cx + size * 0.55f, cy + size * 0.46f)
        }
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawGlobeStatus(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int = palette.accent) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawCircle(cx, cy, size, paint)
        canvas.drawOval(RectF(cx - size * 0.45f, cy - size, cx + size * 0.45f, cy + size), paint)
        canvas.drawLine(cx - size, cy, cx + size, cy, paint)
        canvas.drawArc(RectF(cx - size, cy - size * 0.62f, cx + size, cy + size * 0.62f), 205f, 130f, false, paint)
        canvas.drawArc(RectF(cx - size, cy - size * 0.62f, cx + size, cy + size * 0.62f), 25f, 130f, false, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx + size * 0.72f, cy - size * 0.78f, 4.5f, paint)
    }

    private fun drawThermometerStatus(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int = palette.accent) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx, cy - size, cx, cy + size * 0.46f, paint)
        canvas.drawRoundRect(RectF(cx - 5f, cy - size, cx + 5f, cy + size * 0.54f), 5f, 5f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + size * 0.58f, 8f, paint)
        canvas.drawRect(cx - 2f, cy - size * 0.20f, cx + 2f, cy + size * 0.58f, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawSideControls(canvas: Canvas, w: Float, h: Float) {
        val labelsLeft = listOf(
            CockpitAction.START to if (state.recording) "RECORDING" else "START",
            CockpitAction.TRIP_TYPE to state.tripTypeLabel,
            CockpitAction.AUTO to if (state.automaticArmed) "AUTO ARMED" else "AUTO",
        )
        val labelsRight = listOf(
            CockpitAction.STOP to "STOP",
            CockpitAction.SYNC to "SYNC",
            CockpitAction.HOME_ASSISTANT to "HA LINK",
        )
        val rowTops = floatArrayOf(98f, 216f, 338f)
        val rowBottoms = floatArrayOf(212f, 336f, 464f)
        labelsLeft.forEachIndexed { index, pair ->
            drawReferenceControl(canvas, rowTops[index], rowBottoms[index], pair.first, pair.second, leftSide = true)
        }
        labelsRight.forEachIndexed { index, pair ->
            drawReferenceControl(canvas, rowTops[index], rowBottoms[index], pair.first, pair.second, leftSide = false)
        }
    }

    private fun drawReferenceControl(canvas: Canvas, top: Float, bottom: Float, action: CockpitAction, label: String, leftSide: Boolean) {
        val labelRect = if (leftSide) RectF(36f, top, 307f, bottom) else RectF(972f, top, 1244f, bottom)
        val iconRect = if (leftSide) RectF(307f, top, 428f, bottom) else RectF(852f, top, 972f, bottom)
        val overall = RectF(min(labelRect.left, iconRect.left), top, maxOf(labelRect.right, iconRect.right), bottom)
        touchZones[action] = overall
        drawReferencePanel(canvas, labelRect)
        drawReferencePanel(canvas, iconRect)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 6f
        paint.color = palette.accent
        val underlineY = bottom - 7f
        if (leftSide) canvas.drawLine(88f, underlineY, 270f, underlineY, paint)
        else canvas.drawLine(1008f, underlineY, 1191f, underlineY, paint)
        paint.strokeCap = Paint.Cap.BUTT

        val labelSize = if (label.length > 10) 20f else 24f
        drawText(canvas, label.uppercase(), labelRect.centerX(), labelRect.centerY() + 9f, labelSize, Color.WHITE, Paint.Align.CENTER, true)
        drawControlIcon(canvas, action, iconRect.centerX(), iconRect.centerY(), 31f)
    }

    private fun drawReferencePanel(canvas: Canvas, rect: RectF) {
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, Color.parseColor("#242424"), Color.parseColor("#070707"), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.shader = BitmapShader(dashboardLeather, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.alpha = 52
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.alpha = 255
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#353535")
        canvas.drawRoundRect(rect, 8f, 8f, paint)
    }

    private fun drawFooter(canvas: Canvas, w: Float, h: Float, reading: CockpitReading) {
        val footer = Path().apply {
            moveTo(69f, 467f)
            lineTo(1211f, 467f)
            lineTo(1130f, 592f)
            lineTo(151f, 592f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 467f, 0f, 592f, Color.parseColor("#1C1C1C"), Color.parseColor("#050505"), Shader.TileMode.CLAMP)
        canvas.drawPath(footer, paint)
        paint.shader = BitmapShader(dashboardLeather, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.alpha = 42
        canvas.drawPath(footer, paint)
        paint.alpha = 255
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#4A4A4A")
        canvas.drawPath(footer, paint)

        previousGaugeZone.set(420f, 480f, 520f, 574f)
        nextGaugeZone.set(760f, 480f, 860f, 574f)
        drawChevron(canvas, 467f, 522f, pointsRight = false)
        drawChevron(canvas, 814f, 522f, pointsRight = true)

        val activeNumber = if (state.readings.isEmpty()) 1 else selectedGaugeIndex + 1
        val total = maxOf(state.readings.size, 1)
        drawText(canvas, reading.title.uppercase(), 640f, 531f, 24f, Color.WHITE, Paint.Align.CENTER, true)
        drawText(canvas, "$activeNumber / $total  •  ${state.tripLabel}", 640f, 562f, 15f, palette.accent, Paint.Align.CENTER, false)

        drawText(canvas, state.gpsLabel, 157f, 523f, 16f, palette.accent, Paint.Align.CENTER, true)
        drawText(canvas, state.tripTypeLabel, 258f, 523f, 20f, palette.accent, Paint.Align.CENTER, true)
        drawText(canvas, state.pendingLabel, 950f, 555f, 13f, palette.accent, Paint.Align.CENTER, true)

        val themeZone = RectF(995f, 484f, 1074f, 574f)
        val settingsZone = RectF(1075f, 484f, 1154f, 574f)
        touchZones[CockpitAction.THEME] = themeZone
        touchZones[CockpitAction.SETTINGS] = settingsZone
        drawControlIcon(canvas, CockpitAction.THEME, themeZone.centerX(), 522f, 24f)
        drawControlIcon(canvas, CockpitAction.SETTINGS, settingsZone.centerX(), 522f, 24f)

        paint.style = Paint.Style.FILL
        paint.color = palette.accent
        canvas.drawRoundRect(RectF(935f, 501f, 952f, 535f), 3f, 3f, paint)
        canvas.drawRect(940f, 495f, 947f, 502f, paint)
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, pointsRight: Boolean) {
        val direction = if (pointsRight) 1f else -1f
        val path = Path().apply {
            moveTo(cx - direction * 12f, cy - 18f)
            lineTo(cx + direction * 7f, cy)
            lineTo(cx - direction * 12f, cy + 18f)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.strokeJoin = Paint.Join.MITER
        paint.strokeCap = Paint.Cap.SQUARE
        paint.color = Color.parseColor("#777777")
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
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
            CockpitAction.BLUETOOTH -> drawBluetoothStatus(canvas, cx, cy, size * 0.72f, Color.WHITE)
            CockpitAction.SIDE_LEFT_TOP,
            CockpitAction.SIDE_LEFT_MIDDLE,
            CockpitAction.SIDE_LEFT_BOTTOM,
            CockpitAction.SIDE_RIGHT_TOP,
            CockpitAction.SIDE_RIGHT_MIDDLE,
            CockpitAction.SIDE_RIGHT_BOTTOM,
            -> Unit
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
        canvas.drawBitmap(activeBackgroundBitmap(), null, destination, bitmapPaint)
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
            if (state.readings.isNotEmpty() && previousGaugeZone.contains(designX, designY)) {
                selectedGaugeIndex = (selectedGaugeIndex - 1 + state.readings.size) % state.readings.size
                invalidate()
                performClick()
                return true
            }
            if (state.readings.isNotEmpty() && nextGaugeZone.contains(designX, designY)) {
                selectedGaugeIndex = (selectedGaugeIndex + 1) % state.readings.size
                invalidate()
                performClick()
                return true
            }
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
        private const val DESIGN_HEIGHT = 592f
    }
}
