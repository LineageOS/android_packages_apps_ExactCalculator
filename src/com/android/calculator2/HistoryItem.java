/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.text.Spannable;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryItem {

    private static final String dateFormat = "EEEMMMd";
    private static final String descriptionFormat = "EEEEMMMMd";

    private long mId;
    private Date mDate;
    private Spannable mFormula;

    // This is true only for the "empty history" view.
    private final boolean mIsEmpty;

    public HistoryItem(long id, long millis, Spannable formula) {
        mId = id;
        mDate = new Date(millis);
        mFormula = formula;
        mIsEmpty = false;
    }

    public long getId() {
        return mId;
    }

    public HistoryItem() {
        mIsEmpty = true;
    }

    public boolean isEmptyView() {
        return mIsEmpty;
    }

    public String getDateString() {
        // TODO: Use DateUtils?
        final Locale l = Locale.getDefault();
        final String datePattern = DateFormat.getBestDateTimePattern(l, dateFormat);
        return new SimpleDateFormat(datePattern, l).format(mDate);
    }

    public String getDateDescription() {
        final Locale l = Locale.getDefault();
        final String descriptionPattern = DateFormat.getBestDateTimePattern(l, descriptionFormat);
        return new SimpleDateFormat(descriptionPattern, l).format(mDate);
    }

    public Spannable getFormula() {
        return mFormula;
    }
}