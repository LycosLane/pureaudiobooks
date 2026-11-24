package com.lycoslane.pureaudiobooks.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lycoslane.pureaudiobooks.core.network.BoundedInputStream
import com.lycoslane.pureaudiobooks.core.network.NetworkSecurity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object BookCoverCache {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun invalidate(vararg urls: String?) {
        var removed = false
        urls.forEach { url ->
            if (!url.isNullOrBlank()) {
                NativeBookImageCache.invalidate(url)
                removed = true
            }
        }
        if (removed) {
            _version.value += 1
        }
    }
}

private object NativeBookImageCache {
    private const val TAG = "BookCoverImage"
    private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()

    private val memoryCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun getCached(url: String, maxDecodeSizePx: Int = 512): ImageBitmap? =
        if (url.isBlank()) null else memoryCache.get(cacheKey(url, maxDecodeSizePx))

    fun invalidate(url: String) {
        if (url.isNotBlank()) {
            memoryCache.remove(cacheKey(url, 512))
            memoryCache.remove(cacheKey(url, 1024))
        }
    }

    private fun cacheKey(url: String, maxDecodeSizePx: Int): String = "$url@$maxDecodeSizePx"

    suspend fun load(context: Context, url: String, maxDecodeSizePx: Int = 512): ImageBitmap? {
        if (url.isBlank()) return null
        val key = cacheKey(url, maxDecodeSizePx)
        memoryCache.get(key)?.let { return it }

        val deferred = inFlight.computeIfAbsent(key) {
            cacheScope.async {
                try {
                    loadInternal(context.applicationContext, url, maxDecodeSizePx)
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return deferred.await()
    }

    private fun loadInternal(appContext: Context, url: String, maxDecodeSizePx: Int): ImageBitmap? {
        val key = cacheKey(url, maxDecodeSizePx)
        memoryCache.get(key)?.let { return it }

        if (url.startsWith("content://")) {
            val uri = Uri.parse(url)
            val bitmap = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appContext.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (opts.outWidth <= 0 || opts.outHeight <= 0) return@runCatching null
                opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, maxDecodeSizePx, maxDecodeSizePx)
                opts.inJustDecodeBounds = false
                appContext.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.onFailure {
                Log.w(TAG, "Failed to decode cover from content URI $url", it)
            }.getOrNull() ?: return null
            val imageBitmap = bitmap.asImageBitmap()
            memoryCache.put(key, imageBitmap)
            return imageBitmap
        }

        if (url.startsWith("/") || url.startsWith("file://")) {
            val path = if (url.startsWith("file://")) Uri.parse(url).path ?: "" else url
            val file = File(path)
            val allowedRoots = listOf(
                File(appContext.filesDir, "local_covers").canonicalPath,
                appContext.cacheDir.canonicalPath
            )
            val allowed = runCatching {
                val canonical = file.canonicalPath
                allowedRoots.any { canonical.startsWith("$it/") }
            }.getOrDefault(false)
            if (!allowed || !file.exists()) return null
            val bitmap = decodeSampledFile(file, maxDecodeSizePx) ?: return null
            val imageBitmap = bitmap.asImageBitmap()
            memoryCache.put(key, imageBitmap)
            return imageBitmap
        }

        if (!NetworkSecurity.isValidHttpUrl(url)) return null
        val cacheDir = File(appContext.cacheDir, "artwork_preview").apply { mkdirs() }
        val file = File(cacheDir, sha256(url))
        val bitmap = if (file.exists() && file.length() > 0L) {
            decodeSampledFile(file, maxDecodeSizePx)
        } else {
            downloadPreview(url, file, maxDecodeSizePx)
        } ?: return null
        val imageBitmap = bitmap.asImageBitmap()
        memoryCache.put(key, imageBitmap)
        return imageBitmap
    }

    private fun downloadPreview(url: String, target: File, maxDecodeSizePx: Int): android.graphics.Bitmap? {
        var conn: HttpURLConnection? = null
        var tempFile: File? = null
        return try {
            conn = NetworkSecurity.openValidatedConnection(url, connectTimeoutMs = 8_000, readTimeoutMs = 10_000)
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "Cover download HTTP ${conn.responseCode} for $url")
                return null
            }
            tempFile = File.createTempFile("art_", ".tmp", target.parentFile)
            BoundedInputStream(conn.inputStream, MAX_IMAGE_BYTES).use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            }
            if (!tempFile.renameTo(target)) {
                tempFile.copyTo(target, overwrite = true)
                tempFile.delete()
            }
            tempFile = null
            decodeSampledFile(target, maxDecodeSizePx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download cover preview $url", e)
            tempFile?.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun decodeSampledFile(file: File, maxDecodeSizePx: Int): android.graphics.Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, maxDecodeSizePx, maxDecodeSizePx)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun BookCoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDecodeSizePx: Int = 512
) {
    val context = LocalContext.current
    val cacheVersion by BookCoverCache.version.collectAsState()
    var imageBitmap by remember(url, maxDecodeSizePx, cacheVersion) {
        mutableStateOf(NativeBookImageCache.getCached(url, maxDecodeSizePx))
    }

    LaunchedEffect(url, maxDecodeSizePx, cacheVersion) {
        imageBitmap = NativeBookImageCache.load(context, url, maxDecodeSizePx)
    }

    val bmp = imageBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
