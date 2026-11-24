/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.nextcloud.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviantArtShareNameTest {

    @Test
    fun findsDeviationPageInChromeShareText() {
        assertEquals(
            "https://www.deviantart.com/dyrk-wyst/art/Sunrise-River-1325699990",
            DeviantArtShareName.findPageUrl("https://www.deviantart.com/dyrk-wyst/art/Sunrise-River-1325699990?view=1")
        )
        assertNull(DeviantArtShareName.findPageUrl("https://www.deviantart.com/dyrk-wyst/gallery"))
        assertNull(DeviantArtShareName.findPageUrl("https://example.com/a.jpg"))
        assertNull(DeviantArtShareName.findPageUrl(null))
    }

    @Test
    fun pageUrlFallbackDropsDeviationId() {
        assertEquals(
            "dyrk-wyst" to "Sunrise River",
            DeviantArtShareName.parsePageUrl("https://www.deviantart.com/dyrk-wyst/art/Sunrise-River-1325699990")
        )
    }

    @Test
    fun fileNameKeepsSharedExtensionAndRemovesInvalidCharacters() {
        assertEquals(
            "dyrk-wyst-Sunrise River.jpeg",
            DeviantArtShareName.buildFileName("dyrk-wyst", "Sunrise River", "17954983418615250855942575203020.jpeg")
        )
        assertEquals(
            "artist-Part 1 2 Escape.png",
            DeviantArtShareName.buildFileName("artist", "Part 1/2: Escape?", "1795.png")
        )
        assertNull(DeviantArtShareName.buildFileName("artist", " / ", "1795.png"))
    }
}
