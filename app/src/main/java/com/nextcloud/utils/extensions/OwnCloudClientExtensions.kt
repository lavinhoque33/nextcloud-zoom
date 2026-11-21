/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2023 ZetaTom
 * SPDX-FileCopyrightText: 2023 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */

package com.nextcloud.utils.extensions

import android.content.Context
import com.nextcloud.android.common.ui.network.auth.ServerCredentials
import com.nextcloud.common.NextcloudClient
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.OwnCloudClientFactory

fun OwnCloudClient.toNextcloudClient(context: Context): NextcloudClient = OwnCloudClientFactory.createNextcloudClient(
    baseUri,
    userId,
    credentials.toOkHttpCredentials(),
    context,
    isFollowRedirects
)

fun OwnCloudClient.getPreviewEndpoint(localFileId: Long, x: Int, y: Int): String = baseUri
    .toString() +
    "/index.php/core/preview?fileId=" +
    localFileId +
    "&x=" + (x / 2) + "&y=" + (y / 2) +
    "&a=1&mode=cover&forceIcon=0"

/**
 * Preview whose shorter side is at least [minSide] pixels, limited only by the original image and the
 * server's preview size limits. A very tall strip therefore comes back at its full width.
 */
fun OwnCloudClient.getFullDetailPreviewEndpoint(localFileId: Long, minSide: Int): String = baseUri
    .toString() +
    "/index.php/core/preview?fileId=" +
    localFileId +
    "&x=" + minSide + "&y=" + minSide +
    "&a=1&mode=cover&forceIcon=0"

fun OwnCloudClient.getVideoPreviewEndpoint(localFileId: Long, size: Int): String = baseUri
    .toString() +
    "/index.php/core/preview?fileId=" +
    localFileId +
    "&x=" + size + "&y=" + size +
    "&a=1&forceIcon=0"

/**
 * Used in Android Common
 */
fun OwnCloudClient.toServerCredentials(baseURL: String): ServerCredentials =
    ServerCredentials(baseURL, credentials.username, credentials.authToken)
