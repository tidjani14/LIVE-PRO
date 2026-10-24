package com.tvflux.pro

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// ============================================================================
// Deep Dark Royal Purple Palette (Calm, Non-Distracting, Eye-Friendly)
// ============================================================================
val BeinPrimaryPurple = Color(0xFF34165B)     // Deep Dark Royal Purple
val BeinHighlightPurple = Color(0xFF4F2488)   // Subtle Velvet Purple Accent
val BeinFocusGlow = Color(0xFFE9D5FF)         // Crisp Soft Lavender-White Focus Ring
val BeinDarkBg = Color(0xFF07050C)            // Ultra-Deep Night Background
val BeinSurface = Color(0xFF100A1B)           // Calm Dark Side Rail & Header Surface
val BeinCard = Color(0xFF160E26)              // Dark Velvet Card Surface
val BeinBorder = Color(0xFF271840)            // Subtle Non-Distracting Border
val EmeraldLive = Color(0xFF10B981)           // Active Subscription / Live Status
val CrimsonAlert = Color(0xFFEF4444)          // Expiry / Alert Color

val Gold = BeinHighlightPurple
val ObsidianBg = BeinDarkBg
val SurfaceDark = BeinSurface
val CardDark = BeinCard

private val BeinTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContent {
            LiveProEnterpriseApp()
        }
    }
}

