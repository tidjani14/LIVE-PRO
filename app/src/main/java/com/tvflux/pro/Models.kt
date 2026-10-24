package com.tvflux.pro

import androidx.compose.ui.unit.LayoutDirection
import java.util.UUID

const val APP_BRAND_NAME = "LIVE PRO"
val APP_LOGO_URL: String = listOf("https://", "e.top4top.io", "/p_3941ca8xe1.jpg").joinToString("")
const val ALI_SAT_PROVIDER_NAME = "علي سات (Ali Sat)"
const val ALI_SAT_PHONE_NUMBER = "00213697372480"
const val FAVORITES_BOUQUET_KEY = "__FAV_BOUQUET__"

enum class AppUiLanguage(
    val code: String,
    val nativeLabel: String,
    val layoutDirection: LayoutDirection
) {
    AR("ar", "العربية", LayoutDirection.Rtl),
    FR("fr", "Français", LayoutDirection.Ltr),
    EN("en", "English", LayoutDirection.Ltr)
}

object AppStrings {
    fun tr(lang: AppUiLanguage, ar: String, fr: String, en: String): String = when (lang) {
        AppUiLanguage.AR -> ar
        AppUiLanguage.FR -> fr
        AppUiLanguage.EN -> en
    }

    fun favoritesLabel(lang: AppUiLanguage): String =
        tr(lang, "★ المفضلة", "★ Favoris", "★ Favorites")

    fun allBouquetsLabel(lang: AppUiLanguage): String =
        tr(lang, "الكل", "Tout", "All")
}

enum class MainTab(
    val titleAr: String,
    val titleFr: String,
    val titleEn: String
) {
    LIVE("القنوات", "Direct TV", "Live TV"),
    BOUQUETS("الباقات", "Bouquets", "Bouquets"),
    MOVIES("الأفلام", "Films", "Movies"),
    SERIES("المسلسلات", "Séries", "Series"),
    XTREAM("القوائم / XTREAM", "Listes XTREAM", "XTREAM Lists"),
    FAVORITES("المفضلة", "Favoris", "Favorites"),
    ACTIVATION("التفعيل", "Activation", "Activation"),
    SETTINGS("الإعدادات", "Paramètres", "Settings");

    fun label(lang: AppUiLanguage): String = AppStrings.tr(lang, titleAr, titleFr, titleEn)
}

enum class StreamContentType {
    LIVE_TV,
    MOVIE,
    SERIES
}

enum class PlaylistDisplayMode(
    val titleAr: String,
    val titleFr: String,
    val titleEn: String
) {
    MERGE_ALL(
        "دمج جميع القوائم معاً في قائمة موحدة",
        "Fusionner toutes les listes ensemble",
        "Merge all playlists together"
    ),
    SEPARATE_LISTS(
        "فصل كل قائمة على حدة (المستخدم يختار القائمة)",
        "Séparer chaque liste individuellement",
        "Separate playlists (Switch per playlist)"
    );

    fun label(lang: AppUiLanguage): String = AppStrings.tr(lang, titleAr, titleFr, titleEn)
}

enum class BufferProfile(
    val titleAr: String,
    val descAr: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val targetBufferBytes: Int
) {
    XPOLA_ZERO_LAG(
        "ثبات عالٍ — beIN Pro (افتراضي)",
        "تخزين مسبق متوازن (حتى 32MB) لمنع التقطيع مع فتح سريع للقنوات",
        22_000, 60_000, 1_000, 2_500, 32 * 1024 * 1024
    ),
    BALANCED(
        "متوازن ذكي (Balanced HD)",
        "توازن بين سرعة فتح القناة واستقرار البث المباشر (24MB)",
        16_000, 45_000, 850, 2_000, 24 * 1024 * 1024
    ),
    ULTRA_STABLE(
        "تخزين موسّع للإنترنت المتذبذب (Max Cushion)",
        "تخزين مسبق حتى 90 ثانية (بحد أقصى 48MB) لتعويض تذبذب الشبكة",
        32_000, 90_000, 1_500, 3_500, 48 * 1024 * 1024
    ),
    TURBO_INSTANT(
        "فتح فوري للقنوات (Fast Zapping)",
        "مخصص للإنترنت المستقر للتنقل السريع بين القنوات (16MB)",
        10_000, 28_000, 600, 1_400, 16 * 1024 * 1024
    )
}

enum class PlaybackSpeedOption(val labelAr: String, val speed: Float) {
    NORMAL("1.0x طبيعي (موصى به للبث الحي)", 1.0f),
    FAST_110("1.10x سريع خفيف", 1.10f),
    FAST_125("1.25x أسرع", 1.25f),
    FAST_150("1.50x فائق", 1.5f),
    FAST_200("2.00x مضاعف", 2.0f)
}

enum class VideoResizeMode(val titleAr: String, val exoMode: Int) {
    FILL("ملء الشاشة بالكامل (Fill)", 3),
    FIT("ملائم تلقائي (Fit)", 0),
    ZOOM("تكبير سينمائي (Zoom)", 4),
    FIXED_WIDTH("عرض ثابت", 1),
    FIXED_HEIGHT("ارتفاع ثابت", 2)
}

enum class AiTargetLanguage(val code: String, val titleAr: String) {
    ARABIC("ar", "العربية (Arabic)"),
    FRENCH("fr", "الفرنسية (Français)"),
    ENGLISH("en", "الإنجليزية (English)"),
    SPANISH("es", "الإسبانية (Español)"),
    TURKISH("tr", "التركية (Türkçe)")
}

data class SeriesEpisode(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val url: String,
    val durationLabel: String = ""
)

