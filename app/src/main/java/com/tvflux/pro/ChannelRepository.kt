package com.tvflux.pro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ChannelRepository {
    private val INTERNAL_ENDPOINT: String by lazy {
        listOf("https://", "tvflux4k", ".com", "/p2p/", "api.php").joinToString("")
    }

    val bootstrapHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(CloudflareDohManager.customDns)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(22, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Balanced timeouts for HLS/TS segment & stream requests so stalled sockets never hang forever
    val streamHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(CloudflareDohManager.customDns)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun cancelPendingStreamRequests() {
        runCatching {
            streamHttpClient.dispatcher.cancelAll()
        }
    }

    suspend fun pushCustomerXtreamToPortal(
        deviceId: String,
        deviceName: String,
        account: XtreamAccountConfig
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val form = FormBody.Builder()
                .add("action", "save_xtream")
                .add("device_id", deviceId)
                .add("device_name", deviceName)
                .add("playlist_id", account.id)
                .add("server_name", account.serverName)
                .add("server_url", account.serverUrl)
                .add("xtream_user", account.username)
                .add("xtream_pass", account.password)
                .add("m3u_url", account.m3uUrl)
                .build()
            val req = Request.Builder()
                .url(INTERNAL_ENDPOINT)
                .post(form)
                .header("User-Agent", "LIVE-PRO/5.1 (Android; TV)")
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    suspend fun redeemActivationCode(
        deviceId: String,
        deviceName: String,
        rawCode: String
    ): ApiSyncResult = withContext(Dispatchers.IO) {
        val cleanCode = rawCode.trim().uppercase()
        require(cleanCode.length >= 4) { "يرجى إدخال رمز تنشيط صحيح" }

        val normalizedAsDeviceId = if (cleanCode.startsWith("DEV-")) {
            cleanCode
        } else if (Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(cleanCode)) {
            "DEV-$cleanCode"
        } else if (Regex("^[A-Z0-9]{8}$").matches(cleanCode)) {
            "DEV-${cleanCode.substring(0, 4)}-${cleanCode.substring(4, 8)}"
        } else {
            deviceId
        }

        runCatching {
            val form = FormBody.Builder()
                .add("action", "activate_card")
                .add("device_id", deviceId)
                .add("device_name", deviceName)
                .add("code", cleanCode)
                .add("card_code", cleanCode)
                .build()
            val req = Request.Builder()
                .url(INTERNAL_ENDPOINT)
                .post(form)
                .header("User-Agent", "LIVE-PRO/5.1 (Android; TV)")
                .build()
            httpClient.newCall(req).execute().close()
        }

        val primarySync = syncWithServer(deviceId, deviceName, cleanCode)
        if (primarySync.subscription.active) {
            return@withContext primarySync
        }

        if (normalizedAsDeviceId != deviceId) {
            val altSync = syncWithServer(normalizedAsDeviceId, deviceName, cleanCode)
            if (altSync.subscription.active) {
                return@withContext altSync
            }
        }

        primarySync
    }

    suspend fun syncWithServer(
        deviceId: String,
        deviceName: String,
        activationCode: String = ""
    ): ApiSyncResult = withContext(Dispatchers.IO) {
        val codeParam = if (activationCode.isNotBlank()) "&code=${enc(activationCode)}" else ""
        val url = "$INTERNAL_ENDPOINT?action=get_channels&device_id=${enc(deviceId)}&device_name=${enc(deviceName)}$codeParam"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "LIVE-PRO/5.1 (Android; TV)")
            .header("Accept", "application/json")
            .build()

        val (statusCode, rawBody) = try {
            httpClient.newCall(req).execute().use { resp ->
                Pair(resp.code, resp.body?.string().orEmpty())
            }
        } catch (_: javax.net.ssl.SSLException) {
            error("فشل التحقق الآمن من شهادة TLS لخادم البث.")
        } catch (_: Exception) {
            error("تعذّر الاتصال بخادم البث. تحقّق من الإنترنت.")
        }

        if (rawBody.isBlank()) {
            error("استجابة غير مكتملة من الخادم (رمز $statusCode)")
        }

        val root = try {
            JSONObject(rawBody.trim())
        } catch (_: Exception) {
            error("تعذّر قراءة بيانات الخادم حالياً. حاول مجدداً.")
        }

        val success = root.optBoolean("success", false)
        val dataObj = root.optJSONObject("data")

        val subObj = dataObj?.optJSONObject("subscription") ?: root.optJSONObject("subscription")
        val rawErr = root.optString("error")
        val topError = if (rawErr.isNotBlank()) rawErr else subObj?.optString("message").orEmpty()

        val remoteXtreamLists = mutableListOf<XtreamAccountConfig>()

        val multiArrays = listOfNotNull(
            dataObj?.optJSONArray("xtream_lists"),
            dataObj?.optJSONArray("playlists"),
            dataObj?.optJSONArray("xtream_accounts"),
            root.optJSONArray("xtream_lists"),
            root.optJSONArray("playlists"),
            subObj?.optJSONArray("xtream_lists")
        )
        for (arr in multiArrays) {
            for (i in 0 until arr.length()) {
                val xo = arr.optJSONObject(i) ?: continue
                parseRemoteXtreamObject(xo, i)?.let { cfg ->
                    if (remoteXtreamLists.none { it.serverUrl == cfg.serverUrl && it.username == cfg.username && it.m3uUrl == cfg.m3uUrl }) {
                        remoteXtreamLists.add(cfg)
                    }
                }
            }
        }

        val singleXtreamObj = dataObj?.optJSONObject("xtream")
            ?: root.optJSONObject("xtream")
            ?: subObj?.optJSONObject("xtream")
        if (singleXtreamObj != null) {
            parseRemoteXtreamObject(singleXtreamObj, remoteXtreamLists.size)?.let { cfg ->
                if (remoteXtreamLists.none { it.serverUrl == cfg.serverUrl && it.username == cfg.username }) {
                    remoteXtreamLists.add(cfg)
                }
            }
        }

        val subscription = if (subObj != null) {
            val activeFlag = subObj.optBoolean("active", success && statusCode == 200)
            val reasonStr = subObj.optString("reason", if (success) "ok" else "restricted")
            val days = subObj.optInt("days_left", if (activeFlag) 30 else 0)
            val msgStr = subObj.optString("message", topError)
            val expiredFlag = !activeFlag && (
                reasonStr.contains("expir", ignoreCase = true) ||
                    reasonStr.contains("end", ignoreCase = true) ||
                    msgStr.contains("انته", ignoreCase = true) ||
                    msgStr.contains("منته", ignoreCase = true) ||
                    msgStr.contains("expir", ignoreCase = true) ||
                    days < 0
                )
            SubscriptionInfo(
                active = activeFlag,
                isExpired = expiredFlag,
                reason = reasonStr,
                expireDate = subObj.optString("expire", ""),
                daysLeft = days,
                deviceName = subObj.optString("device_name", deviceName),
                message = msgStr
            )
        } else {
            val expiredFlag = !success && (
                topError.contains("انته", ignoreCase = true) ||
                    topError.contains("منته", ignoreCase = true) ||
                    topError.contains("expir", ignoreCase = true)
                )
            SubscriptionInfo(
                active = success && statusCode == 200,
                isExpired = expiredFlag,
                reason = if (success) "ok" else "error",
                message = topError
            )
        }

        if (!success || !subscription.active) {
            val fallbackMsg = when {
                subscription.message.isNotBlank() -> subscription.message
                topError.isNotBlank() -> topError
                else -> "انتهت صلاحية الاشتراك أو الشاشة غير مفعّلة. يرجى التواصل مع علي سات : $ALI_SAT_PHONE_NUMBER"
            }
            return@withContext ApiSyncResult(
                channels = emptyList(),
                subscription = subscription.copy(
                    active = false,
                    message = fallbackMsg
                ),
                remoteXtreamLists = remoteXtreamLists
            )
        }

        val channelsArr: JSONArray = when {
            dataObj?.opt("channels") is JSONArray -> dataObj.getJSONArray("channels")
            root.opt("data") is JSONArray -> root.getJSONArray("data")
            root.opt("channels") is JSONArray -> root.getJSONArray("channels")
            else -> JSONArray()
        }

        val parsedChannels = ArrayList<TvChannel>()
        val parsedMovies = ArrayList<TvChannel>()
        val parsedSeries = ArrayList<TvChannel>()

        for (i in 0 until channelsArr.length()) {
            val o = channelsArr.optJSONObject(i) ?: continue
            val rawStreamUrl = first(
                o,
                "original_url",
                "encrypted_url",
                "url",
                "stream_url",
                "play_url",
                "link"
            )
            if (rawStreamUrl.isBlank()) continue

            val pipe = splitPipeHeaders(rawStreamUrl)
            val id = o.optInt("id", i + 1)
            val number = o.optInt("number", i + 1)
            val rawName = first(o, "name", "title", "channel_name")
            val name = if (rawName.isNotBlank()) rawName else "قناة $number"
            val rawCat = first(o, "category", "group", "category_name")
            val category = if (rawCat.isNotBlank()) rawCat else "عام"
            val logo = first(o, "logo_url", "logo", "stream_icon", "icon")
                .takeIf { it.startsWith("http://") || it.startsWith("https://") }

            val rawType = first(o, "type", "stream_type", "content_type").lowercase()
            val lowCat = category.lowercase()
            val lowUrl = pipe.cleanUrl.lowercase()

            val contentType = when {
                rawType == "movie" || rawType == "vod" || lowCat.contains("افلام") || lowCat.contains("أفلام") ||
                    lowCat.contains("movie") || lowCat.contains("vod") || lowUrl.contains("/movie/") ->
                    StreamContentType.MOVIE
                rawType == "series" || lowCat.contains("مسلسلات") || lowCat.contains("series") ||
                    lowUrl.contains("/series/") ->
                    StreamContentType.SERIES
                else ->
                    StreamContentType.LIVE_TV
            }

            val item = TvChannel(
                id = id,
                number = number,
                name = name,
                url = pipe.cleanUrl,
                group = category,
                logo = logo,
                userAgent = first(o, "user_agent", "userAgent").takeIf { it.isNotBlank() } ?: pipe.userAgent,
                referer = first(o, "referer", "referrer").takeIf { it.isNotBlank() } ?: pipe.referer,
                origin = first(o, "origin").takeIf { it.isNotBlank() } ?: pipe.origin,
                cookie = first(o, "cookie").takeIf { it.isNotBlank() } ?: pipe.cookie,
                contentType = contentType,
                playlistId = "PORTAL_MAIN",
                playlistName = "قائمة الخادم الرئيسية"
            )

            when (contentType) {
                StreamContentType.MOVIE -> parsedMovies.add(item)
                StreamContentType.SERIES -> parsedSeries.add(item)
                StreamContentType.LIVE_TV -> parsedChannels.add(item)
            }
        }

        parsedChannels.sortBy { it.number }

        ApiSyncResult(
            channels = parsedChannels,
            movies = parsedMovies,
            series = parsedSeries,
            subscription = subscription,
            remoteXtreamLists = remoteXtreamLists
        )
    }

    private fun parseRemoteXtreamObject(xo: JSONObject, index: Int): XtreamAccountConfig? {
        val sUrl = first(xo, "server_url", "url", "host", "portal_url")
        val sUser = first(xo, "username", "user", "xtream_user")
        val sPass = first(xo, "password", "pass", "xtream_pass")
        val m3u = first(xo, "m3u_url", "m3u", "playlist_url")
        val sName = first(xo, "server_name", "name", "title").ifBlank { "قائمة XTREAM #${index + 1}" }
        val id = first(xo, "id", "playlist_id").ifBlank { "REMOTE_${index + 1}_${sUrl.hashCode()}" }

        return if ((sUrl.isNotBlank() && sUser.isNotBlank() && sPass.isNotBlank()) || m3u.isNotBlank()) {
            XtreamAccountConfig(
                id = id,
                serverName = sName,
                serverUrl = sUrl,
                username = sUser,
                password = sPass,
                m3uUrl = m3u,
                enabled = xo.optBoolean("enabled", true),
                isRemoteFromPortal = true,
                orderIndex = index
            )
        } else null
    }

    private data class PipeHeaderResult(
        val cleanUrl: String,
        val userAgent: String? = null,
        val referer: String? = null,
        val origin: String? = null,
        val cookie: String? = null
    )

    private fun splitPipeHeaders(rawUrl: String): PipeHeaderResult {
        if (!rawUrl.contains('|')) return PipeHeaderResult(rawUrl.trim())
        val mainUrl = rawUrl.substringBefore('|').trim()
        val headerPart = rawUrl.substringAfter('|').trim()
        var ua: String? = null
        var ref: String? = null
        var orig: String? = null
        var cook: String? = null

        val pairs = headerPart.split('&', '|')
        for (pair in pairs) {
            val k = pair.substringBefore('=').trim().lowercase()
            val v = pair.substringAfter('=', "").trim()
            if (v.isBlank()) continue
            when (k) {
                "user-agent", "user_agent" -> ua = v
                "referer", "referrer" -> ref = v
                "origin" -> orig = v
                "cookie" -> cook = v
            }
        }
        return PipeHeaderResult(mainUrl, ua, ref, orig, cook)
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k)
            if (v != null && v != JSONObject.NULL) {
                val s = v.toString().trim()
                if (s.isNotBlank() && s != "null") return s
            }
        }
        return ""
    }

    private fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())
}
