/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.ui.preview.media

import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import com.nextcloud.operations.GetMethod
import com.nextcloud.repository.ClientRepository
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

internal object GifPreviewLoader {
    suspend fun load(file: OCFile, cacheDir: File, clients: ClientRepository): Drawable = withContext(Dispatchers.IO) {
        if (file.isDown) return@withContext decode(File(file.storagePath))

        val client = clients.getNextcloudClient() ?: throw IOException("No client available")
        val request = GetMethod(client.getFilesDavUri(file.remotePath), false)
        val original = File.createTempFile("gif-preview-", ".gif", cacheDir)
        try {
            runInterruptible {
                val status = client.execute(request)
                if (!request.isSuccess()) {
                    throw IOException("GIF download failed: HTTP $status")
                }
                val input = request.getResponseBodyAsStream() ?: throw IOException("Missing GIF response body")
                original.outputStream().use { output -> input.copyTo(output) }
            }
            decode(original)
        } finally {
            request.releaseConnection()
            // ImageDecoder keeps an open descriptor for subsequent frames, not the file name.
            Files.deleteIfExists(original.toPath())
        }
    }

    private fun decode(file: File): Drawable =
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val size = info.size
            val sample = BitmapUtils.viewerSampleSize(size.width, size.height, BitmapUtils.VIEWER_MAX_PIXELS)
            decoder.setTargetSampleSize(sample)
        }
}
