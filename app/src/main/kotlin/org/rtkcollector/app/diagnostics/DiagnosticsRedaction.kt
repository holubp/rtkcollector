package org.rtkcollector.app.diagnostics

private val AUTHORIZATION_REGEX = Regex("(?i)Authorization:\\s*\\S+(?:\\s+\\S+)?")
private val SECRET_ASSIGNMENT_REGEX = Regex("(?i)\\b(password|passwd|pwd|token|secret)\\s*[:=]\\s*([^\\s,;]+)")
private val NTRIP_URL_CREDENTIALS_REGEX = Regex("(?i)(ntrip://)[^:@/\\s]+:[^@/\\s]+@")

internal fun redactDiagnosticText(text: String): String =
    text
        .replace(AUTHORIZATION_REGEX, "Authorization: <redacted>")
        .replace(SECRET_ASSIGNMENT_REGEX) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        .replace(NTRIP_URL_CREDENTIALS_REGEX) { match ->
            "${match.groupValues[1]}<redacted>@"
        }
