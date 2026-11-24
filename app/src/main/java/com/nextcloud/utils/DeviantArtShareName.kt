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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
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

    /**
     * `https://www.deviantart.com/<author>/art/<Title-Slug>-<id>`. The slug keeps the title's punctuation, either raw
     * or percent-encoded (`[OPEN]-ADOPT-AUCTION-%E2%80%94-Comet-%22Aphel%22`).
     */
    private val PAGE_URL = Regex(
        """https?://(?:www\.)?deviantart\.com/([A-Za-z0-9_-]+)/art/([^\s/?#]+?)(?:-(\d+))?(?=[/?#\s]|$)"""
    )

    /** Windows-1252 characters for bytes 0x80..0x9F; undefined bytes map to themselves as in Latin-1. */
    private const val CP1252_HIGH =
        "\u20AC\u0081\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\u008D\u017D\u008F" +
            "\u0090\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\u009D\u017E\u0178"
    private const val CP1252_HIGH_START = 0x80
    private const val LATIN1_END = 0xFF

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
        val slug = match.groupValues[2]
        val decodedSlug = try {
            URLDecoder.decode(slug.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            slug
        }
        val title = decodedSlug.replace('-', ' ')
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
                val title = json.get("title")?.asString?.let(::repairMojibake)
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

    /**
     * DeviantArt's oEmbed returns non-ASCII titles as UTF-8 bytes read as Windows-1252 (`—` arrives as `â€”`).
     * Returns [text] with those bytes decoded as UTF-8, or unchanged when it is not such a string.
     */
    internal fun repairMojibake(text: String): String {
        if (text.all { it.code < CP1252_HIGH_START }) {
            return text
        }
        val bytes = ByteArray(text.length)
        text.forEachIndexed { index, char ->
            val byte = if (char.code <= LATIN1_END) {
                char.code
            } else {
                CP1252_HIGH.indexOf(char).takeIf { it >= 0 }?.plus(CP1252_HIGH_START) ?: return text
            }
            bytes[index] = byte.toByte()
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            text
        }
    }
}
