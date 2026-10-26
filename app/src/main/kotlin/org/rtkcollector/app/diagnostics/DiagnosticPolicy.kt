package org.rtkcollector.app.diagnostics

enum class DiagnosticLevel {
    MINIMAL, VERBOSE, DEBUG;
    fun includes(required: DiagnosticLevel): Boolean = ordinal >= required.ordinal
    val advisory: String? get() = when (this) {
        MINIMAL -> null
        VERBOSE -> "Verbose logging enabled"
        DEBUG -> "Debug logging enabled"
    }
    companion object {
        fun fromStored(value: String?, legacyEnabled: Boolean): DiagnosticLevel =
            entries.firstOrNull { it.name == value } ?: if (legacyEnabled) VERBOSE else MINIMAL
    }
}

data class DiagnosticsRetention(val maxOptionalBytes: Long = 10_000_000L, val maxOptionalAgeDays: Int = 7) {
    init { require(maxOptionalBytes > 0); require(maxOptionalAgeDays in AGE_CHOICES_DAYS) }
    companion object {
        val SIZE_CHOICES_MB = listOf(5, 10, 25, 50)
        val AGE_CHOICES_DAYS = listOf(1, 7, 30)
    }
}
