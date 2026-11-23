/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.owncloud.android.ui.preview.pdf

import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.doubleClick
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.rule.GrantPermissionRule
import com.nextcloud.test.GrantTestPermissionRule
import com.nextcloud.test.TestActivity
import com.owncloud.android.AbstractIT
import com.owncloud.android.R
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.ui.activity.FileDisplayActivity
import com.owncloud.android.utils.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PreviewPdfFragmentScreenshotIT : AbstractIT() {
    private val testClassName = "com.owncloud.android.ui.preview.pdf.PreviewPdfFragmentScreenshotIT"

    companion object {
        private const val PDF_FILE_ASSET = "test.pdf"
        private const val RENDER_TIMEOUT_SECONDS = 10L
        private const val CHROME_TIMEOUT_MS = 2_000L
        private const val CHROME_POLL_MS = 16L
        private const val CHROME_SETTLE_MS = 750L
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantTestPermissionRule.grantStorageAndNotification()

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

    @Test
    fun doubleTapRestoresReaderChromeAndSingleTapHidesItAgain() {
        val pdf = testPdf()
        launchActivity<FileDisplayActivity>().use { scenario ->
            awaitVisiblePages(scenario) { it.startPdfPreview(pdf) }
            val reader = onView(withId(R.id.pdf_recycler))
            reader.perform(click())
            assertReadingChrome(scenario, visible = false)
            reader.perform(doubleClick())
            assertReadingChrome(scenario, visible = true)
            SystemClock.sleep(CHROME_SETTLE_MS)
            assertReadingChrome(scenario, visible = true)
            scenario.onActivity {
                val pages = it.findViewById<RecyclerView>(R.id.pdf_recycler)
                assertTrue("Double-tap scrolled away from the top", !pages.canScrollVertically(-1))
            }
            onView(withId(R.id.bottom_navigation)).check(matches(withEffectiveVisibility(GONE)))
            reader.perform(click()).check(matches(isDisplayed()))
            assertReadingChrome(scenario, visible = false)
        }
    }

    @Test
    fun doubleTapKeepsTheSamePartOfThePageOnScreen() {
        val pdf = testPdf()
        launchActivity<FileDisplayActivity>().use { scenario ->
            awaitVisiblePages(scenario) { it.startPdfPreview(pdf) }
            val reader = onView(withId(R.id.pdf_recycler))
            reader.perform(click())
            assertReadingChrome(scenario, visible = false)
            var pageIndex = RecyclerView.NO_POSITION
            var originalY = 0
            val location = IntArray(2)
            scenario.onActivity {
                val pages = it.findViewById<RecyclerView>(R.id.pdf_recycler)
                pages.scrollBy(0, pages.height / 8)
                assertTrue("Fixture must be scrolled", pages.canScrollVertically(-1))
                val page = requireNotNull(pages.findChildViewUnder(pages.width / 2f, pages.height / 2f))
                pageIndex = pages.getChildAdapterPosition(page)
                page.getLocationInWindow(location)
                originalY = location[1]
            }
            reader.perform(doubleClick())
            assertReadingChrome(scenario, visible = true)
            SystemClock.sleep(CHROME_SETTLE_MS)
            assertReadingChrome(scenario, visible = true)
            scenario.onActivity {
                val pages = it.findViewById<RecyclerView>(R.id.pdf_recycler)
                requireNotNull(pages.layoutManager?.findViewByPosition(pageIndex)).getLocationInWindow(location)
                assertEquals("Page moved when the toolbar opened", originalY, location[1])
            }
            reader.perform(click())
            assertReadingChrome(scenario, visible = false)
        }
    }

    @Test
    fun pdfKeepsAppNavigationHiddenUntilBack() {
        val pdf = testPdf()
        val bottomNavigation = onView(withId(R.id.bottom_navigation))
        val reader = onView(withId(R.id.pdf_recycler))
        launchActivity<FileDisplayActivity>().use { scenario ->
            bottomNavigation.check(matches(isDisplayed()))
            awaitVisiblePages(scenario) { it.startPdfPreview(pdf) }
            bottomNavigation.check(matches(withEffectiveVisibility(GONE)))
            assertReadingChrome(scenario, visible = true)

            reader.perform(
                GeneralSwipeAction(Swipe.FAST, GeneralLocation.CENTER, GeneralLocation.TOP_CENTER, Press.FINGER)
            )
            assertReadingChrome(scenario, visible = false)
            reader.perform(swipeDown(), click())
            scenario.onActivity { it.showBottomNavigationBar(true) }
            reader.check(matches(isDisplayed()))
            bottomNavigation.check(matches(withEffectiveVisibility(GONE)))

            scenario.recreate()
            reader.check(matches(isDisplayed()))
            bottomNavigation.check(matches(withEffectiveVisibility(GONE)))
            assertReadingChrome(scenario, visible = false)

            pressBack()
            bottomNavigation.check(matches(isDisplayed()))
            assertReadingChrome(scenario, visible = true)
        }
    }

    private fun assertReadingChrome(scenario: ActivityScenario<FileDisplayActivity>, visible: Boolean) {
        val deadline = SystemClock.uptimeMillis() + CHROME_TIMEOUT_MS
        do {
            var ready = false
            scenario.onActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return@onActivity
                ready = activity.findViewById<View>(R.id.toolbar_frame).isShown == visible &&
                    insets.isVisible(WindowInsetsCompat.Type.navigationBars()) == visible &&
                    insets.isVisible(WindowInsetsCompat.Type.statusBars()) == visible
            }
            if (ready) return
            SystemClock.sleep(CHROME_POLL_MS)
        } while (SystemClock.uptimeMillis() < deadline)
        scenario.onActivity { activity ->
            assertEquals("PDF toolbar", visible, activity.findViewById<View>(R.id.toolbar_frame).isShown)
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
            assertEquals("Android navigation bar", visible, insets.isVisible(WindowInsetsCompat.Type.navigationBars()))
            assertEquals("Android status bar", visible, insets.isVisible(WindowInsetsCompat.Type.statusBars()))
        }
    }

    private fun testPdf(): OCFile = OCFile("/test.pdf").apply {
        storagePath = getFile(PDF_FILE_ASSET).absolutePath
    }

    private fun withRenderedPdf(test: (ActivityScenario<TestActivity>) -> Unit) {
        val pdf = testPdf()
        launchActivity<TestActivity>().use { scenario ->
            awaitVisiblePages(scenario) { it.addFragment(PreviewPdfFragment.newInstance(pdf)) }
            test(scenario)
        }
    }

    private fun <T : FragmentActivity> awaitVisiblePages(scenario: ActivityScenario<T>, openPdf: (T) -> Unit) {
        val rendered = CountDownLatch(1)
        scenario.onActivity { activity ->
            openPdf(activity)
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