@Composable
private fun LiveProEnterpriseApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }
    var deviceId by remember { mutableStateOf(StorageManager.resolveHardwareDeviceId(context)) }
    var deviceName by remember { mutableStateOf(StorageManager.resolveDeviceName()) }
    var savedActivationCode by remember { mutableStateOf("") }

    var xtreamPlaylists by remember { mutableStateOf<List<XtreamAccountConfig>>(emptyList()) }

    var vpnStatus by remember { mutableStateOf(CloudflareTunnelStatus()) }
    var isConnectingVpn by remember { mutableStateOf(false) }
    var autoVpnPrompted by remember { mutableStateOf(false) }

    var portalChannels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var portalMovies by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var portalSeries by remember { mutableStateOf<List<TvChannel>>(emptyList()) }

    var xtreamChannelsMap by remember { mutableStateOf<Map<String, List<TvChannel>>>(emptyMap()) }
    var xtreamMoviesMap by remember { mutableStateOf<Map<String, List<TvChannel>>>(emptyMap()) }
    var xtreamSeriesMap by remember { mutableStateOf<Map<String, List<TvChannel>>>(emptyMap()) }

    var favorites by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var history by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var subscription by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var preferences by remember { mutableStateOf(PlayerPreferences()) }

    var activeTab by remember { mutableStateOf(MainTab.LIVE) }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var selectedMovieCategory by remember { mutableStateOf("الكل") }
    var selectedSeriesCategory by remember { mutableStateOf("الكل") }
    var searchQuery by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("جارٍ مزامنة القنوات...") }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncingXtream by remember { mutableStateOf(false) }
    var playingChannel by remember { mutableStateOf<TvChannel?>(null) }
    var selectedSeriesForEpisodes by remember { mutableStateOf<TvChannel?>(null) }

    // Track the exact last-watched item key & scroll states so returning from Player
    // keeps the D-Pad focus on the exact channel/movie/series the user was watching!
    var lastWatchedStableKey by remember { mutableStateOf<String?>(null) }
    var restoreFocusTrigger by remember { mutableStateOf(0) }
    val liveGridState = remember { LazyGridState() }
    val liveListState = remember { LazyListState() }
    val bouquetSidebarListState = remember { LazyListState() }
    val moviesGridState = remember { LazyGridState() }
    val seriesGridState = remember { LazyGridState() }

    var showExitConfirmation by remember { mutableStateOf(false) }

    val lang = preferences.appLanguage

    val orderedPlaylists = remember(xtreamPlaylists, preferences.playlistOrderIds) {
        if (preferences.playlistOrderIds.isEmpty()) {
            xtreamPlaylists
        } else {
            val map = preferences.playlistOrderIds.withIndex().associate { it.value to it.index }
            xtreamPlaylists.sortedBy { map[it.id] ?: (1000 + it.orderIndex) }
        }
    }

    val allChannels = remember(
        portalChannels,
        xtreamChannelsMap,
        orderedPlaylists,
        preferences.playlistDisplayMode,
        preferences.activePlaylistFilterId,
        preferences.customChannelOrder,
        preferences.hiddenBouquets
    ) {
        val enabledXtreamIds = orderedPlaylists.filter { it.enabled }.map { it.id }
        val combined = mutableListOf<TvChannel>()

        val filterId = preferences.activePlaylistFilterId
        val separateMode = preferences.playlistDisplayMode == PlaylistDisplayMode.SEPARATE_LISTS

        if (!separateMode || filterId == "ALL" || filterId == "PORTAL_MAIN") {
            combined.addAll(portalChannels)
        }
        for (pid in enabledXtreamIds) {
            if (!separateMode || filterId == "ALL" || filterId == pid) {
                combined.addAll(xtreamChannelsMap[pid].orEmpty())
            }
        }

        val distinct = combined.distinctBy { "${it.playlistId}_${it.name}_${it.url}" }
        CustomOrderHelper.sortChannels(distinct, preferences.customChannelOrder, preferences.hiddenBouquets)
    }

    val allMovies = remember(
        portalMovies,
        xtreamMoviesMap,
        orderedPlaylists,
        preferences.playlistDisplayMode,
        preferences.activePlaylistFilterId
    ) {
        val enabledXtreamIds = orderedPlaylists.filter { it.enabled }.map { it.id }
        val combined = mutableListOf<TvChannel>()
        val filterId = preferences.activePlaylistFilterId
        val separateMode = preferences.playlistDisplayMode == PlaylistDisplayMode.SEPARATE_LISTS

        if (!separateMode || filterId == "ALL" || filterId == "PORTAL_MAIN") {
            combined.addAll(portalMovies)
        }
        for (pid in enabledXtreamIds) {
            if (!separateMode || filterId == "ALL" || filterId == pid) {
                combined.addAll(xtreamMoviesMap[pid].orEmpty())
            }
        }
        combined.distinctBy { "${it.playlistId}_${it.name}_${it.url}" }
    }

    val allSeries = remember(
        portalSeries,
        xtreamSeriesMap,
        orderedPlaylists,
        preferences.playlistDisplayMode,
        preferences.activePlaylistFilterId
    ) {
        val enabledXtreamIds = orderedPlaylists.filter { it.enabled }.map { it.id }
        val combined = mutableListOf<TvChannel>()
        val filterId = preferences.activePlaylistFilterId
        val separateMode = preferences.playlistDisplayMode == PlaylistDisplayMode.SEPARATE_LISTS

        if (!separateMode || filterId == "ALL" || filterId == "PORTAL_MAIN") {
            combined.addAll(portalSeries)
        }
        for (pid in enabledXtreamIds) {
            if (!separateMode || filterId == "ALL" || filterId == pid) {
                combined.addAll(xtreamSeriesMap[pid].orEmpty())
            }
        }
        combined.distinctBy { "${it.playlistId}_${it.name}_${it.url}" }
    }

    val favBouquetTitle = AppStrings.favoritesLabel(lang)
    val allBouquetTitle = AppStrings.allBouquetsLabel(lang)

    val rawBouquetSummaries = remember(allChannels, favorites, lang) {
        val grouped = allChannels.groupBy { it.group }
        val list = mutableListOf(
            BouquetSummary(
                name = favBouquetTitle,
                count = favorites.size,
                sampleLogos = favorites.mapNotNull { it.logo }.take(3),
                isFavoritesSpecial = true
            ),
            BouquetSummary(
                name = allBouquetTitle,
                count = allChannels.size,
                sampleLogos = allChannels.mapNotNull { it.logo }.take(3)
            )
        )
        grouped.forEach { (bName, chs) ->
            list.add(
                BouquetSummary(
                    name = bName,
                    count = chs.size,
                    sampleLogos = chs.mapNotNull { it.logo }.take(3)
                )
            )
        }
        list
    }

    val bouquetSummaries = remember(
        rawBouquetSummaries,
        preferences.customBouquetOrder,
        preferences.hiddenBouquets
    ) {
        CustomOrderHelper.sortBouquets(
            rawBouquetSummaries,
            preferences.customBouquetOrder,
            preferences.hiddenBouquets
        )
    }

    fun persistCurrentState() {
        if (!initialized) return
        val flatXtreamChannels = xtreamChannelsMap.values.flatten()
        val snapshot = PersistedAppState(
            deviceId = deviceId,
            deviceName = deviceName,
            activationCode = savedActivationCode,
            xtreamPlaylists = xtreamPlaylists,
            cachedChannels = (portalChannels + flatXtreamChannels).take(3000),
            cachedMovies = allMovies,
            cachedSeries = allSeries,
            favorites = favorites,
            history = history,
            subscription = subscription,
            preferences = preferences
        )
        scope.launch {
            StorageManager.saveState(context, snapshot)
        }
    }

    fun syncSingleXtreamPlaylist(configToSync: XtreamAccountConfig, showToast: Boolean = false) {
        if (!configToSync.isConfigured) return
        // Block adding/uploading new Xtream playlists if player subscription is expired!
        val currentSub = subscription
        if (currentSub != null && (currentSub.isExpired || !currentSub.active)) {
            Toast.makeText(
                context,
                "⛔ انتهت صلاحية المشغل ($deviceId). يرجى تجديد الاشتراك أولاً عبر علي سات: $ALI_SAT_PHONE_NUMBER",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        scope.launch {
            isSyncingXtream = true
            try {
                ChannelRepository.pushCustomerXtreamToPortal(deviceId, deviceName, configToSync)
                val xResult = XtreamRepository.syncXtreamAccount(configToSync)
                xtreamPlaylists = xtreamPlaylists.map {
                    if (it.id == configToSync.id) xResult.account else it
                }.let { list ->
                    if (list.none { it.id == configToSync.id }) list + xResult.account else list
                }
                xtreamChannelsMap = xtreamChannelsMap + (xResult.account.id to xResult.liveChannels)
                xtreamMoviesMap = xtreamMoviesMap + (xResult.account.id to xResult.movies)
                xtreamSeriesMap = xtreamSeriesMap + (xResult.account.id to xResult.series)

                persistCurrentState()
                if (showToast) {
                    Toast.makeText(
                        context,
                        "✅ ${xResult.account.serverName}: ${xResult.liveChannels.size} قناة، ${xResult.movies.size} فيلم، ${xResult.series.size} مسلسل",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                val msg = e.message ?: "تعذّر الاتصال بسيرفر XTREAM"
                xtreamPlaylists = xtreamPlaylists.map {
                    if (it.id == configToSync.id) it.copy(statusNote = "❌ $msg") else it
                }
                if (showToast) {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } finally {
                isSyncingXtream = false
            }
        }
    }

    fun syncAllEnabledXtreamPlaylists(forceRefresh: Boolean = false) {
        val currentSub = subscription
        if (currentSub != null && (currentSub.isExpired || !currentSub.active)) return
        orderedPlaylists.filter { it.enabled && it.isConfigured }.forEach { cfg ->
            if (forceRefresh || xtreamChannelsMap[cfg.id].isNullOrEmpty()) {
                syncSingleXtreamPlaylist(cfg, showToast = false)
            }
        }
    }

    fun syncServerNow(codeOverride: String = savedActivationCode, forceXtreamRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            statusMessage = AppStrings.tr(
                lang,
                "جارٍ التحقق من صلاحية المشغل ومزامنة القوائم عبر رمز الشاشة...",
                "Vérification de la validité et synchronisation via Code Écran...",
                "Checking player validity and syncing lists via Screen Code..."
            )
            try {
                val result = ChannelRepository.syncWithServer(deviceId, deviceName, codeOverride)
                subscription = result.subscription

                if (result.subscription.active) {
                    portalChannels = result.channels
                    portalMovies = result.movies
                    portalSeries = result.series

                    if (result.remoteXtreamLists.isNotEmpty()) {
                        val mergedLists = xtreamPlaylists.toMutableList()
                        for (remoteCfg in result.remoteXtreamLists) {
                            val existingIdx = mergedLists.indexOfFirst {
                                it.id == remoteCfg.id ||
                                    (it.serverUrl.equals(remoteCfg.serverUrl, ignoreCase = true) &&
                                        it.username.equals(remoteCfg.username, ignoreCase = true))
                            }
                            if (existingIdx >= 0) {
                                mergedLists[existingIdx] = remoteCfg.copy(id = mergedLists[existingIdx].id)
                            } else {
                                mergedLists.add(remoteCfg)
                            }
                        }
                        xtreamPlaylists = mergedLists
                    }

                    val expText = if (result.subscription.expireDate.isNotBlank()) {
                        " • ينتهي: ${result.subscription.expireDate}"
                    } else ""
                    statusMessage = "متصل • الصلاحية المتبقية: ${result.subscription.daysLeft} يوم$expText"
                    syncAllEnabledXtreamPlaylists(forceRefresh = forceXtreamRefresh)
                } else {
                    // Subscription expired or inactive -> clear playback & show Ali Sat expiry screen
                    portalChannels = emptyList()
                    playingChannel = null
                    val subMsg = result.subscription.message
                    statusMessage = if (subMsg.isNotBlank()) subMsg else "انتهت صلاحية المشغل. تواصل مع علي سات : $ALI_SAT_PHONE_NUMBER"
                }

                persistCurrentState()
            } catch (e: Exception) {
                statusMessage = e.message ?: "تعذّر الاتصال بالخادم. تحقّق من الإنترنت."
            } finally {
                isLoading = false
            }
        }
    }

    fun redeemDirectCode(inputCode: String) {
        scope.launch {
            isLoading = true
            statusMessage = "جارٍ التحقق من رمز التنشيط..."
            try {
                val result = ChannelRepository.redeemActivationCode(deviceId, deviceName, inputCode)
                subscription = result.subscription
                if (result.subscription.active) {
                    savedActivationCode = inputCode.trim().uppercase()
                    portalChannels = result.channels
                    portalMovies = result.movies
                    portalSeries = result.series
                    activeTab = MainTab.LIVE
                    Toast.makeText(context, "✅ تم تفعيل الاشتراك بنجاح!", Toast.LENGTH_LONG).show()
                } else {
                    val subMsg = result.subscription.message
                    Toast.makeText(
                        context,
                        if (subMsg.isNotBlank()) subMsg else "رمز التنشيط غير صالح أو منتهي",
                        Toast.LENGTH_LONG
                    ).show()
                }
                persistCurrentState()
            } catch (e: Exception) {
                statusMessage = e.message ?: "تعذّر التحقق من رمز التنشيط"
            } finally {
                isLoading = false
            }
        }
    }

    fun startRealWireGuardServiceAndVerify() {
        scope.launch {
            isConnectingVpn = true
            val intent = Intent(context, CloudflareWarpVpnService::class.java).apply {
                action = CloudflareWarpVpnService.ACTION_CONNECT
            }
            runCatching { context.startService(intent) }

            repeat(12) {
                delay(320L)
                if (CloudflareWarpVpnService.isTunnelRunning || CloudflareDohManager.isSystemVpnActive(context)) {
                    return@repeat
                }
            }

            val status = CloudflareDohManager.verifyAndActivateCloudflareTunnel(context)
            vpnStatus = status
            isConnectingVpn = false
            if (status.connected) {
                syncServerNow()
            }
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, CloudflareWarpVpnService::class.java).apply {
                action = CloudflareWarpVpnService.ACTION_CONNECT
            }
            runCatching { context.startService(intent) }
            startRealWireGuardServiceAndVerify()
        } else {
            Toast.makeText(
                context,
                "الاتصال بـ VPN إجباري للدخول إلى المشغل",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun connectVpnAutomatically() {
        val prepareIntent = runCatching { VpnService.prepare(context) }.getOrNull()
        if (prepareIntent != null) {
            runCatching {
                vpnPermissionLauncher.launch(prepareIntent)
            }.onFailure {
                startRealWireGuardServiceAndVerify()
            }
        } else {
            val intent = Intent(context, CloudflareWarpVpnService::class.java).apply {
                action = CloudflareWarpVpnService.ACTION_CONNECT
            }
            runCatching { context.startService(intent) }
            startRealWireGuardServiceAndVerify()
        }
    }

    LaunchedEffect(Unit) {
        val loaded = StorageManager.loadState(context)
        deviceId = loaded.deviceId
        deviceName = loaded.deviceName
        savedActivationCode = loaded.activationCode
        xtreamPlaylists = loaded.xtreamPlaylists
        favorites = loaded.favorites
        history = loaded.history
        subscription = loaded.subscription
        preferences = loaded.preferences
        if (loaded.cachedChannels.isNotEmpty()) {
            portalChannels = loaded.cachedChannels.filter { it.playlistId == "PORTAL_MAIN" }
            val groupedXtream = loaded.cachedChannels
                .filter { it.playlistId != "PORTAL_MAIN" }
                .groupBy { it.playlistId }
            xtreamChannelsMap = groupedXtream
        }
        if (loaded.cachedMovies.isNotEmpty()) {
            portalMovies = loaded.cachedMovies.filter { it.playlistId == "PORTAL_MAIN" }
            xtreamMoviesMap = loaded.cachedMovies.filter { it.playlistId != "PORTAL_MAIN" }.groupBy { it.playlistId }
        }
        if (loaded.cachedSeries.isNotEmpty()) {
            portalSeries = loaded.cachedSeries.filter { it.playlistId == "PORTAL_MAIN" }
            xtreamSeriesMap = loaded.cachedSeries.filter { it.playlistId != "PORTAL_MAIN" }.groupBy { it.playlistId }
        }
        initialized = true

        if (!autoVpnPrompted) {
            autoVpnPrompted = true
            connectVpnAutomatically()
        }
    }

    LaunchedEffect(initialized) {
        if (!initialized) return@LaunchedEffect
        while (true) {
            delay(4500L)
            val activeNow = CloudflareWarpVpnService.isTunnelRunning || CloudflareDohManager.isSystemVpnActive(context)
            if (!activeNow && vpnStatus.connected) {
                playingChannel = null
                vpnStatus = vpnStatus.copy(
                    connected = false,
                    statusText = "تم قطع اتصال الـ VPN الإجباري — يرجى إعادة الاتصال للمتابعة"
                )
            } else if (activeNow && !vpnStatus.connected) {
                vpnStatus = CloudflareDohManager.verifyAndActivateCloudflareTunnel(context)
            }
        }
    }

    BackHandler(enabled = playingChannel == null) {
        if (selectedSeriesForEpisodes != null) {
            selectedSeriesForEpisodes = null
        } else {
            showExitConfirmation = !showExitConfirmation
        }
    }

    fun startPlayback(ch: TvChannel) {
        if (!vpnStatus.connected) {
            connectVpnAutomatically()
            return
        }
        // Save the exact channel/movie/series stableKey so when the user exits the player,
        // focus returns directly to this exact item instead of jumping to the top of the list!
        lastWatchedStableKey = ch.stableKey
        if (ch.contentType == StreamContentType.SERIES && ch.seriesId > 0) {
            selectedSeriesForEpisodes = ch
            return
        }
        history = listOf(ch) + history.filterNot { it.stableKey == ch.stableKey }.take(49)
        persistCurrentState()
        playingChannel = ch
    }

    fun toggleFavorite(ch: TvChannel) {
        val exists = favorites.any { it.stableKey == ch.stableKey }
        favorites = if (exists) {
            favorites.filterNot { it.stableKey == ch.stableKey }
        } else {
            listOf(ch) + favorites
        }
        persistCurrentState()
    }

    val colorScheme = darkColorScheme(
        primary = BeinPrimaryPurple,
        onPrimary = Color.White,
        secondary = BeinHighlightPurple,
        background = BeinDarkBg,
        surface = BeinSurface,
        onBackground = Color.White,
        onSurface = Color.White
    )

    CompositionLocalProvider(LocalLayoutDirection provides lang.layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BeinTypography
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BeinDarkBg)
            ) {
                val isPhoneLandscape = maxHeight < 460.dp
                val currentPlaying = playingChannel
                val sub = subscription

                // 1. Mandatory VPN Gate
                if (!vpnStatus.connected) {
                    MandatoryCloudflareVpnGate(
                        vpnStatus = vpnStatus,
                        isConnecting = isConnectingVpn,
                        lang = lang,
                        compact = isPhoneLandscape,
                        onConnectCloudflareVpn = { connectVpnAutomatically() },
                        onRequestExit = { showExitConfirmation = true }
                    )
                }
                // 2. Strict Player Subscription Expired / Inactive Gate -> Contact Ali Sat: 00213697372480
                else if (sub != null && (sub.isExpired || !sub.active)) {
                    AliSatSubscriptionExpiredGate(
                        deviceId = deviceId,
                        deviceName = deviceName,
                        subscription = sub,
                        lang = lang,
                        isLoading = isLoading || isSyncingXtream,
                        compact = isPhoneLandscape,
                        onVerifyNow = { syncServerNow(forceXtreamRefresh = true) },
                        onRedeemCode = { redeemDirectCode(it) },
                        onRequestExit = { showExitConfirmation = true }
                    )
                }
                // 3. ExoPlayer Fullscreen View
                else if (currentPlaying != null) {
                    val siblings = remember(allChannels, allMovies, currentPlaying) {
                        when (currentPlaying.contentType) {
                            StreamContentType.MOVIE -> allMovies.filter { it.group == currentPlaying.group }.ifEmpty { allMovies }
                            StreamContentType.SERIES -> listOf(currentPlaying)
                            StreamContentType.LIVE_TV -> {
                                val sameGroup = allChannels.filter {
                                    it.group == currentPlaying.group || currentPlaying.group == "عام"
                                }
                                if (sameGroup.isNotEmpty()) sameGroup
                                else if (allChannels.isNotEmpty()) allChannels
                                else listOf(currentPlaying)
                            }
                        }
                    }
                    PlayerScreen(
                        channel = currentPlaying,
                        siblingChannels = siblings,
                        allChannels = if (currentPlaying.contentType == StreamContentType.MOVIE) allMovies else allChannels,
                        favorites = favorites,
                        preferences = preferences,
                        isFavorite = favorites.any { it.stableKey == currentPlaying.stableKey },
                        onToggleFavorite = { toggleFavorite(it) },
                        onUpdatePreferences = {
                            preferences = it
                            persistCurrentState()
                        },
                        onSelectChannel = { nextCh ->
                            lastWatchedStableKey = nextCh.stableKey
                            startPlayback(nextCh)
                        },
                        onBack = {
                            lastWatchedStableKey = currentPlaying.stableKey
                            restoreFocusTrigger += 1
                            playingChannel = null
                        }
                    )
                }
                // 4. Main App Shell (Dark Royal Purple)
                else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LandscapeSideNavigationRail(
                            activeTab = activeTab,
                            lang = lang,
                            compact = isPhoneLandscape,
                            onSelectTab = { tab -> activeTab = tab },
                            onExitClick = { showExitConfirmation = true }
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            TopBeinHeader(
                                deviceId = deviceId,
                                subscription = subscription,
                                vpnStatus = vpnStatus,
                                totalCount = allChannels.size,
                                moviesCount = allMovies.size,
                                seriesCount = allSeries.size,
                                bouquetCount = (bouquetSummaries.size - 1).coerceAtLeast(1),
                                isLoading = isLoading || isSyncingXtream,
                                useGrid = preferences.useGridLayout,
                                lang = lang,
                                playlistMode = preferences.playlistDisplayMode,
                                playlists = orderedPlaylists,
                                activePlaylistFilterId = preferences.activePlaylistFilterId,
                                compact = isPhoneLandscape,
                                onSelectLanguage = { newLang ->
                                    preferences = preferences.copy(appLanguage = newLang)
                                    persistCurrentState()
                                },
                                onSelectPlaylistFilter = { filterId ->
                                    preferences = preferences.copy(activePlaylistFilterId = filterId)
                                    persistCurrentState()
                                },
                                onOpenBouquets = { activeTab = MainTab.BOUQUETS },
                                onOpenXtream = { activeTab = MainTab.XTREAM },
                                onOpenActivation = { activeTab = MainTab.ACTIVATION },
                                onRefresh = { syncServerNow(forceXtreamRefresh = true) }
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                when (activeTab) {
                                    MainTab.LIVE -> BeinCatalogBrowserView(
                                        items = allChannels,
                                        bouquetSummaries = bouquetSummaries,
                                        favorites = favorites,
                                        selectedCategory = selectedCategory,
                                        onSelectCategory = { selectedCategory = it },
                                        searchQuery = searchQuery,
                                        onSearchChange = { searchQuery = it },
                                        statusMessage = statusMessage,
                                        isLoading = isLoading || isSyncingXtream,
                                        useGrid = preferences.useGridLayout,
                                        showBouquetSidebar = preferences.showBouquetSidebar,
                                        onToggleBouquetSidebar = {
                                            preferences = preferences.copy(
                                                showBouquetSidebar = !preferences.showBouquetSidebar
                                            )
                                            persistCurrentState()
                                        },
                                        gridState = liveGridState,
                                        listState = liveListState,
                                        bouquetListState = bouquetSidebarListState,
                                        lastWatchedKey = lastWatchedStableKey,
                                        restoreFocusTrigger = restoreFocusTrigger,
                                        lang = lang,
                                        compact = isPhoneLandscape,
                                        deviceId = deviceId,
                                        onPlay = { startPlayback(it) },
                                        onToggleFavorite = { toggleFavorite(it) },
                                        onRefresh = { syncServerNow(forceXtreamRefresh = true) }
                                    )

                                    MainTab.BOUQUETS -> BouquetsGridDirectoryView(
                                        bouquetSummaries = bouquetSummaries,
                                        selectedCategory = selectedCategory,
                                        lang = lang,
                                        compact = isPhoneLandscape,
                                        onSelectBouquet = { chosenBouquet ->
                                            selectedCategory = chosenBouquet
                                            activeTab = MainTab.LIVE
                                        }
                                    )

                                    MainTab.MOVIES -> VodMoviesAndSeriesCatalogView(
                                        titleAr = "🎬 ${AppStrings.tr(lang, "مكتبة الأفلام", "Catalogue Films", "Movies Library")} (${allMovies.size})",
                                        emptyHintAr = AppStrings.tr(
                                            lang,
                                            "لا توجد أفلام حالياً. يمكنك إدارة قوائمك من تبويب فضاء الزبون عبر رمز الشاشة.",
                                            "Aucun film disponible. Gérez vos listes via Espace Client.",
                                            "No movies available yet. Manage your lists via Customer Space."
                                        ),
                                        items = allMovies,
                                        favorites = favorites,
                                        selectedCategory = selectedMovieCategory,
                                        onSelectCategory = { selectedMovieCategory = it },
                                        gridState = moviesGridState,
                                        lastWatchedKey = lastWatchedStableKey,
                                        restoreFocusTrigger = restoreFocusTrigger,
                                        compact = isPhoneLandscape,
                                        onPlay = { startPlayback(it) },
                                        onToggleFavorite = { toggleFavorite(it) },
                                        onOpenXtream = { activeTab = MainTab.XTREAM },
                                        onRefresh = { syncServerNow(forceXtreamRefresh = true) }
                                    )

                                    MainTab.SERIES -> VodMoviesAndSeriesCatalogView(
                                        titleAr = "📺 ${AppStrings.tr(lang, "مكتبة المسلسلات", "Catalogue Séries", "Series Library")} (${allSeries.size})",
                                        emptyHintAr = AppStrings.tr(
                                            lang,
                                            "لا توجد مسلسلات حالياً. يمكنك إدارة قوائمك من تبويب فضاء الزبون عبر رمز الشاشة.",
                                            "Aucune série disponible. Gérez vos listes via Espace Client.",
                                            "No series available yet. Manage your lists via Customer Space."
                                        ),
                                        items = allSeries,
                                        favorites = favorites,
                                        selectedCategory = selectedSeriesCategory,
                                        onSelectCategory = { selectedSeriesCategory = it },
                                        gridState = seriesGridState,
                                        lastWatchedKey = lastWatchedStableKey,
                                        restoreFocusTrigger = restoreFocusTrigger,
                                        compact = isPhoneLandscape,
                                        onPlay = { startPlayback(it) },
                                        onToggleFavorite = { toggleFavorite(it) },
                                        onOpenXtream = { activeTab = MainTab.XTREAM },
                                        onRefresh = { syncServerNow(forceXtreamRefresh = true) }
                                    )

                                    MainTab.XTREAM -> MultiPlaylistXtreamManagerView(
                                        deviceId = deviceId,
                                        subscription = subscription,
                                        playlists = orderedPlaylists,
                                        playlistMode = preferences.playlistDisplayMode,
                                        activePlaylistFilterId = preferences.activePlaylistFilterId,
                                        lang = lang,
                                        isSyncing = isSyncingXtream || isLoading,
                                        compact = isPhoneLandscape,
                                        onChangePlaylistMode = { mode ->
                                            preferences = preferences.copy(playlistDisplayMode = mode)
                                            persistCurrentState()
                                        },
                                        onSelectActivePlaylist = { pid ->
                                            preferences = preferences.copy(activePlaylistFilterId = pid)
                                            persistCurrentState()
                                        },
                                        onAddOrUpdatePlaylist = { cfg ->
                                            syncSingleXtreamPlaylist(cfg, showToast = true)
                                        },
                                        onMovePlaylist = { fromIdx, toIdx ->
                                            val reordered = CustomOrderHelper.moveItem(orderedPlaylists, fromIdx, toIdx)
                                            xtreamPlaylists = reordered
                                            preferences = preferences.copy(playlistOrderIds = reordered.map { it.id })
                                            persistCurrentState()
                                        },
                                        onTogglePlaylistEnabled = { cfg ->
                                            xtreamPlaylists = xtreamPlaylists.map {
                                                if (it.id == cfg.id) it.copy(enabled = !it.enabled) else it
                                            }
                                            persistCurrentState()
                                        },
                                        onDeletePlaylist = { cfg ->
                                            xtreamPlaylists = xtreamPlaylists.filterNot { it.id == cfg.id }
                                            xtreamChannelsMap = xtreamChannelsMap - cfg.id
                                            xtreamMoviesMap = xtreamMoviesMap - cfg.id
                                            xtreamSeriesMap = xtreamSeriesMap - cfg.id
                                            scope.launch {
                                                ChannelRepository.deleteCustomerXtreamFromPortal(deviceId, cfg.id)
                                            }
                                            persistCurrentState()
                                        },
                                        onPullRemoteListsByScreenCode = {
                                            syncServerNow(forceXtreamRefresh = true)
                                        }
                                    )

                                    MainTab.FAVORITES -> FavoritesAndHistoryView(
                                        favorites = favorites,
                                        history = history,
                                        lang = lang,
                                        compact = isPhoneLandscape,
                                        onPlay = { startPlayback(it) },
                                        onToggleFavorite = { toggleFavorite(it) },
                                        onClearHistory = {
                                            history = emptyList()
                                            persistCurrentState()
                                        }
                                    )

                                    MainTab.ACTIVATION -> ScreenActivationStatusView(
                                        deviceId = deviceId,
                                        deviceName = deviceName,
                                        subscription = subscription,
                                        lang = lang,
                                        isLoading = isLoading,
                                        compact = isPhoneLandscape,
                                        onVerifyNow = { syncServerNow(forceXtreamRefresh = true) },
                                        onRedeemCode = { redeemDirectCode(it) }
                                    )

                                    MainTab.SETTINGS -> SettingsAndVpnView(
                                        preferences = preferences,
                                        rawBouquets = rawBouquetSummaries.filter { !it.isFavoritesSpecial && it.name != allBouquetTitle },
                                        allChannels = allChannels,
                                        vpnStatus = vpnStatus,
                                        compact = isPhoneLandscape,
                                        onReconnectVpn = { connectVpnAutomatically() },
                                        onUpdatePreferences = {
                                            preferences = it
                                            persistCurrentState()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                val seriesItem = selectedSeriesForEpisodes
                if (seriesItem != null) {
                    SeriesEpisodesModal(
                        series = seriesItem,
                        xtreamLists = xtreamPlaylists,
                        compact = isPhoneLandscape,
                        onPlayEpisode = { ep ->
                            selectedSeriesForEpisodes = null
                            val epChannel = seriesItem.copy(
                                name = "${seriesItem.name} - ${ep.title}",
                                url = ep.url,
                                contentType = StreamContentType.MOVIE,
                                seriesId = 0
                            )
                            playingChannel = epChannel
                        },
                        onDismiss = { selectedSeriesForEpisodes = null }
                    )
                }

                if (showExitConfirmation) {
                    ExitConfirmationModal(
                        lang = lang,
                        compact = isPhoneLandscape,
                        onStay = { showExitConfirmation = false },
                        onConfirmExit = {
                            showExitConfirmation = false
                            activity?.finishAffinity()
                        }
                    )
                }
            }
        }
    }
}

private fun copyDeviceCodeToClipboard(context: Context, code: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LIVE PRO Device Code", code))
        Toast.makeText(context, "تم نسخ الرمز: $code", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MandatoryCloudflareVpnGate(
    vpnStatus: CloudflareTunnelStatus,
    isConnecting: Boolean,
    lang: AppUiLanguage,
    compact: Boolean,
    onConnectCloudflareVpn: () -> Unit,
    onRequestExit: () -> Unit
) {
    var connectBtnFocused by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B0D30), BeinDarkBg)
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (compact) 380.dp else 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(22.dp))
                .padding(if (compact) 18.dp else 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            BrandLogoBadge(
                modifier = Modifier.size(if (compact) 64.dp else 84.dp),
                cornerRadius = 18.dp
            )

            Text(
                text = APP_BRAND_NAME,
                color = Color.White,
                fontSize = if (compact) 19.sp else 24.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = AppStrings.tr(
                    lang,
                    "🔒 اتصال الـ VPN التلقائي إجباري لحماية البث وفتح المشغل",
                    "🔒 La connexion VPN automatique est obligatoire pour accéder au lecteur",
                    "🔒 Mandatory Automatic VPN is required to enter the player"
                ),
                color = Color(0xFFD8B4FE),
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isConnecting) {
                    AppStrings.tr(lang, "جارٍ الاتصال التلقائي والتحقق من نفق الـ VPN...", "Connexion VPN automatique...", "Auto-connecting & verifying VPN tunnel...")
                } else {
                    vpnStatus.statusText
                },
                color = if (vpnStatus.connected) EmeraldLive else Color.LightGray,
                fontSize = if (compact) 11.sp else 13.sp,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onConnectCloudflareVpn,
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectBtnFocused) Color.White else BeinPrimaryPurple,
                    contentColor = if (connectBtnFocused) Color.Black else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 48.dp else 56.dp)
                    .scale(if (connectBtnFocused) 1.04f else 1.0f)
                    .border(
                        width = if (connectBtnFocused) 3.dp else 1.5.dp,
                        color = if (connectBtnFocused) BeinFocusGlow else Color.White,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .onFocusChanged { connectBtnFocused = it.isFocused }
                    .focusable()
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = if (connectBtnFocused) Color.Black else Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        AppStrings.tr(lang, "جارٍ التحقق من النفق...", "Vérification...", "Verifying VPN..."),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 13.sp else 15.sp
                    )
                } else {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (connectBtnFocused) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = AppStrings.tr(lang, "تفعيل الـ VPN الإجباري والدخول ⚡", "Activer le VPN et entrer ⚡", "Connect Mandatory VPN & Enter ⚡"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 14.sp else 16.sp
                    )
                }
            }

            TextButton(
                onClick = onRequestExit,
                modifier = Modifier.height(if (compact) 30.dp else 36.dp)
            ) {
                Text(
                    AppStrings.tr(lang, "خروج", "Quitter", "Exit"),
                    color = Color.LightGray,
                    fontSize = if (compact) 11.sp else 12.sp
                )
            }
        }
    }
}

/**
 * Professional Subscription Expired / Inactive Gate:
 * Displays the customer's Screen Code (DEV-XXXX-XXXX) and clear contact details
 * for Ali Sat (00213697372480) to renew/activate the player.
 */
@Composable
private fun AliSatSubscriptionExpiredGate(
    deviceId: String,
    deviceName: String,
    subscription: SubscriptionInfo,
    lang: AppUiLanguage,
    isLoading: Boolean,
    compact: Boolean,
    onVerifyNow: () -> Unit,
    onRedeemCode: (String) -> Unit,
    onRequestExit: () -> Unit
) {
    val context = LocalContext.current
    var directCodeInput by remember { mutableStateOf("") }
    var verifyFocused by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF210E3B), BeinDarkBg)
                )
            )
            .padding(if (compact) 8.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = if (compact) 580.dp else 680.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(22.dp))
                .padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    BrandLogoBadge(
                        modifier = Modifier.size(if (compact) 48.dp else 62.dp),
                        cornerRadius = 14.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = AppStrings.tr(
                                lang,
                                "انتهت صلاحية اشتراك المشغل",
                                "Abonnement du lecteur expiré",
                                "Player Subscription Expired"
                            ),
                            color = Color.White,
                            fontSize = if (compact) 17.sp else 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (subscription.expireDate.isNotBlank()) {
                            Text(
                                text = AppStrings.tr(
                                    lang,
                                    "تاريخ انتهاء الصلاحية: ${subscription.expireDate}",
                                    "Date d'expiration : ${subscription.expireDate}",
                                    "Expiration Date: ${subscription.expireDate}"
                                ),
                                color = Color(0xFFFCA5A5),
                                fontSize = if (compact) 11.sp else 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Ali Sat Contact Box
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(BeinPrimaryPurple, Color(0xFF240F40))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                        .padding(if (compact) 12.dp else 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = AppStrings.tr(
                            lang,
                            "📞 لتجديد صلاحية المشغل وتفعيله فوراً، يرجى التواصل مع علي سات وتقديم رمز الشاشة:",
                            "📞 Pour renouveler et activer le lecteur, contactez Ali Sat avec votre Code Écran :",
                            "📞 To renew & activate the player, contact Ali Sat and provide your Screen Code:"
                        ),
                        color = Color.White,
                        fontSize = if (compact) 12.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "علي سات : $ALI_SAT_PHONE_NUMBER",
                        color = Color.White,
                        fontSize = if (compact) 20.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Screen Code Display Box
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BeinDarkBg)
                        .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(14.dp))
                        .clickable { copyDeviceCodeToClipboard(context, deviceId) }
                        .padding(if (compact) 10.dp else 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = AppStrings.tr(
                            lang,
                            "رمز الشاشة الخاص بجهازك (قدّمه لـ علي سات للتفعيل عن بُعد):",
                            "Votre Code Écran (À communiquer à Ali Sat pour l'activation) :",
                            "Your Screen Code (Provide to Ali Sat for activation):"
                        ),
                        color = Color(0xFFD8B4FE),
                        fontSize = if (compact) 11.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = deviceId,
                            color = Color.White,
                            fontSize = if (compact) 21.sp else 27.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // Optional Activation Code Field
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = directCodeInput,
                        onValueChange = { directCodeInput = it.uppercase() },
                        placeholder = {
                            Text(
                                AppStrings.tr(lang, "إدخال كود تفعيل مباشر (اختياري)", "Code d'activation", "Activation Code"),
                                fontSize = if (compact) 11.sp else 13.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Button(
                        onClick = { onRedeemCode(directCodeInput) },
                        enabled = !isLoading && directCodeInput.trim().length >= 4,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldLive,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.height(if (compact) 44.dp else 52.dp)
                    ) {
                        Text(
                            AppStrings.tr(lang, "تفعيل", "Activer", "Activate"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 11.sp else 13.sp
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onVerifyNow,
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (verifyFocused) Color.White else BeinPrimaryPurple,
                            contentColor = if (verifyFocused) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(if (compact) 44.dp else 50.dp)
                            .scale(if (verifyFocused) 1.03f else 1.0f)
                            .border(
                                width = if (verifyFocused) 3.dp else 1.dp,
                                color = if (verifyFocused) BeinFocusGlow else Color.White,
                                shape = RoundedCornerShape(25.dp)
                            )
                            .onFocusChanged { verifyFocused = it.isFocused }
                            .focusable()
                    ) {
                        Text(
                            AppStrings.tr(
                                lang,
                                "🔄 تحديث والتحقق من تجديد الاشتراك الآن",
                                "🔄 Vérifier le renouvellement",
                                "🔄 Check Subscription Renewal Now"
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 12.sp else 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onRequestExit,
                        modifier = Modifier.height(if (compact) 44.dp else 50.dp)
                    ) {
                        Text(
                            AppStrings.tr(lang, "خروج", "Quitter", "Exit"),
                            color = Color.LightGray,
                            fontSize = if (compact) 11.sp else 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Customer XTREAM Space Manager View:
 * • Integrated with standalone customer_portal.php & api.php via Screen Code
 * • Verifies player subscription validity before allowing new playlists
 */
@Composable
private fun MultiPlaylistXtreamManagerView(
    deviceId: String,
    subscription: SubscriptionInfo?,
    playlists: List<XtreamAccountConfig>,
    playlistMode: PlaylistDisplayMode,
    activePlaylistFilterId: String,
    lang: AppUiLanguage,
    isSyncing: Boolean,
    compact: Boolean,
    onChangePlaylistMode: (PlaylistDisplayMode) -> Unit,
    onSelectActivePlaylist: (String) -> Unit,
    onAddOrUpdatePlaylist: (XtreamAccountConfig) -> Unit,
    onMovePlaylist: (Int, Int) -> Unit,
    onTogglePlaylistEnabled: (XtreamAccountConfig) -> Unit,
    onDeletePlaylist: (XtreamAccountConfig) -> Unit,
    onPullRemoteListsByScreenCode: () -> Unit
) {
    val context = LocalContext.current
    var showAddForm by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf("") }
    var serverName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val isSubActive = subscription == null || (subscription.active && !subscription.isExpired)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1F1036), BeinCard)
                        )
                    )
                    .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                    .padding(if (compact) 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.tr(
                                lang,
                                "📲 لوحة فضاء الزبون — استعادة وإدارة القوائم عبر رمز الشاشة",
                                "📲 Espace Client — Gestion des listes via Code Écran",
                                "📲 Customer Space — Manage Lists via Screen Code"
                            ),
                            color = Color.White,
                            fontSize = if (compact) 14.sp else 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = AppStrings.tr(
                                lang,
                                "أضف أو عدّل قوائمك من صفحة فضاء الزبون (customer_portal.php) عبر رمز الشاشة ثم اضغط استعادة القوائم",
                                "Ajoutez ou modifiez vos listes depuis customer_portal.php avec votre Code Écran",
                                "Add or edit your lists via customer_portal.php using your Screen Code"
                            ),
                            color = Color(0xFFD8B4FE),
                            fontSize = if (compact) 10.sp else 12.sp
                        )
                    }
                    if (subscription != null && subscription.active) {
                        Text(
                            text = "⏳ الصلاحية: ${subscription.daysLeft} يوم",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .background(EmeraldLive.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .border(1.dp, EmeraldLive, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BeinDarkBg)
                        .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(12.dp))
                        .clickable { copyDeviceCodeToClipboard(context, deviceId) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = AppStrings.tr(lang, "رمز الشاشة الخاص بك:", "Votre Code Écran :", "Your Screen Code:"),
                            color = Color.LightGray,
                            fontSize = if (compact) 10.sp else 12.sp
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = deviceId,
                                color = Color.White,
                                fontSize = if (compact) 18.sp else 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BeinFocusableButton(
                            text = if (isSyncing) {
                                AppStrings.tr(lang, "جارٍ الاستعادة...", "Synchronisation...", "Syncing...")
                            } else {
                                AppStrings.tr(lang, "🔄 استعادة القوائم برمز الشاشة", "🔄 Récupérer les listes", "🔄 Restore Lists by Code")
                            },
                            isPrimary = true,
                            compact = compact,
                            onClick = onPullRemoteListsByScreenCode
                        )
                        if (isSubActive) {
                            BeinFocusableButton(
                                text = if (showAddForm) {
                                    AppStrings.tr(lang, "إغلاق", "Fermer", "Close")
                                } else {
                                    AppStrings.tr(lang, "➕ إضافة قائمة", "➕ Ajouter", "➕ Add List")
                                },
                                isPrimary = false,
                                compact = compact,
                                onClick = {
                                    editingId = ""
                                    serverName = ""
                                    serverUrl = ""
                                    username = ""
                                    password = ""
                                    showAddForm = !showAddForm
                                }
                            )
                        }
                    }
                }
            }
        }

        // User Choice: MERGE ALL Lists vs SEPARATE Lists
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BeinCard)
                    .border(1.5.dp, BeinBorder, RoundedCornerShape(16.dp))
                    .padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = AppStrings.tr(
                        lang,
                        "🔀 دمج القوائم أو فصلها (المستخدم يقرر):",
                        "🔀 Fusionner ou séparer les listes :",
                        "🔀 Merge or Separate Playlists:"
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 12.sp else 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlaylistDisplayMode.values().forEach { mode ->
                        val selected = playlistMode == mode
                        BeinFocusableButton(
                            text = mode.label(lang),
                            isPrimary = selected,
                            compact = compact,
                            modifier = Modifier.weight(1f),
                            onClick = { onChangePlaylistMode(mode) }
                        )
                    }
                }

                if (playlistMode == PlaylistDisplayMode.SEPARATE_LISTS && playlists.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            BeinFocusableButton(
                                text = AppStrings.tr(lang, "كل القوائم", "Toutes", "All Lists"),
                                isPrimary = activePlaylistFilterId == "ALL",
                                compact = true,
                                onClick = { onSelectActivePlaylist("ALL") }
                            )
                        }
                        item {
                            BeinFocusableButton(
                                text = AppStrings.tr(lang, "القائمة الرئيسية", "Liste Principale", "Main Portal"),
                                isPrimary = activePlaylistFilterId == "PORTAL_MAIN",
                                compact = true,
                                onClick = { onSelectActivePlaylist("PORTAL_MAIN") }
                            )
                        }
                        items(playlists, key = { it.id }) { pl ->
                            BeinFocusableButton(
                                text = pl.serverName.ifBlank { "XTREAM" },
                                isPrimary = activePlaylistFilterId == pl.id,
                                compact = true,
                                onClick = { onSelectActivePlaylist(pl.id) }
                            )
                        }
                    }
                }
            }
        }

        if (showAddForm && isSubActive) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 780.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BeinCard)
                        .border(2.dp, BeinHighlightPurple, RoundedCornerShape(16.dp))
                        .padding(if (compact) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (editingId.isNotBlank()) "✏️ تعديل بيانات القائمة" else "➕ إضافة قائمة XTREAM جديدة",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            label = { Text(AppStrings.tr(lang, "اسم القائمة", "Nom de la liste", "Playlist Name")) },
                            singleLine = true,
                            modifier = Modifier.weight(0.4f)
                        )
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server URL (http://host:port)") },
                            singleLine = true,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    BeinFocusableButton(
                        text = AppStrings.tr(lang, "حفظ ومزامنة القائمة فوراً ⚡", "Enregistrer et synchroniser ⚡", "Save & Sync Playlist Now ⚡"),
                        isPrimary = true,
                        compact = compact,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                val targetId = editingId.ifBlank { UUID.randomUUID().toString().take(8) }
                                val newCfg = XtreamAccountConfig(
                                    id = targetId,
                                    serverName = serverName.trim().ifBlank { "قائمة XTREAM #${playlists.size + 1}" },
                                    serverUrl = serverUrl.trim(),
                                    username = username.trim(),
                                    password = password.trim(),
                                    enabled = true,
                                    orderIndex = playlists.size
                                )
                                onAddOrUpdatePlaylist(newCfg)
                                editingId = ""
                                serverName = ""
                                serverUrl = ""
                                username = ""
                                password = ""
                                showAddForm = false
                            }
                        }
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BeinCard)
                    .border(1.dp, BeinBorder, RoundedCornerShape(16.dp))
                    .padding(if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = AppStrings.tr(
                        lang,
                        "📋 القوائم المرتبطة برمز الشاشة (${playlists.size}):",
                        "📋 Listes liées au Code Écran (${playlists.size}) :",
                        "📋 Lists Linked to Screen Code (${playlists.size}):"
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 13.sp else 15.sp
                )

                if (playlists.isEmpty()) {
                    Text(
                        text = AppStrings.tr(
                            lang,
                            "لا توجد قوائم مضافة حالياً. يمكنك رفع قوائمك من لوحة فضاء الزبون (customer_portal.php) عبر رمز الشاشة $deviceId ثم الضغط على «استعادة القوائم برمز الشاشة».",
                            "Aucune liste. Ajoutez vos listes via customer_portal.php avec le code $deviceId.",
                            "No playlists yet. Add lists via customer_portal.php using Screen Code $deviceId."
                        ),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                } else {
                    playlists.forEachIndexed { idx, pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (pl.enabled) BeinSurface else Color(0xFF100A19))
                                .border(
                                    1.dp,
                                    if (pl.enabled) BeinHighlightPurple else BeinBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${idx + 1}. ${pl.serverName.ifBlank { "XTREAM" }}",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = pl.statusNote,
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 11.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                DpadSmallControlBtn(
                                    label = "▲",
                                    enabled = idx > 0,
                                    onClick = { onMovePlaylist(idx, idx - 1) }
                                )
                                DpadSmallControlBtn(
                                    label = "▼",
                                    enabled = idx < playlists.lastIndex,
                                    onClick = { onMovePlaylist(idx, idx + 1) }
                                )
                                DpadSmallControlBtn(
                                    label = "✏️",
                                    enabled = isSubActive,
                                    onClick = {
                                        editingId = pl.id
                                        serverName = pl.serverName
                                        serverUrl = pl.serverUrl
                                        username = pl.username
                                        password = pl.password
                                        showAddForm = true
                                    }
                                )
                                DpadSmallControlBtn(
                                    label = "🔄",
                                    enabled = !isSyncing && isSubActive,
                                    onClick = { onAddOrUpdatePlaylist(pl) }
                                )
                                DpadSmallControlBtn(
                                    label = if (pl.enabled) "مفعّلة ✓" else "معطّلة",
                                    enabled = true,
                                    onClick = { onTogglePlaylistEnabled(pl) }
                                )
                                DpadSmallControlBtn(
                                    label = "✕",
                                    enabled = true,
                                    onClick = { onDeletePlaylist(pl) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BeinFocusableButton(
    text: String,
    isPrimary: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .scale(if (focused) 1.05f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> Color.White
                    isPrimary -> BeinPrimaryPurple
                    else -> BeinSurface
                }
            )
            .border(
                width = if (focused) 2.5.dp else 1.2.dp,
                color = if (focused) BeinFocusGlow else if (isPrimary) BeinHighlightPurple else BeinBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 7.dp else 9.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SeriesEpisodesModal(
    series: TvChannel,
    xtreamLists: List<XtreamAccountConfig>,
    compact: Boolean,
    onPlayEpisode: (SeriesEpisode) -> Unit,
    onDismiss: () -> Unit
) {
    var episodes by remember(series.stableKey) { mutableStateOf<List<SeriesEpisode>>(emptyList()) }
    var loading by remember(series.stableKey) { mutableStateOf(true) }

    LaunchedEffect(series.stableKey) {
        loading = true
        episodes = XtreamRepository.fetchSeriesEpisodes(series, xtreamLists)
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 580.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .clip(RoundedCornerShape(18.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                .padding(if (compact) 12.dp else 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📺 ${series.name}",
                        color = Color.White,
                        fontSize = if (compact) 14.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2
                    )
                    Text(
                        text = "اختر الحلقة للمشاهدة الفورية (${episodes.size} حلقة)",
                        color = Color(0xFFD8B4FE),
                        fontSize = if (compact) 10.sp else 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (episodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "لم يتم العثور على حلقات منفصلة؛ جاري التشغيل المباشر...",
                        color = Color.LightGray
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(episodes, key = { it.id }) { ep ->
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (focused) 1.02f else 1.0f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (focused) Color.White else BeinCard)
                                .border(
                                    width = if (focused) 2.5.dp else 1.dp,
                                    color = if (focused) BeinFocusGlow else BeinBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { focused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                        (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                            ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                                    ) {
                                        onPlayEpisode(ep)
                                        true
                                    } else false
                                }
                                .focusable()
                                .clickable { onPlayEpisode(ep) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "موسم ${ep.seasonNumber} • حلقة ${ep.episodeNumber}: ${ep.title}",
                                color = if (focused) Color.Black else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 11.sp else 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (focused) BeinPrimaryPurple else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodMoviesAndSeriesCatalogView(
    titleAr: String,
    emptyHintAr: String,
    items: List<TvChannel>,
    favorites: List<TvChannel>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    gridState: LazyGridState,
    lastWatchedKey: String?,
    restoreFocusTrigger: Int,
    compact: Boolean,
    onPlay: (TvChannel) -> Unit,
    onToggleFavorite: (TvChannel) -> Unit,
    onOpenXtream: () -> Unit,
    onRefresh: () -> Unit
) {
    var vodSearch by remember { mutableStateOf("") }
    val lastWatchedFocusRequester = remember { FocusRequester() }

    val categories = remember(items) {
        val grouped = items.groupBy { it.group }
        listOf(BouquetSummary("الكل", items.size)) +
            grouped.entries.map { BouquetSummary(it.key, it.value.size) }
    }

    val filtered = remember(items, selectedCategory, vodSearch) {
        val q = vodSearch.trim()
        items.filter { item ->
            (selectedCategory == "الكل" || item.group == selectedCategory) &&
                (q.isEmpty() || item.name.contains(q, ignoreCase = true) || item.group.contains(q, ignoreCase = true))
        }
    }

    val favSet = remember(favorites) { favorites.map { it.stableKey }.toHashSet() }

    LaunchedEffect(restoreFocusTrigger, lastWatchedKey, filtered.size) {
        if (lastWatchedKey != null && filtered.isNotEmpty()) {
            val idx = filtered.indexOfFirst { it.stableKey == lastWatchedKey }
            if (idx >= 0) {
                runCatching { gridState.scrollToItem(idx) }
                delay(65L)
                runCatching { lastWatchedFocusRequester.requestFocus() }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 6.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleAr,
                color = Color.White,
                fontSize = if (compact) 13.sp else 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactSearchBar(
                    query = vodSearch,
                    onQueryChange = { vodSearch = it },
                    compact = compact,
                    modifier = Modifier
                        .width(if (compact) 150.dp else 210.dp)
                        .height(if (compact) 28.dp else 34.dp)
                )
                BeinFocusableButton(
                    text = "📲 فضاء الزبون",
                    isPrimary = true,
                    compact = true,
                    onClick = onOpenXtream
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories, key = { it.name }) { cat ->
                val isSelected = selectedCategory == cat.name
                var focused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .height(if (compact) 28.dp else 34.dp)
                        .scale(if (focused) 1.05f else 1.0f)
                        .clip(RoundedCornerShape(14.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .background(
                            when {
                                focused -> Color.White
                                isSelected -> BeinPrimaryPurple
                                else -> BeinCard
                            }
                        )
                        .border(
                            width = if (focused) 2.5.dp else 1.dp,
                            color = if (focused) BeinFocusGlow else if (isSelected) Color.White else BeinBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectCategory(cat.name) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${cat.name} (${cat.count})",
                        color = if (focused) Color.Black else Color.White,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(emptyHintAr, color = Color.LightGray, fontSize = if (compact) 11.sp else 14.sp, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BeinFocusableButton(
                            text = "فتح فضاء الزبون 📲",
                            isPrimary = true,
                            compact = compact,
                            onClick = onOpenXtream
                        )
                        BeinFocusableButton(
                            text = "تحديث الآن 🔄",
                            isPrimary = false,
                            compact = compact,
                            onClick = onRefresh
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = if (compact) 128.dp else 168.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.stableKey }) { item ->
                    val itemMod = if (item.stableKey == lastWatchedKey) {
                        Modifier.focusRequester(lastWatchedFocusRequester)
                    } else {
                        Modifier
                    }
                    BeinChannelCard(
                        channel = item,
                        isFavorite = favSet.contains(item.stableKey),
                        isLastWatched = item.stableKey == lastWatchedKey,
                        compact = compact,
                        modifier = itemMod,
                        onClick = { onPlay(item) },
                        onToggleFavorite = { onToggleFavorite(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExitConfirmationModal(
    lang: AppUiLanguage,
    compact: Boolean,
    onStay: () -> Unit,
    onConfirmExit: () -> Unit
) {
    var stayFocused by remember { mutableStateOf(true) }
    var exitFocused by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onStay) {
        Column(
            modifier = Modifier
                .widthIn(min = if (compact) 280.dp else 330.dp, max = if (compact) 380.dp else 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (compact) 18.dp else 24.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(if (compact) 18.dp else 24.dp))
                .padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = AppStrings.tr(lang, "تأكيد الخروج من التطبيق", "Confirmer la fermeture", "Confirm Exit"),
                color = Color.White,
                fontSize = if (compact) 15.sp else 19.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

            BrandLogoBadge(
                modifier = Modifier.size(if (compact) 62.dp else 86.dp),
                cornerRadius = if (compact) 14.dp else 20.dp
            )

            Spacer(Modifier.height(if (compact) 14.dp else 22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (stayFocused) Color.White else BeinPrimaryPurple,
                        contentColor = if (stayFocused) Color.Black else Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compact) 42.dp else 48.dp)
                        .scale(if (stayFocused) 1.05f else 1.0f)
                        .border(2.5.dp, if (stayFocused) BeinFocusGlow else Color.Transparent, RoundedCornerShape(24.dp))
                        .onFocusChanged { stayFocused = it.isFocused }
                        .focusable()
                ) {
                    Text(
                        AppStrings.tr(lang, "البقاء", "Rester", "Stay"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 11.sp else 13.sp
                    )
                }

                Button(
                    onClick = onConfirmExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (exitFocused) CrimsonAlert else Color(0xFF281220),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compact) 42.dp else 48.dp)
                        .scale(if (exitFocused) 1.05f else 1.0f)
                        .border(2.5.dp, if (exitFocused) Color.White else CrimsonAlert, RoundedCornerShape(24.dp))
                        .onFocusChanged { exitFocused = it.isFocused }
                        .focusable()
                ) {
                    Text(
                        AppStrings.tr(lang, "خروج", "Quitter", "Exit"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 11.sp else 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Top Header with Restored Subscription Duration & Expiry Badge!
 */
@Composable
private fun TopBeinHeader(
    deviceId: String,
    subscription: SubscriptionInfo?,
    vpnStatus: CloudflareTunnelStatus,
    totalCount: Int,
    moviesCount: Int,
    seriesCount: Int,
    bouquetCount: Int,
    isLoading: Boolean,
    useGrid: Boolean,
    lang: AppUiLanguage,
    playlistMode: PlaylistDisplayMode,
    playlists: List<XtreamAccountConfig>,
    activePlaylistFilterId: String,
    compact: Boolean,
    onSelectLanguage: (AppUiLanguage) -> Unit,
    onSelectPlaylistFilter: (String) -> Unit,
    onOpenBouquets: () -> Unit,
    onOpenXtream: () -> Unit,
    onOpenActivation: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1A0D2E), BeinSurface)
                )
            )
            .border(1.dp, BeinBorder, RectangleShape)
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 5.dp else 8.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandLogoBadge(
                modifier = Modifier.size(if (compact) 28.dp else 38.dp),
                cornerRadius = if (compact) 7.dp else 10.dp
            )
            Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = APP_BRAND_NAME,
                        color = Color.White,
                        fontSize = if (compact) 13.sp else 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (vpnStatus.connected) {
                        Text(
                            text = "🔒 VPN",
                            color = Color.White,
                            fontSize = if (compact) 8.sp else 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .background(BeinPrimaryPurple, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    // Restored Subscription Validity Badge in Header!
                    if (subscription != null && subscription.active) {
                        val subBadgeText = if (subscription.expireDate.isNotBlank()) {
                            "⏳ الصلاحية: ${subscription.daysLeft} يوم (${subscription.expireDate})"
                        } else {
                            "⏳ الصلاحية: ${subscription.daysLeft} يوم"
                        }
                        Text(
                            text = subBadgeText,
                            color = Color.White,
                            fontSize = if (compact) 8.sp else 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .background(Color(0xFF0E2E24), RoundedCornerShape(5.dp))
                                .border(1.dp, EmeraldLive.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                                .clickable { onOpenActivation() }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "$totalCount ${AppStrings.tr(lang, "قناة", "Chaînes", "Ch")} • $moviesCount ${AppStrings.tr(lang, "فيلم", "Films", "Movies")} • $seriesCount ${AppStrings.tr(lang, "مسلسل", "Séries", "Series")}",
                    color = Color(0xFFD8B4FE),
                    fontSize = if (compact) 8.sp else 10.sp
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BeinFocusableButton(
                text = "🌐 ${lang.nativeLabel}",
                isPrimary = false,
                compact = true,
                onClick = {
                    val values = AppUiLanguage.values()
                    val next = values[(lang.ordinal + 1) % values.size]
                    onSelectLanguage(next)
                }
            )

            if (playlistMode == PlaylistDisplayMode.SEPARATE_LISTS && playlists.isNotEmpty()) {
                BeinFocusableButton(
                    text = "📋 " + (playlists.find { it.id == activePlaylistFilterId }?.serverName
                        ?: AppStrings.tr(lang, "كل القوائم", "Toutes", "All Lists")),
                    isPrimary = true,
                    compact = true,
                    onClick = {
                        val ids = listOf("ALL", "PORTAL_MAIN") + playlists.map { it.id }
                        val idx = ids.indexOf(activePlaylistFilterId).coerceAtLeast(0)
                        onSelectPlaylistFilter(ids[(idx + 1) % ids.size])
                    }
                )
            }

            BeinFocusableButton(
                text = "📂 ${AppStrings.tr(lang, "الباقات", "Bouquets", "Bouquets")} ($bouquetCount)",
                isPrimary = true,
                compact = true,
                onClick = onOpenBouquets
            )

            BeinFocusableButton(
                text = "📲 ${AppStrings.tr(lang, "فضاء الزبون", "Espace Client", "Customer Space")}",
                isPrimary = false,
                compact = true,
                onClick = onOpenXtream
            )

            BeinFocusableButton(
                text = deviceId,
                isPrimary = false,
                compact = true,
                onClick = onOpenActivation
            )

            BeinFocusableButton(
                text = if (isLoading) "⏳" else "🔄",
                isPrimary = false,
                compact = true,
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun LandscapeSideNavigationRail(
    activeTab: MainTab,
    lang: AppUiLanguage,
    compact: Boolean,
    onSelectTab: (MainTab) -> Unit,
    onExitClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (compact) 64.dp else 88.dp)
            .background(BeinSurface)
            .border(1.dp, BeinBorder, RectangleShape)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(MainTab.values().toList(), key = { it.name }) { tab ->
            val selected = activeTab == tab
            var focused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp)
                    .scale(if (focused) 1.07f else 1.0f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            focused -> Color.White
                            selected -> BeinPrimaryPurple
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (focused) 2.5.dp else if (selected) 1.5.dp else 0.dp,
                        color = if (focused) BeinFocusGlow else if (selected) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                        ) {
                            onSelectTab(tab)
                            true
                        } else false
                    }
                    .focusable()
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = if (compact) 6.dp else 8.dp, horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when (tab) {
                        MainTab.LIVE -> Icons.Default.LiveTv
                        MainTab.BOUQUETS -> Icons.Default.Folder
                        MainTab.MOVIES -> Icons.Default.Movie
                        MainTab.SERIES -> Icons.Default.VideoLibrary
                        MainTab.XTREAM -> Icons.Default.CloudDownload
                        MainTab.FAVORITES -> Icons.Default.Favorite
                        MainTab.ACTIVATION -> Icons.Default.VpnKey
                        MainTab.SETTINGS -> Icons.Default.Settings
                    },
                    contentDescription = tab.label(lang),
                    tint = if (focused) Color.Black else Color.White,
                    modifier = Modifier.size(if (compact) 17.dp else 21.dp)
                )
                if (!compact) {
                    Text(
                        text = tab.label(lang),
                        color = if (focused) Color.Black else Color.White,
                        fontSize = 9.sp,
                        fontWeight = if (selected || focused) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            var exitFocused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 4.dp)
                    .scale(if (exitFocused) 1.07f else 1.0f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (exitFocused) CrimsonAlert else Color.Transparent)
                    .border(2.dp, if (exitFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                    .onFocusChanged { exitFocused = it.isFocused }
                    .focusable()
                    .clickable { onExitClick() }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Exit",
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                )
            }
        }
    }
}

/**
 * Responsive Bouquets Grid Directory View:
 * Adaptive card width + multi-line text so every bouquet name appears in full without clipping!
 */
@Composable
private fun BouquetsGridDirectoryView(
    bouquetSummaries: List<BouquetSummary>,
    selectedCategory: String,
    lang: AppUiLanguage,
    compact: Boolean,
    onSelectBouquet: (String) -> Unit
) {
    var filterText by remember { mutableStateOf("") }
    val filteredBouquets = remember(bouquetSummaries, filterText) {
        val q = filterText.trim()
        if (q.isEmpty()) bouquetSummaries
        else bouquetSummaries.filter { it.name.contains(q, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (compact) 10.dp else 16.dp,
                vertical = if (compact) 6.dp else 10.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📂 ${AppStrings.tr(lang, "دليل الباقات والمفضلة", "Répertoire des Bouquets", "Bouquets Directory")} (${bouquetSummaries.size})",
                    color = Color.White,
                    fontSize = if (compact) 14.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            CompactSearchBar(
                query = filterText,
                onQueryChange = { filterText = it },
                compact = compact,
                modifier = Modifier
                    .width(if (compact) 160.dp else 220.dp)
                    .height(if (compact) 30.dp else 36.dp)
            )
        }

        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (compact) 180.dp else 235.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredBouquets, key = { it.name }) { bq ->
                val isSelected = bq.name == selectedCategory
                var focused by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .defaultMinSize(minHeight = if (compact) 86.dp else 104.dp)
                        .scale(if (focused) 1.03f else 1.0f)
                        .clip(RoundedCornerShape(if (compact) 14.dp else 16.dp))
                        .background(
                            when {
                                focused -> Color.White
                                isSelected -> BeinPrimaryPurple
                                else -> BeinCard
                            }
                        )
                        .border(
                            width = if (focused) 2.8.dp else if (isSelected) 1.8.dp else 1.dp,
                            color = if (focused) BeinFocusGlow else if (isSelected) Color.White else BeinBorder,
                            shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .onKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                    ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                            ) {
                                onSelectBouquet(bq.name)
                                true
                            } else false
                        }
                        .focusable()
                        .clickable { onSelectBouquet(bq.name) }
                        .padding(if (compact) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (bq.isFavoritesSpecial) Icons.Default.Favorite else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (focused) BeinPrimaryPurple else Color.White,
                            modifier = Modifier.size(if (compact) 20.dp else 24.dp)
                        )

                        Text(
                            text = "${bq.count} ${AppStrings.tr(lang, "قناة", "Chaînes", "Ch")}",
                            color = Color.White,
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .background(
                                    if (focused) BeinPrimaryPurple else Color(0xFF23133D),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.height(if (compact) 8.dp else 10.dp))

                    // Full Bouquet Name without clipping!
                    Text(
                        text = bq.name,
                        color = if (focused) Color.Black else Color.White,
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Visible,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BeinCatalogBrowserView(
    items: List<TvChannel>,
    bouquetSummaries: List<BouquetSummary>,
    favorites: List<TvChannel>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    statusMessage: String,
    isLoading: Boolean,
    useGrid: Boolean,
    showBouquetSidebar: Boolean,
    onToggleBouquetSidebar: () -> Unit,
    gridState: LazyGridState,
    listState: LazyListState,
    bouquetListState: LazyListState,
    lastWatchedKey: String?,
    restoreFocusTrigger: Int,
    lang: AppUiLanguage,
    compact: Boolean,
    deviceId: String,
    onPlay: (TvChannel) -> Unit,
    onToggleFavorite: (TvChannel) -> Unit,
    onRefresh: () -> Unit
) {
    val favLabel = AppStrings.favoritesLabel(lang)
    val allLabel = AppStrings.allBouquetsLabel(lang)
    val lastWatchedFocusRequester = remember { FocusRequester() }

    val filteredItems = remember(items, favorites, selectedCategory, searchQuery, favLabel, allLabel) {
        val q = searchQuery.trim()
        val sourceList = if (selectedCategory == favLabel) favorites else items
        sourceList.filter { ch ->
            (selectedCategory == favLabel || selectedCategory == allLabel || selectedCategory == "الكل" || ch.group == selectedCategory) &&
                (q.isEmpty() ||
                    ch.name.contains(q, ignoreCase = true) ||
                    ch.group.contains(q, ignoreCase = true) ||
                    ch.number.toString() == q)
        }
    }

    val favSet = remember(favorites) { favorites.map { it.stableKey }.toHashSet() }

    // Restore exact scroll & focus on the last-watched channel when returning from Player!
    LaunchedEffect(restoreFocusTrigger, lastWatchedKey, filteredItems.size) {
        if (lastWatchedKey != null && filteredItems.isNotEmpty()) {
            val idx = filteredItems.indexOfFirst { it.stableKey == lastWatchedKey }
            if (idx >= 0) {
                if (useGrid) {
                    runCatching { gridState.scrollToItem(idx) }
                } else {
                    runCatching { listState.scrollToItem(idx) }
                }
                delay(65L)
                runCatching { lastWatchedFocusRequester.requestFocus() }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 4.dp else 8.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        if (showBouquetSidebar && bouquetSummaries.size > 1) {
            Column(
                modifier = Modifier
                    .width(if (compact) 168.dp else 225.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinSurface)
                    .border(1.5.dp, BeinBorder, RoundedCornerShape(14.dp))
                    .padding(if (compact) 6.dp else 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📂 ${AppStrings.tr(lang, "الباقات", "Bouquets", "Bouquets")}",
                        color = Color.White,
                        fontSize = if (compact) 11.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                LazyColumn(
                    state = bouquetListState,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(bouquetSummaries, key = { it.name }) { bq ->
                        val isSelected = selectedCategory == bq.name
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .scale(if (focused) 1.03f else 1.0f)
                                .clip(RoundedCornerShape(9.dp))
                                .onFocusChanged { focused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                        (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                            ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                                    ) {
                                        onSelectCategory(bq.name)
                                        true
                                    } else false
                                }
                                .focusable()
                                .background(
                                    when {
                                        focused -> Color.White
                                        isSelected -> BeinPrimaryPurple
                                        else -> BeinCard
                                    }
                                )
                                .border(
                                    width = if (focused) 2.5.dp else if (isSelected) 1.5.dp else 0.dp,
                                    color = if (focused) BeinFocusGlow else if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .clickable { onSelectCategory(bq.name) }
                                .padding(
                                    horizontal = if (compact) 8.dp else 10.dp,
                                    vertical = if (compact) 7.dp else 9.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bq.name,
                                color = if (focused) Color.Black else Color.White,
                                fontSize = if (compact) 11.sp else 13.sp,
                                fontWeight = if (focused || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Visible,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${bq.count}",
                                color = if (focused) BeinPrimaryPurple else Color(0xFFD8B4FE),
                                fontSize = if (compact) 9.sp else 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BeinFocusableButton(
                    text = if (showBouquetSidebar) {
                        "📂 ${AppStrings.tr(lang, "إخفاء الباقات", "Masquer", "Hide Bouquets")}"
                    } else {
                        "📂 ${AppStrings.tr(lang, "إظهار الباقات", "Afficher", "Show Bouquets")}"
                    },
                    isPrimary = showBouquetSidebar,
                    compact = true,
                    onClick = onToggleBouquetSidebar
                )

                CompactSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    compact = compact,
                    modifier = Modifier
                        .widthIn(
                            min = if (compact) 130.dp else 170.dp,
                            max = if (compact) 200.dp else 250.dp
                        )
                        .height(if (compact) 30.dp else 36.dp)
                )

                Text(
                    text = "$selectedCategory (${filteredItems.size})",
                    color = Color(0xFFD8B4FE),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 11.sp else 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee()
                )
            }

            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

            if (filteredItems.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = AppStrings.tr(lang, "لا توجد قنوات مطابقة حالياً", "Aucune chaîne trouvée", "No matching channels"),
                            color = Color.White,
                            fontSize = if (compact) 14.sp else 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "📞 علي سات : $ALI_SAT_PHONE_NUMBER • رمز الشاشة: $deviceId",
                            color = Color(0xFFD8B4FE),
                            fontSize = if (compact) 11.sp else 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        BeinFocusableButton(
                            text = AppStrings.tr(lang, "تحديث القنوات الآن 🔄", "Actualiser maintenant 🔄", "Refresh Channels Now 🔄"),
                            isPrimary = true,
                            compact = compact,
                            onClick = onRefresh
                        )
                    }
                }
            } else if (useGrid) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = if (compact) 132.dp else 174.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.stableKey }) { ch ->
                        val itemMod = if (ch.stableKey == lastWatchedKey) {
                            Modifier.focusRequester(lastWatchedFocusRequester)
                        } else {
                            Modifier
                        }
                        BeinChannelCard(
                            channel = ch,
                            isFavorite = favSet.contains(ch.stableKey),
                            isLastWatched = ch.stableKey == lastWatchedKey,
                            compact = compact,
                            modifier = itemMod,
                            onClick = { onPlay(ch) },
                            onToggleFavorite = { onToggleFavorite(ch) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.stableKey }) { ch ->
                        val itemMod = if (ch.stableKey == lastWatchedKey) {
                            Modifier.focusRequester(lastWatchedFocusRequester)
                        } else {
                            Modifier
                        }
                        ChannelListRow(
                            channel = ch,
                            isFavorite = favSet.contains(ch.stableKey),
                            isLastWatched = ch.stableKey == lastWatchedKey,
                            compact = compact,
                            modifier = itemMod,
                            onClick = { onPlay(ch) },
                            onToggleFavorite = { onToggleFavorite(ch) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BeinCard)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) BeinFocusGlow else BeinBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(if (compact) 13.dp else 16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "بحث...",
                    color = Color.Gray,
                    fontSize = if (compact) 10.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "مسح",
                tint = Color.LightGray,
                modifier = Modifier
                    .size(if (compact) 13.dp else 16.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

/**
 * Clean beIN Dark Purple Channel Card:
 * • NO '#' or Channel Numbers on the logo/card in the main UI!
 * • Full Channel Name & Bouquet Name displayed clearly without edge clipping!
 */
@Composable
private fun BeinChannelCard(
    channel: TvChannel,
    isFavorite: Boolean,
    isLastWatched: Boolean = false,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .scale(if (isFocused) 1.04f else 1.0f)
            .clip(RoundedCornerShape(if (compact) 12.dp else 16.dp))
            .background(
                when {
                    isFocused -> BeinPrimaryPurple
                    isLastWatched -> Color(0xFF23113D)
                    else -> BeinCard
                }
            )
            .border(
                width = if (isFocused) 2.8.dp else if (isLastWatched) 1.8.dp else 1.dp,
                color = if (isFocused) BeinFocusGlow else if (isLastWatched) BeinHighlightPurple else BeinBorder,
                shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 66.dp else 90.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1E0F36), Color(0xFF0E071B))
                    )
                )
                .padding(if (compact) 6.dp else 10.dp)
        ) {
            ChannelAvatar(
                name = channel.name,
                logoUrl = channel.logo,
                modifier = Modifier
                    .size(if (compact) 48.dp else 66.dp)
                    .align(Alignment.Center),
                cornerRadius = if (compact) 10.dp else 14.dp,
                contentScale = ContentScale.Fit,
                innerPadding = if (compact) 4.dp else 6.dp
            )

            // Only show rating star if it's a Movie/Series with a rating — NO '#' or numbers on Live TV logos!
            if (channel.contentType != StreamContentType.LIVE_TV && channel.rating.isNotBlank()) {
                Text(
                    text = "★ ${channel.rating}",
                    color = Color.White,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(BeinPrimaryPurple, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(if (compact) 22.dp else 28.dp)
                    .clip(CircleShape)
                    .background(Color(0x9907050C))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "مفضلة",
                    tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                    modifier = Modifier.size(if (compact) 12.dp else 15.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 8.dp else 12.dp,
                    vertical = if (compact) 7.dp else 10.dp
                )
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Visible,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = channel.group,
                color = if (isFocused) Color(0xFFF3E8FF) else Color(0xFFC4B5FD),
                fontSize = if (compact) 9.sp else 11.sp,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}

@Composable
private fun ChannelListRow(
    channel: TvChannel,
    isFavorite: Boolean,
    isLastWatched: Boolean = false,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.02f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> BeinPrimaryPurple
                    isLastWatched -> Color(0xFF23113D)
                    else -> BeinCard
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else if (isLastWatched) 1.5.dp else 1.dp,
                color = if (isFocused) BeinFocusGlow else if (isLastWatched) BeinHighlightPurple else BeinBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 7.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelAvatar(
            name = channel.name,
            logoUrl = channel.logo,
            modifier = Modifier.size(if (compact) 34.dp else 44.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Text(
                text = channel.group,
                color = Color(0xFFD8B4FE),
                fontSize = if (compact) 10.sp else 12.sp
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(if (compact) 30.dp else 38.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "مفضلة",
                tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                modifier = Modifier.size(if (compact) 16.dp else 20.dp)
            )
        }
    }
}

@Composable
private fun FavoritesAndHistoryView(
    favorites: List<TvChannel>,
    history: List<TvChannel>,
    lang: AppUiLanguage,
    compact: Boolean,
    onPlay: (TvChannel) -> Unit,
    onToggleFavorite: (TvChannel) -> Unit,
    onClearHistory: () -> Unit
) {
    var subTab by remember { mutableStateOf(0) }
    val favSet = remember(favorites) { favorites.map { it.stableKey }.toHashSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BeinFocusableButton(
                    text = "${AppStrings.favoritesLabel(lang)} (${favorites.size})",
                    isPrimary = subTab == 0,
                    compact = compact,
                    onClick = { subTab = 0 }
                )
                BeinFocusableButton(
                    text = "${AppStrings.tr(lang, "المشاهدة أخيراً", "Historique", "Recent")} (${history.size})",
                    isPrimary = subTab == 1,
                    compact = compact,
                    onClick = { subTab = 1 }
                )
            }

            if (subTab == 1 && history.isNotEmpty()) {
                BeinFocusableButton(
                    text = AppStrings.tr(lang, "مسح السجل", "Effacer", "Clear"),
                    isPrimary = false,
                    compact = true,
                    onClick = onClearHistory
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val list = if (subTab == 0) favorites else history
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (subTab == 0) {
                        AppStrings.tr(lang, "لم تقم بإضافة قنوات إلى المفضلة بعد", "Aucun favori pour le moment", "No favorite channels yet")
                    } else {
                        AppStrings.tr(lang, "سجل المشاهدة فارغ حالياً", "Historique vide", "Watch history is empty")
                    },
                    color = Color.LightGray,
                    fontSize = if (compact) 12.sp else 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (compact) 132.dp else 174.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list, key = { it.stableKey }) { ch ->
                    BeinChannelCard(
                        channel = ch,
                        isFavorite = favSet.contains(ch.stableKey),
                        compact = compact,
                        onClick = { onPlay(ch) },
                        onToggleFavorite = { onToggleFavorite(ch) }
                    )
                }
            }
        }
    }
}

/**
 * Restored Full Subscription Validity & Screen Activation Status View!
 */
@Composable
private fun ScreenActivationStatusView(
    deviceId: String,
    deviceName: String,
    subscription: SubscriptionInfo?,
    lang: AppUiLanguage,
    isLoading: Boolean,
    compact: Boolean,
    onVerifyNow: () -> Unit,
    onRedeemCode: (String) -> Unit
) {
    val context = LocalContext.current
    var directCode by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 660.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BeinCard)
                    .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                    .padding(if (compact) 14.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AppStrings.tr(lang, "حالة صلاحية المشغل ورمز الشاشة", "Validité du Lecteur & Code Écran", "Player Validity & Screen Code"),
                    color = Color.White,
                    fontSize = if (compact) 15.sp else 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))

                // Subscription Duration & Expiry Details Card
                if (subscription != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (subscription.active) Color(0xFF0C261E) else Color(0xFF2B1118))
                            .border(
                                1.5.dp,
                                if (subscription.active) EmeraldLive else CrimsonAlert,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (subscription.active) "✅ حالة المشغل: نشط ومفعّل" else "⛔ حالة المشغل: منتهي الصلاحية",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 12.sp else 14.sp
                            )
                            if (subscription.expireDate.isNotBlank()) {
                                Text(
                                    text = "تاريخ انتهاء الصلاحية: ${subscription.expireDate}",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = if (compact) 11.sp else 13.sp
                                )
                            }
                        }
                        Text(
                            text = "${subscription.daysLeft} يوم متبقٍ",
                            color = if (subscription.active) EmeraldLive else CrimsonAlert,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 14.sp else 17.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    text = "📞 للتجديد والتفعيل تواصل مع علي سات : $ALI_SAT_PHONE_NUMBER",
                    color = Color(0xFFD8B4FE),
                    fontSize = if (compact) 13.sp else 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BeinDarkBg)
                        .border(2.dp, BeinHighlightPurple, RoundedCornerShape(12.dp))
                        .clickable { copyDeviceCodeToClipboard(context, deviceId) }
                        .padding(vertical = if (compact) 10.dp else 16.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = deviceId,
                            color = Color.White,
                            fontSize = if (compact) 20.sp else 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = AppStrings.tr(lang, "نسخ رمز الشاشة 📋", "Copier le code 📋", "Copy Screen Code 📋"),
                        isPrimary = true,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { copyDeviceCodeToClipboard(context, deviceId) }
                    )
                    BeinFocusableButton(
                        text = if (isLoading) "..." else AppStrings.tr(lang, "تحديث الصلاحية 🔄", "Actualiser 🔄", "Refresh Validity 🔄"),
                        isPrimary = false,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = onVerifyNow
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 660.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BeinCard)
                    .border(1.dp, BeinBorder, RoundedCornerShape(16.dp))
                    .padding(if (compact) 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = directCode,
                        onValueChange = { directCode = it.uppercase() },
                        placeholder = { Text("A1B2-C3D4", fontSize = if (compact) 11.sp else 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    BeinFocusableButton(
                        text = AppStrings.tr(lang, "تفعيل الآن ✓", "Activer ✓", "Activate ✓"),
                        isPrimary = true,
                        compact = compact,
                        onClick = { if (directCode.trim().length >= 4) onRedeemCode(directCode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsAndVpnView(
    preferences: PlayerPreferences,
    rawBouquets: List<BouquetSummary>,
    allChannels: List<TvChannel>,
    vpnStatus: CloudflareTunnelStatus,
    compact: Boolean,
    onReconnectVpn: () -> Unit,
    onUpdatePreferences: (PlayerPreferences) -> Unit
) {
    val context = LocalContext.current
    val lang = preferences.appLanguage
    var cacheSizeLabel by remember { mutableStateOf(FluxBitmapCache.getCacheSizeMb(context)) }
    var showBouquetReorderModal by remember { mutableStateOf(false) }
    var showChannelReorderModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        item {
            Text(
                text = "⚙️ ${AppStrings.tr(lang, "إعدادات التطبيق واللغة وإدارة الباقات", "Paramètres, Langue & Gestion des Bouquets", "App Settings, Language & Bouquet Management")}",
                color = Color.White,
                fontSize = if (compact) 14.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // 1. Application Language Selector (Arabic / Français / English)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🌐 ${AppStrings.tr(lang, "لغة التطبيق (العربية / Français / English):", "Langue de l'application :", "Application Language:")}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 12.sp else 15.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppUiLanguage.values().forEach { lOpt ->
                        BeinFocusableButton(
                            text = lOpt.nativeLabel,
                            isPrimary = preferences.appLanguage == lOpt,
                            compact = compact,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onUpdatePreferences(preferences.copy(appLanguage = lOpt))
                            }
                        )
                    }
                }
            }
        }

        // 2. Hide Unwanted Bouquets & Reorder Bouquets / Channels
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.5.dp, BeinBorder, RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📂 ${AppStrings.tr(lang, "إخفاء الباقات غير المهمة وترتيب الباقات والقنوات بالريموت:", "Masquer les bouquets inutiles et réorganiser :", "Hide Unwanted Bouquets & Reorder via Remote:")}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (compact) 12.sp else 15.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BeinFocusableButton(
                        text = "👁 ${AppStrings.tr(lang, "إخفاء / ترتيب الباقات", "Masquer / Trier Bouquets", "Hide / Reorder Bouquets")} (${rawBouquets.size})",
                        isPrimary = true,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { showBouquetReorderModal = true }
                    )

                    BeinFocusableButton(
                        text = "📺 ${AppStrings.tr(lang, "ترتيب القنوات", "Trier les chaînes", "Reorder Channels")} (${allChannels.size})",
                        isPrimary = false,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { showChannelReorderModal = true }
                    )

                    if (preferences.customBouquetOrder.isNotEmpty() ||
                        preferences.customChannelOrder.isNotEmpty() ||
                        preferences.hiddenBouquets.isNotEmpty()
                    ) {
                        BeinFocusableButton(
                            text = AppStrings.tr(lang, "إظهار الكل", "Tout afficher", "Reset All"),
                            isPrimary = false,
                            compact = compact,
                            onClick = {
                                onUpdatePreferences(
                                    preferences.copy(
                                        customBouquetOrder = emptyList(),
                                        hiddenBouquets = emptySet(),
                                        customChannelOrder = emptyList()
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // 3. Mandatory VPN Status
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .border(1.5.dp, EmeraldLive.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(if (compact) 10.dp else 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldLive)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "VPN (إجباري تلقائي): ${vpnStatus.statusText}",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 12.sp else 14.sp
                    )
                }
                BeinFocusableButton(
                    text = AppStrings.tr(lang, "تحديث اتصال VPN 🔄", "Actualiser VPN 🔄", "Refresh VPN 🔄"),
                    isPrimary = true,
                    compact = true,
                    onClick = onReconnectVpn
                )
            }
        }

        // 4. Cache Management
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BeinCard)
                    .padding(if (compact) 10.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الذاكرة المؤقتة للشعارات ($cacheSizeLabel)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 11.sp else 13.sp
                    )
                    BeinFocusableButton(
                        text = AppStrings.tr(lang, "مسح الكاش", "Vider le cache", "Clear Cache"),
                        isPrimary = false,
                        compact = true,
                        onClick = {
                            FluxBitmapCache.clear(context)
                            cacheSizeLabel = FluxBitmapCache.getCacheSizeMb(context)
                        }
                    )
                }
            }
        }
    }

    if (showBouquetReorderModal) {
        BouquetReorderDialog(
            rawBouquets = rawBouquets,
            preferences = preferences,
            compact = compact,
            onSave = { newOrder, newHidden ->
                onUpdatePreferences(
                    preferences.copy(
                        customBouquetOrder = newOrder,
                        hiddenBouquets = newHidden
                    )
                )
            },
            onDismiss = { showBouquetReorderModal = false }
        )
    }

    if (showChannelReorderModal) {
        ChannelReorderDialog(
            allChannels = allChannels,
            preferences = preferences,
            compact = compact,
            onSaveOrder = { newChannelOrder ->
                onUpdatePreferences(
                    preferences.copy(customChannelOrder = newChannelOrder)
                )
            },
            onDismiss = { showChannelReorderModal = false }
        )
    }
}

@Composable
private fun BouquetReorderDialog(
    rawBouquets: List<BouquetSummary>,
    preferences: PlayerPreferences,
    compact: Boolean,
    onSave: (List<String>, Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var orderedNames by remember(rawBouquets, preferences.customBouquetOrder) {
        val currentSorted = CustomOrderHelper.sortBouquets(
            rawBouquets,
            preferences.customBouquetOrder,
            preferences.hiddenBouquets,
            includeHidden = true
        ).map { it.name }
        mutableStateOf(currentSorted)
    }
    var hiddenSet by remember(preferences.hiddenBouquets) {
        mutableStateOf(preferences.hiddenBouquets)
    }
    val countsMap = remember(rawBouquets) { rawBouquets.associate { it.name to it.count } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 660.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                .padding(if (compact) 12.dp else 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "📂 إخفاء الباقات غير المهمة وترتيبها بالريموت (▲ / ▼)",
                        color = Color.White,
                        fontSize = if (compact) 14.sp else 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                BeinFocusableButton(
                    text = "حفظ وإغلاق ✓",
                    isPrimary = true,
                    compact = true,
                    onClick = {
                        onSave(orderedNames, hiddenSet)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(orderedNames, key = { _, name -> name }) { idx, bName ->
                    val isHidden = bName in hiddenSet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isHidden) Color(0xFF1A0D18) else BeinCard)
                            .border(1.dp, if (isHidden) CrimsonAlert.copy(alpha = 0.5f) else BeinBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. $bName (${countsMap[bName] ?: 0})" + if (isHidden) " [مخفية]" else "",
                            color = if (isHidden) Color.Gray else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (compact) 11.sp else 13.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DpadSmallControlBtn(
                                label = if (isHidden) "👁 إظهار" else "🚫 إخفاء",
                                enabled = true,
                                onClick = {
                                    hiddenSet = if (isHidden) hiddenSet - bName else hiddenSet + bName
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                            DpadSmallControlBtn(
                                label = "⏫ للأول",
                                enabled = idx > 0,
                                onClick = {
                                    orderedNames = CustomOrderHelper.moveItem(orderedNames, idx, 0)
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▲",
                                enabled = idx > 0,
                                onClick = {
                                    orderedNames = CustomOrderHelper.moveItem(orderedNames, idx, idx - 1)
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▼",
                                enabled = idx < orderedNames.lastIndex,
                                onClick = {
                                    orderedNames = CustomOrderHelper.moveItem(orderedNames, idx, idx + 1)
                                    onSave(orderedNames, hiddenSet)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelReorderDialog(
    allChannels: List<TvChannel>,
    preferences: PlayerPreferences,
    compact: Boolean,
    onSaveOrder: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val groups = remember(allChannels) {
        listOf("الكل") + allChannels.map { it.group }.distinct()
    }
    var selectedBouquet by remember { mutableStateOf(groups.firstOrNull() ?: "الكل") }
    var channelList by remember(allChannels, selectedBouquet) {
        val filtered = if (selectedBouquet == "الكل") allChannels else allChannels.filter { it.group == selectedBouquet }
        mutableStateOf(filtered)
    }

    fun persistNewList(updatedGroupChannels: List<TvChannel>) {
        channelList = updatedGroupChannels
        val updatedKeys = updatedGroupChannels.map { it.stableKey }
        val updatedSet = updatedKeys.toHashSet()
        val fullKeys = mutableListOf<String>()
        fullKeys.addAll(updatedKeys)
        for (ch in allChannels) {
            if (ch.stableKey !in updatedSet) {
                fullKeys.add(ch.stableKey)
            }
        }
        onSaveOrder(fullKeys)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 660.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(BeinSurface)
                .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                .padding(if (compact) 12.dp else 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "📺 ترتيب القنوات بالأسهم (▲ لأعلى / ▼ لأسفل / ⏫ للأول)",
                        color = Color.White,
                        fontSize = if (compact) 13.sp else 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                BeinFocusableButton(
                    text = "تم ✓",
                    isPrimary = true,
                    compact = true,
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it }) { g ->
                    BeinFocusableButton(
                        text = g,
                        isPrimary = g == selectedBouquet,
                        compact = true,
                        onClick = { selectedBouquet = g }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(channelList, key = { _, ch -> ch.stableKey }) { idx, ch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BeinCard)
                            .border(1.dp, BeinBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. ${ch.name} (${ch.group})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 11.sp else 13.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DpadSmallControlBtn(
                                label = "⏫ للأول",
                                enabled = idx > 0,
                                onClick = {
                                    persistNewList(CustomOrderHelper.moveItem(channelList, idx, 0))
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▲",
                                enabled = idx > 0,
                                onClick = {
                                    persistNewList(CustomOrderHelper.moveItem(channelList, idx, idx - 1))
                                }
                            )
                            DpadSmallControlBtn(
                                label = "▼",
                                enabled = idx < channelList.lastIndex,
                                onClick = {
                                    persistNewList(CustomOrderHelper.moveItem(channelList, idx, idx + 1))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DpadSmallControlBtn(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .scale(if (focused) 1.07f else 1.0f)
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    !enabled -> Color(0xFF130D1E)
                    focused -> Color.White
                    else -> BeinPrimaryPurple
                }
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) BeinFocusGlow else Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(7.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (enabled && ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(enabled = enabled)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (!enabled) Color.Gray else if (focused) Color.Black else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
