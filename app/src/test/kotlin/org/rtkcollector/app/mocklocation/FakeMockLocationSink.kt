package org.rtkcollector.app.mocklocation

class FakeMockLocationSink : MockLocationSink {
    val locations = mutableListOf<MockLocationUpdate>()

    override fun publish(update: MockLocationUpdate) {
        locations += update
    }
}
