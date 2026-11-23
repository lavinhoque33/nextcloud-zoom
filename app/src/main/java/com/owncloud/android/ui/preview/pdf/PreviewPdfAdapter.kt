/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.ui.preview.pdf

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.owncloud.android.databinding.PreviewPdfPageItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PreviewPdfAdapter(
    private val renderer: PdfPageRenderer,
    private val aspectRatios: DoubleArray,
    private val renderScope: CoroutineScope
) : RecyclerView.Adapter<PreviewPdfAdapter.ViewHolder>() {
    private var viewportWidth = 0

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    class ViewHolder(val binding: PreviewPdfPageItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var pageIndex = RecyclerView.NO_POSITION
        var pageWidth = 0
        var renderJob: Job? = null

        fun showPage(bitmap: Bitmap) {
            // Row geometry comes from the PDF metadata; swapping the drawable must not request a layout.
            binding.page.setImageDrawable(object : BitmapDrawable(binding.page.resources, bitmap) {
                override fun getIntrinsicWidth(): Int = -1

                override fun getIntrinsicHeight(): Int = -1
            })
        }

        fun cancelRender() {
            renderJob?.cancel()
            renderJob = null
        }

        fun unbind() {
            cancelRender()
            pageIndex = RecyclerView.NO_POSITION
            if (binding.page.drawable != null) binding.page.setImageDrawable(null)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = PreviewPdfPageItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = if (viewportWidth > 0) aspectRatios.size else 0

    fun updateViewportWidth(width: Int) {
        if (width <= 0 || width == viewportWidth) return
        val wasEmpty = viewportWidth == 0
        viewportWidth = width
        if (wasEmpty) {
            notifyItemRangeInserted(0, itemCount)
        } else {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder.pageIndex != position || holder.pageWidth != viewportWidth) {
            holder.unbind()
            holder.pageIndex = position
            holder.pageWidth = viewportWidth
            val height = (viewportWidth * aspectRatios[position]).toInt().coerceAtLeast(1)
            val params = holder.itemView.layoutParams
            if (params.height != height) {
                params.height = height
                holder.itemView.layoutParams = params
            }
        }
        if (holder.itemView.isAttachedToWindow) renderPage(holder)
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        renderPage(holder)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.cancelRender()
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.unbind()
    }

    private fun renderPage(holder: ViewHolder) {
        val index = holder.pageIndex
        val width = holder.pageWidth
        if (index == RecyclerView.NO_POSITION || width != viewportWidth ||
            holder.binding.page.drawable != null || holder.renderJob?.isActive == true
        ) {
            return
        }

        val cached = renderer.cachedPage(index, width)
        if (cached != null) {
            holder.showPage(cached)
            return
        }

        val height = holder.itemView.layoutParams.height
        holder.renderJob = renderScope.launch {
            val bitmap = renderer.renderPage(index, width, height)
            if (width == viewportWidth) holder.showPage(bitmap)
        }
    }
}
