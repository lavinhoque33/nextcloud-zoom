/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.owncloud.android.ui.preview.pdf

import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.nextcloud.test.TestActivity
import com.owncloud.android.AbstractIT
import com.owncloud.android.R
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.utils.ScreenshotTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PreviewPdfFragmentScreenshotIT : AbstractIT() {
    private val testClassName = "com.owncloud.android.ui.preview.pdf.PreviewPdfFragmentScreenshotIT"

    companion object {
        private const val PDF_FILE_ASSET = "test.pdf"
        private const val RENDER_TIMEOUT_SECONDS = 10L
    }

    @Test
    @ScreenshotTest
    fun showPdf() {
        withRenderedPdf { scenario ->
            lateinit var host: TestActivity
            scenario.onActivity { host = it }
            screenshotViaName(host, createName(testClassName + "_" + "showPdf", ""))
        }
    }

    @Test
    fun tappingRenderedPageStaysInReader() {
        withRenderedPdf {
            onView(withId(R.id.pdf_recycler)).perform(click())
            onView(withId(R.id.pdf_recycler)).check(matches(isDisplayed()))
        }
    }

    private fun withRenderedPdf(test: (ActivityScenario<TestActivity>) -> Unit) {
        val pdf = OCFile("/test.pdf").apply { storagePath = getFile(PDF_FILE_ASSET).absolutePath }
        launchActivity<TestActivity>().use { scenario ->
            awaitVisiblePages(scenario, pdf)
            test(scenario)
        }
    }

    private fun awaitVisiblePages(scenario: ActivityScenario<TestActivity>, pdf: OCFile) {
        val rendered = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.addFragment(PreviewPdfFragment.newInstance(pdf))
            activity.supportFragmentManager.executePendingTransactions()
            val pages = activity.findViewById<RecyclerView>(R.id.pdf_recycler)
            pages.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = pages.childCount > 0 &&
                        pages.children.all { it.findViewById<ImageView>(R.id.page).drawable != null }
                    if (ready) {
                        pages.viewTreeObserver.removeOnPreDrawListener(this)
                        rendered.countDown()
                    }
                    return true
                }
            })
        }
        assertTrue("Visible PDF pages did not render", rendered.await(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }
}