data class TvChannel(
    val id: Int,
    val number: Int,
    val name: String,
    val url: String,
    val group: String = "عام",
    val logo: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val cookie: String? = null,
    val contentType: StreamContentType = StreamContentType.LIVE_TV,
    val rating: String = "",
    val releaseYear: String = "",
    val plot: String = "",
    val seriesId: Int = 0,
    val episodes: List<SeriesEpisode> = emptyList(),
    val playlistId: String = "PORTAL_MAIN",
    val playlistName: String = "الرئيسية",
    val streamId: Int = 0,
    val streamBaseUrl: String = "",
    val streamUsername: String = "",
    val streamPassword: String = ""
) {
    val stableKey: String
        get() = "${playlistId}_${contentType.name}_${id}_${number}"

    /**
     * Builds a clean, bounded list of supported stream URLs:
     * 1. Primary URL is ALWAYS tested first.
     * 2. Official Xtream .m3u8 and .ts endpoints (when streamId & server credentials exist).
     * 3. Direct .m3u8 <-> .ts sibling format on the same protocol (without blind HTTP/HTTPS fabrication).
     */
    fun buildFormatCandidateUrls(): List<String> {
        val candidates = LinkedHashSet<String>()
        val primary = url.trim()
        if (primary.isNotBlank()) {
            candidates.add(primary)
        }

        if (contentType == StreamContentType.LIVE_TV && streamId > 0 &&
            streamBaseUrl.isNotBlank() && streamUsername.isNotBlank() && streamPassword.isNotBlank()
        ) {
            val base = streamBaseUrl.removeSuffix("/")
            candidates.add("$base/live/$streamUsername/$streamPassword/$streamId.m3u8")
            candidates.add("$base/live/$streamUsername/$streamPassword/$streamId.ts")
            candidates.add("$base/$streamUsername/$streamPassword/$streamId")
        }

        val baseWithoutQuery = primary.substringBefore("?")
        val querySuffix = if (primary.contains("?")) "?" + primary.substringAfter("?") else ""

        when {
            baseWithoutQuery.endsWith(".ts", ignoreCase = true) -> {
                val prefix = baseWithoutQuery.dropLast(3)
                candidates.add("$prefix.m3u8$querySuffix")
            }
            baseWithoutQuery.endsWith(".m3u8", ignoreCase = true) -> {
                val prefix = baseWithoutQuery.dropLast(5)
                candidates.add("$prefix.ts$querySuffix")
            }
            contentType == StreamContentType.LIVE_TV &&
                !baseWithoutQuery.endsWith(".mp4", ignoreCase = true) &&
                !baseWithoutQuery.endsWith(".mkv", ignoreCase = true) -> {
                candidates.add("$baseWithoutQuery.m3u8$querySuffix")
                candidates.add("$baseWithoutQuery.ts$querySuffix")
            }
        }

        return candidates.filter { it.isNotBlank() }.take(4)
    }
}

data class XtreamAccountConfig(
    val id: String = UUID.randomUUID().toString().take(8),
    val serverName: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val enabled: Boolean = true,
    val isRemoteFromPortal: Boolean = false,
    val orderIndex: Int = 0,
    val statusNote: String = "جاهز للمزامنة",
    val liveCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val expDate: String = ""
) {
    val isConfigured: Boolean
        get() = (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) || m3uUrl.isNotBlank()
}

data class XtreamFullSyncResult(
    val account: XtreamAccountConfig,
    val liveChannels: List<TvChannel>,
    val movies: List<TvChannel>,
    val series: List<TvChannel>
)

data class BouquetSummary(
    val name: String,
    val count: Int,
    val sampleLogos: List<String> = emptyList(),
    val isFavoritesSpecial: Boolean = false
)

data class SubscriptionInfo(
    val active: Boolean = false,
    val isExpired: Boolean = false,
    val reason: String = "",
    val expireDate: String = "",
    val daysLeft: Int = 0,
    val deviceName: String = "",
    val message: String = ""
)

data class ApiSyncResult(
    val channels: List<TvChannel>,
    val movies: List<TvChannel> = emptyList(),
    val series: List<TvChannel> = emptyList(),
    val subscription: SubscriptionInfo,
    val remoteXtreamLists: List<XtreamAccountConfig> = emptyList()
)

data class CloudflareTunnelStatus(
    val connected: Boolean = false,
    val edgeColo: String = "WARP",
    val clientIp: String = "",
    val latencyMs: Long = 0L,
    val systemVpnActive: Boolean = false,
    val statusText: String = "جارٍ التحقق من نفق الـ VPN الإجباري..."
)

data class PlayerPreferences(
    val appLanguage: AppUiLanguage = AppUiLanguage.AR,
    val playlistDisplayMode: PlaylistDisplayMode = PlaylistDisplayMode.MERGE_ALL,
    val activePlaylistFilterId: String = "ALL",
    val playlistOrderIds: List<String> = emptyList(),
    val bufferProfile: BufferProfile = BufferProfile.XPOLA_ZERO_LAG,
    val resizeMode: VideoResizeMode = VideoResizeMode.FILL,
    val autoReconnect: Boolean = true,
    val maxReconnectRetries: Int = 6,
    val preferSoftwareDecoder: Boolean = false,
    val useGridLayout: Boolean = true,
    val showBouquetSidebar: Boolean = true,
    val customBouquetOrder: List<String> = emptyList(),
    val hiddenBouquets: Set<String> = emptySet(),
    val customChannelOrder: List<String> = emptyList(),
    val aiTranslationEnabled: Boolean = true,
    val aiTargetLanguage: AiTargetLanguage = AiTargetLanguage.ARABIC,
    val aiVoiceListenEnabled: Boolean = true,
    val aiTtsVoiceOver: Boolean = false
)
