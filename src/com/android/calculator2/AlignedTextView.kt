/*
 * SPDX-FileCopyrightText: 2015 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.ceil
import kotlin.math.min

/**
 * Extended [TextView] that supports ascent/baseline alignment.
 */
open class AlignedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    // temporary rect for use during layout
    private val tempRect = Rect()

    private var topPaddingOffset = 0
    private var bottomPaddingOffset = 0

    init {
        // Disable any included font padding by default.
        includeFontPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val paint = paint

        // Always align text to the default capital letter height.
        paint.getTextBounds(LATIN_CAPITAL_LETTER, 0, 1, tempRect)

        topPaddingOffset = min(paddingTop, ceil(tempRect.top - paint.ascent()).toInt())
        bottomPaddingOffset = min(paddingBottom, ceil(paint.descent()).toInt())

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun getCompoundPaddingTop(): Int {
        return super.getCompoundPaddingTop() - topPaddingOffset
    }

    override fun getCompoundPaddingBottom(): Int {
        return super.getCompoundPaddingBottom() - bottomPaddingOffset
    }

    companion object {
        private const val LATIN_CAPITAL_LETTER = "H"
    }
}
