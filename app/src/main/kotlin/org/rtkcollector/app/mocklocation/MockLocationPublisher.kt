package org.rtkcollector.app.mocklocation

import android.os.Build
import android.os.Bundle
import org.rtkcollector.core.solution.BestSolutionSelector
import org.rtkcollector.core.solution.BestSolutionSnapshot

data class MockLocationUpdate(
    val latDeg: Double,
    val lonDeg: Double,
    val altitudeM: Double?,
    val mslAltitudeM: Double?,
    val horizontalAccuracyM: Float?,
    val verticalAccuracyM: Float?,
    val timeMillis: Long,
    val satellitesUsed: Int?,
    val satellitesInView: Int?,
)

interface MockLocationSink {
    fun publish(update: MockLocationUpdate)
}

enum class MockLocationPublishResult {
    DISABLED,
    STALE,
    PUBLISHED,
    FAILED,
    NOT_PERMITTED,
}

internal fun mockLocationSetupFailureMessage(error: Throwable): String =
    if (error is SecurityException) {
        "RtkCollector is not the selected mock location app. Enable it in Developer Options."
    } else {
        "Android mock-location provider setup failed: ${error.message ?: error::class.java.simpleName}"
    }

class MockLocationPublisher(
    private val sink: MockLocationSink,
) {
    var lastFailure: Throwable? = null
        private set

    fun publish(snapshot: BestSolutionSnapshot?, enabled: Boolean): MockLocationPublishResult {
        if (!enabled) return MockLocationPublishResult.DISABLED
        val current = snapshot ?: return MockLocationPublishResult.STALE
        if (!current.isFreshFor(maxAgeMillis = BestSolutionSelector.DEFAULT_MAX_AGE_MILLIS)) {
            return MockLocationPublishResult.STALE
        }
        return runCatching {
            sink.publish(
                MockLocationUpdate(
                    latDeg = current.latDeg,
                    lonDeg = current.lonDeg,
                    altitudeM = current.ellipsoidalHeightM,
                    mslAltitudeM = current.mslAltitudeM,
                    horizontalAccuracyM = current.horizontalAccuracyM?.toFloat(),
                    verticalAccuracyM = current.verticalAccuracyM?.toFloat(),
                    timeMillis = current.updatedAtMillis,
                    satellitesUsed = current.satellitesUsed,
                    satellitesInView = current.satellitesInView,
                ),
            )
        }.fold(
            onSuccess = {
                lastFailure = null
                MockLocationPublishResult.PUBLISHED
            },
            onFailure = { error ->
                lastFailure = error
                MockLocationPublishResult.FAILED
            },
        )
    }
}

class AndroidMockLocationSink(
    private val locationManager: android.location.LocationManager,
    private val providerName: String = android.location.LocationManager.GPS_PROVIDER,
) : MockLocationSink {
    override fun publish(update: MockLocationUpdate) {
        val location = android.location.Location(providerName).apply {
            latitude = update.latDeg
            longitude = update.lonDeg
            update.altitudeM?.let { altitude = it }
            update.horizontalAccuracyM?.let { accuracy = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                update.verticalAccuracyM?.let { verticalAccuracyMeters = it }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                update.mslAltitudeM?.let { mslAltitudeMeters = it }
            }
            extras = mockLocationExtras(update)
            time = update.timeMillis
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(providerName, location)
    }
}

internal fun mockLocationExtras(update: MockLocationUpdate): Bundle? {
    val extras = Bundle()
    update.satellitesUsed?.let { used ->
        extras.putInt("satellites", used)
        extras.putInt("satellitesUsed", used)
        extras.putInt("satellitesInUse", used)
    }
    update.satellitesInView?.let { inView ->
        extras.putInt("satellitesInView", inView)
        extras.putInt("satellitesVisible", inView)
    }
    return if (extras.isEmpty) null else extras
}
