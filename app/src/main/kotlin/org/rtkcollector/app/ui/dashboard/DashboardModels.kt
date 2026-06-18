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
                fix = fix.copy(receiverFrequency = planned.fix.receiverFrequency),
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
internal const val DefaultUm980ReceiverFrequency =
    "Frequency BESTNAV/GGA/PPPNAV/ADRNAV/RTKSTATUS/OBSVM -/-/-/-/-/- Hz"
internal const val DefaultUbloxReceiverFrequency =
    "Frequency RAWX/SFRBX/TM2/NAV-PVT/GGA -/-/-/-/- Hz"

internal fun compactDashboardCardColumnCount(
    availableWidthDp: Int,
    availableHeightDp: Int? = null,
): Int =
    if (availableHeightDp != null && availableHeightDp >= availableWidthDp) {
        1
    } else if (availableWidthDp >= CompactDashboardTwoColumnMinWidthDp) {
        2
    } else {
        1
    }

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
    val baseAverageSummary: String? = null,
    val baseAverageWarning: String? = null,
    val baseAverageActive: Boolean = false,
    val baseAverageLatDeg: Double? = null,
    val baseAverageLonDeg: Double? = null,
    val baseAverageHeightM: Double? = null,
    val baseAverageSampleCount: Int = 0,
)

data class CoordinatePair(
    val lat: String,
    val lon: String,
) {
    val latDouble: Double?
        get() = lat.toDoubleOrNull()

    val lonDouble: Double?
        get() = lon.toDoubleOrNull()

    fun displayLabel(): String = "$lat, $lon"
}

data class BaseCoordinateCandidate(
    val coordinates: CoordinatePair,
    val ellipsoidalHeightM: Double,
    val source: String = "MANUAL",
    val sampleCount: Int = 0,
) {
    fun displayLabel(): String =
        "${coordinates.displayLabel()}, h %.3f m".format(java.util.Locale.US, ellipsoidalHeightM)

    fun toManualBasePositionJsonOrNull(): String? {
        val lat = coordinates.latDouble ?: return null
        val lon = coordinates.lonDouble ?: return null
        return "{" +
            "\"latDeg\":$lat," +
            "\"lonDeg\":$lon," +
            "\"heightM\":$ellipsoidalHeightM," +
            "\"frame\":\"UNKNOWN\"," +
            "\"method\":\"MANUAL_KNOWN_POINT\"," +
            "\"source\":\"$source\"," +
            "\"sampleCount\":$sampleCount" +
            "}"
    }

    fun toUm980FixedBaseModeCommandOrNull(): String? {
        val lat = coordinates.latDouble ?: return null
        val lon = coordinates.lonDouble ?: return null
        return "MODE BASE %.10f %.10f %.4f".format(java.util.Locale.US, lat, lon, ellipsoidalHeightM)
    }
}

enum class CoordinateCopyFormat(
    val label: String,
) {
    GEO_URI("geo:lat,lon"),
    LAT_LON("lat,lon"),
    LAT("lat"),
    LON("lon"),
    ;

    fun format(coordinates: CoordinatePair): String =
        when (this) {
            GEO_URI -> "geo:${coordinates.lat},${coordinates.lon}"
            LAT_LON -> "${coordinates.lat},${coordinates.lon}"
            LAT -> coordinates.lat
            LON -> coordinates.lon
    }
}

fun coordinatePairOf(lat: Double, lon: Double): CoordinatePair =
    CoordinatePair(
        lat = "%.10f".format(java.util.Locale.US, lat),
        lon = "%.10f".format(java.util.Locale.US, lon),
    )

fun PositionCardState.coordinatePairOrNull(): CoordinatePair? {
    val parts = latLon.split(",", limit = 2).map { it.trim() }
    if (parts.size != 2 || parts.any { it.isBlank() || it.equals("n/a", ignoreCase = true) }) {
        return null
    }
    return CoordinatePair(lat = parts[0], lon = parts[1])
}

fun PositionCardState.baseCoordinateCandidateOrNull(
    coordinateOverride: CoordinatePair? = null,
    source: String = "MANUAL",
    sampleCount: Int = 0,
    ellipsoidalHeightOverrideM: Double? = null,
): BaseCoordinateCandidate? {
    val coordinates = coordinateOverride ?: coordinatePairOrNull() ?: return null
    val ellipsoidalHeightM = ellipsoidalHeightOverrideM ?: ellipsoidalHeightMetersOrNull() ?: return null
    return BaseCoordinateCandidate(
        coordinates = coordinates,
        ellipsoidalHeightM = ellipsoidalHeightM,
        source = source,
        sampleCount = sampleCount,
    )
}

fun PositionCardState.ellipsoidalHeightMetersOrNull(): Double? =
    ellipsoidalHeight.measurementMetersOrNull()

private fun String.measurementMetersOrNull(): Double? =
    trim()
        .takeUnless { it.isBlank() || it.equals("n/a", ignoreCase = true) }
        ?.substringBefore(' ')
        ?.toDoubleOrNull()

fun PositionCardState.latLonLinesForNarrowLayout(): List<String> {
    val coordinates = coordinatePairOrNull()
    return if (coordinates != null) {
        listOf("Lat ${coordinates.lat}", "Lon ${coordinates.lon}")
    } else {
        listOf(latLon)
    }
}

