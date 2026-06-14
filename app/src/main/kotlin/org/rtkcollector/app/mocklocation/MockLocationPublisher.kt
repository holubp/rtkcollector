package org.rtkcollector.app.mocklocation

import org.rtkcollector.core.solution.BestSolutionSnapshot

data class MockLocationUpdate(
    val latDeg: Double,
    val lonDeg: Double,
    val altitudeM: Double?,
    val horizontalAccuracyM: Float?,
    val timeMillis: Long,
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

class MockLocationPublisher(
    private val sink: MockLocationSink,
) {
    var lastFailure: Throwable? = null
        private set

    fun publish(snapshot: BestSolutionSnapshot?, enabled: Boolean): MockLocationPublishResult {
        if (!enabled) return MockLocationPublishResult.DISABLED
        val current = snapshot ?: return MockLocationPublishResult.STALE
        if (!current.isFresh) return MockLocationPublishResult.STALE
        return runCatching {
            sink.publish(
                MockLocationUpdate(
                    latDeg = current.latDeg,
                    lonDeg = current.lonDeg,
                    altitudeM = current.mslAltitudeM,
                    horizontalAccuracyM = current.horizontalAccuracyM?.toFloat(),
                    timeMillis = current.updatedAtMillis,
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

class FakeMockLocationSink : MockLocationSink {
    val locations = mutableListOf<MockLocationUpdate>()
    override fun publish(update: MockLocationUpdate) {
        locations += update
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
            time = update.timeMillis
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(providerName, location)
    }
}
