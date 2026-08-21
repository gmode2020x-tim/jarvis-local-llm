package ca.gmode.triprecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.settings.SecureSettings
import ca.gmode.triprecorder.sync.SyncScheduler
import ca.gmode.triprecorder.sync.SyncStatusStore
import ca.gmode.triprecorder.tracking.TrackingService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var repository: RecordingRepository
    private lateinit var secureSettings: SecureSettings
    private lateinit var syncStatus: SyncStatusStore

    private lateinit var tripName: EditText
    private lateinit var tripType: Spinner
    private lateinit var baseUrl: EditText
    private lateinit var token: EditText
    private lateinit var recorderStatus: TextView
    private lateinit var telemetryStatus: TextView
    private lateinit var synchronizationStatus: TextView
    private lateinit var gpsChip: TextView
    private lateinit var queueChip: TextView
    private lateinit var homeAssistantChip: TextView
    private lateinit var dashboardClock: TextView
    private lateinit var gauge: DashboardGaugeView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private var startAfterPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (locationGranted && startAfterPermission) startRecording()
        startAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RecordingRepository(AppDatabase.get(this).tripDao())
        secureSettings = SecureSettings(this)
        syncStatus = SyncStatusStore(this)
        setContentView(createContent())
        bindActions()
        refreshContinuously()
    }

    private fun createContent(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(32))
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dashboardClock = text("--:--", 27f, Color.WHITE, bold = true).apply { gravity = Gravity.END }
        content.addView(
            horizontalViews(
                text("GMODE\nTRIP RECORDER", 14f, ORANGE, bold = true),
                dashboardClock,
            ).withBottom(dp(10)),
        )

        gpsChip = statusChip("GPS STANDBY")
        queueChip = statusChip("0 PENDING")
        homeAssistantChip = statusChip("HA CHECKING")
        content.addView(horizontalViews(gpsChip, queueChip, homeAssistantChip).withBottom(dp(10)))

        gauge = DashboardGaugeView(this)
        content.addView(
            MaterialCardView(this).apply {
                radius = dp(22).toFloat()
                strokeWidth = dp(1)
                strokeColor = OUTLINE
                setCardBackgroundColor(Color.BLACK)
                addView(gauge)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                }
            },
        )

        recorderStatus = text("Checking recorder…", 17f, Color.WHITE, bold = true)
        telemetryStatus = text("", 13f, MUTED)
        content.addView(card("TRIP STATUS", recorderStatus, telemetryStatus))

        tripName = editText("Optional trip name")
        tripType = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                TRIP_TYPE_LABELS,
            )
            backgroundTintList = ColorStateList.valueOf(ORANGE)
        }
        startButton = dashboardButton("START TRIP", filled = true)
        stopButton = dashboardButton("STOP + SYNC", filled = false).apply { isEnabled = false }
        content.addView(card("NEW TRIP", tripName, tripType, horizontalButtons(startButton, stopButton)))

        baseUrl = editText("Home Assistant URL").apply { setText(secureSettings.baseUrl) }
        token = editText(if (secureSettings.hasToken()) "Access token saved — leave blank to keep it" else "Long-lived access token").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val save = dashboardButton("SAVE CONNECTION", filled = true)
        val sync = dashboardButton("SYNC NOW", filled = false)
        synchronizationStatus = text("", 13f, MUTED)
        val connectionBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            addView(baseUrl.withBottom(dp(7)))
            addView(token.withBottom(dp(7)))
            addView(horizontalButtons(save, sync).withBottom(dp(7)))
            addView(synchronizationStatus)
        }
        val connectionToggle = dashboardButton("HOME ASSISTANT CONNECTION", filled = false)
        connectionToggle.setOnClickListener {
            connectionBody.visibility = if (connectionBody.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }

        val battery = dashboardButton("S24 BATTERY SETTINGS", filled = false)
        content.addView(card("SYSTEM", horizontalButtons(connectionToggle, battery), connectionBody))
        content.addView(
            text(
                "GPS 5 sec / 5 m  •  BAROMETER  •  ACCELERATION  •  GYROSCOPE\nLocal-first recording; automatic Home Assistant retry.",
                11f,
                Color.parseColor("#777777"),
            ).apply { gravity = Gravity.CENTER }.withBottom(dp(6)),
        )

        save.setOnClickListener { saveConnection() }
        sync.setOnClickListener {
            SyncScheduler.enqueue(this)
            Toast.makeText(this, "Synchronization queued", Toast.LENGTH_SHORT).show()
        }
        battery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        return scroll
    }

    private fun bindActions() {
        startButton.setOnClickListener {
            if (hasFineLocation()) {
                startRecording()
            } else {
                startAfterPermission = true
                val permissions = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissions.toTypedArray())
            }
        }
        stopButton.setOnClickListener { TrackingService.stop(this) }
    }

    private fun startRecording() {
        lifecycleScope.launch {
            val type = TRIP_TYPE_VALUES[tripType.selectedItemPosition]
            val trip = repository.startTrip(tripName.text.toString(), type)
            TrackingService.start(this@MainActivity, trip.id)
            SyncScheduler.enqueue(this@MainActivity)
            tripName.text.clear()
        }
    }

    private fun saveConnection() {
        val url = baseUrl.text.toString().trim().trimEnd('/')
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        if (parsed == null || parsed.host.isNullOrBlank() || parsed.scheme !in setOf("http", "https")) {
            Toast.makeText(this, "Enter a complete http:// or https:// Home Assistant URL", Toast.LENGTH_LONG).show()
            return
        }
        secureSettings.baseUrl = url
        if (token.text.toString().isNotBlank()) secureSettings.saveToken(token.text.toString())
        token.text.clear()
        token.hint = "Access token saved — leave blank to keep it"
        SyncScheduler.enqueue(this)
        Toast.makeText(this, "Connection saved securely", Toast.LENGTH_SHORT).show()
    }

    private fun refreshContinuously() {
        lifecycleScope.launch {
            while (isActive) {
                val active = repository.activeTrip()
                val pending = repository.pendingPointCount()
                if (active == null) {
                    recorderStatus.text = "Ready to record"
                    telemetryStatus.text = "$pending points waiting to synchronize"
                    gauge.setTelemetry(false, "READY", 0, 0.0, "0:00", null)
                    setChip(gpsChip, "GPS STANDBY", false)
                    startButton.isEnabled = true
                    stopButton.isEnabled = false
                } else {
                    val duration = Duration.between(Instant.parse(active.startAt), Instant.now())
                    val speed = ((active.lastSpeedMps ?: 0.0) * 3.6).roundToInt()
                    recorderStatus.text = "${active.title} • ${formatDuration(duration)}"
                    telemetryStatus.text = buildString {
                        append("${"%.2f".format(active.distanceMeters / 1000)} km • $speed km/h")
                        active.lastAccuracyMeters?.let { append(" • GPS ±${it.roundToInt()} m") }
                        append("\n${active.pointCount} recorded • $pending waiting to sync")
                    }
                    gauge.setTelemetry(
                        true,
                        active.title,
                        speed,
                        active.distanceMeters / 1000,
                        formatDuration(duration),
                        active.lastAccuracyMeters?.roundToInt(),
                    )
                    setChip(gpsChip, active.lastAccuracyMeters?.let { "GPS ±${it.roundToInt()} M" } ?: "GPS SEARCHING", active.lastAccuracyMeters != null)
                    startButton.isEnabled = false
                    stopButton.isEnabled = true
                }
                val sync = syncStatus.read()
                synchronizationStatus.text = buildString {
                    append(sync.state)
                    if (sync.message.isNotBlank()) append(" — ${sync.message}")
                }
                setChip(queueChip, "$pending PENDING", pending == 0)
                val configured = secureSettings.baseUrl.isNotBlank() && secureSettings.hasToken()
                val syncFailed = sync.state.contains("fail", ignoreCase = true) ||
                    sync.state.contains("error", ignoreCase = true) ||
                    sync.state.contains("offline", ignoreCase = true)
                val syncGood = configured && !syncFailed && (
                    sync.state.contains("success", ignoreCase = true) ||
                        sync.state.contains("complete", ignoreCase = true) || pending == 0
                    )
                val homeAssistantLabel = when {
                    !configured -> "HA SETUP"
                    syncFailed -> "HA OFFLINE"
                    pending > 0 -> "HA SYNCING"
                    else -> "HA READY"
                }
                setChip(homeAssistantChip, homeAssistantLabel, syncGood)
                dashboardClock.text = LocalTime.now().format(TIME_FORMAT)
                delay(1_000)
            }
        }
    }

    private fun card(label: String, vararg children: android.view.View): MaterialCardView {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            addView(text(label, 11f, ORANGE, bold = true).withBottom(dp(10)))
            children.forEach { child -> addView(child.withBottom(dp(9))) }
        }
        return MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            strokeWidth = dp(1)
            strokeColor = OUTLINE
            setCardBackgroundColor(PANEL)
            addView(body)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
    }

    private fun horizontalButtons(vararg buttons: MaterialButton): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        buttons.forEach { button ->
            addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            })
        }
    }

    private fun horizontalViews(vararg views: android.view.View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index < views.lastIndex) marginEnd = dp(6)
            })
        }
    }

    private fun statusChip(label: String): TextView = text(label, 9.5f, MUTED, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(7), dp(7), dp(7), dp(7))
        background = chipBackground(false)
    }

    private fun setChip(chip: TextView, label: String, good: Boolean) {
        chip.text = label
        chip.setTextColor(if (good) Color.WHITE else MUTED)
        chip.background = chipBackground(good)
    }

    private fun chipBackground(active: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(5).toFloat()
        setColor(if (active) Color.parseColor("#4A2105") else Color.parseColor("#171717"))
        setStroke(dp(1), if (active) ORANGE else OUTLINE)
    }

    private fun dashboardButton(label: String, filled: Boolean): MaterialButton = MaterialButton(this).apply {
        text = label
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        cornerRadius = dp(7)
        minHeight = dp(52)
        insetTop = 0
        insetBottom = 0
        if (filled) {
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setTextColor(Color.BLACK)
        } else {
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#232323"))
            strokeColor = ColorStateList.valueOf(ORANGE)
            strokeWidth = dp(1)
            setTextColor(Color.WHITE)
        }
    }

    private fun editText(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#858585"))
        backgroundTintList = ColorStateList.valueOf(ORANGE)
        setSingleLine(true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(0f, 1.15f)
    }

    private fun <T : android.view.View> T.withBottom(bottom: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = bottom
        }
    }

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun formatDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        return "%d:%02d".format(totalMinutes / 60, totalMinutes % 60)
    }

    companion object {
        private val BACKGROUND = Color.parseColor("#070707")
        private val PANEL = Color.parseColor("#151515")
        private val OUTLINE = Color.parseColor("#393939")
        private val ORANGE = Color.parseColor("#FF7900")
        private val MUTED = Color.parseColor("#AAAAAA")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm")
        private val TRIP_TYPE_LABELS = listOf("Street", "Off road", "Snow", "Water")
        private val TRIP_TYPE_VALUES = listOf("street", "off_road", "snow", "water")
    }
}
