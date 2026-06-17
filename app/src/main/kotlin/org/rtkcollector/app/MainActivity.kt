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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject
import org.rtkcollector.app.profile.CommandProfile
import org.rtkcollector.app.profile.NtripCasterProfile
import org.rtkcollector.app.profile.NtripMountpointProfile
import org.rtkcollector.app.profile.ProfileStores
import org.rtkcollector.app.profile.RecordingProfileStore
import org.rtkcollector.app.profile.RecordingPolicyProfile
import org.rtkcollector.app.profile.SavedRecordingDefaults
import org.rtkcollector.app.profile.StorageProfile
import org.rtkcollector.app.profile.UsbBaudProfile
import org.rtkcollector.app.profile.ntripCasterSecretId
import org.rtkcollector.app.recording.RecordingForegroundService
import org.rtkcollector.app.secrets.NtripSecretStore
import org.rtkcollector.app.usb.UsbDeviceSummary
import org.rtkcollector.core.correction.NtripCredentials
import org.rtkcollector.core.correction.NtripSourcetableClient
import org.rtkcollector.core.correction.NtripSourcetableRequest
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

class MainActivity : Activity() {
    private lateinit var workflowSpinner: Spinner
    private lateinit var receiverSpinner: Spinner
    private lateinit var usbSpinner: Spinner
    private lateinit var commandProfileSpinner: Spinner
    private lateinit var usbBaudProfileSpinner: Spinner
    private lateinit var ntripCasterProfileSpinner: Spinner
    private lateinit var ntripMountpointProfileSpinner: Spinner
    private lateinit var ntripSourcetableSpinner: Spinner
    private lateinit var recordingPolicySpinner: Spinner
    private lateinit var storageProfileSpinner: Spinner
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
    private lateinit var basePositionJsonEdit: EditText
    private lateinit var ntripHostEdit: EditText
    private lateinit var ntripPortEdit: EditText
    private lateinit var ntripMountpointEdit: EditText
    private lateinit var ntripUsernameEdit: EditText
    private lateinit var ntripPasswordEdit: EditText
    private lateinit var ntripGgaEdit: EditText
    private lateinit var recordNtripCorrectionInputCheck: CheckBox
    private lateinit var exportNmeaCheck: CheckBox
    private lateinit var exportJsonSolutionCheck: CheckBox
    private lateinit var exportGpxCheck: CheckBox
    private lateinit var recordRemoteBaseRawCheck: CheckBox
    private lateinit var saveCommandProfileButton: Button
    private lateinit var copyCommandProfileButton: Button
    private lateinit var saveUsbBaudProfileButton: Button
    private lateinit var copyUsbBaudProfileButton: Button
    private lateinit var saveNtripCasterProfileButton: Button
    private lateinit var copyNtripCasterProfileButton: Button
    private lateinit var showNtripPasswordButton: Button
    private lateinit var fetchNtripMountpointsButton: Button
    private lateinit var saveNtripMountpointProfileButton: Button
    private lateinit var copyNtripMountpointProfileButton: Button
    private lateinit var updateNtripButton: Button
    private lateinit var disableNtripButton: Button
    private lateinit var saveRecordingPolicyButton: Button
    private lateinit var copyRecordingPolicyButton: Button
    private lateinit var saveStorageProfileButton: Button
    private lateinit var copyStorageProfileButton: Button
    private lateinit var chooseStorageFolderButton: Button
    private lateinit var refreshUsbButton: Button
    private lateinit var requestUsbPermissionButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val usbManager: UsbManager by lazy { getSystemService(USB_SERVICE) as UsbManager }
    private val profileStore: RecordingProfileStore by lazy { RecordingProfileStore(this) }
    private val profileStores: ProfileStores by lazy { ProfileStores(this) }
    private val secretStore: NtripSecretStore by lazy { NtripSecretStore(this) }
    private var usbDevices: List<UsbDevice> = emptyList()
    private var session: WorkflowDryRunSession? = null
    private var commandProfiles: List<CommandProfile> = emptyList()
    private var usbBaudProfiles: List<UsbBaudProfile> = emptyList()
    private var ntripCasterProfiles: List<NtripCasterProfile> = emptyList()
    private var ntripMountpointProfiles: List<NtripMountpointProfile> = emptyList()
    private var sourcetableMountpoints: List<String> = emptyList()
    private var recordingPolicyProfiles: List<RecordingPolicyProfile> = emptyList()
    private var storageProfiles: List<StorageProfile> = emptyList()

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
        WorkflowOption("Temporary base", Um980WorkflowMode.TEMPORARY_BASE) { receiver ->
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
        commandProfileSpinner = Spinner(this)
        usbBaudProfileSpinner = Spinner(this)
        ntripCasterProfileSpinner = Spinner(this)
        ntripMountpointProfileSpinner = Spinner(this)
        ntripSourcetableSpinner = Spinner(this)
        recordingPolicySpinner = Spinner(this)
        storageProfileSpinner = Spinner(this)
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
        basePositionJsonEdit = multilineEdit("")
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
        recordNtripCorrectionInputCheck = CheckBox(this).apply {
            text = "Record NTRIP correction input stream"
            isChecked = true
        }
        exportNmeaCheck = CheckBox(this).apply {
            text = "Export derived NMEA solution sidecar"
            isChecked = true
        }
        exportJsonSolutionCheck = CheckBox(this).apply {
            text = "Export parsed JSONL solution sidecar"
            isChecked = true
        }
        exportGpxCheck = CheckBox(this).apply {
            text = "Export GPX sidecar when enough fields are available"
            isChecked = false
        }
        recordRemoteBaseRawCheck = CheckBox(this).apply {
            text = "Record remote-base raw observations if source supports them"
            isChecked = false
        }
        refreshUsbButton = Button(this).apply { text = "Refresh USB" }
        requestUsbPermissionButton = Button(this).apply { text = "Request USB permission" }
        saveCommandProfileButton = Button(this).apply { text = "Save command profile" }
        copyCommandProfileButton = Button(this).apply { text = "Copy command profile" }
        saveUsbBaudProfileButton = Button(this).apply { text = "Save USB/baud profile" }
        copyUsbBaudProfileButton = Button(this).apply { text = "Copy USB/baud profile" }
        saveNtripCasterProfileButton = Button(this).apply { text = "Save NTRIP caster profile" }
        copyNtripCasterProfileButton = Button(this).apply { text = "Copy NTRIP caster profile" }
        showNtripPasswordButton = Button(this).apply { text = "Show stored NTRIP password" }
        fetchNtripMountpointsButton = Button(this).apply { text = "Fetch mountpoints from caster" }
        saveNtripMountpointProfileButton = Button(this).apply { text = "Save NTRIP mountpoint profile" }
        copyNtripMountpointProfileButton = Button(this).apply { text = "Copy NTRIP mountpoint profile" }
        updateNtripButton = Button(this).apply { text = "Update NTRIP while recording" }
        disableNtripButton = Button(this).apply { text = "Disable NTRIP now" }
        saveRecordingPolicyButton = Button(this).apply { text = "Save recording policy" }
        copyRecordingPolicyButton = Button(this).apply { text = "Copy recording policy" }
        saveStorageProfileButton = Button(this).apply { text = "Save storage profile" }
        copyStorageProfileButton = Button(this).apply { text = "Copy storage profile" }
        chooseStorageFolderButton = Button(this).apply { text = "Choose SAF recording folder" }
        startButton = Button(this).apply { text = "Start real recording" }
        stopButton = Button(this).apply {
            text = "Stop recording"
            isEnabled = false
        }

        workflowSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workflowOptions.map { it.label })
        receiverSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, receiverOptions.map { it.label })
        workflowSpinner.onItemSelectedListener = rebuildListener()
        receiverSpinner.onItemSelectedListener = rebuildListener()
        loadProfileManager()

        refreshUsbButton.setOnClickListener {
            refreshUsbDevices()
        }
        requestUsbPermissionButton.setOnClickListener {
            requestUsbPermission()
        }
        saveCommandProfileButton.setOnClickListener { saveSelectedCommandProfile(copy = false) }
        copyCommandProfileButton.setOnClickListener { saveSelectedCommandProfile(copy = true) }
        saveUsbBaudProfileButton.setOnClickListener { saveSelectedUsbBaudProfile(copy = false) }
        copyUsbBaudProfileButton.setOnClickListener { saveSelectedUsbBaudProfile(copy = true) }
        saveNtripCasterProfileButton.setOnClickListener { saveSelectedNtripCasterProfile(copy = false) }
        copyNtripCasterProfileButton.setOnClickListener { saveSelectedNtripCasterProfile(copy = true) }
        showNtripPasswordButton.setOnClickListener { showStoredNtripPassword() }
        fetchNtripMountpointsButton.setOnClickListener { fetchNtripMountpoints() }
        saveNtripMountpointProfileButton.setOnClickListener { saveSelectedNtripMountpointProfile(copy = false) }
        copyNtripMountpointProfileButton.setOnClickListener { saveSelectedNtripMountpointProfile(copy = true) }
        updateNtripButton.setOnClickListener { updateNtripWhileRecording() }
        disableNtripButton.setOnClickListener {
            startService(
                Intent(this, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_DISABLE_NTRIP),
            )
        }
        saveRecordingPolicyButton.setOnClickListener { saveSelectedRecordingPolicy(copy = false) }
        copyRecordingPolicyButton.setOnClickListener { saveSelectedRecordingPolicy(copy = true) }
        saveStorageProfileButton.setOnClickListener { saveSelectedStorageProfile(copy = false) }
        copyStorageProfileButton.setOnClickListener { saveSelectedStorageProfile(copy = true) }
        chooseStorageFolderButton.setOnClickListener { chooseStorageFolder() }
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
        root.addView(label("Command profile"))
        root.addView(commandProfileSpinner)
        root.addView(saveCommandProfileButton)
        root.addView(copyCommandProfileButton)
        root.addView(label("USB / baud profile"))
        root.addView(usbBaudProfileSpinner)
        root.addView(saveUsbBaudProfileButton)
        root.addView(copyUsbBaudProfileButton)
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
        root.addView(label("Imported base-position.json"))
        root.addView(basePositionJsonEdit)
        root.addView(label("NTRIP host"))
        root.addView(label("NTRIP caster profile"))
        root.addView(ntripCasterProfileSpinner)
        root.addView(saveNtripCasterProfileButton)
        root.addView(copyNtripCasterProfileButton)
        root.addView(showNtripPasswordButton)
        root.addView(ntripHostEdit)
        root.addView(label("NTRIP port"))
        root.addView(ntripPortEdit)
        root.addView(label("NTRIP mountpoint"))
        root.addView(label("NTRIP mountpoint profile"))
        root.addView(ntripMountpointProfileSpinner)
        root.addView(saveNtripMountpointProfileButton)
        root.addView(copyNtripMountpointProfileButton)
        root.addView(fetchNtripMountpointsButton)
        root.addView(label("Cached caster mountpoints"))
        root.addView(ntripSourcetableSpinner)
        root.addView(ntripMountpointEdit)
        root.addView(label("NTRIP username"))
        root.addView(ntripUsernameEdit)
        root.addView(label("NTRIP password"))
        root.addView(ntripPasswordEdit)
        root.addView(label("Optional GGA upload line"))
        root.addView(ntripGgaEdit)
        root.addView(updateNtripButton)
        root.addView(disableNtripButton)
        root.addView(label("Recording policy"))
        root.addView(recordingPolicySpinner)
        root.addView(recordNtripCorrectionInputCheck)
        root.addView(exportNmeaCheck)
        root.addView(exportJsonSolutionCheck)
        root.addView(exportGpxCheck)
        root.addView(recordRemoteBaseRawCheck)
        root.addView(saveRecordingPolicyButton)
        root.addView(copyRecordingPolicyButton)
        root.addView(label("Storage profile"))
        root.addView(storageProfileSpinner)
        root.addView(chooseStorageFolderButton)
        root.addView(saveStorageProfileButton)
        root.addView(copyStorageProfileButton)
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

    @Deprecated("Uses platform Activity API to avoid adding AndroidX only for SAF selection.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_STORAGE_TREE || resultCode != RESULT_OK) {
            return
        }
        val treeUri = data?.data ?: return
        val grantFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, grantFlags)
        }.onFailure { error ->
            monitorText.text = "Could not persist SAF permission: ${error.message}"
            return
        }

        val selected = selectedStorageProfile() ?: return
        val updated = selected.copy(
            kind = "SAF_TREE",
            treeUri = treeUri.toString(),
            name = if (selected.kind == "APP_PRIVATE") "SAF recording folder" else selected.name,
        )
        storageProfiles = saveOrCopy(storageProfiles, selected, updated, copy = false) { it }
        profileStores.saveStorageProfiles(storageProfiles)
        refreshProfileAdapters()
        monitorText.text = "SAF recording folder saved for storage profile: ${updated.name}"
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

    private fun loadProfileManager() {
        commandProfiles = profileStores.commandProfiles()
        usbBaudProfiles = profileStores.usbBaudProfiles()
        ntripCasterProfiles = profileStores.ntripCasterProfiles()
        ntripMountpointProfiles = profileStores.ntripMountpointProfiles()
        recordingPolicyProfiles = profileStores.recordingPolicyProfiles()
        storageProfiles = profileStores.storageProfiles()
        refreshProfileAdapters()
    }

    private fun refreshProfileAdapters() {
        commandProfileSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, commandProfiles.map { it.name })
        usbBaudProfileSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, usbBaudProfiles.map { it.name })
        ntripCasterProfileSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ntripCasterProfiles.map { it.name })
        ntripMountpointProfileSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ntripMountpointProfiles.map { it.name })
        recordingPolicySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, recordingPolicyProfiles.map { it.name })
        storageProfileSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, storageProfiles.map { it.name })

        commandProfileSpinner.onItemSelectedListener = profileListener { applyCommandProfile() }
        usbBaudProfileSpinner.onItemSelectedListener = profileListener { applyUsbBaudProfile() }
        ntripCasterProfileSpinner.onItemSelectedListener = profileListener { applyNtripCasterProfile() }
        ntripMountpointProfileSpinner.onItemSelectedListener = profileListener { applyNtripMountpointProfile() }
        ntripSourcetableSpinner.onItemSelectedListener = profileListener { applyCachedSourcetableMountpoint() }
        recordingPolicySpinner.onItemSelectedListener = profileListener { applyRecordingPolicy() }
        storageProfileSpinner.onItemSelectedListener = profileListener { renderStorageProfile() }

        applyCommandProfile()
        applyUsbBaudProfile()
        applyNtripCasterProfile()
        applyNtripMountpointProfile()
        refreshSourcetableMountpointAdapter()
        applyRecordingPolicy()
        renderStorageProfile()
    }

    private fun profileListener(apply: () -> Unit): AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = apply()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

    private fun selectedCommandProfile(): CommandProfile? =
        commandProfiles.getOrNull(commandProfileSpinner.selectedItemPosition.coerceAtLeast(0))

    private fun selectedUsbBaudProfile(): UsbBaudProfile? =
        usbBaudProfiles.getOrNull(usbBaudProfileSpinner.selectedItemPosition.coerceAtLeast(0))

    private fun selectedNtripCasterProfile(): NtripCasterProfile? =
        ntripCasterProfiles.getOrNull(ntripCasterProfileSpinner.selectedItemPosition.coerceAtLeast(0))

    private fun selectedNtripMountpointProfile(): NtripMountpointProfile? =
        ntripMountpointProfiles.getOrNull(ntripMountpointProfileSpinner.selectedItemPosition.coerceAtLeast(0))

    private fun selectedRecordingPolicy(): RecordingPolicyProfile? =
        recordingPolicyProfiles.getOrNull(recordingPolicySpinner.selectedItemPosition.coerceAtLeast(0))

    private fun selectedStorageProfile(): StorageProfile? =
        storageProfiles.getOrNull(storageProfileSpinner.selectedItemPosition.coerceAtLeast(0))

    private fun applyCommandProfile() {
        selectedCommandProfile()?.let { profile ->
            initCommandsEdit.setText(profile.initScript)
            shutdownCommandsEdit.setText(profile.shutdownScript)
        }
    }

    private fun applyUsbBaudProfile() {
        selectedUsbBaudProfile()?.let { profile ->
            profileBaudEdit.setText(profile.profileBaud.toString())
            serialBaudEdit.setText(profile.serialBaud.toString())
        }
    }

    private fun applyNtripCasterProfile() {
        selectedNtripCasterProfile()?.let { profile ->
            ntripHostEdit.setText(profile.host)
            ntripPortEdit.setText(profile.port.toString())
            ntripUsernameEdit.setText(profile.username)
            ntripPasswordEdit.setText("")
            ntripPasswordEdit.hint = if (profile.secretId.isNotBlank() && secretStore.hasPassword(profile.secretId)) {
                "Stored password will be used if left blank"
            } else {
                "Not saved in session metadata"
            }
            sourcetableMountpoints = profile.sourcetableMountpoints
            refreshSourcetableMountpointAdapter()
        }
    }

    private fun refreshSourcetableMountpointAdapter() {
        ntripSourcetableSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            if (sourcetableMountpoints.isEmpty()) listOf("No cached mountpoints") else sourcetableMountpoints,
        )
    }

    private fun applyCachedSourcetableMountpoint() {
        val mountpoint = sourcetableMountpoints.getOrNull(ntripSourcetableSpinner.selectedItemPosition.coerceAtLeast(0))
        if (!mountpoint.isNullOrBlank()) {
            ntripMountpointEdit.setText(mountpoint)
        }
    }

    private fun applyNtripMountpointProfile() {
        selectedNtripMountpointProfile()?.let { profile ->
            ntripMountpointEdit.setText(profile.mountpoint)
            recordRemoteBaseRawCheck.isEnabled = profile.remoteBaseRawAvailable
            if (!profile.remoteBaseRawAvailable) {
                recordRemoteBaseRawCheck.isChecked = false
            }
        }
    }

    private fun applyRecordingPolicy() {
        selectedRecordingPolicy()?.let { profile ->
            recordNtripCorrectionInputCheck.isChecked = profile.recordNtripCorrectionInput
            exportNmeaCheck.isChecked = profile.exportNmea
            exportJsonSolutionCheck.isChecked = profile.exportJsonSolution
            exportGpxCheck.isChecked = profile.exportGpx
            recordRemoteBaseRawCheck.isChecked = profile.recordRemoteBaseRaw && recordRemoteBaseRawCheck.isEnabled
        }
    }

    private fun renderStorageProfile() {
        val profile = selectedStorageProfile()
        if (profile?.kind == "SAF_TREE") {
            monitorText.text = "Storage profile selected: ${profile.name}. SAF recording will be validated at start."
        }
    }

    private fun saveSelectedCommandProfile(copy: Boolean) {
        val selected = selectedCommandProfile() ?: return
        val updated = selected.copy(
            initScript = initCommandsEdit.text.toString(),
            shutdownScript = shutdownCommandsEdit.text.toString(),
        )
        commandProfiles = saveOrCopy(commandProfiles, selected, updated, copy) { it.copyProfile(profileStores.duplicateId("command"), "${it.name} copy") }
        profileStores.saveCommandProfiles(commandProfiles)
        refreshProfileAdapters()
    }

    private fun saveSelectedUsbBaudProfile(copy: Boolean) {
        val selected = selectedUsbBaudProfile() ?: return
        val updated = selected.copy(
            profileBaud = profileBaudEdit.text.toString().trim().toIntOrNull() ?: selected.profileBaud,
            serialBaud = serialBaudEdit.text.toString().trim().toIntOrNull() ?: selected.serialBaud,
            usbVid = selectedUsbDevice()?.vendorId,
            usbPid = selectedUsbDevice()?.productId,
            usbDeviceName = selectedUsbDevice()?.deviceName,
            usbProductName = selectedUsbDevice()?.productName,
        )
        usbBaudProfiles = saveOrCopy(usbBaudProfiles, selected, updated, copy) { it.copyProfile(profileStores.duplicateId("usb"), "${it.name} copy") }
        profileStores.saveUsbBaudProfiles(usbBaudProfiles)
        refreshProfileAdapters()
    }

    private fun saveSelectedNtripCasterProfile(copy: Boolean) {
        val selected = selectedNtripCasterProfile() ?: return
        val secretId = ntripCasterSecretId(selected.id)
        if (ntripPasswordEdit.text.toString().isNotBlank()) {
            secretStore.putPassword(secretId, ntripPasswordEdit.text.toString())
        }
        val updated = selected.copy(
            host = ntripHostEdit.text.toString().trim(),
            port = ntripPortEdit.text.toString().trim().toIntOrNull() ?: selected.port,
            username = ntripUsernameEdit.text.toString().trim(),
            secretId = secretId,
        )
        ntripCasterProfiles = saveOrCopy(ntripCasterProfiles, selected, updated, copy) { it.copyProfile(profileStores.duplicateId("caster"), "${it.name} copy") }
        profileStores.saveNtripCasterProfiles(ntripCasterProfiles)
        refreshProfileAdapters()
    }

    private fun showStoredNtripPassword() {
        val secretId = selectedNtripCasterProfile()?.secretId.orEmpty()
        monitorText.text = when {
            secretId.isBlank() -> "No stored NTRIP password reference for this caster profile."
            else -> secretStore.getPassword(secretId)?.let { "Stored password: $it" }
                ?: "No stored password found for this caster profile."
        }
    }

    private fun fetchNtripMountpoints() {
        val selected = selectedNtripCasterProfile() ?: return
        val host = ntripHostEdit.text.toString().trim()
        val port = parseIntField(ntripPortEdit, "NTRIP port", 1..65535) ?: return
        if (host.isBlank()) {
            monitorText.text = "Cannot fetch mountpoints: NTRIP host is blank."
            return
        }
        val username = ntripUsernameEdit.text.toString().trim()
        val secretRef = ntripCasterSecretId(selected.id)
        val password = resolveNtripPassword(secretRef)
        val credentials = username.takeIf(String::isNotBlank)?.let { NtripCredentials(it, password) }
        monitorText.text = "Fetching NTRIP sourcetable from $host:$port..."

        Thread(
            {
                runCatching {
                    NtripSourcetableClient(
                        NtripSourcetableRequest(
                            host = host,
                            port = port,
                            credentials = credentials,
                        ),
                    ).fetch()
                }.onSuccess { result ->
                    runOnUiThread {
                        val updated = selected.copy(
                            host = host,
                            port = port,
                            username = username,
                            secretId = secretRef,
                            sourcetableMountpoints = result.mountpoints,
                        )
                        ntripCasterProfiles = ntripCasterProfiles.map { if (it.id == selected.id) updated else it }
                        profileStores.saveNtripCasterProfiles(ntripCasterProfiles)
                        sourcetableMountpoints = result.mountpoints
                        refreshProfileAdapters()
                        monitorText.text = "Fetched ${result.mountpoints.size} mountpoints from $host. Select one or type a mountpoint directly."
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        monitorText.text = "Mountpoint fetch failed: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            },
            "rtkcollector-ntrip-sourcetable",
        ).start()
    }

    private fun saveSelectedNtripMountpointProfile(copy: Boolean) {
        val selected = selectedNtripMountpointProfile() ?: return
        val casterId = selectedNtripCasterProfile()?.id ?: selected.casterProfileId
        val updated = selected.copy(
            casterProfileId = casterId,
            mountpoint = ntripMountpointEdit.text.toString().trim(),
            remoteBaseRawAvailable = recordRemoteBaseRawCheck.isEnabled,
        )
        ntripMountpointProfiles = saveOrCopy(ntripMountpointProfiles, selected, updated, copy) { it.copyProfile(profileStores.duplicateId("mountpoint"), "${it.name} copy") }
        profileStores.saveNtripMountpointProfiles(ntripMountpointProfiles)
        refreshProfileAdapters()
    }

    private fun saveSelectedRecordingPolicy(copy: Boolean) {
        val selected = selectedRecordingPolicy() ?: return
        val updated = selected.copy(
            recordNtripCorrectionInput = recordNtripCorrectionInputCheck.isChecked,
            exportNmea = exportNmeaCheck.isChecked,
            exportJsonSolution = exportJsonSolutionCheck.isChecked,
            exportGpx = exportGpxCheck.isChecked,
            recordRemoteBaseRaw = recordRemoteBaseRawCheck.isChecked,
        )
        recordingPolicyProfiles = saveOrCopy(recordingPolicyProfiles, selected, updated, copy) { it.copyProfile(profileStores.duplicateId("recording"), "${it.name} copy") }
        profileStores.saveRecordingPolicyProfiles(recordingPolicyProfiles)
        refreshProfileAdapters()
    }

    private fun saveSelectedStorageProfile(copy: Boolean) {
        val selected = selectedStorageProfile() ?: return
        storageProfiles = saveOrCopy(storageProfiles, selected, selected, copy) { it.copyProfile(profileStores.duplicateId("storage"), "${it.name} copy") }
        profileStores.saveStorageProfiles(storageProfiles)
        refreshProfileAdapters()
    }

    private fun chooseStorageFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_STORAGE_TREE)
    }

    private fun <T> saveOrCopy(
        profiles: List<T>,
        selected: T,
        updated: T,
        copy: Boolean,
        duplicate: (T) -> T,
    ): List<T> =
        if (copy) {
            profiles + duplicate(updated)
        } else {
            profiles.map { if (it == selected) updated else it }
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
        val um980Mode = workflowOption.um980Mode
        if (um980Mode == null) {
            monitorText.text = "Cannot start: replay/test workflows are not live USB recording workflows."
            return
        }
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
        val storageProfile = selectedStorageProfile()
        if (storageProfile?.kind == "SAF_TREE") {
            val treeUri = storageProfile.treeUri
            if (treeUri.isNullOrBlank() || !hasPersistedSafWritePermission(treeUri)) {
                monitorText.text = "Cannot start: choose the SAF recording folder again so Android grants write permission."
                return
            }
        }
        val profileBaud = parseIntField(profileBaudEdit, "profile baud", 9600..921600) ?: return
        val serialBaud = parseIntField(serialBaudEdit, "serial baud", 9600..921600) ?: return
        val ntripPort = parseIntField(ntripPortEdit, "NTRIP port", 1..65535) ?: return
        val baseCoordinate = if (workflowOption.requiresBaseCoordinate()) parseBaseCoordinate(showError = true) ?: return else null
        val basePositionJson = baseCoordinate?.toBasePositionJson().orEmpty()
        modeCommandsEdit.setText(buildUm980Commands(um980Mode, baseCoordinate).joinToString("\n"))
        val initCommands = commandLines(initCommandsEdit.text.toString())
        val baudSwitchCommands = baudSwitchCommands(profileBaud, serialBaud)
        val modeCommands = commandLines(modeCommandsEdit.text.toString())
        val shutdownCommands = commandLines(shutdownCommandsEdit.text.toString())
        val invalidCommand = (initCommands + baudSwitchCommands + modeCommands + shutdownCommands).firstOrNull { command ->
            runCatching { Um980RuntimeCommandValidator.validateRuntimeCommand(command) }.isFailure
        }
        if (invalidCommand != null) {
            monitorText.text = "Cannot start: invalid or unsafe receiver command: $invalidCommand"
            return
        }
        val host = ntripHostEdit.text.toString().trim()
        val mountpoint = ntripMountpointEdit.text.toString().trim()
        val ntripEnabled = workflowOption.requiresNtrip()
        if (ntripEnabled && (host.isBlank() || mountpoint.isBlank())) {
            monitorText.text = "Cannot start: this workflow requires NTRIP host and mountpoint."
            return
        }
        val username = ntripUsernameEdit.text.toString().trim()
        val secretRef = selectedNtripCasterProfile()?.let { ntripCasterSecretId(it.id) }.orEmpty()
        val runtimePassword = resolveNtripPassword(secretRef)
        saveRecordingDefaults(host, ntripPort, mountpoint, username, secretRef)

        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START
            putExtra(RecordingForegroundService.EXTRA_USB_DEVICE, device)
            putExtra(RecordingForegroundService.EXTRA_PROFILE_BAUD, profileBaud)
            putExtra(RecordingForegroundService.EXTRA_SERIAL_BAUD, serialBaud)
            putStringArrayListExtra(RecordingForegroundService.EXTRA_INIT_COMMANDS, ArrayList(initCommands))
            putStringArrayListExtra(RecordingForegroundService.EXTRA_BAUD_SWITCH_COMMANDS, ArrayList(baudSwitchCommands))
            putStringArrayListExtra(RecordingForegroundService.EXTRA_MODE_COMMANDS, ArrayList(modeCommands))
            putStringArrayListExtra(RecordingForegroundService.EXTRA_SHUTDOWN_COMMANDS, ArrayList(shutdownCommands))
            putExtra(RecordingForegroundService.EXTRA_NTRIP_ENABLED, ntripEnabled)
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
            putExtra(RecordingForegroundService.EXTRA_UM980_PROFILE_ID, um980Mode.name)
            putExtra(RecordingForegroundService.EXTRA_COMMAND_PROFILE_ID, selectedCommandProfile()?.id)
            putExtra(RecordingForegroundService.EXTRA_USB_BAUD_PROFILE_ID, selectedUsbBaudProfile()?.id)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_CASTER_PROFILE_ID, selectedNtripCasterProfile()?.id)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT_PROFILE_ID, selectedNtripMountpointProfile()?.id)
            putExtra(RecordingForegroundService.EXTRA_RECORDING_POLICY_ID, selectedRecordingPolicy()?.id)
            putExtra(RecordingForegroundService.EXTRA_STORAGE_PROFILE_ID, storageProfile?.id)
            putExtra(RecordingForegroundService.EXTRA_STORAGE_KIND, storageProfile?.kind ?: "APP_PRIVATE")
            putExtra(RecordingForegroundService.EXTRA_STORAGE_TREE_URI, storageProfile?.treeUri)
            putExtra(RecordingForegroundService.EXTRA_RECORD_NTRIP_CORRECTION_INPUT, recordNtripCorrectionInputCheck.isChecked)
            putExtra(RecordingForegroundService.EXTRA_EXPORT_NMEA, exportNmeaCheck.isChecked)
            putExtra(RecordingForegroundService.EXTRA_EXPORT_JSON_SOLUTION, exportJsonSolutionCheck.isChecked)
            putExtra(RecordingForegroundService.EXTRA_RECORD_REMOTE_BASE_RAW, recordRemoteBaseRawCheck.isChecked)
            putExtra(RecordingForegroundService.EXTRA_COORDINATE_SOURCE, baseCoordinate?.source?.name ?: "NONE")
            putExtra(RecordingForegroundService.EXTRA_BASE_POSITION_JSON, basePositionJson)
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

    private fun updateNtripWhileRecording() {
        val host = ntripHostEdit.text.toString().trim()
        val port = parseIntField(ntripPortEdit, "NTRIP port", 1..65535) ?: return
        val mountpoint = ntripMountpointEdit.text.toString().trim()
        if (host.isBlank() || mountpoint.isBlank()) {
            monitorText.text = "Cannot update NTRIP: host and mountpoint are required."
            return
        }
        val username = ntripUsernameEdit.text.toString().trim()
        val secretRef = selectedNtripCasterProfile()?.let { ntripCasterSecretId(it.id) }.orEmpty()
        val runtimePassword = resolveNtripPassword(secretRef)
        val intent = Intent(this, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_UPDATE_NTRIP
            putExtra(RecordingForegroundService.EXTRA_NTRIP_HOST, host)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_PORT, port)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_MOUNTPOINT, mountpoint)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_USERNAME, username)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_PASSWORD, runtimePassword)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_SECRET_REF, secretRef)
            putExtra(RecordingForegroundService.EXTRA_NTRIP_GGA, ntripGgaEdit.text.toString())
        }
        startService(intent)
        monitorText.text = "Requested live NTRIP update."
    }

    private fun buildServiceStateText(intent: Intent): String =
        buildString {
            appendLine()
            appendLine("Service running: ${intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)}")
            appendLine("Lifecycle: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_LIFECYCLE) ?: "n/a"}")
            appendLine("Raw recording active: ${intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_RAW_ACTIVE, false)}")
            appendLine("Corrections active: ${intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_CORRECTIONS_ACTIVE, false)}")
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
            appendLine("Error category: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_CATEGORY) ?: "NONE"}")
            appendLine("Error severity: ${intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_SEVERITY) ?: "NONE"}")
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
        runtimeBaud: Int? = null,
    ): List<String> =
        Um980CommandBuilder().build(
            Um980CommandProfileRequest(
                mode = mode,
                comPort = "COM1",
                outputIntervalSeconds = 1.0,
                enablePpp = mode == Um980WorkflowMode.TEMPORARY_BASE || mode == Um980WorkflowMode.TEMPORARY_BASE_NTRIP,
                baseCoordinate = baseCoordinate,
                runtimeBaud = runtimeBaud,
            ),
        )

    private fun baudSwitchCommands(profileBaud: Int, serialBaud: Int): List<String> =
        if (profileBaud == serialBaud) emptyList() else listOf("CONFIG COM1 $serialBaud")

    private fun parseBaseCoordinate(showError: Boolean): Um980BaseCoordinate? {
        val hasManualCoordinate = listOf(baseLatEdit, baseLonEdit, baseHeightEdit).any { it.text.toString().isNotBlank() }
        val importedJson = basePositionJsonEdit.text.toString().trim()
        if (hasManualCoordinate && importedJson.isNotBlank()) {
            if (showError) {
                monitorText.text = "Cannot start: use either manual coordinates or imported base-position.json, not both."
            }
            return null
        }
        if (importedJson.isNotBlank()) {
            return parseImportedBaseCoordinate(importedJson, showError)
        }
        if (!hasManualCoordinate && !showError) {
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

    private fun parseImportedBaseCoordinate(json: String, showError: Boolean): Um980BaseCoordinate? =
        runCatching {
            val parsed = JSONObject(json)
            val lat = parsed.doubleOrNull("latDeg") ?: parsed.doubleOrNull("latitudeDegrees")
            val lon = parsed.doubleOrNull("lonDeg") ?: parsed.doubleOrNull("longitudeDegrees")
            val height = parsed.doubleOrNull("heightM") ?: parsed.doubleOrNull("heightMeters")
            require(lat != null && lon != null && height != null) {
                "base-position.json must include latDeg/lonDeg/heightM or latitudeDegrees/longitudeDegrees/heightMeters."
            }
            Um980BaseCoordinate(
                latDeg = lat,
                lonDeg = lon,
                heightM = height,
                frame = parsed.stringOrNull("frame").orEmpty().ifBlank { parsed.stringOrNull("datum").orEmpty() },
                epoch = parsed.stringOrNull("epoch"),
                antennaHeightM = parsed.doubleOrNull("antennaHeightM") ?: parsed.doubleOrNull("antennaHeightMeters"),
                antennaReferencePoint = parsed.stringOrNull("antennaReferencePoint"),
                source = Um980CoordinateSource.IMPORTED_BASE_POSITION_JSON,
            )
        }.getOrElse { error ->
            if (showError) {
                monitorText.text = "Cannot start: ${error.message ?: "invalid base-position.json"}"
            }
            null
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

    private fun WorkflowOption.requiresNtrip(): Boolean =
        um980Mode == Um980WorkflowMode.ROVER_NTRIP || um980Mode == Um980WorkflowMode.TEMPORARY_BASE_NTRIP

    private fun hasPersistedSafWritePermission(treeUri: String): Boolean =
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri.toString() == treeUri && permission.isWritePermission
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

    private fun JSONObject.doubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name).takeIf { !it.isNaN() } else null

    private fun JSONObject.stringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf(String::isNotBlank) else null

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
        const val REQUEST_STORAGE_TREE = 42
    }
}
