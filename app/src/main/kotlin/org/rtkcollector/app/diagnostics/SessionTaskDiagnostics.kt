package org.rtkcollector.app.diagnostics

fun recordSessionTaskFailure(
    store: DiagnosticsStore,
    enabled: Boolean,
    label: String,
    category: DiagnosticCategory,
    error: Throwable,
) {
    val errorClass = error::class.java.name
    val headline = "$label failed: ${error.message ?: errorClass}"
    val message = "$headline\n${error.stackTraceToString()}"
    store.appendRuntime(
        enabled = enabled,
        record = RuntimeDiagnosticRecord(
            timestampMillis = System.currentTimeMillis(),
            category = category,
            severity = "ERROR",
            message = message,
            attributes = mapOf(
                "task" to label,
                "exception" to errorClass,
            ),
        ),
    )
}
