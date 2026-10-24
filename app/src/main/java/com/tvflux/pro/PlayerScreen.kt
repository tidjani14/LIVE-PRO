@file:Suppress("DEPRECATION")

package com.tvflux.pro

import android.app.Activity
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

data class TrackOption(
    val title: String,
    val subtitle: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val isSelected: Boolean
)

private const val DEFAULT_BEIN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; SHIELD Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 LIVEPRO-beIN/5.1"

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: TvChannel,
    siblingChannels: List<TvChannel>,
    allChannels: List<TvChannel>,
    favorites: List<TvChannel>,
    preferences: PlayerPreferences,
    isFavorite: Boolean,
    onToggleFavorite: (TvChannel) -> Unit,
    onUpdatePreferences: (PlayerPreferences) -> Unit,
    onSelectChannel: (TvChannel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val lang = preferences.appLanguage

    var showControls by remember { mutableStateOf(false) }
    var showQuickDrawer by remember { mutableStateOf(false) }
    var drawerSelectedBouquet by remember { mutableStateOf(channel.group) }
    var focusFirstChannelTrigger by remember { mutableStateOf(0) }

    var showTracksDialog by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(preferences.resizeMode) }
    var playbackSpeed by remember { mutableStateOf(PlaybackSpeedOption.NORMAL) }

    var isBuffering by remember { mutableStateOf(true) }
    var showBufferingBadge by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var errorMessage by remember(channel.stableKey) { mutableStateOf<String?>(null) }
    var lastErrorCodeName by remember(channel.stableKey) { mutableStateOf("") }
    var retryCount by remember(channel.stableKey) { mutableStateOf(0) }
    var videoResolutionLabel by remember(channel.stableKey) { mutableStateOf("") }
    var currentTracksSnapshot by remember { mutableStateOf(Tracks.EMPTY) }

    // Primary URL is ALWAYS index 0. Alternatives are only tried if primary fails.
    val formatCandidateUrls = remember(channel.stableKey, channel.url) {
        channel.buildFormatCandidateUrls()
    }
    var candidateIndex by remember(channel.stableKey) { mutableStateOf(0) }
    var formatAttemptMode by remember(channel.stableKey) { mutableStateOf(0) }

    var aiTranslationEnabled by remember(preferences.aiTranslationEnabled) {
        mutableStateOf(preferences.aiTranslationEnabled)
    }
    var aiTargetLang by remember(preferences.aiTargetLanguage) {
        mutableStateOf(preferences.aiTargetLanguage)
    }
    var aiVoiceListenEnabled by remember(preferences.aiVoiceListenEnabled) {
        mutableStateOf(preferences.aiVoiceListenEnabled)
    }
    var aiTtsVoiceOver by remember(preferences.aiTtsVoiceOver) {
        mutableStateOf(preferences.aiTtsVoiceOver)
    }
    var rawCueSubtitle by remember(channel.stableKey) { mutableStateOf("") }
    var aiTranslatedSubtitle by remember(channel.stableKey) { mutableStateOf("") }
    var lastCueTimestampMs by remember(channel.stableKey) { mutableStateOf(0L) }
    var numberInputBuffer by remember { mutableStateOf("") }

    val rootPlayerFocusRequester = remember { FocusRequester() }
    val activeBouquetFocusRequester = remember { FocusRequester() }
    val firstChannelInBouquetFocusRequester = remember { FocusRequester() }
    val activeChannelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showQuickDrawer, showTracksDialog, showControls) {
        if (!showQuickDrawer && !showTracksDialog && !showControls) {
            delay(60L)
            runCatching { rootPlayerFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isBuffering, errorMessage) {
        if (isBuffering && errorMessage == null) {
            delay(1000L)
            if (isBuffering) showBufferingBadge = true
        } else {
            showBufferingBadge = false
        }
    }

    val favLabel = AppStrings.favoritesLabel(lang)
    val allLabel = AppStrings.allBouquetsLabel(lang)

    val allBouquets = remember(
        allChannels,
        favorites,
        preferences.customBouquetOrder,
        preferences.hiddenBouquets,
        lang
    ) {
        val visibleChannels = allChannels.filter { it.group !in preferences.hiddenBouquets }
        val grouped = visibleChannels.groupBy { it.group }

        val rawSummaries = mutableListOf<BouquetSummary>()
        rawSummaries.add(
            BouquetSummary(
                name = favLabel,
                count = favorites.size,
                isFavoritesSpecial = true
            )
        )
        rawSummaries.add(
            BouquetSummary(
                name = allLabel,
                count = visibleChannels.size
            )
        )
        grouped.forEach { (gName, list) ->
            rawSummaries.add(BouquetSummary(name = gName, count = list.size))
        }

        val sortedSummaries = CustomOrderHelper.sortBouquets(
            rawSummaries,
            preferences.customBouquetOrder,
            preferences.hiddenBouquets
        )

        sortedSummaries.map { summary ->
            val chList = when {
                summary.isFavoritesSpecial -> favorites
                summary.name == allLabel -> visibleChannels
                else -> grouped[summary.name] ?: emptyList()
            }
            summary.name to CustomOrderHelper.sortChannels(
                chList,
                preferences.customChannelOrder,
                preferences.hiddenBouquets
            )
        }
    }

    val drawerChannels = remember(allBouquets, drawerSelectedBouquet, siblingChannels) {
        allBouquets.find { it.first == drawerSelectedBouquet }?.second
            ?: allBouquets.firstOrNull { !it.first.startsWith("★") && it.second.isNotEmpty() }?.second
            ?: siblingChannels
    }

    val effectiveUrl = remember(formatCandidateUrls, candidateIndex) {
        formatCandidateUrls.getOrElse(candidateIndex.coerceIn(0, (formatCandidateUrls.size - 1).coerceAtLeast(0))) {
            channel.url
        }
    }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(aiTtsVoiceOver) {
        if (aiTtsVoiceOver) {
            var instance: TextToSpeech? = null
            instance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching { instance?.language = Locale(aiTargetLang.code) }
                }
            }
            ttsEngine = instance
        }
        onDispose {
            runCatching {
                ttsEngine?.stop()
                ttsEngine?.shutdown()
            }
            ttsEngine = null
        }
    }

    // Keyed by channel.stableKey so switching channels immediately cancels any in-flight translation!
    LaunchedEffect(channel.stableKey, rawCueSubtitle, aiTranslationEnabled, aiTargetLang) {
        if (!aiTranslationEnabled) {
            aiTranslatedSubtitle = ""
            return@LaunchedEffect
        }
        if (rawCueSubtitle.isNotBlank()) {
            val result = AiSubtitleTranslator.translateSubtitleText(rawCueSubtitle, aiTargetLang)
            if (result.isNotBlank()) {
                aiTranslatedSubtitle = result
                lastCueTimestampMs = System.currentTimeMillis()
                if (aiTtsVoiceOver) {
                    runCatching {
                        ttsEngine?.speak(result, TextToSpeech.QUEUE_FLUSH, null, "ai_sub")
                    }
                }
            }
        }
    }

    LaunchedEffect(channel.stableKey, aiTranslationEnabled, aiTargetLang) {
        if (!aiTranslationEnabled) return@LaunchedEffect
        var tick = 0
        while (aiTranslationEnabled) {
            val now = System.currentTimeMillis()
            if (aiTranslatedSubtitle.isBlank() || (now - lastCueTimestampMs) > 10_000L) {
                val smartLine = AiSubtitleTranslator.generateSmartStreamCaption(
                    channel = channel,
                    targetLang = aiTargetLang,
                    tick = tick
                )
                if (smartLine.isNotBlank()) {
                    aiTranslatedSubtitle = smartLine
                }
                tick++
            }
            delay(8_000L)
        }
    }

    DisposableEffect(aiTranslationEnabled, aiVoiceListenEnabled, channel.stableKey) {
        var recognizer: SpeechRecognizer? = null
        if (aiTranslationEnabled && aiVoiceListenEnabled && SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        scope.launch {
                            delay(2000L)
                            if (aiTranslationEnabled && aiVoiceListenEnabled) {
                                runCatching { recognizer?.startListening(intent) }
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) rawCueSubtitle = text
                        scope.launch {
                            delay(500L)
                            if (aiTranslationEnabled && aiVoiceListenEnabled) {
                                runCatching { recognizer?.startListening(intent) }
                            }
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) rawCueSubtitle = text
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                recognizer?.startListening(intent)
            }
        }
        onDispose {
            runCatching {
                recognizer?.stopListening()
                recognizer?.destroy()
            }
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val httpFactory = remember {
        OkHttpDataSource.Factory(ChannelRepository.streamHttpClient)
            .setUserAgent(DEFAULT_BEIN_USER_AGENT)
    }

    // Single stable ExoPlayer instance across the entire PlayerScreen lifecycle
    val player = remember {
        val bp = preferences.bufferProfile
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 192) < 160
        val safeTargetBytes = if (isLowRam) {
            bp.targetBufferBytes.coerceAtMost(16 * 1024 * 1024)
        } else {
            bp.targetBufferBytes
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bp.minBufferMs,
                bp.maxBufferMs,
                bp.playbackBufferMs,
                bp.rebufferMs
            )
            .setTargetBufferBytes(safeTargetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(10_000, false)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                if (preferences.preferSoftwareDecoder) {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )
            setEnableDecoderFallback(true)
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorMode(TsExtractor.MODE_MULTI_PMT)
            .setTsExtractorTimestampSearchBytes(188 * 3200)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(httpFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedSampleRateAdaptiveness(true)
                    .setSelectUndeterminedTextLanguage(true)
                    .setPreferredTextLanguage(aiTargetLang.code)
                    .setIgnoredTextSelectionFlags(0)
            )
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
    }

    LaunchedEffect(player, playbackSpeed) {
        player.playbackParameters = PlaybackParameters(playbackSpeed.speed)
    }

    // Cancel stale stream HTTP requests and reset player cleanly whenever channel or URL changes
    LaunchedEffect(player, channel.stableKey, effectiveUrl, formatAttemptMode) {
        isBuffering = true
        errorMessage = null
        showControls = false
        player.stop()

        val customHeaders = mutableMapOf(
            "Connection" to "keep-alive",
            "Accept" to "*/*",
            "Icy-MetaData" to "1"
        )
        if (!channel.referer.isNullOrBlank()) customHeaders["Referer"] = channel.referer
        if (!channel.origin.isNullOrBlank()) customHeaders["Origin"] = channel.origin
        if (!channel.cookie.isNullOrBlank()) customHeaders["Cookie"] = channel.cookie
        httpFactory.setDefaultRequestProperties(customHeaders)
        httpFactory.setUserAgent(
            channel.userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_BEIN_USER_AGENT
        )

        val itemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(effectiveUrl))

        if (channel.contentType == StreamContentType.LIVE_TV) {
            val liveConfig = MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(7_500L)
                .setMinOffsetMs(3_000L)
                .setMaxOffsetMs(30_000L)
                .setMinPlaybackSpeed(1.0f)
                .setMaxPlaybackSpeed(1.0f)
                .build()
            itemBuilder.setLiveConfiguration(liveConfig)
        }

        val low = effectiveUrl.lowercase()
        when (formatAttemptMode) {
            1 -> itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            2 -> itemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
            else -> {
                when {
                    low.contains(".mpd") -> itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                    low.contains(".m3u8") || low.contains("output=m3u8") || low.contains("type=m3u8") ->
                        itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    low.endsWith(".ts") || low.contains(".ts?") || low.contains("output=ts") ->
                        itemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
                    low.contains(".ism") -> itemBuilder.setMimeType(MimeTypes.APPLICATION_SS)
                    low.startsWith("rtsp://") -> itemBuilder.setMimeType(MimeTypes.APPLICATION_RTSP)
                }
            }
        }

        player.setMediaItem(itemBuilder.build())
        player.playWhenReady = true
        player.prepare()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                    lastErrorCodeName = ""
                    retryCount = 0
                    currentTracksSnapshot = player.currentTracks
                } else if (playbackState == Player.STATE_ENDED && preferences.autoReconnect &&
                    channel.contentType == StreamContentType.LIVE_TV
                ) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracksSnapshot = tracks
                if (aiTranslationEnabled) {
                    val hasSelectedText = tracks.groups.any { g ->
                        g.type == C.TRACK_TYPE_TEXT && g.isSelected
                    }
                    if (!hasSelectedText) {
                        val firstTextGroup = tracks.groups.firstOrNull { g ->
                            g.type == C.TRACK_TYPE_TEXT && g.length > 0
                        }
                        if (firstTextGroup != null) {
                            runCatching {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(firstTextGroup.mediaTrackGroup, 0)
                                    )
                                    .build()
                            }
                        }
                    }
                }
            }

            override fun onCues(cueGroup: CueGroup) {
                val joined = cueGroup.cues.mapNotNull { it.text?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                if (joined.isNotBlank()) {
                    rawCueSubtitle = joined
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title = listOfNotNull(
                    mediaMetadata.title?.toString()?.trim(),
                    mediaMetadata.subtitle?.toString()?.trim(),
                    mediaMetadata.description?.toString()?.trim()
                ).filter { it.isNotBlank() }.joinToString(" — ")
                if (title.isNotBlank()) {
                    rawCueSubtitle = title
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val tag = when {
                        videoSize.height >= 2160 -> "4K UHD"
                        videoSize.height >= 1080 -> "1080p FHD"
                        videoSize.height >= 720 -> "720p HD"
                        else -> "${videoSize.height}p SD"
                    }
                    videoResolutionLabel = "${videoSize.width}×${videoSize.height} • $tag"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                } else {
                    lastErrorCodeName = error.errorCodeName
                    errorMessage = AppStrings.tr(
                        lang,
                        "جارٍ إعادة المحاولة بصيغة بديلة (${error.errorCodeName})...",
                        "Nouvelle tentative (${error.errorCodeName})...",
                        "Retrying alternate stream format (${error.errorCodeName})..."
                    )
                }
            }
        }
        player.addListener(listener)
        onDispose {
            ChannelRepository.cancelPendingStreamRequests()
            player.removeListener(listener)
            player.release()
        }
    }

    // Bounded Exponential Backoff Retry (Keyed by channel.stableKey so switching channel cancels old retries)
    LaunchedEffect(channel.stableKey, errorMessage, retryCount) {
        if (errorMessage != null && preferences.autoReconnect) {
            if (retryCount >= preferences.maxReconnectRetries) {
                errorMessage = AppStrings.tr(
                    lang,
                    "تعذّر فتح القناة بعد ${preferences.maxReconnectRetries} محاولات ($lastErrorCodeName)",
                    "Échec de lecture après ${preferences.maxReconnectRetries} tentatives ($lastErrorCodeName)",
                    "Playback failed after ${preferences.maxReconnectRetries} attempts ($lastErrorCodeName)"
                )
                return@LaunchedEffect
            }
            val backoffMs = (750L * (retryCount + 1)).coerceAtMost(4_000L)
            delay(backoffMs)
            retryCount += 1

            if (formatAttemptMode < 2) {
                formatAttemptMode += 1
            } else if (formatCandidateUrls.size > 1) {
                formatAttemptMode = 0
                candidateIndex = (candidateIndex + 1) % formatCandidateUrls.size
            } else {
                formatAttemptMode = 0
                errorMessage = null
                player.seekToDefaultPosition()
                player.prepare()
                player.play()
            }
        }
    }

    LaunchedEffect(numberInputBuffer) {
        if (numberInputBuffer.isNotEmpty()) {
            delay(950L)
            val targetNum = numberInputBuffer.toIntOrNull()
            numberInputBuffer = ""
            if (targetNum != null) {
                val match = allChannels.find { it.number == targetNum }
                if (match != null) {
                    showControls = false
                    onSelectChannel(match)
                }
            }
        }
    }

    LaunchedEffect(showControls, isPlaying, showQuickDrawer, showTracksDialog) {
        if (showControls && isPlaying && !showQuickDrawer && !showTracksDialog && errorMessage == null) {
            delay(3800L)
            showControls = false
        }
    }

    BackHandler {
        when {
            showTracksDialog -> showTracksDialog = false
            showQuickDrawer -> showQuickDrawer = false
            showControls -> showControls = false
            else -> onBack()
        }
    }

    fun switchRelativeChannel(offset: Int) {
        val list = siblingChannels.ifEmpty { allChannels }
        if (list.size <= 1) return
        val idx = list.indexOfFirst { it.stableKey == channel.stableKey }
        if (idx == -1) return
        val nextIdx = (idx + offset + list.size) % list.size
        showControls = false
        onSelectChannel(list[nextIdx])
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootPlayerFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (showQuickDrawer || showTracksDialog) {
                    return@onKeyEvent false
                }
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val code = event.nativeKeyEvent.keyCode
                    if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                        val digit = (code - KeyEvent.KEYCODE_0).toString()
                        if (numberInputBuffer.length < 4) {
                            numberInputBuffer += digit
                        }
                        return@onKeyEvent true
                    }
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            if (!showControls) {
                                drawerSelectedBouquet = channel.group
                                showQuickDrawer = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_INFO -> {
                            showControls = !showControls
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            switchRelativeChannel(-1)
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            switchRelativeChannel(1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showControls) {
                                switchRelativeChannel(-1)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showControls) {
                                switchRelativeChannel(1)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!showControls) {
                                if (channel.contentType != StreamContentType.LIVE_TV) {
                                    player.seekTo((player.currentPosition - 15_000L).coerceAtLeast(0L))
                                } else {
                                    drawerSelectedBouquet = channel.group
                                    showQuickDrawer = true
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!showControls) {
                                if (channel.contentType != StreamContentType.LIVE_TV) {
                                    player.seekTo(player.currentPosition + 15_000L)
                                } else {
                                    drawerSelectedBouquet = channel.group
                                    showQuickDrawer = true
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (player.isPlaying) player.pause() else player.play()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showQuickDrawer) showQuickDrawer = false
                else showControls = !showControls
            }
    ) {
        val isPhoneLandscape = maxHeight < 460.dp

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    this.useController = false
                    this.resizeMode = resizeMode.exoMode
                    this.keepScreenOn = true
                    this.isFocusable = false
                    this.isFocusableInTouchMode = false
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode.exoMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (numberInputBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .background(Color(0xEB120B20), RoundedCornerShape(12.dp))
                    .border(2.dp, BeinHighlightPurple, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "#$numberInputBuffer",
                    color = Color.White,
                    fontSize = if (isPhoneLandscape) 20.sp else 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (showBufferingBadge || errorMessage != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD9120B20), RoundedCornerShape(24.dp))
                    .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(if (isPhoneLandscape) 18.dp else 22.dp)
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White,
                        fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (aiTranslationEnabled) {
            val subtitleDisplay = when {
                aiTranslatedSubtitle.isNotBlank() -> aiTranslatedSubtitle
                else -> "🤖 ${AppStrings.tr(lang, "ترجمة الذكاء الاصطناعي الفورية نشطة", "Traduction IA en direct active", "Live AI Translation Active")} (${aiTargetLang.titleAr})"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (showControls) (if (isPhoneLandscape) 58.dp else 76.dp) else 24.dp,
                        start = 28.dp,
                        end = 28.dp
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE60D0918))
                    .border(1.5.dp, BeinHighlightPurple, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = subtitleDisplay,
                    color = Color.White,
                    fontSize = if (isPhoneLandscape) 14.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xE00A0812),
                                Color(0x220A0812),
                                Color(0x220A0812),
                                Color(0xEB0A0812)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(
                            horizontal = if (isPhoneLandscape) 12.dp else 20.dp,
                            vertical = if (isPhoneLandscape) 6.dp else 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        FocusableCircleButton(
                            onClick = onBack,
                            icon = Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            sizeDp = if (isPhoneLandscape) 36.dp else 44.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        ChannelAvatar(
                            name = channel.name,
                            logoUrl = channel.logo,
                            modifier = Modifier.size(if (isPhoneLandscape) 34.dp else 44.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "#${channel.number}",
                                    color = Color.White,
                                    fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .background(BeinPrimaryPurple, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = channel.group,
                                    color = Color.White,
                                    fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (videoResolutionLabel.isNotBlank()) {
                                    Text(
                                        text = videoResolutionLabel,
                                        color = EmeraldLive,
                                        fontSize = if (isPhoneLandscape) 10.sp else 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusableCircleButton(
                            onClick = { onToggleFavorite(channel) },
                            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                            contentDescription = "المفضلة",
                            sizeDp = if (isPhoneLandscape) 36.dp else 44.dp
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                            FocusableCircleButton(
                                onClick = {
                                    runCatching {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity.enterPictureInPictureMode(params)
                                    }
                                },
                                icon = Icons.Default.ExitToApp,
                                contentDescription = "PiP",
                                sizeDp = if (isPhoneLandscape) 36.dp else 44.dp
                            )
                        }

                        FocusableCircleButton(
                            onClick = {
                                showControls = false
                                drawerSelectedBouquet = channel.group
                                showQuickDrawer = true
                            },
                            icon = Icons.Default.List,
                            contentDescription = "الباقات والقنوات",
                            sizeDp = if (isPhoneLandscape) 36.dp else 44.dp
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (isPhoneLandscape) 14.dp else 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (channel.contentType != StreamContentType.LIVE_TV) {
                        FocusableCircleButton(
                            onClick = { player.seekTo((player.currentPosition - 15_000L).coerceAtLeast(0L)) },
                            icon = Icons.Default.FastRewind,
                            tint = Color.White,
                            contentDescription = "-15s",
                            sizeDp = if (isPhoneLandscape) 38.dp else 48.dp
                        )
                    } else if (siblingChannels.size > 1) {
                        FocusableCircleButton(
                            onClick = { switchRelativeChannel(-1) },
                            icon = Icons.Default.KeyboardArrowRight,
                            tint = Color.White,
                            contentDescription = "السابقة",
                            sizeDp = if (isPhoneLandscape) 38.dp else 48.dp
                        )
                    }

                    var centerPlayFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier
                            .size(if (isPhoneLandscape) 48.dp else 60.dp)
                            .scale(if (centerPlayFocused) 1.12f else 1.0f)
                            .onFocusChanged { centerPlayFocused = it.isFocused }
                            .focusable()
                            .background(if (centerPlayFocused) Color.White else BeinPrimaryPurple, CircleShape)
                            .border(3.dp, if (centerPlayFocused) BeinFocusGlow else Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "تشغيل/إيقاف",
                            tint = if (centerPlayFocused) Color.Black else Color.White,
                            modifier = Modifier.size(if (isPhoneLandscape) 24.dp else 32.dp)
                        )
                    }

                    if (channel.contentType != StreamContentType.LIVE_TV) {
                        FocusableCircleButton(
                            onClick = { player.seekTo(player.currentPosition + 15_000L) },
                            icon = Icons.Default.FastForward,
                            tint = Color.White,
                            contentDescription = "+15s",
                            sizeDp = if (isPhoneLandscape) 38.dp else 48.dp
                        )
                    } else if (siblingChannels.size > 1) {
                        FocusableCircleButton(
                            onClick = { switchRelativeChannel(1) },
                            icon = Icons.Default.KeyboardArrowLeft,
                            tint = Color.White,
                            contentDescription = "التالية",
                            sizeDp = if (isPhoneLandscape) 38.dp else 48.dp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(
                            horizontal = if (isPhoneLandscape) 10.dp else 18.dp,
                            vertical = if (isPhoneLandscape) 6.dp else 12.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusableActionPill(
                            text = "📂 ${AppStrings.tr(lang, "القنوات والباقات", "Chaînes & Bouquets", "Channels & Bouquets")}",
                            isPrimary = true,
                            compact = isPhoneLandscape,
                            onClick = {
                                showControls = false
                                drawerSelectedBouquet = channel.group
                                showQuickDrawer = true
                            }
                        )
                        FocusableActionPill(
                            text = "🤖 AI: ${if (aiTranslationEnabled) "ON ✓" else "OFF"}",
                            isPrimary = aiTranslationEnabled,
                            compact = isPhoneLandscape,
                            onClick = {
                                val next = !aiTranslationEnabled
                                aiTranslationEnabled = next
                                onUpdatePreferences(
                                    preferences.copy(aiTranslationEnabled = next)
                                )
                            }
                        )
                        FocusableActionPill(
                            text = "⚙️ ${AppStrings.tr(lang, "الجودة والصوت والترجمة", "Qualité, Audio & IA", "Quality, Audio & AI")}",
                            isPrimary = false,
                            compact = isPhoneLandscape,
                            onClick = { showTracksDialog = true }
                        )
                        FocusableActionPill(
                            text = "🔄 M3U8/TS (${candidateIndex + 1}/${formatCandidateUrls.size})",
                            isPrimary = false,
                            compact = isPhoneLandscape,
                            onClick = {
                                formatAttemptMode = 0
                                retryCount = 0
                                candidateIndex = (candidateIndex + 1) % formatCandidateUrls.size.coerceAtLeast(1)
                            }
                        )
                        FocusableActionPill(
                            text = resizeMode.titleAr,
                            isPrimary = false,
                            compact = isPhoneLandscape,
                            onClick = {
                                val modes = VideoResizeMode.values()
                                resizeMode = modes[(resizeMode.ordinal + 1) % modes.size]
                            }
                        )
                    }
                }
            }
        }

        // =========================================================================
        // FIXED EXOPLAYER CHANNEL & BOUQUET DRAWER:
        // • Favorites (★ المفضلة) is right at the top of Bouquets!
        // • Clicking (OK/Enter or Touch) on ANY Bouquet NEVER hides the drawer;
        //   instead it smoothly slides D-Pad focus to the FIRST channel of that bouquet!
        // =========================================================================
        if (showQuickDrawer) {
            val bouquetListState = rememberLazyListState()
            val channelListState = rememberLazyListState()

            val activeBouquetIdx = remember(allBouquets, drawerSelectedBouquet) {
                allBouquets.indexOfFirst { it.first == drawerSelectedBouquet }.coerceAtLeast(0)
            }
            val activeChannelIdx = remember(drawerChannels, channel.stableKey) {
                drawerChannels.indexOfFirst { it.stableKey == channel.stableKey }.coerceAtLeast(0)
            }

            LaunchedEffect(Unit) {
                runCatching { bouquetListState.scrollToItem(activeBouquetIdx) }
                runCatching { channelListState.scrollToItem(activeChannelIdx) }
                delay(70L)
                runCatching { activeChannelFocusRequester.requestFocus() }
                    .onFailure { runCatching { firstChannelInBouquetFocusRequester.requestFocus() } }
                    .onFailure { runCatching { activeBouquetFocusRequester.requestFocus() } }
            }

            LaunchedEffect(focusFirstChannelTrigger) {
                if (focusFirstChannelTrigger > 0) {
                    runCatching { channelListState.animateScrollToItem(0) }
                    delay(65L)
                    runCatching { firstChannelInBouquetFocusRequester.requestFocus() }
                }
            }

            Row(
                modifier = Modifier
                    .width(if (isPhoneLandscape) 335.dp else 430.dp)
                    .fillMaxHeight(if (isPhoneLandscape) 0.88f else 0.82f)
                    .align(Alignment.CenterStart)
                    .padding(start = if (isPhoneLandscape) 8.dp else 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF20D0917))
                    .border(2.dp, BeinHighlightPurple, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks inside drawer so background never closes it! */ }
                    .padding(if (isPhoneLandscape) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(if (isPhoneLandscape) 125.dp else 158.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161026))
                        .padding(6.dp)
                ) {
                    Text(
                        text = "📂 ${AppStrings.tr(lang, "الباقات", "Bouquets", "Bouquets")} (${allBouquets.size})",
                        color = Color.White,
                        fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        state = bouquetListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(allBouquets, key = { _, pair -> pair.first }) { idx, (bName, bList) ->
                            val selected = drawerSelectedBouquet == bName
                            var bFocused by remember { mutableStateOf(false) }
                            val bModifier = if (idx == activeBouquetIdx) {
                                Modifier.focusRequester(activeBouquetFocusRequester)
                            } else {
                                Modifier
                            }
                            Row(
                                modifier = bModifier
                                    .fillMaxWidth()
                                    .scale(if (bFocused) 1.04f else 1.0f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        when {
                                            bFocused -> Color.White
                                            selected -> BeinPrimaryPurple
                                            else -> Color(0xFF211838)
                                        }
                                    )
                                    .border(
                                        width = if (bFocused) 2.5.dp else if (selected) 1.5.dp else 0.dp,
                                        color = if (bFocused) BeinFocusGlow else if (selected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(9.dp)
                                    )
                                    .onFocusChanged { bFocused = it.isFocused }
                                    .onKeyEvent { ev ->
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (ev.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_CENTER,
                                                KeyEvent.KEYCODE_ENTER,
                                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    drawerSelectedBouquet = bName
                                                    focusFirstChannelTrigger += 1
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .focusable()
                                    .clickable {
                                        drawerSelectedBouquet = bName
                                        focusFirstChannelTrigger += 1
                                    }
                                    .padding(horizontal = 8.dp, vertical = if (isPhoneLandscape) 6.dp else 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bName,
                                    color = if (bFocused) Color.Black else Color.White,
                                    fontSize = if (isPhoneLandscape) 10.sp else 12.sp,
                                    fontWeight = if (bFocused || selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${bList.size}",
                                    color = if (bFocused) BeinPrimaryPurple else Color(0xFFD8B4FE),
                                    fontSize = if (isPhoneLandscape) 9.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$drawerSelectedBouquet (${drawerChannels.size})",
                            color = Color.White,
                            fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showQuickDrawer = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    if (drawerChannels.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = AppStrings.tr(
                                    lang,
                                    "لا توجد قنوات في هذه الباقة حالياً",
                                    "Aucune chaîne dans ce bouquet",
                                    "No channels in this bouquet"
                                ),
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(drawerChannels, key = { _, it -> it.stableKey }) { idx, item ->
                                val active = item.stableKey == channel.stableKey
                                val isFavItem = favorites.any { it.stableKey == item.stableKey }
                                var rowFocused by remember { mutableStateOf(false) }
                                val chModifier = when {
                                    idx == 0 && idx == activeChannelIdx ->
                                        Modifier
                                            .focusRequester(firstChannelInBouquetFocusRequester)
                                            .focusRequester(activeChannelFocusRequester)
                                    idx == 0 ->
                                        Modifier.focusRequester(firstChannelInBouquetFocusRequester)
                                    idx == activeChannelIdx ->
                                        Modifier.focusRequester(activeChannelFocusRequester)
                                    else -> Modifier
                                }
                                Row(
                                    modifier = chModifier
                                        .fillMaxWidth()
                                        .scale(if (rowFocused) 1.03f else 1.0f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            when {
                                                rowFocused -> Color.White
                                                active -> BeinPrimaryPurple
                                                else -> Color(0xFF1A132B)
                                            }
                                        )
                                        .border(
                                            width = if (rowFocused) 2.5.dp else if (active) 1.5.dp else 0.dp,
                                            color = if (rowFocused) BeinFocusGlow else if (active) Color.White else Color.Transparent,
                                            shape = RoundedCornerShape(9.dp)
                                        )
                                        .onFocusChanged { rowFocused = it.isFocused }
                                        .onKeyEvent { ev ->
                                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                when (ev.nativeKeyEvent.keyCode) {
                                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                                    KeyEvent.KEYCODE_ENTER -> {
                                                        showControls = false
                                                        showQuickDrawer = false
                                                        onSelectChannel(item)
                                                        true
                                                    }
                                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                        runCatching { activeBouquetFocusRequester.requestFocus() }
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .focusable()
                                        .clickable {
                                            showControls = false
                                            showQuickDrawer = false
                                            onSelectChannel(item)
                                        }
                                        .padding(horizontal = 8.dp, vertical = if (isPhoneLandscape) 5.dp else 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${item.number}",
                                        color = if (rowFocused) BeinPrimaryPurple else Color(0xFFD8B4FE),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.width(30.dp)
                                    )
                                    ChannelAvatar(
                                        name = item.name,
                                        logoUrl = item.logo,
                                        modifier = Modifier.size(if (isPhoneLandscape) 22.dp else 26.dp),
                                        cornerRadius = 6.dp,
                                        innerPadding = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = item.name,
                                        color = if (rowFocused) Color.Black else Color.White,
                                        fontSize = if (isPhoneLandscape) 11.sp else 13.sp,
                                        fontWeight = if (active || rowFocused) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isFavItem) {
                                        Text(
                                            text = "★",
                                            color = if (rowFocused) BeinPrimaryPurple else Color(0xFFFF4D6D),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showTracksDialog) {
            InteractiveTrackAndSpeedModal(
                player = player,
                tracks = currentTracksSnapshot,
                currentSpeed = playbackSpeed,
                aiTranslationEnabled = aiTranslationEnabled,
                aiTargetLang = aiTargetLang,
                aiVoiceListenEnabled = aiVoiceListenEnabled,
                aiTtsVoiceOver = aiTtsVoiceOver,
                onUpdateAiSettings = { enabled, langTarget, voiceListen, tts ->
                    aiTranslationEnabled = enabled
                    aiTargetLang = langTarget
                    aiVoiceListenEnabled = voiceListen
                    aiTtsVoiceOver = tts
                    onUpdatePreferences(
                        preferences.copy(
                            aiTranslationEnabled = enabled,
                            aiTargetLanguage = langTarget,
                            aiVoiceListenEnabled = voiceListen,
                            aiTtsVoiceOver = tts
                        )
                    )
                },
                onSelectSpeed = { playbackSpeed = it },
                compact = isPhoneLandscape,
                onDismiss = { showTracksDialog = false }
            )
        }
    }
}

@Composable
private fun FocusableCircleButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
    sizeDp: androidx.compose.ui.unit.Dp = 44.dp
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(sizeDp)
            .scale(if (focused) 1.14f else 1.0f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(if (focused) Color.White else Color(0xCC1C1230), CircleShape)
            .border(
                width = if (focused) 2.8.dp else 1.5.dp,
                color = if (focused) BeinFocusGlow else BeinHighlightPurple.copy(alpha = 0.7f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.Black else tint,
            modifier = Modifier.size(sizeDp * 0.52f)
        )
    }
}

@Composable
private fun FocusableActionPill(
    text: String,
    isPrimary: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .scale(if (focused) 1.08f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
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
            .background(
                when {
                    focused -> Color.White
                    isPrimary -> BeinPrimaryPurple
                    else -> Color(0xD91A122B)
                }
            )
            .border(
                width = if (focused) 2.8.dp else 1.2.dp,
                color = if (focused) BeinFocusGlow else BeinHighlightPurple.copy(alpha = 0.75f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun InteractiveTrackAndSpeedModal(
    player: ExoPlayer,
    tracks: Tracks,
    currentSpeed: PlaybackSpeedOption,
    aiTranslationEnabled: Boolean,
    aiTargetLang: AiTargetLanguage,
    aiVoiceListenEnabled: Boolean,
    aiTtsVoiceOver: Boolean,
    onUpdateAiSettings: (Boolean, AiTargetLanguage, Boolean, Boolean) -> Unit,
    onSelectSpeed: (PlaybackSpeedOption) -> Unit,
    compact: Boolean,
    onDismiss: () -> Unit
) {
    var selectedSection by remember { mutableStateOf(0) }
    val firstItemFocus = remember { FocusRequester() }

    val videoOptions = remember(tracks) {
        val list = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { gIndex, group ->
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (tIndex in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIndex)
                    if (fmt.height > 0) {
                        val badge = when {
                            fmt.height >= 2160 -> "4K UHD"
                            fmt.height >= 1080 -> "Full HD 1080p"
                            fmt.height >= 720 -> "HD 720p"
                            else -> "SD ${fmt.height}p"
                        }
                        val br = if (fmt.bitrate > 0) "${fmt.bitrate / 1000} kbps" else "Adaptive"
                        val fps = if (fmt.frameRate > 0) " • ${fmt.frameRate.toInt()}fps" else ""
                        list.add(
                            TrackOption(
                                title = "$badge (${fmt.width}×${fmt.height})",
                                subtitle = "معدل البث: $br$fps",
                                groupIndex = gIndex,
                                trackIndex = tIndex,
                                isSelected = group.isTrackSelected(tIndex)
                            )
                        )
                    }
                }
            }
        }
        list.sortedByDescending { it.title }
    }

    val audioOptions = remember(tracks) {
        val list = mutableListOf<TrackOption>()
        var commentatorNum = 1
        tracks.groups.forEachIndexed { gIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIndex in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIndex)
                    val rawLabel = fmt.label?.takeIf { it.isNotBlank() }
                    val rawLang = fmt.language?.takeIf { it.isNotBlank() && it != "und" }
                    val title = when {
                        rawLabel != null && rawLang != null -> "معلق / صوت $commentatorNum: $rawLabel ($rawLang)"
                        rawLabel != null -> "معلق / صوت $commentatorNum: $rawLabel"
                        rawLang != null -> "معلق / مسار صوتي $commentatorNum ($rawLang)"
                        else -> "المعلق الصوتي / المسار رقم $commentatorNum"
                    }
                    val channelsDesc = if (fmt.channelCount >= 6) "صوت محيطي 5.1" else "ستيريو واضح"
                    val br = if (fmt.bitrate > 0) " • ${fmt.bitrate / 1000} kbps" else ""
                    list.add(
                        TrackOption(
                            title = title,
                            subtitle = "$channelsDesc$br",
                            groupIndex = gIndex,
                            trackIndex = tIndex,
                            isSelected = group.isTrackSelected(tIndex)
                        )
                    )
                    commentatorNum++
                }
            }
        }
        list
    }

    val hasVideoOverride = remember(player.trackSelectionParameters) {
        player.trackSelectionParameters.overrides.keys.any { it.type == C.TRACK_TYPE_VIDEO }
    }

    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { firstItemFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = if (compact) 560.dp else 680.dp)
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(if (compact) 0.90f else 0.84f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF130E21))
                    .border(2.dp, BeinHighlightPurple, RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(if (compact) 12.dp else 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "الجودة، المعلق الصوتي، وترجمة الذكاء الاصطناعي 🤖",
                            color = Color.White,
                            fontSize = if (compact) 13.sp else 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "تنقل بأسهم الريموت (▲▼◄►) واضغط (OK) للاختيار الفوري",
                            color = Color.LightGray,
                            fontSize = if (compact) 9.sp else 11.sp
                        )
                    }
                    FocusableCircleButton(
                        onClick = onDismiss,
                        icon = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        sizeDp = if (compact) 32.dp else 38.dp
                    )
                }

                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InteractiveModalTabButton(
                        title = "📺 الجودة (${videoOptions.size + 1})",
                        selected = selectedSection == 0,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(firstItemFocus),
                        onClick = { selectedSection = 0 }
                    )
                    InteractiveModalTabButton(
                        title = "🎙 المعلق (${audioOptions.size.coerceAtLeast(1)})",
                        selected = selectedSection == 1,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedSection = 1 }
                    )
                    InteractiveModalTabButton(
                        title = "🤖 ترجمة AI",
                        selected = selectedSection == 2,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedSection = 2 }
                    )
                    InteractiveModalTabButton(
                        title = "⚡ السرعة",
                        selected = selectedSection == 3,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedSection = 3 }
                    )
                }

                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (selectedSection) {
                        0 -> {
                            item {
                                InteractiveTrackCard(
                                    title = "تلقائي متكيّف (Auto Adaptive HD) — موصى به",
                                    subtitle = "يختار أعلى جودة مستقرة حسب سرعة الاتصال",
                                    isSelected = !hasVideoOverride,
                                    compact = compact,
                                    onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                            .build()
                                        onDismiss()
                                    }
                                )
                            }

                            itemsIndexed(videoOptions) { _, opt ->
                                InteractiveTrackCard(
                                    title = opt.title,
                                    subtitle = opt.subtitle,
                                    isSelected = hasVideoOverride && opt.isSelected,
                                    compact = compact,
                                    onClick = {
                                        val group = tracks.groups[opt.groupIndex].mediaTrackGroup
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(
                                                TrackSelectionOverride(group, opt.trackIndex)
                                            )
                                            .build()
                                        onDismiss()
                                    }
                                )
                            }
                        }
                        1 -> {
                            if (audioOptions.isEmpty()) {
                                item {
                                    InteractiveTrackCard(
                                        title = "المسار الصوتي الافتراضي للمصدر",
                                        subtitle = "هذه القناة تبث بمسار تعليق صوتي موحّد",
                                        isSelected = true,
                                        compact = compact,
                                        onClick = onDismiss
                                    )
                                }
                            } else {
                                itemsIndexed(audioOptions) { _, opt ->
                                    InteractiveTrackCard(
                                        title = opt.title,
                                        subtitle = opt.subtitle,
                                        isSelected = opt.isSelected,
                                        compact = compact,
                                        onClick = {
                                            val group = tracks.groups[opt.groupIndex].mediaTrackGroup
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setOverrideForType(
                                                    TrackSelectionOverride(group, opt.trackIndex)
                                                )
                                                .build()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                        2 -> {
                            item {
                                InteractiveTrackCard(
                                    title = if (aiTranslationEnabled) "🤖 ترجمة الذكاء الاصطناعي الفورية: مفعّلة ✓" else "🤖 تفعيل ترجمة الذكاء الاصطناعي الفورية",
                                    subtitle = "ترجمة النصوص والصوت المباشر والسياق فوراً على الشاشة",
                                    isSelected = aiTranslationEnabled,
                                    compact = compact,
                                    onClick = {
                                        onUpdateAiSettings(!aiTranslationEnabled, aiTargetLang, aiVoiceListenEnabled, aiTtsVoiceOver)
                                    }
                                )
                            }
                            item {
                                InteractiveTrackCard(
                                    title = "🎙 التقاط وترجمة الصوت المباشر بالذكاء الاصطناعي (Voice AI)",
                                    subtitle = "يستمع لصوت القناة أو الفيلم ويحوله لترجمة فورية",
                                    isSelected = aiVoiceListenEnabled,
                                    compact = compact,
                                    onClick = {
                                        onUpdateAiSettings(true, aiTargetLang, !aiVoiceListenEnabled, aiTtsVoiceOver)
                                    }
                                )
                            }
                            item {
                                InteractiveTrackCard(
                                    title = "🔊 نطق الترجمة صوتياً (AI Voice-Over TTS)",
                                    subtitle = "قراءة الترجمة بصوت واضح أثناء المشاهدة",
                                    isSelected = aiTtsVoiceOver,
                                    compact = compact,
                                    onClick = {
                                        onUpdateAiSettings(true, aiTargetLang, aiVoiceListenEnabled, !aiTtsVoiceOver)
                                    }
                                )
                            }
                            itemsIndexed(AiTargetLanguage.values().toList()) { _, langOpt ->
                                InteractiveTrackCard(
                                    title = "لغة الترجمة: ${langOpt.titleAr}",
                                    subtitle = "ترجمة فورية بالذكاء الاصطناعي إلى ${langOpt.titleAr}",
                                    isSelected = aiTargetLang == langOpt,
                                    compact = compact,
                                    onClick = {
                                        onUpdateAiSettings(true, langOpt, aiVoiceListenEnabled, aiTtsVoiceOver)
                                    }
                                )
                            }
                        }
                        else -> {
                            itemsIndexed(PlaybackSpeedOption.values().toList()) { _, spd ->
                                InteractiveTrackCard(
                                    title = "سرعة التشغيل: ${spd.labelAr}",
                                    subtitle = if (spd == PlaybackSpeedOption.NORMAL) {
                                        "السرعة المثالية لاستقرار القنوات المباشرة"
                                    } else {
                                        "تغيير سرعة عرض الفيديو والصوت"
                                    },
                                    isSelected = currentSpeed == spd,
                                    compact = compact,
                                    onClick = {
                                        onSelectSpeed(spd)
                                        onDismiss()
                                    }
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
private fun InteractiveModalTabButton(
    title: String,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(if (compact) 34.dp else 42.dp)
            .scale(if (focused) 1.05f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
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
            .background(
                when {
                    focused -> Color.White
                    selected -> BeinPrimaryPurple
                    else -> Color(0xFF1F1633)
                }
            )
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) BeinFocusGlow else if (selected) Color.White else Color(0xFF352654),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (focused) Color.Black else Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 9.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InteractiveTrackCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (focused) 1.02f else 1.0f)
            .clip(RoundedCornerShape(12.dp))
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
            .background(
                when {
                    focused -> Color.White
                    isSelected -> BeinPrimaryPurple.copy(alpha = 0.45f)
                    else -> Color(0xFF1B142E)
                }
            )
            .border(
                width = if (focused) 2.8.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    focused -> BeinFocusGlow
                    isSelected -> Color.White
                    else -> Color(0xFF2E224A)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 8.dp else 11.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (focused) Color.Black else Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 11.sp else 13.sp
            )
            Text(
                text = subtitle,
                color = if (focused) Color.DarkGray else Color.LightGray,
                fontSize = if (compact) 9.sp else 11.sp
            )
        }

        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 24.dp)
                .clip(CircleShape)
                .background(if (isSelected) EmeraldLive else Color(0xFF281D40))
                .border(1.dp, if (focused) Color.Black else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "محدد",
                    tint = Color.Black,
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp)
                )
            }
        }
    }
}
