package org.rtkcollector.app

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.rtkcollector.app.profile.RecordingProfileStore
import org.rtkcollector.app.profile.SavedRecordingDefaults
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.usb.UsbDeviceSummary
import org.rtkcollector.core.workflow.ReceiverCapabilityFixtures
import org.rtkcollector.core.workflow.ReceiverCommandPlan
import org.rtkcollector.core.workflow.ReceiverCommandPlanExamples
import org.rtkcollector.core.workflow.SessionArtifact
import org.rtkcollector.core.workflow.WorkflowDryRunSession
import org.rtkcollector.core.workflow.WorkflowExamples
import org.rtkcollector.core.workflow.WorkflowSpec
import org.rtkcollector.receiver.unicore.Um980BaseCoordinate
import org.rtkcollector.receiver.unicore.Um980CommandBuilder
import org.rtkcollector.receiver.unicore.Um980CommandProfileRequest
import org.rtkcollector.receiver.unicore.Um980CoordinateSource
import org.rtkcollector.receiver.unicore.Um980RuntimeCommandValidator
import org.rtkcollector.receiver.unicore.Um980WorkflowMode
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var workflowSpinner: Spinner
    private lateinit var receiverSpinner: Spinner
    private lateinit var usbSpinner: Spinner
    private lateinit var detailsText: TextView
    private lateinit var validationText: TextView
    private lateinit var monitorText: TextView
    private lateinit var usbStatusText: TextView
    private lateinit var serialBaudEdit: EditText
    private lateinit var profileBaudEdit: EditText
    private lateinit var initCommandsEdit: EditText
    private lateinit var modeCommandsEdit: EditText
    private lateinit var shutdownCommandsEdit: EditText
    private lateinit var baseLatEdit: EditText
    private lateinit var baseLonEdit: EditText
    private lateinit var baseHeightEdit: EditText
    private lateinit var baseFrameEdit: EditText
    private lateinit var baseEpochEdit: EditText
    private lateinit var antennaHeightEdit: EditText
    private lateinit var antennaReferencePointEdit: EditText
    private lateinit var ntripHostEdit: EditText
    private lateinit var ntripPortEdit: EditText
    private lateinit var ntripMountpointEdit: EditText
    private lateinit var ntripUsernameEdit: EditText
    private lateinit var ntripPasswordEdit: EditText
    private lateinit var ntripGgaEdit: EditText
    private lateinit var refreshUsbButton: Button
    private lateinit var requestUsbPermissionButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val usbManager: UsbManager by lazy { getSystemService(USB_SERVICE) as UsbManager }
    private val profileStore: RecordingProfileStore by lazy { RecordingProfileStore(this) }
    private val secretStore: NtripSecretStore by lazy { NtripSecretStore(this) }
    private var usbDevices: List<UsbDevice> = emptyList()
    private var session: WorkflowDryRunSession? = null

    private val receiverOptions = listOf(
        ReceiverOption("UM980/N4", "um980-n4"),
        ReceiverOption("u-blox M8P-0", "ublox-m8p0"),
        ReceiverOption("u-blox M8P-2", "ublox-m8p2"),
        ReceiverOption("u-blox M8T", "ublox-m8t"),
        ReceiverOption("Generic NMEA + RTCM", "generic-nmea-rtcm"),
    )

    private val workflowOptions = listOf(
        WorkflowOption("Plain rover recording", Um980WorkflowMode.ROVER) { receiver ->
            WorkflowExamples.plainRoverRecording(receiver.capabilities(), receiver.profileId)
        },
        WorkflowOption("Rover + NTRIP to receiver", Um980WorkflowMode.ROVER_NTRIP) { receiver ->
            WorkflowExamples.roverWithNtripToReceiver(receiver.capabilities(), receiver.profileId)
        },
        WorkflowOption("Temporary base preparation", Um980WorkflowMode.TEMPORARY_BASE) { receiver ->
            WorkflowExamples.temporaryBasePreparation(receiver.capabilities(), receiver.profileId)
        },
        WorkflowOption("Temporary base + NTRIP", Um980WorkflowMode.TEMPORARY_BASE_NTRIP) { receiver ->
            WorkflowExamples.temporaryBasePreparationWithNtripToReceiver(receiver.capabilities(), receiver.profileId)
        },
        WorkflowOption("Fixed base from accepted position", Um980WorkflowMode.FIXED_BASE_STATUS) { receiver ->
            WorkflowExamples.fixedBaseFromBasePosition(receiver.capabilities(), receiver.profileId)
        },
        WorkflowOption("Fixed base RTCM local recording", Um980WorkflowMode.FIXED_BASE_RTCM_OUTPUT) { receiver ->
            WorkflowExamples.fixedBaseFromBasePosition(receiver.capabilities(), receiver.profileId).copy(
                id = "fixed-base-rtcm-local-recording",
                name = "Fixed base RTCM local recording",
            )
        },
        WorkflowOption("Replay test", null) { receiver ->
            WorkflowExamples.replayTest(receiver.capabilities(), receiver.profileId)
        },
    )

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                renderUsbStatus()
            }
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == RecordingForegroundService.ACTION_STATE) {
                monitorText.text = buildServiceStateText(intent)
                stopButton.isEnabled = intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)
                startButton.isEnabled = !stopButton.isEnabled
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceivers()
        val defaults = profileStore.loadDefaults()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "RtkCollector"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Experimental UM980 E2E recorder. No maps, shapefiles, GIS editing or field-feature collection."
            textSize = 14f
        })

        workflowSpinner = Spinner(this)
        receiverSpinner = Spinner(this)
        usbSpinner = Spinner(this)
        detailsText = TextView(this).apply { textSize = 14f }
        validationText = TextView(this).apply { textSize = 14f }
        monitorText = TextView(this).apply { textSize = 14f }
        usbStatusText = TextView(this).apply { textSize = 14f }
        serialBaudEdit = edit(defaults.serialBaud.toString())
        profileBaudEdit = edit(defaults.profileBaud.toString())
        initCommandsEdit = multilineEdit("# Optional user init commands")
        modeCommandsEdit = multilineEdit("")
        shutdownCommandsEdit = multilineEdit("")
        baseLatEdit = edit("")
        baseLonEdit = edit("")
        baseHeightEdit = edit("")
        baseFrameEdit = edit("ETRF2000")
        baseEpochEdit = edit("")
        antennaHeightEdit = edit("")
        antennaReferencePointEdit = edit("ARP")
        ntripHostEdit = edit(defaults.ntripHost)
        ntripPortEdit = edit(defaults.ntripPort.toString())
        ntripMountpointEdit = edit(defaults.ntripMountpoint)
        ntripUsernameEdit = edit(defaults.ntripUsername)
        ntripPasswordEdit = edit("")
        ntripPasswordEdit.hint = if (defaults.ntripSecretId.isNotBlank() && secretStore.hasPassword(defaults.ntripSecretId)) {
            "Stored password will be used if left blank"
        } else {
            "Not saved in session metadata"
        }
        ntripGgaEdit = edit("")
        refreshUsbButton = Button(this).apply { text = "Refresh USB" }
        requestUsbPermissionButton = Button(this).apply { text = "Request USB permission" }
        startButton = Button(this).apply { text = "Start real recording" }
        stopButton = Button(this).apply {
            text = "Stop recording"
            isEnabled = false
        }

        workflowSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workflowOptions.map { it.label })
        receiverSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, receiverOptions.map { it.label })
        workflowSpinner.onItemSelectedListener = rebuildListener()
        receiverSpinner.onItemSelectedListener = rebuildListener()

        refreshUsbButton.setOnClickListener {
            refreshUsbDevices()
        }
        requestUsbPermissionButton.setOnClickListener {
            requestUsbPermission()
        }
        startButton.setOnClickListener {
            startRealRecording()
        }
        stopButton.setOnClickListener {
            startService(RecordingForegroundService.stopIntent(this))
        }

        root.addView(label("Workflow"))
        root.addView(workflowSpinner)
        root.addView(label("Receiver profile"))
        root.addView(receiverSpinner)
        root.addView(label("USB device"))
        root.addView(usbSpinner)
        root.addView(refreshUsbButton)
        root.addView(requestUsbPermissionButton)
        root.addView(usbStatusText)
        root.addView(label("Profile baud"))
        root.addView(profileBaudEdit)
        root.addView(label("Serial baud after profile"))
        root.addView(serialBaudEdit)
        root.addView(label("User init sequence"))
        root.addView(initCommandsEdit)
        root.addView(label("Workflow mode sequence"))
        root.addView(modeCommandsEdit)
        root.addView(label("Shutdown sequence"))
        root.addView(shutdownCommandsEdit)
        root.addView(label("Accepted base latitude degrees"))
        root.addView(baseLatEdit)
        root.addView(label("Accepted base longitude degrees"))
        root.addView(baseLonEdit)
        root.addView(label("Accepted base ellipsoidal height metres"))
        root.addView(baseHeightEdit)
        root.addView(label("Base coordinate frame/datum"))
        root.addView(baseFrameEdit)
        root.addView(label("Base coordinate epoch"))
        root.addView(baseEpochEdit)
        root.addView(label("Antenna height metres"))
        root.addView(antennaHeightEdit)
        root.addView(label("Antenna reference point"))
        root.addView(antennaReferencePointEdit)
        root.addView(label("NTRIP host"))
        root.addView(ntripHostEdit)
        root.addView(label("NTRIP port"))
        root.addView(ntripPortEdit)
        root.addView(label("NTRIP mountpoint"))
        root.addView(ntripMountpointEdit)
        root.addView(label("NTRIP username"))
        root.addView(ntripUsernameEdit)
        root.addView(label("NTRIP password"))
        root.addView(ntripPasswordEdit)
        root.addView(label("Optional GGA upload line"))
        root.addView(ntripGgaEdit)
        root.addView(detailsText)
        root.addView(validationText)
        root.addView(startButton)
        root.addView(stopButton)
        root.addView(monitorText)

        setContentView(ScrollView(this).apply { addView(root) })
        refreshUsbDevices()
        rebuildSession()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(usbPermissionReceiver) }
        runCatching { unregisterReceiver(serviceStateReceiver) }
        super.onDestroy()
    }

    private fun registerReceivers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION), RECEIVER_NOT_EXPORTED)
            registerReceiver(serviceStateReceiver, IntentFilter(RecordingForegroundService.ACTION_STATE), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbPermissionReceiver, IntentFilter(ACTION_USB_PERMISSION))
            @Suppress("DEPRECATION")
            registerReceiver(serviceStateReceiver, IntentFilter(RecordingForegroundService.ACTION_STATE))
        }
    }

    private fun rebuildListener(): AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                rebuildSession()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

    private fun rebuildSession() {
        val workflowOption = workflowOptions[workflowSpinner.selectedItemPosition.coerceAtLeast(0)]
        val receiverOption = receiverOptions[receiverSpinner.selectedItemPosition.coerceAtLeast(0)]
        val workflow = workflowOption.build(receiverOption)
        val commandPlan = commandPlanFor(workflow)
        session = WorkflowDryRunSession.create(workflow, commandPlan)
        updateGeneratedModeCommands(workflowOption, receiverOption)
        renderWorkflowDetails()
    }

    private fun updateGeneratedModeCommands(workflowOption: WorkflowOption, receiverOption: ReceiverOption) {
        val mode = workflowOption.um980Mode
        modeCommandsEdit.setText(
            when {
                receiverOption.profileId != "um980-n4" -> "# Real V1 recording currently generates UM980/N4 runtime commands only."
                mode == null -> "# Replay test does not send receiver mode commands."
                mode == Um980WorkflowMode.FIXED_BASE_STATUS || mode == Um980WorkflowMode.FIXED_BASE_RTCM_OUTPUT -> {
                    val coordinate = parseBaseCoordinate(showError = false)
                    if (coordinate == null) {
                        "# Enter accepted base coordinates above to generate fixed-base UM980 commands."
                    } else {
                        buildUm980Commands(mode, coordinate).joinToString("\n")
                    }
                }
                else -> buildUm980Commands(mode, null).joinToString("\n")
            },
        )
    }

    private fun renderWorkflowDetails() {
        val current = session ?: return
        val workflow = current.workflow
        val commandPlan = current.commandPlan

        detailsText.text = buildString {
            appendLine()
            appendLine("Workflow: ${workflow.name}")
            appendLine("Receiver role: ${workflow.receiverRole}")
            appendLine("Receiver profile: ${workflow.receiverProfileId}")
            appendLine("Init sequence: ${commandPlan.initSequence.name}")
            appendLine("Mode sequence: ${commandPlan.modeSequence.name}")
            appendLine("Shutdown sequence: ${commandPlan.shutdownSequence.name}")
            appendLine("Expected artifacts:")
            workflow.recording.expectedSessionArtifacts.sortedBy(SessionArtifact::name).forEach {
                appendLine("  ${it.name}")
            }
        }

        validationText.text = buildString {
            appendLine()
            appendLine(if (current.validation.valid) "Workflow validation: valid" else "Workflow validation: blocked")
            current.validation.errors.forEach { appendLine("ERROR ${it.code}: ${it.message}") }
            current.validation.warnings.forEach { appendLine("WARN ${it.code}: ${it.message}") }
        }
    }

    private fun refreshUsbDevices() {
        usbDevices = usbManager.deviceList.values.toList().sortedBy { it.deviceName }
        usbSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            if (usbDevices.isEmpty()) listOf("No USB devices") else usbDevices.map { UsbDeviceSummary.from(it).label },
        )
        renderUsbStatus()
    }

    private fun renderUsbStatus() {
        val device = selectedUsbDevice()
        usbStatusText.text = when {
            device == null -> "USB: no selected device"
            usbManager.hasPermission(device) -> "USB: permission granted for ${device.deviceName}"
            else -> "USB: permission needed for ${device.deviceName}"
        }
    }

    private fun requestUsbPermission() {
        val device = selectedUsbDevice() ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags)
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun startRealRecording() {
        val current = session ?: return
        if (!current.validation.valid) {
            monitorText.text = "Cannot start: workflow validation is blocked."
            return
        }
        val workflow = current.workflow
        val workflowOption = selectedWorkflowOption()
        val receiverOption = selectedReceiverOption()
        if (receiverOption.profileId != "um980-n4") {
            monitorText.text = "Cannot start: experimental real recording V1 currently supports UM980/N4 only."
            return
        }
        val device = selectedUsbDevice()
        if (device == null) {
            monitorText.text = "Cannot start: no USB device selected."
            return
        }
        if (!usbManager.hasPermission(device)) {
            monitorText.text = "Cannot start: USB permission is not granted."
            return
        }
        val profileBaud = parseIntField(profileBaudEdit, "profile baud", 9600..921600) ?: return
        val serialBaud = parseIntField(serialBaudEdit, "serial baud", 9600..921600) ?: return
        val ntripPort = parseIntField(ntripPortEdit, "NTRIP port", 1..65535) ?: return
        val baseCoordinate = if (workflowOption.requiresBaseCoordinate()) parseBaseCoordinate(showError = true) ?: return else null
        if (workflowOption.um980Mode != null) {
            modeCommandsEdit.setText(buildUm980Commands(workflowOption.um980Mode, baseCoordinate).joinToString("\n"))
        }
        val startupCommands = commandLines(initCommandsEdit.text.toString()) + commandLines(modeCommandsEdit.text.toString())
        val shutdownCommands = commandLines(shutdownCommandsEdit.text.toString())
        val invalidCommand = (startupCommands + shutdownCommands).firstOrNull { command ->
            runCatching { Um980RuntimeCommandValidator.validateRuntimeCommand(command) }.isFailure
        }
        if (invalidCommand != null) {
            monitorText.text = "Cannot start: invalid or unsafe receiver command: $invalidCommand"
            return
        }
        val host = ntripHostEdit.text.toString().trim()
        val mountpoint = ntripMountpointEdit.text.toString().trim()
        val username = ntripUsernameEdit.text.toString().trim()
        val secretRef = ntripSecretRef(host, mountpoint, username)
        val runtimePassword = resolveNtripPassword(secretRef)
        saveRecordingDefaults(host, ntripPort, mountpoint, username, secretRef)

        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START
            putExtra(RecordingForegroundService.EXTRA_USB_DEVICE, device)
            putExtra(RecordingForegroundService.EXTRA_PROFILE_BAUD, profileBaud)
            putExtra(RecordingForegroundService.EXTRA_SERIAL_BAUD, serialBaud)
            putStringArrayListExtra(RecordingForegroundService.EXTRA_STARTUP_COMMANDS, ArrayList(startupCommands))
            putStringArrayListExtra(RecordingForegroundService.EXTRA_SHUTDOWN_COMMANDS, ArrayList(shutdownCommands))
            putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, host)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, ntripPort)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT, mountpoint)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_USERNAME, username)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_PASSWORD, runtimePassword)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_SECRET_REF, secretRef)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_GGA, ntripGgaEdit.text.toString())
            putExtra(RecordingForegroundService.EXTRA_WORKFLOW_ID, workflow.id)
            putExtra(RecordingForegroundService.EXTRA_WORKFLOW_NAME, workflow.name)
            putExtra(RecordingForegroundService.EXTRA_RECEIVER_ROLE, workflow.receiverRole.name)
            putExtra(RecordingForegroundService.EXTRA_RECEIVER_PROFILE_ID, workflow.receiverProfileId)
            putExtra(RecordingForegroundService.EXTRA_UM980_PROFILE_ID, workflowOption.um980Mode?.name ?: "")
            putExtra(RecordingForegroundService.EXTRA_COORDINATE_SOURCE, baseCoordinate?.source?.name ?: "NONE")
            putExtra(RecordingForegroundService.EXTRA_BASE_POSITION_JSON, baseCoordinate?.toBasePositionJson().orEmpty())
            putExtra(RecordingForegroundService.EXTRA_VALIDATION_SUMMARY, validationSummary(current))
            putStringArrayListExtra(
                RecordingForegroundService.EXTRA_EXPECTED_ARTIFACTS,
                ArrayList(workflow.recording.expectedSessionArtifacts.map(SessionArtifact::name).sorted()),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        monitorText.text = "Starting recording service..."
    }

    private fun buildServiceStateText(intent: Intent): String =
        buildString {
            appendLine()
            appendLine("Service running: ${intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)}")
            appendLine("Session: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SESSION_PATH) ?: "n/a"}")
            appendLine("Receiver RX bytes: ${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RX_BYTES, 0)}")
            appendLine("TX to receiver bytes: ${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_TX_BYTES, 0)}")
            appendLine("Correction input bytes: ${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_CORRECTION_BYTES, 0)}")
            appendLine("NTRIP: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP) ?: "n/a"}")
            val ggaFixQuality = intent.getIntExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, -1)
            appendLine("GGA fix quality: ${if (ggaFixQuality >= 0) ggaFixQuality else "n/a"}")
            appendLine("BESTNAV position: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE) ?: "n/a"}")
            appendLine("PPP status: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS) ?: "n/a"}")
            appendLine("RTCM frames seen: ${intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RTCM_FRAMES, 0)}")
            appendLine("Last error: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR) ?: "none"}")
        }

    private fun selectedUsbDevice(): UsbDevice? =
        usbDevices.getOrNull(usbSpinner.selectedItemPosition.coerceAtLeast(0))

    private fun selectedWorkflowOption(): WorkflowOption =
        workflowOptions[workflowSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun selectedReceiverOption(): ReceiverOption =
        receiverOptions[receiverSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun buildUm980Commands(
        mode: Um980WorkflowMode,
        baseCoordinate: Um980BaseCoordinate?,
    ): List<String> =
        Um980CommandBuilder().build(
            Um980CommandProfileRequest(
                mode = mode,
                comPort = "COM1",
                outputIntervalSeconds = 1.0,
                enablePpp = mode == Um980WorkflowMode.TEMPORARY_BASE || mode == Um980WorkflowMode.TEMPORARY_BASE_NTRIP,
                baseCoordinate = baseCoordinate,
            ),
        )

    private fun parseBaseCoordinate(showError: Boolean): Um980BaseCoordinate? {
        val hasAnyCoordinate = listOf(baseLatEdit, baseLonEdit, baseHeightEdit).any { it.text.toString().isNotBlank() }
        if (!hasAnyCoordinate && !showError) {
            return null
        }
        val lat = parseDoubleField(baseLatEdit, "base latitude", -90.0..90.0, showError) ?: return null
        val lon = parseDoubleField(baseLonEdit, "base longitude", -180.0..180.0, showError) ?: return null
        val height = parseDoubleField(baseHeightEdit, "base height", -1000.0..10000.0, showError) ?: return null
        val frame = baseFrameEdit.text.toString().trim()
        if (frame.isBlank()) {
            if (showError) {
                monitorText.text = "Cannot start: base coordinate frame/datum is required."
            }
            return null
        }
        return Um980BaseCoordinate(
            latDeg = lat,
            lonDeg = lon,
            heightM = height,
            frame = frame,
            epoch = baseEpochEdit.text.toString().trim().takeIf(String::isNotBlank),
            antennaHeightM = antennaHeightEdit.text.toString().trim().toDoubleOrNull(),
            antennaReferencePoint = antennaReferencePointEdit.text.toString().trim().takeIf(String::isNotBlank),
            source = Um980CoordinateSource.MANUAL,
        )
    }

    private fun parseDoubleField(field: EditText, label: String, range: ClosedFloatingPointRange<Double>, showError: Boolean): Double? {
        val value = field.text.toString().trim().toDoubleOrNull()
        if (value == null || value !in range) {
            if (showError) {
                monitorText.text = "Cannot start: $label must be ${range.start}..${range.endInclusive}."
            }
            return null
        }
        return value
    }

    private fun WorkflowOption.requiresBaseCoordinate(): Boolean =
        um980Mode == Um980WorkflowMode.FIXED_BASE_STATUS || um980Mode == Um980WorkflowMode.FIXED_BASE_RTCM_OUTPUT

    private fun ntripSecretRef(host: String, mountpoint: String, username: String): String =
        if (host.isBlank() || mountpoint.isBlank() || username.isBlank()) {
            ""
        } else {
            "ntrip:${host.lowercase(Locale.US)}:${mountpoint}:${username}"
        }

    private fun resolveNtripPassword(secretRef: String): String {
        val typedPassword = ntripPasswordEdit.text.toString()
        if (secretRef.isBlank()) {
            return typedPassword
        }
        if (typedPassword.isNotBlank()) {
            secretStore.putPassword(secretRef, typedPassword)
            return typedPassword
        }
        return secretStore.getPassword(secretRef).orEmpty()
    }

    private fun saveRecordingDefaults(
        host: String,
        port: Int,
        mountpoint: String,
        username: String,
        secretRef: String,
    ) {
        profileStore.saveDefaults(
            SavedRecordingDefaults(
                ntripHost = host,
                ntripPort = port,
                ntripMountpoint = mountpoint,
                ntripUsername = username,
                ntripSecretId = secretRef,
                profileBaud = profileBaudEdit.text.toString().trim().toIntOrNull() ?: 230400,
                serialBaud = serialBaudEdit.text.toString().trim().toIntOrNull() ?: 230400,
            ),
        )
    }

    private fun validationSummary(current: WorkflowDryRunSession): String =
        "valid=${current.validation.valid}; errors=${current.validation.errors.size}; warnings=${current.validation.warnings.size}"

    private fun Um980BaseCoordinate.toBasePositionJson(): String =
        buildString {
            append("{")
            append("\"latDeg\":${latDeg},")
            append("\"lonDeg\":${lonDeg},")
            append("\"heightM\":${heightM},")
            append("\"frame\":\"${frame.jsonEscape()}\",")
            append("\"epoch\":${epoch.jsonStringOrNull()},")
            append("\"method\":\"MANUAL_KNOWN_POINT\",")
            append("\"antennaHeightM\":${antennaHeightM ?: "null"},")
            append("\"antennaReferencePoint\":${antennaReferencePoint.jsonStringOrNull()},")
            append("\"source\":\"${source.name}\"")
            append("}")
        }

    private fun String?.jsonStringOrNull(): String =
        if (this == null) "null" else "\"${jsonEscape()}\""

    private fun String.jsonEscape(): String =
        buildString {
            this@jsonEscape.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }

    private fun commandPlanFor(workflow: WorkflowSpec): ReceiverCommandPlan =
        ReceiverCommandPlanExamples.safeReferencePlan(
            receiverProfileId = workflow.receiverProfileId,
            receiverRole = workflow.receiverRole,
        )

    private fun commandLines(text: String): List<String> =
        text.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    private fun parseIntField(field: EditText, label: String, range: IntRange): Int? {
        val value = field.text.toString().trim().toIntOrNull()
        if (value == null || value !in range) {
            monitorText.text = "Cannot start: $label must be ${range.first}..${range.last}."
            return null
        }
        return value
    }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
        }

    private fun edit(text: String): EditText =
        EditText(this).apply {
            setText(text)
            textSize = 14f
            setSingleLine(true)
        }

    private fun multilineEdit(text: String): EditText =
        EditText(this).apply {
            setText(text)
            textSize = 13f
            minLines = 3
            setSingleLine(false)
        }

    private data class ReceiverOption(
        val label: String,
        val profileId: String,
    ) {
        fun capabilities() =
            when (profileId) {
                "um980-n4" -> ReceiverCapabilityFixtures.um980N4()
                "ublox-m8p0" -> ReceiverCapabilityFixtures.ubloxM8p0()
                "ublox-m8p2" -> ReceiverCapabilityFixtures.ubloxM8p2()
                "ublox-m8t" -> ReceiverCapabilityFixtures.ubloxM8t()
                else -> ReceiverCapabilityFixtures.genericNmeaRtcm()
            }
    }

    private data class WorkflowOption(
        val label: String,
        val um980Mode: Um980WorkflowMode?,
        val build: (ReceiverOption) -> WorkflowSpec,
    )

    private companion object {
        const val ACTION_USB_PERMISSION = "org.rtkcollector.app.USB_PERMISSION"
    }
}
