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
}

data class RuntimeDiagnosticRecord(
    val timestampMillis: Long,
    val category: DiagnosticCategory,
    val severity: String,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    fun toJsonLine(): String {
        val messageJson = JSONObject()
            .put("timestampMillis", timestampMillis)
            .put("category", category.name)
            .put("severity", severity)
            .put("message", redactDiagnosticText(message))

        val attributesJson = JSONObject()
        attributes.forEach { (key, value) ->
            attributesJson.put(key, redactDiagnosticText(value))
        }

        messageJson.put("attributes", attributesJson)
        return messageJson.toString()
    }
}

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
)
