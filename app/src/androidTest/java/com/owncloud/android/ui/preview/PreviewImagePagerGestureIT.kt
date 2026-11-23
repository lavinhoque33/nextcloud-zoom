/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.ui.preview

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.recyclerview.widget.RecyclerView
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.owncloud.android.ui.preview.media.DetailZoomPhotoView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewImagePagerGestureIT {
    @UiThreadTest
    @Test
    fun zoomedImagePansToBothEdgesButPagerOnlyDragsAtFit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ContextThemeWrapper(instrumentation.targetContext, androidx.appcompat.R.style.Theme_AppCompat)
        val pager = ViewPager2(context)
        pager.layoutDirection = View.LAYOUT_DIRECTION_LTR
        pager.adapter = ImagePages()
        pager.setCurrentItem(MIDDLE_PAGE, false)
        val spec = View.MeasureSpec.makeMeasureSpec(PAGE_SIZE, View.MeasureSpec.EXACTLY)
        pager.measure(spec, spec)
        pager.layout(0, 0, PAGE_SIZE, PAGE_SIZE)
        val image = checkNotNull(pager.findViewWithTag<DetailZoomPhotoView>(MIDDLE_PAGE))

        image.scale = 2f
        swipe(pager, leftward = true) {
            assertEquals(ViewPager2.SCROLL_STATE_IDLE, pager.scrollState)
            assertEquals(MIDDLE_PAGE, pager.currentItem)
            assertEquals(PAGE_SIZE.toFloat(), image.displayRect.right, 1f)
        }

        image.scale = 2f
        swipe(pager, leftward = false) {
            assertEquals(ViewPager2.SCROLL_STATE_IDLE, pager.scrollState)
            assertEquals(MIDDLE_PAGE, pager.currentItem)
            assertEquals(0f, image.displayRect.left, 1f)
        }

        // An animated zoom-out can land a few floating-point steps above the fitted scale.
        image.scale = Math.nextUp(image.minimumScale)
        swipe(pager, leftward = true) {
            assertEquals(ViewPager2.SCROLL_STATE_DRAGGING, pager.scrollState)
        }
    }

    private fun swipe(pager: ViewPager2, leftward: Boolean, beforeUp: () -> Unit) {
        val startX = PAGE_SIZE * if (leftward) 0.9f else 0.1f
        val endX = PAGE_SIZE - startX
        val downTime = SystemClock.uptimeMillis()
        fun dispatch(action: Int, step: Int) {
            val x = startX + (endX - startX) * step.coerceAtMost(SWIPE_STEPS) / SWIPE_STEPS
            val event = MotionEvent.obtain(downTime, downTime + step * STEP_MS, action, x, PAGE_SIZE / 2f, 0)
            try {
                pager.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }

        dispatch(MotionEvent.ACTION_DOWN, 0)
        try {
            for (step in 1..SWIPE_STEPS) {
                dispatch(MotionEvent.ACTION_MOVE, step)
            }
            beforeUp()
        } finally {
            dispatch(MotionEvent.ACTION_UP, SWIPE_STEPS + 1)
        }
    }

    private class ImagePages : RecyclerView.Adapter<ImagePage>() {
        private val bitmap = Bitmap.createBitmap(PAGE_SIZE, PAGE_SIZE, Bitmap.Config.ARGB_8888)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagePage {
            val image = DetailZoomPhotoView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                setImageBitmap(bitmap)
            }
            return ImagePage(image)
        }

        override fun onBindViewHolder(holder: ImagePage, position: Int) {
            holder.itemView.tag = position
        }

        override fun getItemCount(): Int = 3
    }

    private class ImagePage(image: DetailZoomPhotoView) : RecyclerView.ViewHolder(image)

    private companion object {
        const val MIDDLE_PAGE = 1
        const val PAGE_SIZE = 600
        const val SWIPE_STEPS = 12
        const val STEP_MS = 16L
    }
}
