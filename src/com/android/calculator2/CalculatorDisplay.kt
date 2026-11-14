/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class CalculatorDisplay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        // Draw the children in reverse order so that the toolbar is on top.
        isChildrenDrawingOrderEnabled = true
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        // Reverse the normal drawing order.
        return (childCount - 1) - i
    }
}
