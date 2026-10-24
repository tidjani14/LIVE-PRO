package com.tvflux.pro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Customer Bouquet & Channel Ordering Helper (D-Pad friendly up/down/top/hide).
 */
object CustomOrderHelper {

    fun sortBouquets(
        rawBouquets: List<BouquetSummary>,
        customOrder: List<String>,
        hiddenBouquets: Set<String>,
        includeHidden: Boolean = false
    ): List<BouquetSummary> {
        val visible = if (includeHidden) {
            rawBouquets
        } else {
            rawBouquets.filter {
                it.isFavoritesSpecial || it.name == "الكل" || it.name !in hiddenBouquets
            }
        }
        if (customOrder.isEmpty()) return visible
        val orderMap = customOrder.withIndex().associate { it.value to it.index }
        return visible.sortedWith(
            compareBy<BouquetSummary> { bq ->
                when {
                    bq.isFavoritesSpecial -> -2
                    bq.name == "الكل" -> -1
                    else -> orderMap[bq.name] ?: (10_000 + rawBouquets.indexOf(bq))
                }
            }
        )
    }

    fun sortChannels(
        rawChannels: List<TvChannel>,
        customChannelOrder: List<String>,
        hiddenBouquets: Set<String>
    ): List<TvChannel> {
        val filtered = if (hiddenBouquets.isEmpty()) {
            rawChannels
        } else {
            rawChannels.filter { it.group !in hiddenBouquets }
        }
        if (customChannelOrder.isEmpty()) return filtered
        val orderMap = customChannelOrder.withIndex().associate { it.value to it.index }
        return filtered.sortedWith(
            compareBy<TvChannel> { ch ->
                orderMap[ch.stableKey] ?: (100_000 + ch.number)
            }
        )
    }

    fun <T> moveItem(list: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return list
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }
}

/**
 * Full Multi-Playlist Xtream Codes & M3U Client (Live TV + VOD Movies + Series + Remote Screen Code API).
 */
object XtreamRepository {

