package com.example.aura.core.security

/**
 * Utility for sanitizing sensitive credentials, API keys, and authorization tokens
 * before they can be recorded in activity logs, error reports, or telemetry.
 */
object SecretSanitizer {

    private val BEARER_PATTERN = Regex("Bearer\\s+([A-Za-z0-9_\\-\\.]+)", RegexOption.IGNORE_CASE)
    private val GENERIC_KEY_PATTERN = Regex("(?:api_?key|secret|password|access_?token|auth_?token)\\s*[:=]\\s*[\"']?([^\"'\\s,;&]+)[\"']?", RegexOption.IGNORE_CASE)
    private val OPENAI_KEY_PATTERN = Regex("sk-[A-Za-z0-9_\\-]{20,}")
    private val GOOGLE_KEY_PATTERN = Regex("AIza[0-9A-Za-z_\\-]{35}")

    /**
     * Replaces detected keys and tokens in text with [REDACTED].
     */
    fun sanitize(text: String?): String {
        if (text.isNullOrBlank()) return text ?: ""
        var sanitized = text
        sanitized = BEARER_PATTERN.replace(sanitized) { matchResult ->
            "Bearer [REDACTED]"
        }
        sanitized = OPENAI_KEY_PATTERN.replace(sanitized) { "[REDACTED_API_KEY]" }
        sanitized = GOOGLE_KEY_PATTERN.replace(sanitized) { "[REDACTED_API_KEY]" }
        sanitized = GENERIC_KEY_PATTERN.replace(sanitized) { matchResult ->
            val full = matchResult.value
            val secret = matchResult.groupValues.getOrNull(1) ?: ""
            if (secret.isNotBlank()) {
                full.replace(secret, "[REDACTED]")
            } else {
                full
            }
        }
        return sanitized
    }

    /**
     * Sanitizes map values (such as metadata and parameter summaries).
     */
    fun sanitizeMap(map: Map<String, String>): Map<String, String> {
        val sensitiveKeyNames = setOf("key", "apikey", "api_key", "secret", "password", "token", "auth", "credential")
        return map.mapValues { (k, v) ->
            if (sensitiveKeyNames.any { k.contains(it, ignoreCase = true) }) {
                "[REDACTED]"
            } else {
                sanitize(v)
            }
        }
    }
}
