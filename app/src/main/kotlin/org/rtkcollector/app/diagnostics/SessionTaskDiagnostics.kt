package org.rtkcollector.app.diagnostics

fun recordSessionTaskFailure(
    store: DiagnosticsStore,
    enabled: Boolean,
    label: String,
    category: DiagnosticCategory,
    error: Throwable,
    level: DiagnosticLevel = if (enabled) DiagnosticLevel.VERBOSE else DiagnosticLevel.MINIMAL,
    directWrites: Boolean = false,
) {
    val errorClass = error::class.java.name
    val headline = "$label failed: ${error.message ?: errorClass}"
    val diagnostics = RuntimeDiagnostics(store, directWrites = directWrites, level = { level }) { enabled }
    diagnostics.record(RuntimeDiagnosticRecord(System.currentTimeMillis(), category, "ERROR", headline), DiagnosticLevel.MINIMAL)
    val message = if (level == DiagnosticLevel.DEBUG) "$headline\n${error.stackTraceToString()}" else headline
    diagnostics.record(
        RuntimeDiagnosticRecord(
            timestampMillis = System.currentTimeMillis(),
            category = category,
            severity = "ERROR",
            message = message,
            attributes = mapOf(
                "task" to label,
                "exception" to errorClass,
            ),
        ), if (level == DiagnosticLevel.DEBUG) DiagnosticLevel.DEBUG else DiagnosticLevel.VERBOSE,
    )
}