data class CoordinateAveragingState(
    val active: Boolean = false,
    val sessionLocation: String? = null,
    val fixType: String = "",
    val sampleCount: Int = 0,
    val meanLat: Double? = null,
    val meanLon: Double? = null,
    val meanEllipsoidalHeightM: Double? = null,
    val stoppedReason: String? = null,
) {
    fun averageCoordinateOrNull(): CoordinatePair? {
        val lat = meanLat ?: return null
        val lon = meanLon ?: return null
        return coordinatePairOf(lat, lon)
    }

    fun averageBaseCandidateOrNull(): BaseCoordinateCandidate? {
        val coordinates = averageCoordinateOrNull() ?: return null
        val height = meanEllipsoidalHeightM ?: return null
        return BaseCoordinateCandidate(
            coordinates = coordinates,
            ellipsoidalHeightM = height,
            source = "AVERAGE",
            sampleCount = sampleCount,
        )
    }

    val statusLabel: String
        get() = when {
            active && sampleCount > 0 -> "Avg ${sampleCount}x ${averageBaseCandidateOrNull()?.displayLabel().orEmpty()}".trim()
            active -> "Avg active"
            stoppedReason != null && sampleCount > 0 -> "$stoppedReason · Avg ${sampleCount}x ${averageBaseCandidateOrNull()?.displayLabel().orEmpty()}".trim()
            sampleCount > 0 -> "Avg ${sampleCount}x ${averageBaseCandidateOrNull()?.displayLabel().orEmpty()}".trim()
            stoppedReason != null -> stoppedReason
            else -> "Avg off"
        }
}

fun PositionCardState.serviceCoordinateAveragingState(): CoordinateAveragingState =
    CoordinateAveragingState(
        active = baseAverageActive,
        sampleCount = baseAverageSampleCount,
        meanLat = baseAverageLatDeg,
        meanLon = baseAverageLonDeg,
        meanEllipsoidalHeightM = baseAverageHeightM,
        stoppedReason = baseAverageWarning,
    )

fun startCoordinateAveraging(
    sessionLocation: String?,
    fixType: String,
    coordinates: CoordinatePair?,
    ellipsoidalHeightM: Double?,
): CoordinateAveragingState {
    val activeSessionLocation = sessionLocation.activeSessionLocationOrNull()
        ?: return CoordinateAveragingState(stoppedReason = "No active session")
    if (fixType.isMissingFixType()) {
        return CoordinateAveragingState(stoppedReason = "No fix")
    }
    val lat = coordinates?.latDouble
    val lon = coordinates?.lonDouble
    if (ellipsoidalHeightM == null) {
        return CoordinateAveragingState(stoppedReason = "No ellipsoidal height")
    }
    return if (lat != null && lon != null) {
        CoordinateAveragingState(
            active = true,
            sessionLocation = activeSessionLocation,
            fixType = fixType,
            sampleCount = 1,
            meanLat = lat,
            meanLon = lon,
            meanEllipsoidalHeightM = ellipsoidalHeightM,
        )
    } else {
        CoordinateAveragingState(stoppedReason = "No coordinate")
    }
}

fun CoordinateAveragingState.addSample(
    sessionLocation: String?,
    fixType: String,
    coordinates: CoordinatePair?,
    ellipsoidalHeightM: Double?,
): CoordinateAveragingState {
    if (!active) return this
    val activeSessionLocation = sessionLocation.activeSessionLocationOrNull()
    if (activeSessionLocation == null || activeSessionLocation != this.sessionLocation) {
        return copy(
            active = false,
            stoppedReason = "Session changed",
        )
    }
    if (fixType.isMissingFixType()) {
        return copy(
            active = false,
            stoppedReason = "No fix",
        )
    }
    if (fixType != this.fixType) {
        return copy(
            active = false,
            stoppedReason = "Fix changed",
        )
    }
    val lat = coordinates?.latDouble
    val lon = coordinates?.lonDouble
    if (ellipsoidalHeightM == null) {
        return copy(
            active = false,
            stoppedReason = "No ellipsoidal height",
        )
    }
    if (lat == null || lon == null || meanLat == null || meanLon == null || meanEllipsoidalHeightM == null) {
        return copy(
            active = false,
            stoppedReason = "No coordinate",
        )
    }
    val nextCount = sampleCount + 1
    return copy(
        sampleCount = nextCount,
        meanLat = meanLat + (lat - meanLat) / nextCount,
        meanLon = meanLon + (lon - meanLon) / nextCount,
        meanEllipsoidalHeightM = meanEllipsoidalHeightM + (ellipsoidalHeightM - meanEllipsoidalHeightM) / nextCount,
        stoppedReason = null,
    )
}

private fun String.isMissingFixType(): Boolean {
    val normalized = trim()
    return normalized.isBlank() || normalized.equals("n/a", ignoreCase = true) || normalized.equals("none", ignoreCase = true)
}

internal fun String?.activeSessionLocationOrNull(): String? =
    this
        ?.trim()
        ?.takeUnless { it.isBlank() || it.equals("n/a", ignoreCase = true) }

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
    val receiverFrequency: String = DefaultUm980ReceiverFrequency,
    val receiverMode: String = "n/a",
    val bestSolution: String = "n/a",
    val mockLocation: String = "Disabled",
)

data class NtripCardState(
    val url: String = "n/a",
    val status: String = "n/a",
    val transferred: String = "n/a",
    val stationId: String = "n/a",
    val baseLatLon: String = "n/a",
    val rates: String = "n/a",
    val uploadStatus: String = "Disabled",
    val uploadUrl: String = "n/a",
    val uploadBytes: String = "0 B",
    val uploadDroppedBytes: String = "0 B",
    val uploadLastError: String? = null,
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
