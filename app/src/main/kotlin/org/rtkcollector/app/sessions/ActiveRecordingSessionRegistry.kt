package org.rtkcollector.app.sessions

import java.nio.file.Paths

internal class ActiveSessionRegistry {
    private val lock = Any()
    private var activeLocation: String? = null
    private val destructiveOperationLocations = mutableSetOf<String>()

    fun activate(location: String) {
        val key = normalizedLocation(location)
        synchronized(lock) {
            require(key !in destructiveOperationLocations) {
                "Cannot start recording while this session is being changed destructively."
            }
            val current = activeLocation
            require(current == null || current == key) {
                "Another recording session is already active."
            }
            activeLocation = key
        }
    }

    fun deactivate(location: String) {
        val key = normalizedLocation(location)
        synchronized(lock) {
            if (activeLocation == key) activeLocation = null
        }
    }

    fun isActive(location: String): Boolean =
        synchronized(lock) { activeLocation == normalizedLocation(location) }

    fun isAnyActive(): Boolean = synchronized(lock) { activeLocation != null }

    fun requireInactive(location: String, action: String) {
        require(!isActive(location)) {
            "Cannot $action the active recording session. Stop recording first."
        }
    }

    fun requireNoActiveRecording(action: String) {
        require(!isAnyActive()) {
            "Cannot $action while recording. Stop recording first."
        }
    }

    fun <T> withDestructiveOperation(location: String, action: String, block: () -> T): T {
        val key = normalizedLocation(location)
        synchronized(lock) {
            require(activeLocation != key) {
                "Cannot $action the active recording session. Stop recording first."
            }
            require(destructiveOperationLocations.add(key)) {
                "Cannot $action this session because another destructive operation is already in progress."
            }
        }
        return try {
            block()
        } finally {
            synchronized(lock) {
                check(destructiveOperationLocations.remove(key)) {
                    "Destructive operation lease was already released for $key."
                }
            }
        }
    }

    private fun normalizedLocation(location: String): String {
        require(location.isNotBlank()) { "Session location must not be blank." }
        val trimmedLocation = location.trim()
        if (trimmedLocation.startsWith("content://")) return trimmedLocation
        return runCatching {
            Paths.get(trimmedLocation).toAbsolutePath().normalize().toString()
        }.getOrElse { trimmedLocation }
    }
}

internal object ActiveRecordingSessionRegistry {
    private val delegate = ActiveSessionRegistry()

    fun activate(location: String) = delegate.activate(location)

    fun deactivate(location: String) = delegate.deactivate(location)

    fun isActive(location: String): Boolean = delegate.isActive(location)

    fun isAnyActive(): Boolean = delegate.isAnyActive()

    fun requireInactive(location: String, action: String) = delegate.requireInactive(location, action)

    fun requireNoActiveRecording(action: String) = delegate.requireNoActiveRecording(action)

    fun <T> withDestructiveOperation(location: String, action: String, block: () -> T): T =
        delegate.withDestructiveOperation(location, action, block)
}
