package org.rtkcollector.app.ui.dashboard

data class DashboardState(
    val isRecording: Boolean,
    val status: DashboardStatus,
    val position: PositionCardState,
    val fix: FixCardState,
    val ntrip: NtripCardState,
    val files: FilesCardState,
    val profiles: ProfilesCardState,
    val primaryAction: DashboardAction,
    val secondaryActions: List<DashboardAction>,
    val lastError: String? = null,
    val errorCategory: String = "NONE",
    val errorSeverity: String = "NONE",
) {
    fun withPlannedConfiguration(planned: DashboardState): DashboardState =
        if (isRecording) {
            this
        } else {
            copy(
                status = planned.status,
                profiles = planned.profiles,
            )
        }

    companion object {
        fun planned(
            workflow: String,
            mountpoint: String,
            receiver: String,
            storage: String,
            position: PositionCardState = PositionCardState(),
            fix: FixCardState = FixCardState(),
            ntrip: NtripCardState = NtripCardState(),
            files: FilesCardState = FilesCardState(),
            profiles: ProfilesCardState = ProfilesCardState(),
            lastError: String? = null,
            errorCategory: String = "NONE",
            errorSeverity: String = "NONE",
        ): DashboardState =
            DashboardState(
                isRecording = false,
                status = DashboardStatus(
                    workflow = workflow,
                    mountpoint = mountpoint,
                    receiver = receiver,
                    storage = storage,
                    settingsSet = profiles.settingsSet,
                ),
                position = position,
                fix = fix,
                ntrip = ntrip,
                files = files,
                profiles = profiles,
                primaryAction = DashboardAction("Start", DashboardActionKind.START),
                secondaryActions = listOf(DashboardAction("USB access", DashboardActionKind.USB_PERMISSION)),
                lastError = lastError,
                errorCategory = errorCategory,
                errorSeverity = errorSeverity,
            )

        fun running(
            status: DashboardStatus,
            position: PositionCardState,
            fix: FixCardState,
            ntrip: NtripCardState,
            files: FilesCardState,
            profiles: ProfilesCardState = ProfilesCardState(),
            lastError: String? = null,
            errorCategory: String = "NONE",
            errorSeverity: String = "NONE",
        ): DashboardState =
            DashboardState(
                isRecording = true,
                status = status,
                position = position,
                fix = fix,
                ntrip = ntrip,
                files = files,
                profiles = profiles,
                primaryAction = DashboardAction("Stop", DashboardActionKind.STOP),
                secondaryActions = listOf(
                    DashboardAction("NTRIP", DashboardActionKind.NTRIP),
                    DashboardAction("Mark", DashboardActionKind.MARK),
                ),
                lastError = lastError,
                errorCategory = errorCategory,
                errorSeverity = errorSeverity,
            )
    }
}

fun DashboardState.errorClipboardText(): String? {
    val message = lastError?.takeIf { it.isNotBlank() } ?: return null
    return "$errorCategory: $message"
}

private fun String.isMissingOrBogusMountpoint(): Boolean {
    val value = trim()
    return value.isBlank() ||
        value.equals("n/a", ignoreCase = true) ||
        value.equals("a", ignoreCase = true)
}

data class DashboardStatus(
    val settingsSet: String = "n/a",
    val workflow: String = "n/a",
    val mountpoint: String = "n/a",
    val receiver: String = "n/a",
    val storage: String = "n/a",
)

internal const val CompactDashboardTwoColumnMinWidthDp = 340

internal fun compactDashboardCardColumnCount(availableWidthDp: Int): Int =
    if (availableWidthDp >= CompactDashboardTwoColumnMinWidthDp) 2 else 1

internal fun shouldUseRailDashboard(
    layoutPreference: DashboardLayoutPreference,
    availableWidthDp: Int,
    availableHeightDp: Int,
): Boolean =
    layoutPreference == DashboardLayoutPreference.RAIL &&
        availableWidthDp >= 640 &&
        availableWidthDp > availableHeightDp

internal enum class DashboardSetupItem(val label: String) {
    WORKFLOW("Workflow"),
    MOUNTPOINT("Mountpoint"),
    RECEIVER("Receiver"),
    STORAGE("Storage"),
}

internal val defaultDashboardSetupItems: List<DashboardSetupItem> = listOf(
    DashboardSetupItem.WORKFLOW,
    DashboardSetupItem.MOUNTPOINT,
    DashboardSetupItem.RECEIVER,
    DashboardSetupItem.STORAGE,
)

data class PositionCardState(
    val latLon: String = "n/a",
    val ellipsoidalHeight: String = "n/a",
    val altitude: String = "n/a",
    val utcTime: String = "n/a",
    val latError: String = "n/a",
    val lonError: String = "n/a",
)

fun PositionCardState.latLonLinesForNarrowLayout(): List<String> {
    val parts = latLon.split(",", limit = 2).map { it.trim() }
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        listOf("Lat ${parts[0]}", "Lon ${parts[1]}")
    } else {
        listOf(latLon)
    }
}

data class FixCardState(
    val fixType: String = "n/a",
    val satellites: String = "n/a",
    val pdop: String = "n/a",
    val hdopVdop: String = "n/a",
    val horizontalAccuracy: String = "n/a",
    val verticalAccuracy: String = "n/a",
    val differentialAge: String = "n/a",
    val baseline: String = "n/a",
    val pppStatus: String = "n/a",
    val rtkStatus: String = "n/a",
    val rtklibStatus: String = "Not configured",
    val receiverFrequency: String = "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz",
    val receiverMode: String = "n/a",
)

data class NtripCardState(
    val url: String = "n/a",
    val status: String = "n/a",
    val transferred: String = "n/a",
    val stationId: String = "n/a",
    val baseLatLon: String = "n/a",
    val rates: String = "n/a",
)

data class FilesCardState(
    val sessionLocation: String = "n/a",
    val receiverRxBytes: String = "0 B",
    val txToReceiverBytes: String = "0 B",
    val ntripBytes: String = "0 B",
    val nmeaBytes: String = "0 B",
    val zipShareEnabled: Boolean = false,
) {
    val hasRecording: Boolean
        get() = sessionLocation.isNotBlank() && !sessionLocation.equals("n/a", ignoreCase = true)

    val zipShareLabel: String
        get() = when {
            !hasRecording -> "No recording available yet"
            zipShareEnabled -> "Available"
            else -> "After stop"
        }
}

data class ProfilesCardState(
    val settingsSet: String = "n/a",
    val commandProfile: String = "n/a",
    val baudProfile: String = "n/a",
    val ntripCasterProfile: String = "n/a",
    val recordingOutputProfile: String = "n/a",
    val storageLocationProfile: String = "n/a",
)

data class DashboardAction(
    val label: String,
    val kind: DashboardActionKind,
)

enum class DashboardActionKind {
    START,
    STOP,
    NTRIP,
    MARK,
    USB_PERMISSION,
    MENU,
    SHARE_ZIP,
    NEW_SESSION,
}
