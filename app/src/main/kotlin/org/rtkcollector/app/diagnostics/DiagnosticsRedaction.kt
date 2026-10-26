package org.rtkcollector.app.diagnostics

import org.json.JSONArray
import org.json.JSONObject

private val AUTHORIZATION_REGEX = Regex("(?i)Authorization:\\s*\\S+(?:\\s+\\S+)?")
private const val SECRET_KEYS = "password|passwd|pwd|token|secret|sourcePassword|access_token|refresh_token"
private const val SECRET_VALUE = "(?:\"(?:\\\\.|[^\"\\\\])*(?:\"|$)|'(?:\\\\.|[^'\\\\])*(?:'|$)|[^\\s,;}]+)"
private val SECRET_ASSIGNMENT_REGEX = Regex("(?i)\\b($SECRET_KEYS)\\s*[:=]\\s*$SECRET_VALUE")
private val JSON_SECRET_REGEX = Regex("(?i)([\"'](?:$SECRET_KEYS|authorization)[\"']\\s*:\\s*)$SECRET_VALUE")
private val NTRIP_URL_CREDENTIALS_REGEX = Regex("(?i)((?:ntrips?|https?)://)[^:@/\\s]+:[^@/\\s]+@")
private val SOURCE_PASSWORD_REGEX = Regex("(?m)\\bSOURCE[ \\t]+\\S+|(?i:\\bSOURCE[ \\t]+\\S+(?=[ \\t]+/))")
private val SECRET_CONTAINER = Regex("(?i)\\b[\\w-]*(?:passwords|secrets|credentials|tokens)[\"']?\\s*[:=]")
private val ENCODED_SECRET = Regex("(?i)password|passwd|pwd|token|secret|authorization")

internal fun redactDiagnosticText(text: String): String {
    return normalizeJson(text, 0)
}

private fun normalizeJson(text: String, depth: Int): String {
    if (depth > 8) return "<omitted>"
    val normalized = runCatching {
        when {
            text.trimStart().startsWith("{") -> sanitizeJson(JSONObject(text), depth).toString()
            text.trimStart().startsWith("[") -> sanitizeJson(JSONArray(text), depth).toString()
            else -> redactPlainText(text)
        }
    }.getOrElse { redactPlainText(text) }
    return normalized
}

private fun redactPlainText(text: String): String {
    if (SECRET_CONTAINER.containsMatchIn(text) || (text.contains("\\\"") && ENCODED_SECRET.containsMatchIn(text))) {
        return "<credential-bearing diagnostic content omitted>"
    }
    return text
        .replace(SOURCE_PASSWORD_REGEX, "SOURCE <redacted>")
        .replace(AUTHORIZATION_REGEX, "Authorization: <redacted>")
        .replace(JSON_SECRET_REGEX) { "${it.groupValues[1]}\"<redacted>\"" }
        .replace(SECRET_ASSIGNMENT_REGEX) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        .replace(NTRIP_URL_CREDENTIALS_REGEX) { match ->
            "${match.groupValues[1]}<redacted>@"
        }
}

private fun sanitizeJson(value: Any, depth: Int): Any {
    if (depth > 8) return "<omitted>"
    return when (value) {
        is JSONObject -> JSONObject().also { safe ->
            value.keys().forEach { key ->
                val normalized = key.lowercase()
                val secret = listOf("password", "passwd", "token", "secret", "authorization", "credential").any(normalized::contains) ||
                    normalized in setOf("pwd", "pass", "username", "user", "apikey", "api_key", "privatekey", "private_key")
                safe.put(key, if (secret) "<redacted>" else sanitizeJson(value.get(key), depth + 1))
            }
        }
        is JSONArray -> JSONArray().also { safe -> for (index in 0 until value.length()) safe.put(sanitizeJson(value.get(index), depth + 1)) }
        is String -> normalizeJson(value, depth + 1)
        else -> value
    }
}
