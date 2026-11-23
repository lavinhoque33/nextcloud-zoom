/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.ui.preview.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

internal class PdfPageRenderer private constructor(private val renderer: PdfRenderer) : Closeable {
    private val cache = object : LruCache<Long, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun readPageAspectRatios(): DoubleArray {
        val context = currentCoroutineContext()
        return DoubleArray(renderer.pageCount) { index ->
            context.ensureActive()
            withPage(index) { page ->
                val width = page.width
                val height = page.height
                if (width <= 0 || height <= 0) throw IOException("Invalid PDF page dimensions")
                height.toDouble() / width
            }
        }
    }

    fun cachedPage(index: Int, width: Int): Bitmap? = cache[cacheKey(index, width)]

    suspend fun renderPage(index: Int, width: Int, height: Int): Bitmap = withContext(dispatcher) {
        val key = cacheKey(index, width)
        cache[key]?.let { return@withContext it }
        val bitmap = withPage(index) { page ->
            ensureActive()
            val scale = rasterScale(width, height)
            val rasterWidth = (width * scale).toInt().coerceAtLeast(1)
            val rasterHeight = (height * scale).toInt().coerceAtLeast(1)
            createBitmap(rasterWidth, rasterHeight).apply {
                eraseColor(Color.WHITE)
                ensureActive()
                page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        cache.put(key, bitmap)
        bitmap
    }

    private inline fun <T> withPage(index: Int, action: (PdfRenderer.Page) -> T): T = try {
        renderer.openPage(index).use(action)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        throw IOException("Unable to read PDF page ${index + 1}", e)
    }

    override fun close() {
        // Evicted bitmaps may still be in use by a view; don't recycle them.
        cache.evictAll()
        renderer.close()
    }

    companion object {
        private const val CACHE_SIZE_BYTES = 24 * 1024 * 1024
        private const val MAX_BITMAP_EDGE = 4096
        private const val MAX_BITMAP_PIXELS = 4 * 1024 * 1024

        // PdfRenderer allows only one open page at a time.
        private val dispatcher = Dispatchers.IO.limitedParallelism(1)

        suspend fun withDocument(path: String, block: suspend (PdfPageRenderer) -> Unit) = withContext(dispatcher) {
            // The adapter's render jobs are children of this scope, so they finish before close.
            open(path).use { block(it) }
        }

        private fun cacheKey(index: Int, width: Int): Long = (width.toLong() shl Int.SIZE_BITS) or index.toLong()

        private fun rasterScale(width: Int, height: Int): Double {
            val edge = maxOf(width, height)
            val pixels = width.toLong() * height
            val edgeScale = if (edge > MAX_BITMAP_EDGE) MAX_BITMAP_EDGE.toDouble() / edge else 1.0
            val pixelScale = if (pixels > MAX_BITMAP_PIXELS) sqrt(MAX_BITMAP_PIXELS.toDouble() / pixels) else 1.0
            return minOf(edgeScale, pixelScale)
        }

        @Suppress("TooGenericExceptionCaught")
        private fun open(path: String): PdfPageRenderer {
            val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (failure: Throwable) {
                // Ownership transfers to PdfRenderer only if construction succeeds.
                try {
                    descriptor.close()
                } catch (closeFailure: IOException) {
                    failure.addSuppressed(closeFailure)
                }
                throw failure
            }
            return PdfPageRenderer(renderer)
        }
    }
}
