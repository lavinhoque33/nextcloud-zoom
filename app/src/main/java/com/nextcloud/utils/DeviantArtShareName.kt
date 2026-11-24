/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.nextcloud.utils

import androidx.annotation.WorkerThread
import com.google.gson.JsonParser
import com.owncloud.android.MainApp
import com.owncloud.android.lib.common.utils.Log_OC
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Names an image shared from a DeviantArt page as `{author}-{title}.{ext}`.
 *
 * Chrome shares an image as a timestamp-named temporary file and passes the page it came from in
 * `EXTRA_TEXT`. The author and title come from DeviantArt's public oEmbed endpoint, or from the page
 * URL itself when that request fails.
 */
object DeviantArtShareName {
    private const val TAG = "DeviantArtShareName"
    private const val OEMBED_ENDPOINT = "https://backend.deviantart.com/oembed?url="
    private const val TIMEOUT_SECONDS = 8L

    /** `https://www.deviantart.com/<author>/art/<Title-Slug>-<id>` */
    private val PAGE_URL = Regex(
        """https?://(?:www\.)?deviantart\.com/([A-Za-z0-9_-]+)/art/([A-Za-z0-9_-]+?)(?:-(\d+))?(?=[/?#\s]|$)"""
    )

    /** Characters that are invalid in file names on common file systems or Nextcloud. */
    private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]+""")
    private val WHITESPACE = Regex("""\s+""")

    private val client by lazy {
        OkHttpClient.Builder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    /** Returns the deviation page URL in a share's text, or null when the share is not from DeviantArt. */
    @JvmStatic
    fun findPageUrl(sharedText: String?): String? = sharedText?.let { PAGE_URL.find(it)?.value }

    /**
     * Returns the upload name for [sharedFileName] shared from [pageUrl], keeping its extension,
     * or null when neither oEmbed nor the URL yield an author and title.
     */
    @JvmStatic
    @WorkerThread
    fun resolveFileName(pageUrl: String, sharedFileName: String): String? {
        val (author, title) = fetchAuthorAndTitle(pageUrl) ?: parsePageUrl(pageUrl) ?: return null
        return buildFileName(author, title, sharedFileName)
    }

    internal fun parsePageUrl(pageUrl: String): Pair<String, String>? {
        val match = PAGE_URL.find(pageUrl) ?: return null
        val author = match.groupValues[1]
        val title = match.groupValues[2].replace('-', ' ')
        return author to title
    }

    internal fun buildFileName(author: String, title: String, sharedFileName: String): String? {
        val cleanAuthor = clean(author)
        val cleanTitle = clean(title)
        if (cleanAuthor.isEmpty() || cleanTitle.isEmpty()) {
            return null
        }
        val extension = sharedFileName.substringAfterLast('.', "")
        val baseName = "$cleanAuthor-$cleanTitle"
        return if (extension.isEmpty()) baseName else "$baseName.$extension"
    }

    private fun clean(part: String): String =
        part.replace(INVALID_FILE_NAME_CHARS, " ").replace(WHITESPACE, " ").trim().trimEnd('.')

    private fun fetchAuthorAndTitle(pageUrl: String): Pair<String, String>? {
        val request = Request.Builder()
            .url(OEMBED_ENDPOINT + URLEncoder.encode(pageUrl, Charsets.UTF_8.name()))
            .header("User-Agent", MainApp.getUserAgent())
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    Log_OC.w(TAG, "oEmbed returned HTTP ${response.code} for $pageUrl")
                    return null
                }
                val json = JsonParser.parseString(body).asJsonObject
                val author = json.get("author_name")?.asString
                val title = json.get("title")?.asString
                if (author.isNullOrBlank() || title.isNullOrBlank()) null else author to title
            }
        } catch (e: IOException) {
            Log_OC.w(TAG, "oEmbed request failed for $pageUrl: ${e.message}")
            null
        } catch (e: RuntimeException) {
            Log_OC.w(TAG, "oEmbed response unreadable for $pageUrl: ${e.message}")
            null
        }
    }
}
