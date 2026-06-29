package org.rtkcollector.app.recording

import org.rtkcollector.app.diagnostics.redactDiagnosticText
import org.rtkcollector.core.correction.NtripCasterUploadEvent

internal fun casterUploadEventJson(event: NtripCasterUploadEvent): String =
    buildString {
        append("{\"type\":\"base-caster-upload\"")
        append(",\"kind\":\"${redactDiagnosticText(event.kind).jsonEscaped()}\"")
        append(",\"message\":\"${redactDiagnosticText(event.message).jsonEscaped()}\"")
        append(",\"timestampMillis\":${event.timestampMillis}}")
    }

private fun String.jsonEscaped(): String =
    buildString {
        for (char in this@jsonEscaped) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
