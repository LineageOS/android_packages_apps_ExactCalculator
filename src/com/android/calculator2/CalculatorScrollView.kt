/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import kotlin.math.max

class CalculatorScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    override fun measureChild(
        child: View,
        parentWidthMeasureSpec: Int,
        parentHeightMeasureSpec: Int,
    ) {
        // Allow child to be as wide as they want.
        val unspecifiedWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
            MeasureSpec.getSize(parentWidthMeasureSpec), UNSPECIFIED
        )

        val lp = child.layoutParams
        val childWidthMeasureSpec = getChildMeasureSpecCompat(
            unspecifiedWidthMeasureSpec,
            0 /* padding */, lp.width
        )
        val childHeightMeasureSpec = getChildMeasureSpecCompat(
            parentHeightMeasureSpec,
            paddingTop + paddingBottom, lp.height
        )

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int,
    ) {
        // Allow child to be as wide as they want.
        val unspecifiedWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
            MeasureSpec.getSize(parentWidthMeasureSpec), UNSPECIFIED
        )

        val lp = child.layoutParams as MarginLayoutParams
        val childWidthMeasureSpec = getChildMeasureSpecCompat(
            unspecifiedWidthMeasureSpec,
            lp.leftMargin + lp.rightMargin, lp.width
        )
        val childHeightMeasureSpec = getChildMeasureSpecCompat(
            parentHeightMeasureSpec,
            paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin, lp.height
        )

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    companion object {
        private fun getChildMeasureSpecCompat(spec: Int, padding: Int, childDimension: Int): Int {
            if (MeasureSpec.getMode(spec) == UNSPECIFIED
                && (childDimension == MATCH_PARENT || childDimension == WRAP_CONTENT)
            ) {
                val size = max(0, MeasureSpec.getSize(spec) - padding)
                return MeasureSpec.makeMeasureSpec(size, UNSPECIFIED)
            }
            return ViewGroup.getChildMeasureSpec(spec, padding, childDimension)
        }
    }
}
