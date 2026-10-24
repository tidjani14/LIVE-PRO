package com.tvflux.pro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object WarpConfigFetcher {
    private val WARP_API_URL: String by lazy {
        listOf("https://", "tvflux4k", ".com", "/test/", "warp.php").joinToString("")
    }

    suspend fun fetchConfig(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(WARP_API_URL)
                .header("User-Agent", "LIVE-PRO/5.1")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            ChannelRepository.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string()?.trim() ?: return@withContext null
                if (body.isEmpty()) return@withContext null

                val rawConfig = if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    if (json.optString("status") == "success" || json.optBoolean("status", false) || json.optBoolean("success", false)) {
                        val c1 = json.optString("config_text")
                        val c2 = json.optString("config")
                        val c3 = json.optString("data")
                        when {
                            c1.isNotBlank() -> c1
                            c2.isNotBlank() -> c2
                            else -> c3
                        }
                    } else {
                        ""
                    }
                } else if (body.contains("[Interface]", ignoreCase = true)) {
                    body
                } else {
                    ""
                }

                sanitizeWireGuardConfig(rawConfig)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeWireGuardConfig(raw: String): String? {
        if (raw.isBlank() || !raw.contains("[Interface]", ignoreCase = true)) return null
        val allowedInterfaceKeys = setOf("privatekey", "address", "dns", "mtu", "listenport")
        val allowedPeerKeys = setOf("publickey", "presharedkey", "allowedips", "endpoint", "persistentkeepalive")

        var currentSection = ""
        var hasPrivateKey = false
        var hasPublicKey = false
        var hasEndpoint = false
        val cleanLines = mutableListOf<String>()

        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue

            if (trimmed.equals("[Interface]", ignoreCase = true)) {
                currentSection = "interface"
                cleanLines.add("[Interface]")
                continue
            }
            if (trimmed.equals("[Peer]", ignoreCase = true)) {
                currentSection = "peer"
                cleanLines.add("[Peer]")
                continue
            }

            if (trimmed.contains("=")) {
                val key = trimmed.substringBefore("=").trim().lowercase()
                var value = trimmed.substringAfter("=").trim()
                if (value.isEmpty()) continue

                if (currentSection == "interface" && key in allowedInterfaceKeys) {
                    if (key == "privatekey") hasPrivateKey = true
                    cleanLines.add("${trimmed.substringBefore("=").trim()} = $value")
                } else if (currentSection == "peer" && key in allowedPeerKeys) {
                    if (key == "publickey") hasPublicKey = true
                    if (key == "endpoint") {
                        hasEndpoint = true
                        if (value.contains("engage.cloudflareclient.com", ignoreCase = true)) {
                            val port = value.substringAfterLast(":", "2408")
                            value = "162.159.192.1:$port"
                        }
                    }
                    cleanLines.add("${trimmed.substringBefore("=").trim()} = $value")
                }
            }
        }

        if (!hasPrivateKey || !hasPublicKey || !hasEndpoint) return null
        val result = cleanLines.joinToString("\n")
        return result.takeIf {
            it.contains("[Interface]") && it.contains("[Peer]")
        }
    }
}