    fun normalizeServerUrl(raw: String): String {
        var clean = raw.trim().removeSuffix("/")
        if (clean.isBlank()) return ""
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = "http://$clean"
        }
        if (clean.contains("/player_api.php", ignoreCase = true)) {
            clean = clean.substringBefore("/player_api.php")
        }
        if (clean.contains("/get.php", ignoreCase = true)) {
            clean = clean.substringBefore("/get.php")
        }
        return clean.removeSuffix("/")
    }

    suspend fun syncXtreamAccount(config: XtreamAccountConfig): XtreamFullSyncResult =
        withContext(Dispatchers.IO) {
            val serverLabel = config.serverName.trim().ifBlank { "XTREAM" }
            val playlistId = config.id.ifBlank { "XTREAM_1" }

            if (config.m3uUrl.isNotBlank() && (config.serverUrl.isBlank() || config.username.isBlank())) {
                val m3uText = fetchUrlText(config.m3uUrl.trim())
                val parsed = parseM3uPlaylist(m3uText, serverLabel, playlistId, serverLabel)
                val live = parsed.filter { it.contentType == StreamContentType.LIVE_TV }
                val movies = parsed.filter { it.contentType == StreamContentType.MOVIE }
                val series = parsed.filter { it.contentType == StreamContentType.SERIES }
                if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                    error("تعذّر قراءة ملف M3U من الرابط المحدد")
                }
                return@withContext XtreamFullSyncResult(
                    account = config.copy(
                        enabled = true,
                        liveCount = live.size,
                        movieCount = movies.size,
                        seriesCount = series.size,
                        statusNote = "متصل ✓ (${live.size} قناة • ${movies.size} فيلم)"
                    ),
                    liveChannels = live,
                    movies = movies,
                    series = series
                )
            }

            val baseUrl = normalizeServerUrl(config.serverUrl)
            val user = config.username.trim()
            val pass = config.password.trim()
            require(baseUrl.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                "يرجى إدخال رابط سيرفر XTREAM واسم المستخدم وكلمة المرور"
            }

            val uEnc = URLEncoder.encode(user, "UTF-8")
            val pEnc = URLEncoder.encode(pass, "UTF-8")
            val apiBase = "$baseUrl/player_api.php?username=$uEnc&password=$pEnc"

            val authJsonStr = fetchUrlText(apiBase)
            var expLabel = ""
            if (authJsonStr.startsWith("{")) {
                runCatching {
                    val root = JSONObject(authJsonStr)
                    val userInfo = root.optJSONObject("user_info")
                    if (userInfo != null) {
                        val auth = userInfo.optInt("auth", 1)
                        val status = userInfo.optString("status", "Active")
                        if (auth == 0 || status.equals("Expired", ignoreCase = true) || status.equals("Banned", ignoreCase = true)) {
                            error("حساب XTREAM غير نشط أو منتهي الصلاحية ($status)")
                        }
                        val expSec = userInfo.optString("exp_date", "").toLongOrNull()
                        if (expSec != null && expSec > 0L) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            expLabel = sdf.format(Date(expSec * 1000L))
                        }
                    }
                }.onFailure { e ->
                    if (e.message?.contains("حساب XTREAM") == true) throw e
                }
            }

            coroutineScope {
                val liveCatsDeferred = async { fetchCategoryMap("$apiBase&action=get_live_categories") }
                val vodCatsDeferred = async { fetchCategoryMap("$apiBase&action=get_vod_categories") }
                val seriesCatsDeferred = async { fetchCategoryMap("$apiBase&action=get_series_categories") }

                val liveStreamsDeferred = async { fetchJsonArray("$apiBase&action=get_live_streams") }
                val vodStreamsDeferred = async { fetchJsonArray("$apiBase&action=get_vod_streams") }
                val seriesStreamsDeferred = async { fetchJsonArray("$apiBase&action=get_series") }

                val liveCats = liveCatsDeferred.await()
                val vodCats = vodCatsDeferred.await()
                val seriesCats = seriesCatsDeferred.await()

                val liveArr = liveStreamsDeferred.await()
                val vodArr = vodStreamsDeferred.await()
                val seriesArr = seriesStreamsDeferred.await()

                val liveList = ArrayList<TvChannel>(liveArr.length())
                for (i in 0 until liveArr.length()) {
                    val o = liveArr.optJSONObject(i) ?: continue
                    val streamId = o.optInt("stream_id", o.optInt("num", i + 1))
                    if (streamId <= 0) continue
                    val name = o.optString("name", "قناة $streamId").trim()
                    val catId = o.optString("category_id", "")
                    val groupName = liveCats[catId] ?: "باقات $serverLabel"
                    val icon = o.optString("stream_icon", "").trim().takeIf { it.startsWith("http") }
                    val streamUrl = "$baseUrl/live/$uEnc/$pEnc/$streamId.m3u8"
                    liveList.add(
                        TvChannel(
                            id = 500_000 + streamId,
                            number = o.optInt("num", i + 1),
                            name = name,
                            url = streamUrl,
                            group = groupName,
                            logo = icon,
                            contentType = StreamContentType.LIVE_TV,
                            playlistId = playlistId,
                            playlistName = serverLabel,
                            streamId = streamId,
                            streamBaseUrl = baseUrl,
                            streamUsername = uEnc,
                            streamPassword = pEnc
                        )
                    )
                }

                val movieList = ArrayList<TvChannel>(vodArr.length())
                for (i in 0 until vodArr.length()) {
                    val o = vodArr.optJSONObject(i) ?: continue
                    val streamId = o.optInt("stream_id", i + 1)
                    if (streamId <= 0) continue
                    val name = o.optString("name", o.optString("title", "فيلم $streamId")).trim()
                    val catId = o.optString("category_id", "")
                    val groupName = vodCats[catId] ?: "أفلام $serverLabel"
                    val icon = o.optString("stream_icon", o.optString("cover", "")).trim().takeIf { it.startsWith("http") }
                    val ext = o.optString("container_extension", "mp4").trim().ifBlank { "mp4" }
                    val rating = o.optString("rating", o.optString("rating_5based", "")).trim()
                    val streamUrl = "$baseUrl/movie/$uEnc/$pEnc/$streamId.$ext"
                    movieList.add(
                        TvChannel(
                            id = 700_000 + streamId,
                            number = i + 1,
                            name = name,
                            url = streamUrl,
                            group = groupName,
                            logo = icon,
                            contentType = StreamContentType.MOVIE,
                            rating = rating,
                            playlistId = playlistId,
                            playlistName = serverLabel,
                            streamId = streamId,
                            streamBaseUrl = baseUrl,
                            streamUsername = uEnc,
                            streamPassword = pEnc
                        )
                    )
                }

                val seriesList = ArrayList<TvChannel>(seriesArr.length())
                for (i in 0 until seriesArr.length()) {
                    val o = seriesArr.optJSONObject(i) ?: continue
                    val seriesId = o.optInt("series_id", i + 1)
                    if (seriesId <= 0) continue
                    val name = o.optString("name", o.optString("title", "مسلسل $seriesId")).trim()
                    val catId = o.optString("category_id", "")
                    val groupName = seriesCats[catId] ?: "مسلسلات $serverLabel"
                    val cover = o.optString("cover", "").trim().takeIf { it.startsWith("http") }
                    val rating = o.optString("rating", "").trim()
                    val plot = o.optString("plot", "").trim()
                    val releaseDate = o.optString("releaseDate", o.optString("year", "")).trim()
                    seriesList.add(
                        TvChannel(
                            id = 900_000 + seriesId,
                            number = i + 1,
                            name = name,
                            url = "$apiBase&action=get_series_info&series_id=$seriesId",
                            group = groupName,
                            logo = cover,
                            contentType = StreamContentType.SERIES,
                            rating = rating,
                            releaseYear = releaseDate,
                            plot = plot,
                            seriesId = seriesId,
                            playlistId = playlistId,
                            playlistName = serverLabel,
                            streamBaseUrl = baseUrl,
                            streamUsername = uEnc,
                            streamPassword = pEnc
                        )
                    )
                }

                if (liveList.isEmpty() && movieList.isEmpty() && seriesList.isEmpty()) {
                    val m3uUrl = "$baseUrl/get.php?username=$uEnc&password=$pEnc&type=m3u_plus&output=m3u8"
                    val m3uText = fetchUrlText(m3uUrl).ifBlank {
                        fetchUrlText("$baseUrl/get.php?username=$uEnc&password=$pEnc&type=m3u_plus&output=ts")
                    }
                    val parsedM3u = parseM3uPlaylist(m3uText, serverLabel, playlistId, serverLabel)
                    liveList.addAll(parsedM3u.filter { it.contentType == StreamContentType.LIVE_TV })
                    movieList.addAll(parsedM3u.filter { it.contentType == StreamContentType.MOVIE })
                    seriesList.addAll(parsedM3u.filter { it.contentType == StreamContentType.SERIES })
                }

                if (liveList.isEmpty() && movieList.isEmpty() && seriesList.isEmpty()) {
                    error("تم الاتصال بالسيرفر ولكن لم يتم العثور على قوائم. تأكد من الرابط وبيانات الحساب.")
                }

                val updatedConfig = config.copy(
                    serverUrl = baseUrl,
                    enabled = true,
                    liveCount = liveList.size,
                    movieCount = movieList.size,
                    seriesCount = seriesList.size,
                    expDate = expLabel,
                    statusNote = "متصل ✓ (${liveList.size} قناة • ${movieList.size} فيلم • ${seriesList.size} مسلسل)"
                )

                XtreamFullSyncResult(
                    account = updatedConfig,
                    liveChannels = liveList,
                    movies = movieList,
                    series = seriesList
                )
            }
        }

    suspend fun fetchSeriesEpisodes(
        seriesChannel: TvChannel,
        xtreamLists: List<XtreamAccountConfig>
    ): List<SeriesEpisode> = withContext(Dispatchers.IO) {
        if (seriesChannel.episodes.isNotEmpty()) return@withContext seriesChannel.episodes
        val matchingAccount = xtreamLists.find { it.id == seriesChannel.playlistId }
            ?: xtreamLists.firstOrNull { it.isConfigured }
        val baseUrl = seriesChannel.streamBaseUrl.ifBlank {
            matchingAccount?.let { normalizeServerUrl(it.serverUrl) }.orEmpty()
        }
        val user = seriesChannel.streamUsername.ifBlank {
            matchingAccount?.username?.trim()?.let { URLEncoder.encode(it, "UTF-8") }.orEmpty()
        }
        val pass = seriesChannel.streamPassword.ifBlank {
            matchingAccount?.password?.trim()?.let { URLEncoder.encode(it, "UTF-8") }.orEmpty()
        }
        if (baseUrl.isBlank() || user.isBlank() || pass.isBlank() || seriesChannel.seriesId <= 0) {
            return@withContext emptyList()
        }

        val infoUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=${seriesChannel.seriesId}"

        try {
            val raw = fetchUrlText(infoUrl)
            if (!raw.startsWith("{")) return@withContext emptyList()
            val root = JSONObject(raw)
            val episodesObj = root.optJSONObject("episodes") ?: return@withContext emptyList()
            val result = mutableListOf<SeriesEpisode>()
            val seasonKeys = episodesObj.keys()
            while (seasonKeys.hasNext()) {
                val sKey = seasonKeys.next()
                val sNum = sKey.toIntOrNull() ?: 1
                val epArr = episodesObj.optJSONArray(sKey) ?: continue
                for (i in 0 until epArr.length()) {
                    val ep = epArr.optJSONObject(i) ?: continue
                    val epId = ep.optString("id", "").trim()
                    if (epId.isBlank()) continue
                    val epNum = ep.optInt("episode_num", i + 1)
                    val epTitle = ep.optString("title", "الحلقة $epNum").trim()
                    val ext = ep.optString("container_extension", "mp4").trim().ifBlank { "mp4" }
                    val dur = ep.optJSONObject("info")?.optString("duration", "").orEmpty()
                    val playUrl = "$baseUrl/series/$user/$pass/$epId.$ext"
                    result.add(
                        SeriesEpisode(
                            id = epId,
                            seasonNumber = sNum,
                            episodeNumber = epNum,
                            title = if (epTitle.isNotBlank()) epTitle else "الموسم $sNum - الحلقة $epNum",
                            url = playUrl,
                            durationLabel = dur
                        )
                    )
                }
            }
            result.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchCategoryMap(url: String): Map<String, String> {
        val arr = fetchJsonArray(url)
        val map = HashMap<String, String>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("category_id", "").trim()
            val name = o.optString("category_name", "").trim()
            if (id.isNotBlank() && name.isNotBlank()) {
                map[id] = name
            }
        }
        return map
    }

    private fun fetchJsonArray(url: String): JSONArray {
        val txt = fetchUrlText(url)
        if (!txt.startsWith("[")) return JSONArray()
        return runCatching { JSONArray(txt) }.getOrDefault(JSONArray())
    }

    private fun fetchUrlText(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "IPTVSmartersPlayer/6.0 (LIVE-PRO-BEIN)")
                .header("Accept", "application/json, */*")
                .build()
            ChannelRepository.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return ""
                resp.body?.string()?.trim().orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun parseM3uPlaylist(
        m3u: String,
        defaultGroup: String,
        playlistId: String,
        playlistName: String
    ): List<TvChannel> {
        if (!m3u.contains("#EXTINF", ignoreCase = true)) return emptyList()
        val list = mutableListOf<TvChannel>()
        var currentName = ""
        var currentGroup = defaultGroup
        var currentLogo: String? = null
        var counter = 1

        for (rawLine in m3u.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                currentName = line.substringAfterLast(",", "قناة $counter").trim()
                currentGroup = extractAttr(line, "group-title").ifBlank { defaultGroup }
                currentLogo = extractAttr(line, "tvg-logo").takeIf { it.startsWith("http") }
            } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtsp://"))) {
                val lowUrl = line.lowercase()
                val type = when {
                    lowUrl.contains("/movie/") || lowUrl.endsWith(".mp4") || lowUrl.endsWith(".mkv") ->
                        StreamContentType.MOVIE
                    lowUrl.contains("/series/") ->
                        StreamContentType.SERIES
                    else ->
                        StreamContentType.LIVE_TV
                }
                list.add(
                    TvChannel(
                        id = 600_000 + counter,
                        number = counter,
                        name = currentName.ifBlank { "قناة $counter" },
                        url = line,
                        group = currentGroup,
                        logo = currentLogo,
                        contentType = type,
                        playlistId = playlistId,
                        playlistName = playlistName
                    )
                )
                counter++
            }
        }
        return list
    }

    private fun extractAttr(extInf: String, attr: String): String {
        val regex = Regex("$attr\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return regex.find(extInf)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }
}
