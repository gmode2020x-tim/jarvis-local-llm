package ca.gmode.triprecorder

import android.Manifest
import android.annotation.SuppressLint
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
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import ca.gmode.triprecorder.auto.AutoRecordingManager
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import ca.gmode.triprecorder.settings.AppearanceConfig
import ca.gmode.triprecorder.settings.AppearanceSettings
import ca.gmode.triprecorder.settings.DashboardPalette
import ca.gmode.triprecorder.settings.DashboardConfig
import ca.gmode.triprecorder.settings.DashboardSettings
import ca.gmode.triprecorder.settings.SecureSettings
import ca.gmode.triprecorder.sync.SyncScheduler
import ca.gmode.triprecorder.sync.SyncStatusStore
import ca.gmode.triprecorder.tracking.TrackingService
import ca.gmode.triprecorder.tracking.LiveTelemetry
import ca.gmode.triprecorder.tracking.LiveTelemetryStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var repository: RecordingRepository
    private lateinit var secureSettings: SecureSettings
    private lateinit var syncStatus: SyncStatusStore
    private lateinit var autoSettings: AutoRecordingSettings
    private lateinit var autoState: AutoRecordingStateStore
    private lateinit var autoManager: AutoRecordingManager
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var appearanceSettings: AppearanceSettings
    private lateinit var dashboardSettings: DashboardSettings
    private lateinit var liveTelemetryStore: LiveTelemetryStore
    private lateinit var palette: DashboardPalette
    private lateinit var dashboardConfig: DashboardConfig

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
    private val cockpitGauges = linkedMapOf<String, CockpitGaugeView>()
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var autoEnabledSwitch: MaterialSwitch
    private lateinit var homeLocationStatus: TextView
    private lateinit var autoStatus: TextView
    private lateinit var autoPermissionStatus: TextView
    private lateinit var homeRadiusInput: EditText
    private lateinit var returnDwellInput: EditText
    private lateinit var locationIntervalInput: EditText
    private lateinit var minimumDistanceInput: EditText
    private lateinit var autoTripType: Spinner
    private lateinit var themeSpinner: Spinner
    private lateinit var customAccentInput: EditText
    private var pendingHomeLatitude: Double? = null
    private var pendingHomeLongitude: Double? = null
    private var startAfterPermission = false
    private var captureHomeAfterPermission = false
    private var saveAutoAfterPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (locationGranted && startAfterPermission) startRecording()
        if (locationGranted && captureHomeAfterPermission) captureHomeLocation()
        if (locationGranted && saveAutoAfterPermission) saveAutoSettings()
        startAfterPermission = false
        captureHomeAfterPermission = false
        saveAutoAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RecordingRepository(AppDatabase.get(this).tripDao())
        secureSettings = SecureSettings(this)
        syncStatus = SyncStatusStore(this)
        autoSettings = AutoRecordingSettings(this)
        autoState = AutoRecordingStateStore(this)
        autoManager = AutoRecordingManager(this)
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        appearanceSettings = AppearanceSettings(this)
        dashboardSettings = DashboardSettings(this)
        liveTelemetryStore = LiveTelemetryStore(this)
        palette = appearanceSettings.palette()
        dashboardConfig = dashboardSettings.read()
        applySystemBarPalette()
        setContentView(createContent())
        bindActions()
        refreshContinuously()
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarPalette() {
        window.statusBarColor = palette.background
        window.navigationBarColor = palette.background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (::autoEnabledSwitch.isInitialized) {
            refreshAutoUi()
            if (autoSettings.read().enabled) autoManager.refreshRegistration { _, _ -> refreshAutoUi() }
        }
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

        val vehicleLabel = DashboardSettings.VEHICLES.first { it.id == dashboardConfig.vehicleId }.label
        content.addView(
            horizontalViews(
                text("$vehicleLabel COCKPIT", 12f, Color.WHITE, bold = true),
                text("LIVE S24 TELEMETRY", 10f, ORANGE, bold = true).apply { gravity = Gravity.END },
            ).withBottom(dp(6)),
        )
        val cockpitGrid = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        cockpitGauges.clear()
        dashboardConfig.gaugeIds.forEach { gaugeId ->
            val gaugeView = CockpitGaugeView(this).apply { setPalette(palette) }
            cockpitGauges[gaugeId] = gaugeView
            cockpitGrid.addView(
                gaugeView,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(154)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                },
            )
        }
        content.addView(cockpitGrid.withBottom(dp(12)))

        recorderStatus = text("Checking recorder…", 17f, Color.WHITE, bold = true)
        telemetryStatus = text("", 13f, MUTED)
        content.addView(card("TRIP STATUS", recorderStatus, telemetryStatus))

        tripName = editText("Optional trip name")
        tripType = Spinner(this).apply {
            adapter = tripTypeAdapter()
            backgroundTintList = ColorStateList.valueOf(ORANGE)
        }
        startButton = dashboardButton("START TRIP", filled = true)
        stopButton = dashboardButton("STOP + SYNC", filled = false).apply { isEnabled = false }
        content.addView(card("NEW TRIP", tripName, tripType, horizontalButtons(startButton, stopButton)))

        val automaticConfig = autoSettings.read()
        pendingHomeLatitude = automaticConfig.homeLatitude
        pendingHomeLongitude = automaticConfig.homeLongitude
        autoEnabledSwitch = MaterialSwitch(this).apply {
            text = "START WHEN I LEAVE HOME"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = automaticConfig.enabled
            thumbTintList = checkedStateList(ORANGE, Color.parseColor("#777777"))
            trackTintList = checkedStateList(palette.activeSurface, Color.parseColor("#333333"))
        }
        autoStatus = text(autoState.status(), 13f, Color.WHITE, bold = true)
        homeLocationStatus = text(homeLocationLabel(), 12f, MUTED)
        autoPermissionStatus = text("", 12f, MUTED)
        homeRadiusInput = numberInput(automaticConfig.homeRadiusMeters)
        returnDwellInput = numberInput(automaticConfig.returnDwellMinutes)
        locationIntervalInput = numberInput(automaticConfig.locationIntervalSeconds)
        minimumDistanceInput = numberInput(automaticConfig.minimumDistanceMeters)
        autoTripType = Spinner(this).apply {
            adapter = tripTypeAdapter()
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setSelection(TRIP_TYPE_VALUES.indexOf(automaticConfig.tripType).coerceAtLeast(0))
        }
        val useCurrentLocation = dashboardButton("USE CURRENT LOCATION", filled = false)
        val saveAutomatic = dashboardButton("SAVE AUTO SETTINGS", filled = true)
        val locationPermission = dashboardButton("LOCATION PERMISSION", filled = false)
        content.addView(
            card(
                "AUTOMATIC RECORDING",
                autoEnabledSwitch,
                autoStatus,
                homeLocationStatus,
                useCurrentLocation,
                horizontalViews(
                    labeledInput("HOME RADIUS (M)", homeRadiusInput),
                    labeledInput("RETURN DELAY (MIN)", returnDwellInput),
                ),
                horizontalViews(
                    labeledInput("GPS INTERVAL (SEC)", locationIntervalInput),
                    labeledInput("MIN MOVEMENT (M)", minimumDistanceInput),
                ),
                labeledInput("AUTOMATIC TRIP TYPE", autoTripType),
                horizontalButtons(saveAutomatic, locationPermission),
                autoPermissionStatus,
                text(
                    "Leave the home zone to start. Returning inside it for the delay above stops only an automatically started trip. Manual trips remain under manual control.",
                    11f,
                    MUTED,
                ),
            ),
        )

        val appearanceConfig = appearanceSettings.read()
        themeSpinner = Spinner(this).apply {
            adapter = themeAdapter()
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setSelection(AppearanceSettings.PRESETS.indexOfFirst { it.id == appearanceConfig.themeId }.coerceAtLeast(0))
        }
        customAccentInput = editText("Custom #RRGGBB (optional)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            appearanceConfig.customAccent?.let { setText(AppearanceSettings.colorToHex(it)) }
        }
        val saveTheme = dashboardButton("SAVE + APPLY THEME", filled = true)
        val usePresetColor = dashboardButton("USE PRESET COLOR", filled = false)
        content.addView(
            card(
                "APPEARANCE",
                labeledInput("DASHBOARD THEME", themeSpinner),
                labeledInput("CUSTOM ACCENT COLOR", customAccentInput),
                horizontalButtons(saveTheme, usePresetColor),
                text(
                    "The selected theme changes the dashboard background, panels, gauge, buttons, borders, and highlights. Leave the custom color blank to use the theme's original accent.",
                    11f,
                    MUTED,
                ),
            ),
        )

        val vehicleSpinner = Spinner(this).apply {
            adapter = labelAdapter(DashboardSettings.VEHICLES.map { it.label })
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setSelection(DashboardSettings.VEHICLES.indexOfFirst { it.id == dashboardConfig.vehicleId }.coerceAtLeast(0))
        }
        val gaugeOrder = (
            dashboardConfig.gaugeIds + DashboardSettings.GAUGES.map { it.id }.filterNot { it in dashboardConfig.gaugeIds }
            ).toMutableList()
        val selectedGaugeIds = dashboardConfig.gaugeIds.toMutableSet()
        val gaugeRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val renderRows = {
            renderGaugeRows(gaugeRows, gaugeOrder, selectedGaugeIds)
        }
        renderRows()
        val saveCockpit = dashboardButton("SAVE + APPLY COCKPIT", filled = true)
        val vehicleDefaults = dashboardButton("USE VEHICLE DEFAULTS", filled = false)
        val zeroLevel = dashboardButton("ZERO PITCH + ROLL", filled = false)
        content.addView(
            card(
                "COCKPIT LAYOUT",
                labeledInput("VEHICLE PROFILE", vehicleSpinner),
                text("Enabled gauges fill the dashboard left-to-right in this order. Use the arrows to change each position.", 11f, MUTED),
                gaugeRows,
                horizontalButtons(saveCockpit, vehicleDefaults),
                zeroLevel,
            ),
        )

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
                "GPS ${automaticConfig.locationIntervalSeconds} sec / ${automaticConfig.minimumDistanceMeters} m  •  BAROMETER  •  ACCELERATION  •  GYROSCOPE\nLocal-first recording; automatic Home Assistant retry.",
                11f,
                MUTED,
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
        useCurrentLocation.setOnClickListener {
            if (hasFineLocation()) {
                captureHomeLocation()
            } else {
                captureHomeAfterPermission = true
                requestForegroundLocationPermissions()
            }
        }
        saveAutomatic.setOnClickListener { saveAutoSettings() }
        locationPermission.setOnClickListener { openAppLocationSettings() }
        saveTheme.setOnClickListener { saveAppearance(usePresetAccent = false) }
        usePresetColor.setOnClickListener { saveAppearance(usePresetAccent = true) }
        saveCockpit.setOnClickListener {
            val selected = gaugeOrder.filter { it in selectedGaugeIds }.take(DashboardSettings.MAX_GAUGES)
            if (selected.isEmpty()) {
                Toast.makeText(this, "Enable at least one gauge", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val vehicle = DashboardSettings.VEHICLES[vehicleSpinner.selectedItemPosition]
            dashboardSettings.save(
                DashboardConfig(
                    vehicle.id,
                    selected,
                    dashboardConfig.pitchOffsetDegrees,
                    dashboardConfig.rollOffsetDegrees,
                ),
            )
            Toast.makeText(this, "${vehicle.label} cockpit applied", Toast.LENGTH_SHORT).show()
            recreate()
        }
        vehicleDefaults.setOnClickListener {
            val vehicle = DashboardSettings.VEHICLES[vehicleSpinner.selectedItemPosition]
            val defaults = DashboardSettings.defaultGauges(vehicle.id)
            selectedGaugeIds.clear()
            selectedGaugeIds.addAll(defaults)
            gaugeOrder.clear()
            gaugeOrder.addAll(defaults + DashboardSettings.GAUGES.map { it.id }.filterNot { it in defaults })
            renderRows()
        }
        zeroLevel.setOnClickListener {
            val telemetry = liveTelemetryStore.read()
            if (telemetry.pitchDegrees == null || telemetry.rollDegrees == null) {
                Toast.makeText(this, "Start a trip and wait for S24 orientation data before zeroing", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val vehicle = DashboardSettings.VEHICLES[vehicleSpinner.selectedItemPosition]
            val selected = gaugeOrder.filter { it in selectedGaugeIds }.take(DashboardSettings.MAX_GAUGES)
            dashboardSettings.save(
                DashboardConfig(vehicle.id, selected, telemetry.pitchDegrees, telemetry.rollDegrees),
            )
            Toast.makeText(this, "Pitch and roll zeroed for this phone mount", Toast.LENGTH_SHORT).show()
            recreate()
        }
        refreshAutoUi()
        return scroll
    }

    private fun bindActions() {
        startButton.setOnClickListener {
            if (hasFineLocation()) {
                startRecording()
            } else {
                startAfterPermission = true
                requestForegroundLocationPermissions()
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

    private fun requestForegroundLocationPermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    @SuppressLint("MissingPermission")
    private fun captureHomeLocation() {
        if (!hasFineLocation()) {
            captureHomeAfterPermission = true
            requestForegroundLocationPermissions()
            return
        }
        homeLocationStatus.text = "Getting a precise home location…"
        val cancellation = CancellationTokenSource()
        fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    homeLocationStatus.text = "No GPS fix yet — move near a window and try again"
                    return@addOnSuccessListener
                }
                pendingHomeLatitude = location.latitude
                pendingHomeLongitude = location.longitude
                homeLocationStatus.text = homeLocationLabel(location.accuracy)
                Toast.makeText(this, "Home location captured — save automatic settings", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { error ->
                homeLocationStatus.text = "Could not get location: ${error.message ?: "try again"}"
            }
    }

    private fun saveAutoSettings() {
        if (autoEnabledSwitch.isChecked && !hasFineLocation()) {
            saveAutoAfterPermission = true
            requestForegroundLocationPermissions()
            return
        }
        if (autoEnabledSwitch.isChecked && (pendingHomeLatitude == null || pendingHomeLongitude == null)) {
            Toast.makeText(this, "Use current location before enabling automatic recording", Toast.LENGTH_LONG).show()
            return
        }
        val config = AutoRecordingConfig(
            enabled = autoEnabledSwitch.isChecked,
            homeLatitude = pendingHomeLatitude,
            homeLongitude = pendingHomeLongitude,
            homeRadiusMeters = homeRadiusInput.intValue(AutoRecordingConfig.DEFAULT_HOME_RADIUS_METERS),
            returnDwellMinutes = returnDwellInput.intValue(AutoRecordingConfig.DEFAULT_RETURN_DWELL_MINUTES),
            locationIntervalSeconds = locationIntervalInput.intValue(AutoRecordingConfig.DEFAULT_LOCATION_INTERVAL_SECONDS),
            minimumDistanceMeters = minimumDistanceInput.intValue(AutoRecordingConfig.DEFAULT_MINIMUM_DISTANCE_METERS),
            tripType = TRIP_TYPE_VALUES[autoTripType.selectedItemPosition],
        ).normalized()
        autoSettings.save(config)
        populateAutoInputs(config)
        if (config.enabled && !autoManager.hasBackgroundLocation()) {
            autoState.updateStatus("Saved — choose Allow all the time for automatic departures")
            refreshAutoUi()
            Toast.makeText(this, "In Permissions > Location, choose Allow all the time", Toast.LENGTH_LONG).show()
            openAppLocationSettings()
            return
        }
        autoManager.refreshRegistration { _, message ->
            runOnUiThread {
                refreshAutoUi()
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openAppLocationSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
        )
    }

    private fun refreshAutoUi() {
        val config = autoSettings.read()
        autoEnabledSwitch.isChecked = config.enabled
        autoStatus.text = autoState.status()
        autoPermissionStatus.text = when {
            !config.enabled -> "Optional — automatic recording is disabled"
            !autoManager.hasFineLocation() -> "Precise location permission is required"
            !autoManager.hasBackgroundLocation() -> "Not armed — set Location to Allow all the time"
            else -> "Location access is ready for automatic departures"
        }
    }

    private fun populateAutoInputs(config: AutoRecordingConfig) {
        homeRadiusInput.setText(config.homeRadiusMeters.toString())
        returnDwellInput.setText(config.returnDwellMinutes.toString())
        locationIntervalInput.setText(config.locationIntervalSeconds.toString())
        minimumDistanceInput.setText(config.minimumDistanceMeters.toString())
    }

    private fun homeLocationLabel(accuracyMeters: Float? = null): String {
        val latitude = pendingHomeLatitude ?: return "Home location is not set"
        val longitude = pendingHomeLongitude ?: return "Home location is not set"
        val accuracy = accuracyMeters?.let { " • GPS ±${it.roundToInt()} m" }.orEmpty()
        return "Home: %.6f, %.6f%s".format(latitude, longitude, accuracy)
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

    private fun saveAppearance(usePresetAccent: Boolean) {
        val accentText = customAccentInput.text.toString().trim()
        val customAccent = when {
            usePresetAccent || accentText.isBlank() -> null
            else -> AppearanceSettings.parseRgbHex(accentText)
                ?: run {
                    Toast.makeText(this, "Enter a color as #RRGGBB, such as #FF7900", Toast.LENGTH_LONG).show()
                    return
                }
        }
        val preset = AppearanceSettings.PRESETS[themeSpinner.selectedItemPosition]
        appearanceSettings.save(AppearanceConfig(themeId = preset.id, customAccent = customAccent))
        Toast.makeText(this, "${preset.label} theme applied", Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun refreshContinuously() {
        lifecycleScope.launch {
            while (isActive) {
                val active = repository.activeTrip()
                val pending = repository.pendingPointCount()
                if (active == null) {
                    recorderStatus.text = "Ready to record"
                    telemetryStatus.text = "$pending points waiting to synchronize"
                    updateCockpit(null, liveTelemetryStore.read(), Duration.ZERO)
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
                    updateCockpit(active, liveTelemetryStore.read(), duration)
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
                autoStatus.text = autoState.status()
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

    private fun renderGaugeRows(
        container: LinearLayout,
        order: MutableList<String>,
        selected: MutableSet<String>,
    ) {
        container.removeAllViews()
        order.forEachIndexed { index, gaugeId ->
            val definition = DashboardSettings.GAUGES.first { it.id == gaugeId }
            val toggle = MaterialSwitch(this).apply {
                text = definition.label
                textSize = 13f
                setTextColor(Color.WHITE)
                isChecked = gaugeId in selected
                thumbTintList = checkedStateList(ORANGE, Color.parseColor("#777777"))
                trackTintList = checkedStateList(palette.activeSurface, Color.parseColor("#333333"))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        if (selected.size >= DashboardSettings.MAX_GAUGES) {
                            isChecked = false
                            Toast.makeText(this@MainActivity, "Choose up to ${DashboardSettings.MAX_GAUGES} gauges", Toast.LENGTH_SHORT).show()
                        } else {
                            selected.add(gaugeId)
                        }
                    } else {
                        selected.remove(gaugeId)
                    }
                }
            }
            val up = smallOrderButton("▲", "Move ${definition.label} up").apply {
                isEnabled = index > 0
                setOnClickListener {
                    java.util.Collections.swap(order, index, index - 1)
                    renderGaugeRows(container, order, selected)
                }
            }
            val down = smallOrderButton("▼", "Move ${definition.label} down").apply {
                isEnabled = index < order.lastIndex
                setOnClickListener {
                    java.util.Collections.swap(order, index, index + 1)
                    renderGaugeRows(container, order, selected)
                }
            }
            container.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(toggle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(up)
                    addView(down)
                }.withBottom(dp(3)),
            )
        }
    }

    private fun smallOrderButton(label: String, description: String): MaterialButton = MaterialButton(this).apply {
        text = label
        contentDescription = description
        textSize = 13f
        minWidth = dp(42)
        minimumWidth = dp(42)
        minHeight = dp(38)
        insetTop = 0
        insetBottom = 0
        setPadding(0, 0, 0, 0)
        backgroundTintList = stateList(PANEL, palette.inactiveSurface)
        strokeColor = stateList(OUTLINE, OUTLINE)
        strokeWidth = dp(1)
        setTextColor(stateList(ORANGE, Color.parseColor("#555555")))
    }

    private fun updateCockpit(
        active: ca.gmode.triprecorder.data.TripEntity?,
        telemetry: LiveTelemetry,
        duration: Duration,
    ) {
        cockpitGauges.forEach { (id, view) ->
            val liveForTrip = active != null && telemetry.tripId == active.id
            val unavailable = if (active == null) "READY" else "WAITING"
            when (id) {
                "speed" -> {
                    val value = if (liveForTrip) telemetry.speedKph else active?.lastSpeedMps?.times(3.6)
                    view.setReading("Speed", value?.roundToInt()?.toString() ?: "--", "km/h", value?.div(160.0), if (value == null) unavailable else "GPS SPEED")
                }
                "trip_time" -> view.setReading("Trip time", formatDuration(duration), "h:mm", duration.toMinutes().div(480.0), if (active == null) "READY" else "RECORDING")
                "distance" -> {
                    val value = active?.distanceMeters?.div(1000.0)
                    view.setReading("Distance", value?.let { "%.1f".format(it) } ?: "--", "km", value?.div(200.0), unavailable)
                }
                "altitude" -> {
                    val value = if (liveForTrip) telemetry.altitudeMeters else active?.lastAltitudeMeters
                    view.setReading("Altitude", value?.roundToInt()?.toString() ?: "--", "m", value?.plus(100.0)?.div(2100.0), "GPS")
                }
                "elevation_gain" -> {
                    val value = telemetry.elevationGainMeters.takeIf { liveForTrip }
                    view.setReading("Elevation gain", value?.roundToInt()?.toString() ?: "--", "m", value?.div(1000.0), "ASCENT")
                }
                "compass" -> {
                    val value = telemetry.bearingDegrees.takeIf { liveForTrip }
                    view.setReading("Compass", value?.let(::cardinalDirection) ?: "--", value?.let { "${it.roundToInt()}°" } ?: "degrees", value?.div(360.0), "GPS HEADING")
                }
                "pitch" -> angleGauge(
                    view,
                    "Pitch",
                    telemetry.pitchDegrees.takeIf { liveForTrip }?.let { normalizeAngle(it - dashboardConfig.pitchOffsetDegrees) },
                    unavailable,
                )
                "roll" -> angleGauge(
                    view,
                    "Roll",
                    telemetry.rollDegrees.takeIf { liveForTrip }?.let { normalizeAngle(it - dashboardConfig.rollOffsetDegrees) },
                    unavailable,
                )
                "g_force" -> {
                    val value = telemetry.accelerationPeakMs2?.div(9.80665)?.takeIf { liveForTrip }
                    view.setReading("G-force", value?.let { "%.2f".format(it) } ?: "--", "g", value?.div(2.0), if (value == null) unavailable else "PEAK")
                }
                "battery" -> {
                    val value = telemetry.batteryPercent.takeIf { liveForTrip }
                    view.setReading("Phone battery", value?.roundToInt()?.toString() ?: "--", "%", value?.div(100.0), "S24")
                }
                "gps_satellites" -> {
                    val value = telemetry.satelliteCount.takeIf { liveForTrip }
                    view.setReading("GPS status", value?.toString() ?: "--", "satellites", value?.div(20.0), "USED IN FIX")
                }
                "gps_accuracy" -> {
                    val value = telemetry.accuracyMeters.takeIf { liveForTrip } ?: active?.lastAccuracyMeters
                    view.setReading("GPS accuracy", value?.roundToInt()?.toString() ?: "--", "± meters", value?.let { (1.0 - it / 100.0).coerceAtLeast(0.0) }, if (value != null) "FIX" else unavailable)
                }
                "coordinates" -> {
                    val coordinate = if (liveForTrip && telemetry.latitude != null && telemetry.longitude != null) {
                        "%.4f\n%.4f".format(telemetry.latitude, telemetry.longitude)
                    } else {
                        "--"
                    }
                    view.setReading("Location", coordinate, "lat / lon", if (liveForTrip) 1.0 else null, "GPS")
                }
                "pressure" -> {
                    val value = telemetry.pressureHpa.takeIf { liveForTrip }
                    view.setReading("Barometer", value?.let { "%.0f".format(it) } ?: "--", "hPa", value?.minus(850.0)?.div(250.0), if (value == null) unavailable else "S24 SENSOR")
                }
            }
        }
    }

    private fun angleGauge(view: CockpitGaugeView, label: String, value: Double?, status: String) {
        view.setReading(label, value?.let { "%+.0f".format(it) } ?: "--", "degrees", value?.let { abs(it) / 45.0 }, if (value == null) status else "S24 ORIENTATION")
    }

    private fun normalizeAngle(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun cardinalDirection(degrees: Double): String {
        val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return points[((degrees + 22.5) / 45.0).toInt().mod(points.size)]
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

    private fun labeledInput(label: String, input: android.view.View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(label, 9.5f, ORANGE, bold = true).withBottom(dp(3)))
        addView(input)
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
        setColor(if (active) palette.activeSurface else palette.inactiveSurface)
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
            backgroundTintList = stateList(ORANGE, palette.activeSurface)
            setTextColor(stateList(contrastText(ORANGE), Color.parseColor("#777777")))
        } else {
            backgroundTintList = stateList(PANEL, palette.inactiveSurface)
            strokeColor = stateList(ORANGE, OUTLINE)
            strokeWidth = dp(1)
            setTextColor(stateList(Color.WHITE, Color.parseColor("#777777")))
        }
    }

    private fun tripTypeAdapter(): ArrayAdapter<String> = object : ArrayAdapter<String>(
        this,
        android.R.layout.simple_spinner_item,
        TRIP_TYPE_LABELS,
    ) {
        override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View =
            spinnerRow(position, dropdown = false)

        override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View =
            spinnerRow(position, dropdown = true)

        private fun spinnerRow(position: Int, dropdown: Boolean): TextView = TextView(this@MainActivity).apply {
            text = getItem(position)
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(if (dropdown) 14 else 10), dp(14), dp(if (dropdown) 14 else 10))
            setBackgroundColor(if (dropdown) PANEL else Color.TRANSPARENT)
        }
    }

    private fun themeAdapter(): ArrayAdapter<String> = object : ArrayAdapter<String>(
        this,
        android.R.layout.simple_spinner_item,
        AppearanceSettings.PRESETS.map { it.label },
    ) {
        override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View =
            themeRow(position, dropdown = false)

        override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View =
            themeRow(position, dropdown = true)

        private fun themeRow(position: Int, dropdown: Boolean): TextView = TextView(this@MainActivity).apply {
            val preset = AppearanceSettings.PRESETS[position]
            text = "●  ${preset.label}"
            textSize = 16f
            setTextColor(preset.accent)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(if (dropdown) 14 else 10), dp(14), dp(if (dropdown) 14 else 10))
            setBackgroundColor(if (dropdown) preset.panel else Color.TRANSPARENT)
        }
    }

    private fun labelAdapter(labels: List<String>): ArrayAdapter<String> = object : ArrayAdapter<String>(
        this,
        android.R.layout.simple_spinner_item,
        labels,
    ) {
        override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View = row(position, false)

        override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View = row(position, true)

        private fun row(position: Int, dropdown: Boolean): TextView = TextView(this@MainActivity).apply {
            text = getItem(position)
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(if (dropdown) 14 else 10), dp(14), dp(if (dropdown) 14 else 10))
            setBackgroundColor(if (dropdown) PANEL else Color.TRANSPARENT)
        }
    }

    private fun stateList(enabled: Int, disabled: Int): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled),
        ),
        intArrayOf(enabled, disabled),
    )

    private fun checkedStateList(checked: Int, unchecked: Int): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked),
        ),
        intArrayOf(checked, unchecked),
    )

    private fun contrastText(background: Int): Int {
        val red = (background shr 16) and 0xFF
        val green = (background shr 8) and 0xFF
        val blue = background and 0xFF
        val luminance = red * 299 + green * 587 + blue * 114
        return if (luminance >= 145_000) Color.BLACK else Color.WHITE
    }

    private fun editText(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setTextColor(Color.WHITE)
        setHintTextColor(MUTED)
        backgroundTintList = ColorStateList.valueOf(ORANGE)
        setSingleLine(true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun numberInput(value: Int): EditText = editText("").apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value.toString())
        selectAll()
    }

    private fun EditText.intValue(fallback: Int): Int = text.toString().trim().toIntOrNull() ?: fallback

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
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm")
        private val TRIP_TYPE_LABELS = listOf("Street", "Off road", "Snow", "Water")
        private val TRIP_TYPE_VALUES = listOf("street", "off_road", "snow", "water")
    }

    private val BACKGROUND: Int get() = palette.background
    private val PANEL: Int get() = palette.panel
    private val OUTLINE: Int get() = palette.outline
    private val ORANGE: Int get() = palette.accent
    private val MUTED: Int get() = palette.muted
}
