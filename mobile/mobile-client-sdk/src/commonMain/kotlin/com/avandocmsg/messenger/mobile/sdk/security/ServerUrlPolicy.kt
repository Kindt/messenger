package com.avandocmsg.messenger.mobile.sdk.security

/**
 * Lab allows HTTP only to known QEMU/dev hosts; production URLs must use HTTPS.
 */
object ServerUrlPolicy {
    private val LAB_HTTP_HOSTS = setOf(
        "10.0.2.2",
        "127.0.0.1",
        "localhost",
        "192.168.76.10",
        "192.168.76.20",
    )

    fun validate(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        require(trimmed.isNotBlank()) { "URL не указан" }

        val withScheme = when {
            trimmed.contains("//") -> trimmed
            else -> "https://$trimmed"
        }

        val match = URL_PATTERN.matchEntire(withScheme)
            ?: throw IllegalArgumentException("Некорректный URL сервера")

        val scheme = match.groupValues[1].lowercase()
        val authority = match.groupValues[2]
        val host = authority.lowercase().substringBefore(':')
        val path = match.groupValues[3]

        when (scheme) {
            "https" -> Unit
            "http" -> require(LAB_HTTP_HOSTS.contains(host)) {
                "HTTP разрешён только для lab-хостов (QEMU 10.0.2.2, 127.0.0.1)"
            }
            else -> throw IllegalArgumentException("Поддерживаются только http/https")
        }

        require(host.isNotBlank()) { "Некорректный host в URL" }
        require(!host.contains("..")) { "Некорректный host в URL" }

        return "$scheme://$authority$path"
    }

    fun isLabHttpUrl(url: String): Boolean {
        val m = URL_PATTERN.matchEntire(url.trim().removeSuffix("/")) ?: return false
        return m.groupValues[1].lowercase() == "http" &&
            LAB_HTTP_HOSTS.contains(m.groupValues[2].lowercase().substringBefore(':'))
    }

    private val URL_PATTERN = Regex(
        "^(https?)://([^/]+)(/.*)?$",
        RegexOption.IGNORE_CASE
    )
}
