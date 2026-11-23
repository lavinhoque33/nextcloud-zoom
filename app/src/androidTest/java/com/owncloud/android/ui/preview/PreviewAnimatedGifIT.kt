/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.ui.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import com.nextcloud.client.jobs.upload.FileUploadWorker
import com.owncloud.android.AbstractOnServerIT
import com.owncloud.android.R
import com.owncloud.android.db.OCUpload
import com.owncloud.android.ui.preview.media.DetailZoomPhotoView
import com.owncloud.android.ui.preview.media.PreviewMediaActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class PreviewAnimatedGifIT : AbstractOnServerIT() {
    @Test
    fun remoteGifRendersBothFrames() {
        val fixture = File.createTempFile("preview-animation", ".gif", targetContext.cacheDir)
        try {
            fixture.writeBytes(Base64.decode(TWO_FRAME_GIF, Base64.DEFAULT))
            uploadOCUpload(
                OCUpload(fixture.absolutePath, REMOTE_PATH, account.name),
                FileUploadWorker.LOCAL_BEHAVIOUR_FORGET
            )
        } finally {
            fixture.delete()
        }

        val remoteGif = checkNotNull(storageManager.getFileByDecryptedRemotePath(REMOTE_PATH))
        assertEquals("image/gif", remoteGif.mimeType)
        assertFalse("The viewer must start without a local copy", remoteGif.isDown)

        val intent = PreviewMediaActivity.previewFileIntent(targetContext, user, remoteGif)
        launchActivity<PreviewMediaActivity>(intent).use { assertFramesLoop(it) }
    }

    private fun assertFramesLoop(scenario: ActivityScenario<PreviewMediaActivity>) {
        val handler = Handler(Looper.getMainLooper())
        val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val deadline = SystemClock.elapsedRealtime() + PLAYBACK_TIMEOUT_MILLIS
        val frames = mutableListOf<Int>()
        while (frames.size < LOOP_FRAME_COUNT && SystemClock.elapsedRealtime() < deadline) {
            val color = copyCenterPixel(scenario, pixel, handler)
            if (color != null && (color == Color.RED || color == Color.BLUE) && color != frames.lastOrNull()) {
                frames.add(color)
            }
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }

        assertEquals(
            "Expected a full red/blue loop, but observed ${frames.map { Integer.toHexString(it) }}",
            LOOP_FRAME_COUNT,
            frames.size
        )
    }

    private fun copyCenterPixel(
        scenario: ActivityScenario<PreviewMediaActivity>,
        pixel: Bitmap,
        handler: Handler
    ): Int? {
        val result = CompletableFuture<Int?>()
        scenario.onActivity { activity ->
            val image = activity.findViewById<DetailZoomPhotoView>(R.id.image)
            if (image == null || !image.isShown || image.width == 0 || image.height == 0) {
                result.complete(null)
            } else {
                val position = IntArray(2)
                image.getLocationInWindow(position)
                val x = position[0] + image.width / 2
                val y = position[1] + image.height / 2
                PixelCopy.request(activity.window, Rect(x, y, x + 1, y + 1), pixel, { status ->
                    result.complete(if (status == PixelCopy.SUCCESS) pixel.getPixel(0, 0) else null)
                }, handler)
            }
        }
        return result.get(PIXEL_COPY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val REMOTE_PATH = "/PreviewAnimatedGifIT/animation.gif"
        private const val LOOP_FRAME_COUNT = 3
        private const val PLAYBACK_TIMEOUT_MILLIS = 20_000L
        private const val POLL_INTERVAL_MILLIS = 50L
        private const val PIXEL_COPY_TIMEOUT_SECONDS = 2L

        // GIF89a: two solid 64x64 frames (red, then blue), each held for 600 ms, looping indefinitely.
        private const val TWO_FRAME_GIF =
            "R0lGODlhQABAAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQEPAAAACwAAAAAQABAAAAIaQABCBxIsKDB" +
                "gwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjR" +
                "o0iTKl3KtKnTp1CjSp1KtarVq1izagUQEAAh+QQFPAABACwAAAAAQABAAIEAAP8AAAAAAAAAAAAIaQABCBxIsKDBgwgTKlzI" +
                "sKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3K" +
                "tKnTp1CjSp1KtarVq1izagUQEAA7"
    }
}
