/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2

import android.text.Spannable
import android.text.format.DateUtils

class HistoryItem {

    val evaluatorIndex: Long

    /** Date in millis */
    val timeInMillis: Long
    val formula: Spannable?

    /** This is true only for the "empty history" view. */
    val isEmptyView: Boolean

    constructor(evaluatorIndex: Long, millis: Long, formula: Spannable?) {
        this.evaluatorIndex = evaluatorIndex
        timeInMillis = millis
        this.formula = formula
        isEmptyView = false
    }

    constructor() {
        evaluatorIndex = 0
        timeInMillis = 0
        formula = null
        isEmptyView = true
    }

    /**
     * @return String in format "n days ago"
     * For n > 7, the date is returned.
     */
    val dateString: CharSequence
        get() = DateUtils.getRelativeTimeSpanString(
            timeInMillis, System.currentTimeMillis(),
            DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        )
}
