package org.rtkcollector.app.diagnostics

import org.json.JSONObject

enum class DiagnosticCategory {
    APP,
    USB,
    STORAGE,
    NTRIP,
    RECEIVER_COMMAND,
    SERVICE,
    MOCK_LOCATION,
    RTKLIB,
}

data class RuntimeDiagnosticRecord(
    val timestampMillis: Long,
    val category: DiagnosticCategory,
    val severity: String,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
    val code: String = "runtime-diagnostic",
    val elapsedRealtimeMillis: Long = System.nanoTime() / 1_000_000L,
) {
    fun toJsonLine(): String {
        val messageJson = JSONObject()
            .put("schemaVersion", 1)
            .put("type", code)
            .put("elapsedRealtimeMillis", elapsedRealtimeMillis)
            .put("timestampMillis", timestampMillis)
            .put("category", category.name)
            .put("severity", severity)
            .put("message", redactDiagnosticText(message).take(2048))

        val attributesJson = JSONObject()
        attributes.entries.take(24).forEach { (key, value) ->
            if (key in SAFE_DIAGNOSTIC_KEYS) attributesJson.put(key, redactDiagnosticText(value).take(256))
        }

        messageJson.put("attributes", attributesJson)
        return messageJson.toString()
    }
}

private val SAFE_DIAGNOSTIC_KEYS = setOf("task", "exception", "receiverFamily", "staleForMillis",
    "bytesSinceLastValidFrame", "label", "url", "reason", "level", "receiverRxBytes", "txBytes",
    "correctionBytes", "workflowId", "receiverDriverId", "commandProfileId", "usbBaudProfileId",
    "settingsSetId", "storageKind", "ntripEnabled", "rtklibEnabled", "sessionId")

data class PerformanceDiagnosticSample(
    val timestampMillis: Long,
    val receiverRxBytes: Long,
    val correctionInputBytes: Long,
    val txToReceiverBytes: Long,
    val sessionTotalBytes: Long,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val threadCount: Int,
    val mockLastIntervalMs: Long?,
) {
    fun toJsonLine(): String {
        val json = JSONObject()
            .put("timestampMillis", timestampMillis)
            .put("receiverRxBytes", receiverRxBytes)
            .put("correctionInputBytes", correctionInputBytes)
            .put("txToReceiverBytes", txToReceiverBytes)
            .put("sessionTotalBytes", sessionTotalBytes)
            .put("heapUsedBytes", heapUsedBytes)
            .put("heapMaxBytes", heapMaxBytes)
            .put("threadCount", threadCount)

        if (mockLastIntervalMs != null) {
            json.put("mockLastIntervalMs", mockLastIntervalMs)
        }

        return json.toString()
    }
}

data class DiagnosticsStatus(
    val runtimeLoggingEnabled: Boolean,
    val performanceMonitoringEnabled: Boolean,
    val runtimeBytes: Long,
    val performanceBytes: Long,
    val runtimeFiles: Int,
    val performanceFiles: Int,
    val level: DiagnosticLevel = if (runtimeLoggingEnabled) DiagnosticLevel.VERBOSE else DiagnosticLevel.MINIMAL,
    val retention: DiagnosticsRetention = DiagnosticsRetention(),
    val essentialBytes: Long = 0,
    val essentialFiles: Int = 0,
    val droppedRecords: Long = 0,
    val writeFailures: Long = 0,
)
