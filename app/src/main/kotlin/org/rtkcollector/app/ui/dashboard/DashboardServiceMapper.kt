package org.rtkcollector.app.ui.dashboard

import android.content.Intent
import org.rtkcollector.app.recording.RecordingForegroundService

fun dashboardStateFromRecordingIntent(intent: Intent): DashboardState {
    val running = intent.getBooleanExtra(RecordingForegroundService.EXTRA_STATE_RUNNING, false)
    val lastError = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR)
    val errorCategory = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_CATEGORY) ?: "NONE"
    val errorSeverity = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ERROR_SEVERITY) ?: "NONE"
    val status = DashboardStatus(
        settingsSet = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SETTINGS_SET_LABEL) ?: "n/a",
        workflow = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_WORKFLOW_LABEL) ?: "n/a",
        mountpoint = mountpointFromUrl(intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_URL)),
        receiver = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_LABEL) ?: "n/a",
        storage = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_STORAGE_LABEL) ?: "n/a",
    )
    val position = PositionCardState(
        latLon = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_LAT_LON) ?: "n/a",
        ellipsoidalHeight = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ELLIPSOIDAL_HEIGHT) ?: "n/a",
        altitude = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_ALTITUDE) ?: "n/a",
        utcTime = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_UTC_TIME) ?: "n/a",
        latError = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_LAT_ERROR) ?: "n/a",
        lonError = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_LON_ERROR) ?: "n/a",
    )
    val ggaFixQuality = intent.getIntExtra(RecordingForegroundService.EXTRA_STATE_GGA_FIX_QUALITY, -1).takeIf { it >= 0 }
    val bestnavPositionType = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BESTNAV_POSITION_TYPE)
    val pppStatus = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_PPP_STATUS)
    val fix = FixCardState(
        fixType = displayFixType(bestnavPositionType, ggaFixQuality),
        satellites = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SATELLITES) ?: "n/a",
        pdop = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_PDOP) ?: "n/a",
        hdopVdop = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_HDOP_VDOP) ?: "n/a",
        horizontalAccuracy = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_HORIZONTAL_ACCURACY) ?: "n/a",
        verticalAccuracy = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_VERTICAL_ACCURACY) ?: "n/a",
        differentialAge = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_DIFFERENTIAL_AGE) ?: "n/a",
        baseline = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_BASELINE) ?: "n/a",
        pppStatus = displayPppStatus(pppStatus),
        rtkStatus = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_RECEIVER_RTK_STATUS) ?: "n/a",
        receiverFrequency = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_UM980_FREQUENCY)
            ?: "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz",
        receiverMode = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_UM980_MODE) ?: "n/a",
    )
    val ntrip = NtripCardState(
        url = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_URL) ?: "n/a",
        status = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP) ?: "n/a",
        transferred = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_TRANSFERRED) ?: "0 B",
        stationId = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_STATION_ID) ?: "n/a",
        baseLatLon = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_BASE_LAT_LON) ?: "n/a",
        rates = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_NTRIP_RATES) ?: "n/a",
    )
    val files = FilesCardState(
        sessionLocation = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SESSION_PATH) ?: "n/a",
        receiverRxBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_RX_BYTES, 0)),
        txToReceiverBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_TX_BYTES, 0)),
        ntripBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_CORRECTION_BYTES, 0)),
        nmeaBytes = formatBytes(intent.getLongExtra(RecordingForegroundService.EXTRA_STATE_NMEA_BYTES, 0)),
        zipShareEnabled = !running,
    )
    val profiles = ProfilesCardState(
        settingsSet = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SETTINGS_SET_LABEL) ?: "n/a",
        commandProfile = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SETTINGS_COMMAND_PROFILE_LABEL) ?: "n/a",
        baudProfile = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SETTINGS_BAUD_PROFILE_LABEL) ?: "n/a",
        ntripCasterProfile = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SETTINGS_NTRIP_CASTER_PROFILE_LABEL)
            ?: "n/a",
        recordingOutputProfile = intent.getStringExtra(
            RecordingForegroundService.EXTRA_STATE_SETTINGS_RECORDING_OUTPUT_PROFILE_LABEL,
        ) ?: "n/a",
        storageLocationProfile = intent.getStringExtra(RecordingForegroundService.EXTRA_STATE_SETTINGS_STORAGE_PROFILE_LABEL)
            ?: "n/a",
    )
    return if (running) {
        DashboardState.running(
            status = status,
            position = position,
            fix = fix,
            ntrip = ntrip,
            files = files,
            profiles = profiles,
            lastError = lastError,
            errorCategory = errorCategory,
            errorSeverity = errorSeverity,
        )
    } else {
        DashboardState.planned(
            workflow = status.workflow,
            mountpoint = status.mountpoint,
            receiver = status.receiver,
            storage = status.storage,
            position = position,
            fix = fix,
            ntrip = ntrip,
            files = files,
            profiles = profiles,
            lastError = lastError,
            errorCategory = errorCategory,
            errorSeverity = errorSeverity,
        )
    }
}

private fun mountpointFromUrl(url: String?): String =
    url
        ?.takeUnless { it.equals("n/a", ignoreCase = true) }
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() && it != url }
        ?: "n/a"

private fun displayFixType(
    bestnavPositionType: String?,
    ggaFixQuality: Int?,
): String {
    if (bestnavPositionType.equals("PPP_CONVERGING", ignoreCase = true)) {
        interpretGgaFixQuality(ggaFixQuality)
            .takeIf(::isMeaningfulSolutionStatus)
            ?.let { return it }
    }
    val bestnav = bestnavPositionType
        ?.takeIf(::isMeaningfulSolutionStatus)
        ?.let(::displayBestnavPositionType)
        ?.takeIf(::isMeaningfulSolutionStatus)
    return bestnav
        ?: interpretGgaFixQuality(ggaFixQuality)
}

private fun displayPppStatus(pppStatus: String?): String =
    pppStatus?.takeIf(::isMeaningfulSolutionStatus) ?: "n/a"

private fun isMeaningfulSolutionStatus(status: String): Boolean =
    status.isNotBlank() &&
        !status.equals("n/a", ignoreCase = true) &&
        !status.equals("NONE", ignoreCase = true)

private fun displayBestnavPositionType(positionType: String): String =
    when (positionType.uppercase()) {
        "PSRDIFF", "SBAS", "INS_PSRDIFF" -> "DGPS"
        "NARROW_FLOAT", "IONOFREE_FLOAT", "L1_FLOAT", "INS_RTKFLOAT" -> "RTK float"
        "NARROW_INT", "WIDE_INT", "L1_INT", "INS_RTKFIXED" -> "RTK fix"
        "SINGLE", "INS_PSRSP" -> "Single"
        "PPP" -> "PPP"
        "PPP_CONVERGING" -> "PPP converging"
        else -> positionType
    }
