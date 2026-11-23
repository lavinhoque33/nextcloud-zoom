/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.owncloud.android.ui.preview.pdf

import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.utils.MenuUtils
import com.nextcloud.utils.SnackbarUtil
import com.nextcloud.utils.extensions.getParcelableArgument
import com.owncloud.android.R
import com.owncloud.android.databinding.PreviewPdfFragmentBinding
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.lib.common.utils.Log_OC
import com.owncloud.android.ui.activity.FileDisplayActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PreviewPdfFragment : Fragment(R.layout.preview_pdf_fragment) {
    private lateinit var file: OCFile
    private var restoreReadingChrome: (() -> Unit)? = null

    companion object {
        private const val ARG_FILE = "FILE"
        private const val STATE_FULLSCREEN = "pdfFullscreen"

        @JvmStatic
        fun newInstance(file: OCFile): PreviewPdfFragment = PreviewPdfFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_FILE, file)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = PreviewPdfFragmentBinding.bind(view).pdfRecycler
        val owner = viewLifecycleOwner
        file = requireArguments().getParcelableArgument(ARG_FILE, OCFile::class.java)!!
        recycler.contentDescription = file.fileName
        bindReadingGestures(recycler)
        if (savedInstanceState?.getBoolean(STATE_FULLSCREEN) == true) {
            recycler.doOnAttach { hideReadingChrome() }
        }
        owner.lifecycleScope.launch {
            try {
                val path = file.storagePath ?: throw IOException("PDF has no local path")
                PdfPageRenderer.withDocument(path) { renderer ->
                    displayPages(recycler, renderer, renderer.readPageAspectRatios())
                }
            } catch (e: SecurityException) {
                Log_OC.e(this@PreviewPdfFragment, "Trying to open a password-protected PDF", e)
                owner.lifecycle.withResumed { closeWithError(R.string.pdf_password_protected) }
            } catch (e: IOException) {
                Log_OC.e(this@PreviewPdfFragment, "Unable to read PDF", e)
                owner.lifecycle.withResumed { closeWithError(R.string.pdf_unreadable) }
            }
        }
    }

    private fun bindReadingGestures(recycler: RecyclerView) {
        val lifecycle = viewLifecycleOwner.lifecycle
        val taps = GestureDetector(recycler.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) hideReadingChrome()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                showReadingChrome()
                return true
            }
        })
        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                taps.onTouchEvent(event)
                return false
            }
        })
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) hideReadingChrome()
            }
        })
    }

    private fun hideReadingChrome() {
        if (restoreReadingChrome != null) return
        val host = activity as? FileDisplayActivity ?: return
        val toolbar = host.findViewById<View>(R.id.toolbar_frame)
        val controller = WindowCompat.getInsetsController(host.window, host.window.decorView)
        val previousBehavior = controller.systemBarsBehavior
        val previousVisibility = toolbar.visibility
        restoreReadingChrome = {
            toolbar.visibility = previousVisibility
            controller.systemBarsBehavior = previousBehavior
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        toolbar.isVisible = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showReadingChrome() {
        restoreReadingChrome?.invoke()
        restoreReadingChrome = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FULLSCREEN, restoreReadingChrome != null)
    }

    override fun onDestroyView() {
        showReadingChrome()
        super.onDestroyView()
    }

    private suspend fun displayPages(
        recycler: RecyclerView,
        renderer: PdfPageRenderer,
        aspectRatios: DoubleArray
    ): Unit = withContext(Dispatchers.Main.immediate) {
        val adapter = PreviewPdfAdapter(renderer, aspectRatios, this)
        val resizeListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            adapter.updateViewportWidth(view.width - view.paddingLeft - view.paddingRight)
        }
        adapter.updateViewportWidth(recycler.width - recycler.paddingLeft - recycler.paddingRight)
        recycler.itemAnimator = null
        recycler.adapter = adapter
        recycler.addOnLayoutChangeListener(resizeListener)
        try {
            awaitCancellation()
        } finally {
            recycler.removeOnLayoutChangeListener(resizeListener)
            recycler.adapter = null
        }
    }

    private fun closeWithError(@StringRes message: Int) {
        parentFragmentManager.popBackStack()
        SnackbarUtil.show(activity, message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        MenuUtils.hideAll(menu)
    }

    override fun onResume() {
        super.onResume()
        val parent = activity
        if (parent is FileDisplayActivity) {
            parent.showSortListGroup(false)
            parent.updateActionBarTitleAndHomeButton(file)
        }
    }
}
