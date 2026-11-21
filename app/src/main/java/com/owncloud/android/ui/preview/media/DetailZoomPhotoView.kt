/*
 * Nextcloud - Android Client
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */

package com.owncloud.android.ui.preview.media

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.max
import kotlin.math.min

/**
 * [PhotoView] whose zoom levels follow the image's shape instead of PhotoView's fixed 1x / 1.75x / 3x.
 *
 * Zoom 1 fits the whole image on screen, which for a very tall strip leaves it only a few dozen pixels
 * wide, and PhotoView's default 3x maximum never reaches readable size. Here:
 * - double tap (medium) fills the screen along the image's short side, e.g. fit-to-width for a strip;
 * - the maximum allows [DETAIL_HEADROOM] times past that, and past one image pixel per screen pixel.
 */
class DetailZoomPhotoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : PhotoView(context, attrs, defStyle) {

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateScaleLevels()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScaleLevels()
    }

    private fun updateScaleLevels() {
        // Also runs from the ImageView constructor (android:src), before PhotoView's attacher exists;
        // the view has no size then, so this returns early.
        val image = drawable ?: return
        val viewWidth = width - paddingLeft - paddingRight
        val viewHeight = height - paddingTop - paddingBottom
        val imageWidth = image.intrinsicWidth
        val imageHeight = image.intrinsicHeight
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return
        }

        val widthRatio = viewWidth.toFloat() / imageWidth
        val heightRatio = viewHeight.toFloat() / imageHeight
        // Image-to-view scale at zoom 1 (fit centre).
        val fitScale = min(widthRatio, heightRatio)
        // Zoom at which the image fills the view along its short side.
        val fillZoom = max(widthRatio, heightRatio) / fitScale
        // Zoom at which one image pixel covers one screen pixel.
        val bitmapWidth = (image as? BitmapDrawable)?.bitmap?.width ?: imageWidth
        val pixelZoom = bitmapWidth / (imageWidth * fitScale)

        val medium = if (fillZoom >= ELONGATED_FILL_ZOOM) fillZoom else DEFAULT_MEDIUM_SCALE
        val maximum = maxOf(DEFAULT_MAXIMUM_SCALE, medium * DETAIL_HEADROOM, pixelZoom * DETAIL_HEADROOM)
        setScaleLevels(DEFAULT_MINIMUM_SCALE, medium, maximum)
    }

    companion object {
        // PhotoView's own defaults, kept for ordinary images.
        private const val DEFAULT_MINIMUM_SCALE = 1f
        private const val DEFAULT_MEDIUM_SCALE = 1.75f
        private const val DEFAULT_MAXIMUM_SCALE = 3f

        // Images that need at least this zoom to fill the screen get fit-to-fill as the double-tap level.
        private const val ELONGATED_FILL_ZOOM = 2f

        // How far past "fills the screen" and "1:1 pixels" zooming may go.
        private const val DETAIL_HEADROOM = 3f
    }
}
