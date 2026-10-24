package com.tvflux.pro

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object FluxBitmapCache {
    private val memoryCache = LruCache<String, ImageBitmap>(140)
    // Limit concurrent logo downloads so scrolling through hundreds of channels never spikes RAM or network
    private val downloadSemaphore = Semaphore(4)

    fun get(url: String): ImageBitmap? = synchronized(memoryCache) { memoryCache.get(url) }

    fun put(url: String, bmp: ImageBitmap) = synchronized(memoryCache) {
        memoryCache.put(url, bmp)
    }

    fun getCacheSizeMb(context: Context): String {
        val thumbDir = File(context.cacheDir, "flux_thumbs")
        val bytes = thumbDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    fun clear(context: Context) {
        synchronized(memoryCache) { memoryCache.evictAll() }
        runCatching {
            File(context.cacheDir, "flux_thumbs").deleteRecursively()
        }
    }

    suspend fun load(context: Context, url: String, maxDimensionPx: Int = 220): ImageBitmap? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        get(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            downloadSemaphore.withPermit {
                get(url)?.let { return@withPermit it }
                try {
                    val cacheDir = File(context.cacheDir, "flux_thumbs").apply { mkdirs() }
                    val diskFile = File(cacheDir, sha256(url))

                    val bytes: ByteArray = if (diskFile.exists() && diskFile.length() > 64L) {
                        diskFile.readBytes()
                    } else {
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Android; LIVE-PRO/5.1)")
                            .header("Accept", "image/*,*/*;q=0.8")
                            .build()
                        ChannelRepository.httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@withPermit null
                            val b = resp.body?.bytes() ?: return@withPermit null
                            if (b.size in 64..(1536 * 1024)) {
                                runCatching { diskFile.writeBytes(b) }
                            }
                            b
                        }
                    }

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withPermit null

                    var sampleSize = 1
                    while (bounds.outWidth / sampleSize > maxDimensionPx ||
                        bounds.outHeight / sampleSize > maxDimensionPx
                    ) {
                        sampleSize *= 2
                    }

                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                        ?: return@withPermit null
                    val imgBmp = bmp.asImageBitmap()
                    put(url, imgBmp)
                    imgBmp
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun BrandLogoBadge(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf(FluxBitmapCache.get(APP_LOGO_URL)) }

    LaunchedEffect(Unit) {
        if (bitmap == null) {
            bitmap = FluxBitmapCache.load(context, APP_LOGO_URL, maxDimensionPx = 280)
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF150E24))
            .border(1.5.dp, BeinHighlightPurple.copy(alpha = 0.85f), shape),
        contentAlignment = Alignment.Center
    ) {
        val currentBmp = bitmap
        if (currentBmp != null) {
            Image(
                bitmap = currentBmp,
                contentDescription = APP_BRAND_NAME,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = APP_BRAND_NAME,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ChannelAvatar(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    contentScale: ContentScale = ContentScale.Fit,
    innerPadding: Dp = 5.dp
) {
    val context = LocalContext.current
    var bitmap by remember(logoUrl) {
        mutableStateOf(logoUrl?.let { FluxBitmapCache.get(it) })
    }

    LaunchedEffect(logoUrl) {
        if (bitmap == null && !logoUrl.isNullOrBlank()) {
            bitmap = FluxBitmapCache.load(context, logoUrl)
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val beinAvatarBrush = remember {
        Brush.linearGradient(
            listOf(Color(0xFF281545), Color(0xFF120B20))
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(beinAvatarBrush),
        contentAlignment = Alignment.Center
    ) {
        val currentBmp = bitmap
        if (currentBmp != null) {
            Image(
                bitmap = currentBmp,
                contentDescription = name,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            val initials = remember(name) {
                val joined = name.trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.take(1).uppercase() }
                if (joined.isNotBlank()) joined else "TV"
            }
            Text(
                text = initials,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
