package ca.gmode.triprecorder

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import ca.gmode.triprecorder.auto.AutoRecordingManager
import ca.gmode.triprecorder.auto.HomeWifiReader
import ca.gmode.triprecorder.data.AppDatabase
import ca.gmode.triprecorder.data.RecordingRepository
import ca.gmode.triprecorder.data.TripEntity
import ca.gmode.triprecorder.export.TripExportFormat
import ca.gmode.triprecorder.export.TripFileExporter
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import ca.gmode.triprecorder.settings.AutoRecordingSettings
import ca.gmode.triprecorder.settings.AutoRecordingStateStore
import ca.gmode.triprecorder.settings.AppearanceConfig
import ca.gmode.triprecorder.settings.AppearanceSettings
import ca.gmode.triprecorder.settings.DashboardPalette
import ca.gmode.triprecorder.settings.DashboardConfig
import ca.gmode.triprecorder.settings.DashboardSettings
import ca.gmode.triprecorder.settings.SecureSettings
import ca.gmode.triprecorder.settings.SideButtonConfig
import ca.gmode.triprecorder.settings.SideButtonSettings
import ca.gmode.triprecorder.settings.SideButtonSlot
import ca.gmode.triprecorder.settings.SideButtonTarget
import ca.gmode.triprecorder.sync.SyncScheduler
import ca.gmode.triprecorder.sync.SyncStatusStore
import ca.gmode.triprecorder.tracking.DashboardTelemetry
import ca.gmode.triprecorder.tracking.GaugeDisplayMath
import ca.gmode.triprecorder.tracking.LevelCalibration
import ca.gmode.triprecorder.tracking.LiveTelemetry
import ca.gmode.triprecorder.tracking.LiveTelemetryStore
import ca.gmode.triprecorder.tracking.SensorCollector
import ca.gmode.triprecorder.tracking.TrackingService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private data class DashboardPhoneStatus(
    val wifiConnected: Boolean,
    val networkConnected: Boolean,
    val bluetoothEnabled: Boolean?,
    val batteryPercent: Int?,
    val batteryCharging: Boolean,
    val batteryTemperatureC: Double?,
)

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
    private lateinit var calibrationSensors: SensorCollector
    private lateinit var sideButtonSettings: SideButtonSettings
    private lateinit var palette: DashboardPalette
    private lateinit var dashboardConfig: DashboardConfig
    private var sideButtonConfig: List<SideButtonConfig> = emptyList()
    private lateinit var landscapeCockpit: LandscapeCockpitView
    private var showingSettings = false
    private var quickTripType = "off_road"
    private var requestedTripType: String? = null
    private var pendingExportTripId: String? = null
    private var pendingExportFormatId: String? = null

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
    private lateinit var homeWifiInput: EditText
    private lateinit var homeWifiStatus: TextView
    private lateinit var wifiDepartureDelayInput: EditText
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
    private var captureWifiAfterPermission = false
    private var saveAutoAfterPermission = false
    private var levelCalibrationInProgress = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (locationGranted && startAfterPermission) startRecording(requestedTripType)
        if (locationGranted && captureHomeAfterPermission) captureHomeLocation()
        if (locationGranted && captureWifiAfterPermission) captureCurrentHomeWifi()
        if (locationGranted && saveAutoAfterPermission) saveAutoSettings()
        startAfterPermission = false
        requestedTripType = null
        captureHomeAfterPermission = false
        captureWifiAfterPermission = false
        saveAutoAfterPermission = false
    }

    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val destination = result.data?.data
        val tripId = pendingExportTripId
        val format = TripExportFormat.fromId(pendingExportFormatId)
        pendingExportTripId = null
        pendingExportFormatId = null
        if (result.resultCode != android.app.Activity.RESULT_OK || destination == null || tripId == null || format == null) {
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            runCatching {
                val trip = repository.trip(tripId) ?: error("The selected trip is no longer available")
                val points = repository.tripPoints(tripId)
                val document = withContext(Dispatchers.Default) { TripFileExporter.render(trip, points, format) }
                withContext(Dispatchers.IO) {
                    val stream = contentResolver.openOutputStream(destination, "w")
                        ?: error("Android could not open the selected file")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(document) }
                }
                points.size
            }.onSuccess { pointCount ->
                Toast.makeText(
                    this@MainActivity,
                    "Exported $pointCount points as ${format.label.substringBefore(" — ")}",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "Export failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val wifiPanelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        captureCurrentHomeWifi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExportTripId = savedInstanceState?.getString(STATE_EXPORT_TRIP_ID)
        pendingExportFormatId = savedInstanceState?.getString(STATE_EXPORT_FORMAT_ID)
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
        calibrationSensors = SensorCollector(this)
        sideButtonSettings = SideButtonSettings(this)
        sideButtonConfig = sideButtonSettings.read()
        palette = appearanceSettings.palette()
        dashboardConfig = dashboardSettings.read()
        calibrationSensors.onOrientationChanged = { orientation ->
            if (::landscapeCockpit.isInitialized && !showingSettings) {
                landscapeCockpit.setLiveAttitude(
                    pitchDegrees = orientation.pitchDegrees?.let { normalizeAngle(it - dashboardConfig.pitchOffsetDegrees) },
                    rollDegrees = orientation.rollDegrees?.let {
                        GaugeDisplayMath.mirroredRollDegrees(it, dashboardConfig.rollOffsetDegrees)
                    },
                )
            }
        }
        quickTripType = "off_road"
        applySystemBarPalette()
        enterImmersiveMode()
        showCockpitScreen()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (showingSettings) showCockpitScreen() else finish()
                }
            },
        )
        refreshContinuously()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_EXPORT_TRIP_ID, pendingExportTripId)
        outState.putString(STATE_EXPORT_FORMAT_ID, pendingExportFormatId)
        super.onSaveInstanceState(outState)
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showCockpitScreen() {
        showingSettings = false
        palette = appearanceSettings.palette()
        dashboardConfig = dashboardSettings.read()
        val vehicle = dashboardVehicle(quickTripType)
        applySystemBarPalette()
        landscapeCockpit = LandscapeCockpitView(this).apply {
            setPalette(palette)
            setState(
                CockpitState(
                    vehicleId = vehicle.id,
                    vehicleLabel = vehicle.label,
                    tripTypeLabel = tripTypeLabel(quickTripType),
                    offRoadSceneId = dashboardConfig.offRoadSceneId,
                    automaticArmed = autoSettings.read().enabled && autoManager.hasBackgroundLocation(),
                    readings = dashboardConfig.gaugeIds.map { placeholderReading(it) },
                    sideButtons = sideButtonConfig,
                    vehicleViewModeId = dashboardConfig.vehicleViewModeId,
                    attitudeCautionDegrees = dashboardConfig.attitudeCautionDegrees,
                    attitudeLimitDegrees = dashboardConfig.attitudeLimitDegrees,
                ),
            )
            onAction = ::handleCockpitAction
        }
        setContentView(landscapeCockpit)
    }

    private fun showSettingsScreen() {
        showingSettings = true
        setContentView(createContent(showCockpitPreview = false))
        bindActions()
    }

    private fun handleCockpitAction(action: CockpitAction) {
        when (action) {
            CockpitAction.START -> {
                requestedTripType = quickTripType
                if (hasFineLocation()) startRecording(quickTripType) else {
                    startAfterPermission = true
                    requestForegroundLocationPermissions()
                }
            }
            CockpitAction.STOP -> TrackingService.stop(this)
            CockpitAction.AUTO, CockpitAction.SETTINGS, CockpitAction.HOME_ASSISTANT -> showSettingsScreen()
            CockpitAction.SYNC -> {
                SyncScheduler.enqueue(this)
                Toast.makeText(this, "Synchronization queued", Toast.LENGTH_SHORT).show()
            }
            CockpitAction.TRIP_TYPE -> {
                val index = TRIP_TYPE_VALUES.indexOf(quickTripType).coerceAtLeast(0)
                quickTripType = TRIP_TYPE_VALUES[(index + 1) % TRIP_TYPE_VALUES.size]
                Toast.makeText(this, "New trips: ${tripTypeLabel(quickTripType)}", Toast.LENGTH_SHORT).show()
            }
            CockpitAction.THEME -> {
                val current = appearanceSettings.read()
                val index = AppearanceSettings.PRESETS.indexOfFirst { it.id == current.themeId }.coerceAtLeast(0)
                val next = AppearanceSettings.PRESETS[(index + 1) % AppearanceSettings.PRESETS.size]
                appearanceSettings.save(AppearanceConfig(next.id, null))
                palette = appearanceSettings.palette()
                landscapeCockpit.setPalette(palette)
                applySystemBarPalette()
                Toast.makeText(this, next.label, Toast.LENGTH_SHORT).show()
            }
            CockpitAction.BLUETOOTH -> handleBluetoothIndicatorTap()
            CockpitAction.SIDE_LEFT_TOP -> launchSideButton(SideButtonSlot.LEFT_TOP)
            CockpitAction.SIDE_LEFT_MIDDLE -> launchSideButton(SideButtonSlot.LEFT_MIDDLE)
            CockpitAction.SIDE_LEFT_BOTTOM -> launchSideButton(SideButtonSlot.LEFT_BOTTOM)
            CockpitAction.SIDE_RIGHT_TOP -> launchSideButton(SideButtonSlot.RIGHT_TOP)
            CockpitAction.SIDE_RIGHT_MIDDLE -> launchSideButton(SideButtonSlot.RIGHT_MIDDLE)
            CockpitAction.SIDE_RIGHT_BOTTOM -> launchSideButton(SideButtonSlot.RIGHT_BOTTOM)
        }
    }

    private fun launchSideButton(slot: SideButtonSlot) {
        val config = sideButtonConfig.firstOrNull { it.slot == slot } ?: SideButtonSettings.DEFAULTS.getValue(slot)
        when (config.target) {
            SideButtonSettings.ACTION_START -> handleCockpitAction(CockpitAction.START)
            SideButtonSettings.ACTION_STOP -> handleCockpitAction(CockpitAction.STOP)
            SideButtonSettings.ACTION_TRIP_TYPE -> handleCockpitAction(CockpitAction.TRIP_TYPE)
            SideButtonSettings.ACTION_AUTO, SideButtonSettings.ACTION_SETTINGS -> showSettingsScreen()
            SideButtonSettings.ACTION_SYNC -> handleCockpitAction(CockpitAction.SYNC)
            SideButtonSettings.ACTION_HOME_ASSISTANT -> showSettingsScreen()
            SideButtonSettings.ACTION_OPEN_RADIO,
            SideButtonSettings.ACTION_OPEN_NAVIGATION,
            SideButtonSettings.ACTION_OPEN_MUSIC,
            SideButtonSettings.ACTION_OPEN_CAMERA,
            SideButtonSettings.ACTION_OPEN_PHONE,
            SideButtonSettings.ACTION_OPEN_BROWSER,
            SideButtonSettings.ACTION_OPEN_APPS,
            -> launchPhoneFunction(config)
            else -> launchInstalledApp(config)
        }
    }

    private fun launchPhoneFunction(config: SideButtonConfig) {
        val candidates = when (config.target) {
            SideButtonSettings.ACTION_OPEN_RADIO -> listOfNotNull(
                preferredLauncherIntent(listOf("radio", "tunein", "iheart", "sirius")),
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=listen+to+radio")),
            )
            SideButtonSettings.ACTION_OPEN_NAVIGATION -> listOf(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")),
            )
            SideButtonSettings.ACTION_OPEN_MUSIC -> listOf(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")),
            )
            SideButtonSettings.ACTION_OPEN_CAMERA -> listOf(
                Intent("android.media.action.STILL_IMAGE_CAMERA"),
                Intent("android.media.action.IMAGE_CAPTURE"),
            )
            SideButtonSettings.ACTION_OPEN_PHONE -> listOf(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
            SideButtonSettings.ACTION_OPEN_BROWSER -> listOf(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
            )
            SideButtonSettings.ACTION_OPEN_APPS -> listOf(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            else -> emptyList()
        }
        val intent = candidates.firstOrNull { it.resolveActivity(packageManager) != null }
        if (intent == null) {
            Toast.makeText(this, "No app is installed for ${config.label}", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "Could not open ${config.label}", Toast.LENGTH_LONG).show()
            }
    }

    private fun preferredLauncherIntent(keywords: List<String>): Intent? {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = packageManager.queryIntentActivities(launcher, 0).firstOrNull { result ->
            val label = result.loadLabel(packageManager).toString().lowercase()
            keywords.any(label::contains)
        } ?: return null
        val activity = match.activityInfo ?: return null
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(activity.packageName, activity.name)
        }
    }

    private fun launchInstalledApp(config: SideButtonConfig) {
        val flattened = config.target.removePrefix(SideButtonSettings.APP_PREFIX)
        val component = ComponentName.unflattenFromString(flattened)
        if (component == null) {
            Toast.makeText(this, "${config.label} is not configured", Toast.LENGTH_SHORT).show()
            return
        }
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setComponent(component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(launchIntent) }
            .onFailure {
                Toast.makeText(this, "${config.label} is no longer installed — choose another app in Settings", Toast.LENGTH_LONG).show()
            }
    }

    private fun handleBluetoothIndicatorTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun tripTypeLabel(value: String): String = TRIP_TYPE_LABELS[
        TRIP_TYPE_VALUES.indexOf(value).coerceAtLeast(0)
    ].uppercase()

    private fun dashboardVehicle(tripType: String) = DashboardSettings.vehicle(
        dashboardConfig.vehicleIdForTripType(tripType),
    )

    private fun placeholderReading(gaugeId: String): CockpitReading {
        val label = DashboardSettings.GAUGES.firstOrNull { it.id == gaugeId }?.label ?: gaugeId
        return CockpitReading(label, "--", "waiting", gaugeId = gaugeId)
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

    override fun onStart() {
        super.onStart()
        calibrationSensors.start()
    }

    override fun onStop() {
        calibrationSensors.stop()
        super.onStop()
    }

    private fun createContent(showCockpitPreview: Boolean = true): ScrollView {
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
        if (!showCockpitPreview) {
            val back = dashboardButton("← COCKPIT", filled = false).apply { setOnClickListener { showCockpitScreen() } }
            content.addView(horizontalViews(back, text("SETTINGS", 18f, ORANGE, bold = true), dashboardClock).withBottom(dp(10)))
        }
        if (showCockpitPreview) {
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

        val vehicleLabel = dashboardVehicle(quickTripType).label
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
        }

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

        val exportTripSpinner = Spinner(this).apply {
            adapter = labelAdapter(listOf("Loading locally recorded trips…"))
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            isEnabled = false
        }
        val exportFormatSpinner = Spinner(this).apply {
            adapter = labelAdapter(TripExportFormat.entries.map { it.label })
            backgroundTintList = ColorStateList.valueOf(ORANGE)
        }
        val exportStatus = text("Trips remain available here after Home Assistant synchronization.", 11f, MUTED)
        val exportButton = dashboardButton("EXPORT TRIP FILE", filled = true).apply { isEnabled = false }
        var exportTrips: List<TripEntity> = emptyList()
        content.addView(
            card(
                "EXPORT RECORDED TRIP",
                labeledInput("RECORDED TRIP", exportTripSpinner),
                labeledInput("FILE FORMAT", exportFormatSpinner),
                exportButton,
                exportStatus,
                text(
                    "GPX works with most navigation and trail apps. KML opens in Google Earth, GeoJSON works with mapping/GIS tools, and CSV preserves the detailed phone telemetry for spreadsheets.",
                    11f,
                    MUTED,
                ),
            ),
        )
        exportButton.setOnClickListener {
            val trip = exportTrips.getOrNull(exportTripSpinner.selectedItemPosition)
            if (trip == null) {
                Toast.makeText(this, "No recorded trip is selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val format = TripExportFormat.entries[exportFormatSpinner.selectedItemPosition]
            launchTripExport(trip, format)
        }
        lifecycleScope.launch {
            exportTrips = repository.recentTrips()
            if (exportTrips.isEmpty()) {
                exportTripSpinner.adapter = labelAdapter(listOf("No locally recorded trips yet"))
                exportTripSpinner.isEnabled = false
                exportButton.isEnabled = false
                exportStatus.text = "Record a trip before exporting."
            } else {
                exportTripSpinner.adapter = labelAdapter(exportTrips.map(::exportTripLabel))
                exportTripSpinner.isEnabled = true
                exportButton.isEnabled = true
                exportStatus.text = "${exportTrips.size} most recent local trip${if (exportTrips.size == 1) "" else "s"} available"
            }
        }

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
        homeWifiStatus = text(homeWifiLabel(automaticConfig.homeWifiSsid), 12f, MUTED)
        autoPermissionStatus = text("", 12f, MUTED)
        homeRadiusInput = numberInput(automaticConfig.homeRadiusMeters)
        homeWifiInput = editText("Home Wi-Fi name (SSID)").apply {
            automaticConfig.homeWifiSsid?.let(::setText)
            maxLines = 1
        }
        wifiDepartureDelayInput = numberInput(automaticConfig.wifiDepartureDelayMinutes)
        returnDwellInput = numberInput(automaticConfig.returnDwellMinutes)
        locationIntervalInput = numberInput(automaticConfig.locationIntervalSeconds)
        minimumDistanceInput = numberInput(automaticConfig.minimumDistanceMeters)
        autoTripType = Spinner(this).apply {
            adapter = tripTypeAdapter()
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setSelection(TRIP_TYPE_VALUES.indexOf(automaticConfig.tripType).coerceAtLeast(0))
        }
        val useCurrentLocation = dashboardButton("USE CURRENT LOCATION", filled = false)
        val useCurrentWifi = dashboardButton("USE CURRENT WI-FI", filled = false)
        val chooseWifi = dashboardButton("CHOOSE WI-FI IN ANDROID", filled = false)
        val saveAutomatic = dashboardButton("SAVE AUTO SETTINGS", filled = true)
        val locationPermission = dashboardButton("LOCATION PERMISSION", filled = false)
        content.addView(
            card(
                "AUTOMATIC RECORDING",
                autoEnabledSwitch,
                autoStatus,
                homeLocationStatus,
                useCurrentLocation,
                homeWifiStatus,
                labeledInput("HOME WI-FI (OPTIONAL)", homeWifiInput),
                horizontalButtons(useCurrentWifi, chooseWifi),
                horizontalViews(
                    labeledInput("HOME RADIUS (M)", homeRadiusInput),
                    labeledInput("WI-FI DEPARTURE DELAY (MIN)", wifiDepartureDelayInput),
                ),
                horizontalViews(
                    labeledInput("RETURN DELAY (MIN)", returnDwellInput),
                    labeledInput("GPS INTERVAL (SEC)", locationIntervalInput),
                ),
                horizontalViews(
                    labeledInput("MIN MOVEMENT (M)", minimumDistanceInput),
                    labeledInput("AUTOMATIC TRIP TYPE", autoTripType),
                ),
                horizontalButtons(saveAutomatic, locationPermission),
                autoPermissionStatus,
                text(
                    "Hybrid mode uses both signals: leaving home Wi-Fi starts the confirmation timer, and GPS must also place the phone outside the home radius before a trip starts. GPS remains active if Wi-Fi status is unavailable. Returning inside the GPS zone for the return delay stops only an automatically started trip.",
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

        sideButtonConfig = sideButtonSettings.read()
        val installedTargets = discoverSideButtonTargets()
        val targetOptions = (SideButtonSettings.BUILT_IN_TARGETS + installedTargets + sideButtonConfig.mapNotNull { config ->
            if (config.target.startsWith(SideButtonSettings.APP_PREFIX) && installedTargets.none { it.id == config.target }) {
                SideButtonTarget(config.target, "Missing app — ${config.label}")
            } else null
        }).distinctBy { it.id }
        val buttonLabels = mutableMapOf<SideButtonSlot, EditText>()
        val buttonTargets = mutableMapOf<SideButtonSlot, Spinner>()
        val buttonIcons = mutableMapOf<SideButtonSlot, Spinner>()
        val sideButtonRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sideBySlot = sideButtonConfig.associateBy { it.slot }
        SideButtonSlot.entries.forEach { slot ->
            val config = sideBySlot[slot] ?: SideButtonSettings.DEFAULTS.getValue(slot)
            val labelInput = editText("Button text").apply {
                setText(config.label)
                maxLines = 1
            }
            val targetSpinner = Spinner(this).apply {
                adapter = labelAdapter(targetOptions.map { it.label })
                backgroundTintList = ColorStateList.valueOf(ORANGE)
                setSelection(targetOptions.indexOfFirst { it.id == config.target }.coerceAtLeast(0))
            }
            val iconSpinner = Spinner(this).apply {
                adapter = labelAdapter(SideButtonSettings.ICONS.map { it.label })
                backgroundTintList = ColorStateList.valueOf(ORANGE)
                setSelection(SideButtonSettings.ICONS.indexOfFirst { it.id == config.iconId }.coerceAtLeast(0))
            }
            buttonLabels[slot] = labelInput
            buttonTargets[slot] = targetSpinner
            buttonIcons[slot] = iconSpinner
            sideButtonRows.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(slot.label.uppercase(), 11f, Color.WHITE, bold = true).withBottom(dp(5)))
                    addView(
                        horizontalViews(
                            labeledInput("TEXT", labelInput),
                            labeledInput("ICON", iconSpinner),
                        ).withBottom(dp(5)),
                    )
                    addView(labeledInput("OPENS", targetSpinner).withBottom(dp(11)))
                },
            )
        }
        val saveSideButtons = dashboardButton("SAVE + APPLY SIDE BUTTONS", filled = true)
        content.addView(
            card(
                "LEFT + RIGHT DASHBOARD BUTTONS",
                text(
                    "Set the text, icon, and action for every side button. App targets include launchable apps currently installed on this phone.",
                    11f,
                    MUTED,
                ),
                sideButtonRows,
                saveSideButtons,
            ),
        )
        saveSideButtons.setOnClickListener {
            val saved = SideButtonSlot.entries.map { slot ->
                SideButtonConfig(
                    slot = slot,
                    label = buttonLabels.getValue(slot).text.toString(),
                    target = targetOptions[buttonTargets.getValue(slot).selectedItemPosition].id,
                    iconId = SideButtonSettings.ICONS[buttonIcons.getValue(slot).selectedItemPosition].id,
                ).normalized()
            }
            sideButtonSettings.save(saved)
            sideButtonConfig = sideButtonSettings.read()
            Toast.makeText(this, "Dashboard side buttons applied", Toast.LENGTH_SHORT).show()
            showCockpitScreen()
        }

        fun categoryVehicleSpinner(vehicles: List<ca.gmode.triprecorder.settings.VehicleProfile>, selectedId: String) = Spinner(this).apply {
            adapter = labelAdapter(vehicles.map { it.label })
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setSelection(vehicles.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
        }
        val streetVehicles = DashboardSettings.vehiclesForTripType("street")
        val offRoadVehicles = DashboardSettings.vehiclesForTripType("off_road")
        val snowVehicles = DashboardSettings.vehiclesForTripType("snow")
        val waterVehicles = DashboardSettings.vehiclesForTripType("water")
        val streetVehicleSpinner = categoryVehicleSpinner(streetVehicles, dashboardConfig.streetVehicleId)
        val offRoadVehicleSpinner = categoryVehicleSpinner(offRoadVehicles, dashboardConfig.vehicleId)
        val snowVehicleSpinner = categoryVehicleSpinner(snowVehicles, dashboardConfig.snowVehicleId)
        val waterVehicleSpinner = categoryVehicleSpinner(waterVehicles, dashboardConfig.waterVehicleId)
        val offRoadSceneSpinner = Spinner(this).apply {
            adapter = labelAdapter(DashboardSettings.OFF_ROAD_SCENES.map { it.label })
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setSelection(DashboardSettings.OFF_ROAD_SCENES.indexOfFirst { it.id == dashboardConfig.offRoadSceneId }.coerceAtLeast(0))
        }
        val vehicleViewSpinner = Spinner(this).apply {
            adapter = labelAdapter(DashboardSettings.VIEW_MODES.map { it.label })
            backgroundTintList = ColorStateList.valueOf(ORANGE)
            setSelection(DashboardSettings.VIEW_MODES.indexOfFirst { it.id == dashboardConfig.vehicleViewModeId }.coerceAtLeast(0))
        }
        val attitudeCaution = editText("Caution angle in degrees").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("${dashboardConfig.attitudeCautionDegrees.toInt()}")
        }
        val attitudeLimit = editText("Limit angle in degrees").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("${dashboardConfig.attitudeLimitDegrees.toInt()}")
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
        val zeroLevel = dashboardButton("CALIBRATE PITCH + ROLL ZERO", filled = false)
        val calibrationStatus = text(
            "Saved zero: pitch ${formatCalibration(dashboardConfig.pitchOffsetDegrees)}, roll ${formatCalibration(dashboardConfig.rollOffsetDegrees)}",
            11f,
            MUTED,
        )
        content.addView(
            card(
                "COCKPIT LAYOUT",
                text("The 3D vehicle follows the scene: Street — Truck; Dirt — SxS; Sand — Sand rail; Snow — Snowmobile; Water — Mini jet boat.", 11f, MUTED),
                labeledInput("OFF ROAD SCENE", offRoadSceneSpinner),
                labeledInput("3D CAMERA", vehicleViewSpinner),
                labeledInput("CAUTION START", attitudeCaution),
                labeledInput("LIMIT START", attitudeLimit),
                text("Mount the S24 in landscape with its back facing forward. Drag inside the gauge to orbit the real 3D vehicle. Chase mode returns smoothly to the high rear view; Free orbit keeps the selected view.", 11f, MUTED),
                gaugeRows,
                horizontalButtons(saveCockpit, vehicleDefaults),
                text("LEVEL CALIBRATION: Park on flat ground, stop completely, leave the phone in its normal mount, then press the button and release the phone. Calibration is rejected if the S24 detects movement.", 11f, MUTED),
                zeroLevel,
                calibrationStatus,
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
        useCurrentWifi.setOnClickListener { captureCurrentHomeWifi() }
        chooseWifi.setOnClickListener {
            wifiPanelLauncher.launch(Intent(Settings.Panel.ACTION_WIFI))
        }
        saveAutomatic.setOnClickListener { saveAutoSettings() }
        locationPermission.setOnClickListener { openAppLocationSettings() }
        saveTheme.setOnClickListener { saveAppearance(usePresetAccent = false) }
        usePresetColor.setOnClickListener { saveAppearance(usePresetAccent = true) }
        saveCockpit.setOnClickListener {
            val selected = gaugeOrder.filter { it in selectedGaugeIds }
            if (selected.isEmpty()) {
                Toast.makeText(this, "Choose at least one gauge", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val cautionDegrees = attitudeCaution.text.toString().toDoubleOrNull()
            val limitDegrees = attitudeLimit.text.toString().toDoubleOrNull()
            if (cautionDegrees == null || limitDegrees == null || cautionDegrees !in 5.0..40.0 || limitDegrees < cautionDegrees + 5.0 || limitDegrees > 60.0) {
                Toast.makeText(this, "Use caution 5–40° and a limit at least 5° higher (maximum 60°)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val selectedScene = DashboardSettings.OFF_ROAD_SCENES[offRoadSceneSpinner.selectedItemPosition].id
            dashboardSettings.save(
                DashboardConfig(
                    vehicleId = if (selectedScene == "sand") "sand_rail" else DashboardSettings.DEFAULT_VEHICLE_ID,
                    streetVehicleId = DashboardSettings.DEFAULT_STREET_VEHICLE_ID,
                    snowVehicleId = DashboardSettings.DEFAULT_SNOW_VEHICLE_ID,
                    waterVehicleId = DashboardSettings.DEFAULT_WATER_VEHICLE_ID,
                    offRoadSceneId = selectedScene,
                    gaugeIds = selected,
                    pitchOffsetDegrees = dashboardConfig.pitchOffsetDegrees,
                    rollOffsetDegrees = dashboardConfig.rollOffsetDegrees,
                    vehicleViewModeId = DashboardSettings.VIEW_MODES[vehicleViewSpinner.selectedItemPosition].id,
                    attitudeCautionDegrees = cautionDegrees,
                    attitudeLimitDegrees = limitDegrees,
                ),
            )
            Toast.makeText(this, "Trip-type vehicle choices applied", Toast.LENGTH_SHORT).show()
            recreate()
        }
        vehicleDefaults.setOnClickListener {
            val defaults = DashboardSettings.defaultGauges(DashboardSettings.DEFAULT_VEHICLE_ID)
            selectedGaugeIds.clear()
            selectedGaugeIds.addAll(defaults)
            gaugeOrder.clear()
            gaugeOrder.addAll(defaults + DashboardSettings.GAUGES.map { it.id }.filterNot { it in defaults })
            renderRows()
        }
        zeroLevel.setOnClickListener {
            if (levelCalibrationInProgress) {
                Toast.makeText(this, "Level calibration is already sampling", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            levelCalibrationInProgress = true
            zeroLevel.isEnabled = false
            calibrationStatus.text = "Release the phone — settling…"
            calibrationSensors.snapshotAndReset()
            Toast.makeText(this, "Keep the stopped vehicle and phone still", Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                delay(LevelCalibration.SETTLE_MS)
                calibrationSensors.snapshotAndReset()
                calibrationStatus.text = "Sampling level position…"
                delay(LevelCalibration.SAMPLE_MS)
                val orientation = calibrationSensors.orientation()
                val motion = calibrationSensors.snapshotAndReset()
                levelCalibrationInProgress = false
                zeroLevel.isEnabled = true
                if (orientation.pitchDegrees == null || orientation.rollDegrees == null) {
                    calibrationStatus.text = "Orientation sensor unavailable — calibration not changed"
                    Toast.makeText(this@MainActivity, "S24 orientation data is not available yet; try again", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val accelerationPeak = motion.accelerationPeakMs2 ?: 0.0
                val gyroscopePeak = motion.gyroscopePeakRadS ?: 0.0
                if (!LevelCalibration.isStationary(accelerationPeak, gyroscopePeak)) {
                    calibrationStatus.text = "Movement detected — zero was not changed"
                    Toast.makeText(this@MainActivity, "Movement detected. Stop completely and try calibration again.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                dashboardConfig = dashboardConfig.copy(
                    pitchOffsetDegrees = orientation.pitchDegrees,
                    rollOffsetDegrees = orientation.rollDegrees,
                ).normalized()
                dashboardSettings.save(dashboardConfig)
                calibrationStatus.text = "Saved zero: pitch ${formatCalibration(dashboardConfig.pitchOffsetDegrees)}, roll ${formatCalibration(dashboardConfig.rollOffsetDegrees)}"
                Toast.makeText(this@MainActivity, "Pitch and roll zero saved for this phone mount", Toast.LENGTH_SHORT).show()
            }
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

    private fun startRecording(requestedType: String? = null) {
        lifecycleScope.launch {
            val type = requestedType ?: if (::tripType.isInitialized) {
                TRIP_TYPE_VALUES[tripType.selectedItemPosition]
            } else {
                quickTripType
            }
            val name = if (::tripName.isInitialized) tripName.text.toString() else ""
            val trip = repository.startTrip(name, type)
            TrackingService.start(this@MainActivity, trip.id)
            SyncScheduler.enqueue(this@MainActivity)
            if (::tripName.isInitialized) tripName.text.clear()
        }
    }

    private fun launchTripExport(trip: TripEntity, format: TripExportFormat) {
        pendingExportTripId = trip.id
        pendingExportFormatId = format.id
        exportFileLauncher.launch(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = format.mimeType
                putExtra(Intent.EXTRA_TITLE, TripFileExporter.suggestedFileName(trip, format))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            },
        )
    }

    private fun exportTripLabel(trip: TripEntity): String {
        val status = if (trip.status == "active") "RECORDING" else trip.startAt.take(10)
        return "${trip.title} • ${tripTypeLabel(trip.tripType)} • $status • ${trip.pointCount} pts"
    }

    private fun requestForegroundLocationPermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
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
            homeWifiSsid = HomeWifiReader.normalizeSsid(homeWifiInput.text.toString()),
            wifiDepartureDelayMinutes = wifiDepartureDelayInput.intValue(
                AutoRecordingConfig.DEFAULT_WIFI_DEPARTURE_DELAY_MINUTES,
            ),
            returnDwellMinutes = returnDwellInput.intValue(AutoRecordingConfig.DEFAULT_RETURN_DWELL_MINUTES),
            locationIntervalSeconds = locationIntervalInput.intValue(AutoRecordingConfig.DEFAULT_LOCATION_INTERVAL_SECONDS),
            minimumDistanceMeters = minimumDistanceInput.intValue(AutoRecordingConfig.DEFAULT_MINIMUM_DISTANCE_METERS),
            tripType = TRIP_TYPE_VALUES[autoTripType.selectedItemPosition],
        ).normalized()
        autoSettings.save(config)
        populateAutoInputs(config)
        homeWifiStatus.text = homeWifiLabel(config.homeWifiSsid)
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
        homeWifiStatus.text = homeWifiLabel(config.homeWifiSsid)
        autoPermissionStatus.text = when {
            !config.enabled -> "Optional — automatic recording is disabled"
            !autoManager.hasFineLocation() -> "Precise location permission is required"
            !autoManager.hasBackgroundLocation() -> "Not armed — set Location to Allow all the time"
            else -> "Location access is ready for automatic departures"
        }
    }

    private fun populateAutoInputs(config: AutoRecordingConfig) {
        homeRadiusInput.setText(config.homeRadiusMeters.toString())
        homeWifiInput.setText(config.homeWifiSsid.orEmpty())
        wifiDepartureDelayInput.setText(config.wifiDepartureDelayMinutes.toString())
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

    private fun captureCurrentHomeWifi() {
        if (!hasFineLocation()) {
            captureWifiAfterPermission = true
            requestForegroundLocationPermissions()
            return
        }
        val ssid = HomeWifiReader(this).currentSsid()
        if (ssid == null) {
            homeWifiStatus.text = "No named Wi-Fi is connected — connect to the home network and try again"
            Toast.makeText(this, "Connect to your home Wi-Fi, then press Use current Wi-Fi", Toast.LENGTH_LONG).show()
            return
        }
        homeWifiInput.setText(ssid)
        homeWifiStatus.text = "Selected current Wi-Fi: $ssid — save automatic settings"
        Toast.makeText(this, "$ssid selected as home Wi-Fi", Toast.LENGTH_LONG).show()
    }

    private fun homeWifiLabel(configuredSsid: String?): String {
        val home = HomeWifiReader.normalizeSsid(configuredSsid)
            ?: return "Home Wi-Fi is optional and is not set"
        val connected = HomeWifiReader(this).isConnectedTo(home)
        return if (connected) {
            "Home Wi-Fi: $home • connected now"
        } else {
            "Home Wi-Fi: $home • not connected now"
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
                val storedTelemetry = liveTelemetryStore.read()
                val duration = active?.let { Duration.between(Instant.parse(it.startAt), Instant.now()) } ?: Duration.ZERO
                val phoneStatus = readDashboardPhoneStatus()
                val dashboardSensors = if (levelCalibrationInProgress) null else calibrationSensors.snapshotAndReset()
                val telemetry = DashboardTelemetry.merge(
                    stored = storedTelemetry,
                    activeTripId = active?.id,
                    sensors = dashboardSensors,
                    orientation = calibrationSensors.orientation(),
                    batteryPercent = phoneStatus.batteryPercent,
                )
                val gpsLabel: String
                if (active == null) {
                    if (::recorderStatus.isInitialized) recorderStatus.text = "Ready to record"
                    if (::telemetryStatus.isInitialized) telemetryStatus.text = "$pending points waiting to synchronize"
                    updateCockpit(null, telemetry, duration)
                    gpsLabel = "GPS STANDBY"
                    if (::gpsChip.isInitialized) setChip(gpsChip, gpsLabel, false)
                    if (::startButton.isInitialized) startButton.isEnabled = true
                    if (::stopButton.isInitialized) stopButton.isEnabled = false
                } else {
                    val speed = ((active.lastSpeedMps ?: 0.0) * 3.6).roundToInt()
                    if (::recorderStatus.isInitialized) recorderStatus.text = "${active.title} • ${formatDuration(duration)}"
                    if (::telemetryStatus.isInitialized) telemetryStatus.text = buildString {
                        append("${"%.2f".format(active.distanceMeters / 1000)} km • $speed km/h")
                        active.lastAccuracyMeters?.let { append(" • GPS ±${it.roundToInt()} m") }
                        append("\n${active.pointCount} recorded • $pending waiting to sync")
                    }
                    updateCockpit(active, telemetry, duration)
                    gpsLabel = active.lastAccuracyMeters?.let { "GPS ±${it.roundToInt()} M" } ?: "GPS SEARCHING"
                    if (::gpsChip.isInitialized) setChip(gpsChip, gpsLabel, active.lastAccuracyMeters != null)
                    if (::startButton.isInitialized) startButton.isEnabled = false
                    if (::stopButton.isInitialized) stopButton.isEnabled = true
                }
                val sync = syncStatus.read()
                if (::synchronizationStatus.isInitialized) synchronizationStatus.text = buildString {
                    append(sync.state)
                    if (sync.message.isNotBlank()) append(" — ${sync.message}")
                }
                if (::queueChip.isInitialized) setChip(queueChip, "$pending PENDING", pending == 0)
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
                if (::homeAssistantChip.isInitialized) setChip(homeAssistantChip, homeAssistantLabel, syncGood)
                if (::autoStatus.isInitialized) autoStatus.text = autoState.status()
                val currentTime = LocalTime.now().format(TIME_FORMAT)
                if (::dashboardClock.isInitialized) dashboardClock.text = currentTime
                if (::landscapeCockpit.isInitialized && !showingSettings) {
                    val currentTripType = active?.tripType ?: quickTripType
                    val vehicle = dashboardVehicle(currentTripType)
                    val course = GaugeDisplayMath.hybridCourse(
                        gpsCourseDegrees = telemetry.bearingDegrees.takeIf { active != null },
                        speedKph = active?.lastSpeedMps?.times(3.6),
                        magneticHeadingDegrees = telemetry.magneticHeadingDegrees,
                    )
                    landscapeCockpit.setState(
                        CockpitState(
                            time = currentTime,
                            vehicleId = vehicle.id,
                            vehicleLabel = vehicle.label,
                            tripTypeLabel = tripTypeLabel(currentTripType),
                            offRoadSceneId = dashboardConfig.offRoadSceneId,
                            recording = active != null,
                            automaticArmed = autoSettings.read().enabled && autoManager.hasBackgroundLocation(),
                            gpsLabel = gpsLabel,
                            pendingLabel = "$pending PENDING",
                            homeAssistantLabel = homeAssistantLabel,
                            wifiConnected = phoneStatus.wifiConnected,
                            networkConnected = phoneStatus.networkConnected,
                            bluetoothEnabled = phoneStatus.bluetoothEnabled,
                            gpsReady = active?.lastAccuracyMeters != null,
                            satelliteCount = telemetry.satelliteCount,
                            pendingCount = pending,
                            homeAssistantConnected = configured && !syncFailed,
                            batteryPercent = phoneStatus.batteryPercent ?: telemetry.batteryPercent?.roundToInt(),
                            batteryCharging = phoneStatus.batteryCharging,
                            batteryTemperatureC = phoneStatus.batteryTemperatureC,
                            tripDurationLabel = formatDuration(duration),
                            tripLabel = if (active == null) {
                                "READY • LOCAL-FIRST RECORDING"
                            } else {
                                "${active.title.uppercase()} • ${formatDuration(duration)} • ${"%.1f".format(active.distanceMeters / 1000)} KM"
                            },
                            readings = dashboardConfig.gaugeIds.map { readingFor(it, active, telemetry, duration) },
                            sideButtons = sideButtonConfig,
                            vehicleViewModeId = dashboardConfig.vehicleViewModeId,
                            pitchDegrees = telemetry.pitchDegrees?.let { normalizeAngle(it - dashboardConfig.pitchOffsetDegrees) },
                            rollDegrees = telemetry.rollDegrees?.let {
                                GaugeDisplayMath.mirroredRollDegrees(it, dashboardConfig.rollOffsetDegrees)
                            },
                            courseDegrees = course?.degrees,
                            courseSource = course?.source,
                            attitudeCautionDegrees = dashboardConfig.attitudeCautionDegrees,
                            attitudeLimitDegrees = dashboardConfig.attitudeLimitDegrees,
                        ),
                    )
                }
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
                        selected.add(gaugeId)
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
        val tripType = active?.tripType ?: quickTripType
        cockpitGauges.forEach { (id, view) ->
            val reading = readingFor(id, active, telemetry, duration)
            view.setReading(reading, tripType)
        }
    }

    private fun readingFor(
        id: String,
        active: ca.gmode.triprecorder.data.TripEntity?,
        telemetry: LiveTelemetry,
        duration: Duration,
    ): CockpitReading {
        val liveForTrip = active != null && telemetry.tripId == active.id
        val unavailable = if (active == null) "READY" else "WAITING"
        val tripType = active?.tripType ?: quickTripType
        fun reading(
            gaugeId: String,
            title: String,
            text: String,
            unit: String,
            numericValue: Double?,
            subtitle: String,
            angle: Double? = null,
        ) = CockpitReading(
            title = title,
            value = text,
            unit = unit,
            progress = GaugeScaleCatalog.progress(gaugeId, numericValue, tripType),
            subtitle = subtitle,
            angleDegrees = angle,
            gaugeId = gaugeId,
            numericValue = numericValue,
        )
        return when (id) {
            "speed" -> {
                val value = if (liveForTrip) telemetry.speedKph else active?.lastSpeedMps?.times(3.6)
                reading(id, "Speed", value?.roundToInt()?.toString() ?: "--", "km/h", value, if (value == null) unavailable else "GPS SPEED")
            }
            "trip_time" -> reading(id, "Trip time", formatDuration(duration), "h:mm", duration.toMinutes().toDouble(), if (active == null) "READY" else "RECORDING")
            "distance" -> {
                val value = active?.distanceMeters?.div(1000.0)
                reading(id, "Distance", value?.let { "%.1f".format(it) } ?: "--", "km", value, if (value == null) unavailable else "TRIP")
            }
            "altitude" -> {
                val value = if (liveForTrip) telemetry.altitudeMeters else active?.lastAltitudeMeters
                reading(id, "GPS altitude", value?.roundToInt()?.toString() ?: "--", "m", value, if (value == null) unavailable else "WGS84")
            }
            "elevation_gain" -> {
                val value = telemetry.elevationGainMeters.takeIf { liveForTrip }
                reading(id, "Elevation gain", value?.roundToInt()?.toString() ?: "--", "m", value, if (value == null) unavailable else "ASCENT")
            }
            "compass" -> {
                val value = telemetry.bearingDegrees.takeIf { liveForTrip }
                reading(id, "GPS course", value?.let(::cardinalDirection) ?: "--", value?.let { "${it.roundToInt()}°" } ?: "degrees", value, if (value == null) unavailable else "COURSE OVER GROUND", value)
            }
            "attitude" -> {
                val pitch = telemetry.pitchDegrees?.let { normalizeAngle(it - dashboardConfig.pitchOffsetDegrees) }
                val roll = telemetry.rollDegrees?.let { GaugeDisplayMath.mirroredRollDegrees(it, dashboardConfig.rollOffsetDegrees) }
                CockpitReading(
                    title = "Attitude",
                    value = if (pitch == null || roll == null) "P --  R --" else "P ${"%+.0f°".format(pitch)}  R ${"%+.0f°".format(roll)}",
                    unit = "",
                    progress = null,
                    subtitle = if (pitch == null || roll == null) unavailable else "LIVE 3D • DRAG TO ORBIT",
                    gaugeId = id,
                )
            }
            "pitch" -> angleReading(id, "Pitch", telemetry.pitchDegrees?.let { normalizeAngle(it - dashboardConfig.pitchOffsetDegrees) }, unavailable, tripType)
            "roll" -> angleReading(
                id,
                "Roll",
                telemetry.rollDegrees?.let { GaugeDisplayMath.mirroredRollDegrees(it, dashboardConfig.rollOffsetDegrees) },
                unavailable,
                tripType,
            )
            "g_force" -> {
                val value = telemetry.accelerationPeakMs2?.div(9.80665)
                reading(id, "Shock peak", value?.let { "%.2f".format(it) } ?: "--", "g", value, if (value == null) unavailable else "LINEAR ACCELERATION")
            }
            "battery" -> {
                val value = telemetry.batteryPercent
                reading(id, "Phone battery", value?.roundToInt()?.toString() ?: "--", "%", value, if (value == null) unavailable else "S24")
            }
            "gps_satellites" -> {
                val value = telemetry.satelliteCount.takeIf { liveForTrip }?.toDouble()
                reading(id, "GPS satellites", value?.roundToInt()?.toString() ?: "--", "used in fix", value, if (value == null) unavailable else "GNSS")
            }
            "gps_accuracy" -> {
                val value = telemetry.accuracyMeters.takeIf { liveForTrip } ?: active?.lastAccuracyMeters
                reading(id, "GPS accuracy", value?.roundToInt()?.toString() ?: "--", "± m", value, if (value != null) "FIX QUALITY" else unavailable)
            }
            "coordinates" -> {
                val coordinate = if (liveForTrip && telemetry.latitude != null && telemetry.longitude != null) "%.4f  %.4f".format(telemetry.latitude, telemetry.longitude) else "--"
                reading(id, "Coordinates", coordinate, "lat / lon", if (liveForTrip) 1.0 else null, if (liveForTrip) "GPS POSITION" else unavailable)
            }
            "pressure" -> {
                val value = telemetry.pressureHpa
                reading(id, "Station pressure", value?.let { "%.0f".format(it) } ?: "--", "hPa", value, if (value == null) unavailable else "S24 BAROMETER")
            }
            else -> placeholderReading(id)
        }
    }

    private fun angleReading(id: String, label: String, value: Double?, status: String, tripType: String): CockpitReading = CockpitReading(
        label,
        value?.let { "%+.0f°".format(it) } ?: "--",
        "",
        GaugeScaleCatalog.progress(id, value, tripType),
        if (value == null) status else "S24 ORIENTATION",
        value,
        id,
        value,
    )

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

    @Suppress("DEPRECATION")
    private fun discoverSideButtonTargets(): List<SideButtonTarget> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { result ->
                val activity = result.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activity.packageName, activity.name)
                val appLabel = result.loadLabel(packageManager).toString().trim().ifBlank { activity.packageName }
                SideButtonTarget(
                    id = SideButtonSettings.APP_PREFIX + component.flattenToString(),
                    label = "APP — $appLabel",
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase() }
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

    private fun readDashboardPhoneStatus(): DashboardPhoneStatus {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val networkConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val wifiConnected = networkConnected && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val batteryManager = getSystemService(BatteryManager::class.java)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val broadcastPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        val rawBattery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryPercent = broadcastPercent ?: rawBattery.takeIf { it in 0..100 }
        val rawTemperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperatureC = rawTemperature
            ?.takeIf { it != Int.MIN_VALUE && it in -500..1000 }
            ?.div(10.0)

        return DashboardPhoneStatus(
            wifiConnected = wifiConnected,
            networkConnected = networkConnected,
            bluetoothEnabled = readBluetoothEnabled(),
            batteryPercent = batteryPercent,
            batteryCharging = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> batteryManager.isCharging
            },
            batteryTemperatureC = temperatureC,
        )
    }

    @SuppressLint("MissingPermission")
    private fun readBluetoothEnabled(): Boolean? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) return null
        return runCatching { getSystemService(BluetoothManager::class.java).adapter?.isEnabled }.getOrNull()
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

    private fun formatCalibration(value: Double): String = "%+.1f°".format(value)

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm")
        private val TRIP_TYPE_LABELS = listOf("Street", "Off road", "Snow", "Water")
        private val TRIP_TYPE_VALUES = listOf("street", "off_road", "snow", "water")
        private const val STATE_EXPORT_TRIP_ID = "pending_export_trip_id"
        private const val STATE_EXPORT_FORMAT_ID = "pending_export_format_id"
    }

    private val BACKGROUND: Int get() = palette.background
    private val PANEL: Int get() = palette.panel
    private val OUTLINE: Int get() = palette.outline
    private val ORANGE: Int get() = palette.accent
    private val MUTED: Int get() = palette.muted
}
