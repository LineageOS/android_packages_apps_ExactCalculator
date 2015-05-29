/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Extended {@link TextView} that supports ascent/baseline alignment.
 */
public class AlignedTextView extends TextView {

    private static final String LATIN_CAPITAL_LETTER = "H";
    private static final CharsetEncoder LATIN_CHARSET_ENCODER =
            Charset.forName("ISO-8859-1").newEncoder();

    // temporary rect for use during layout
    private final Rect mTempRect = new Rect();

    private int mTopPaddingOffset;
    private int mBottomPaddingOffset;

    public AlignedTextView(Context context) {
        this(context, null /* attrs */);
    }

    public AlignedTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public AlignedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Disable any included font padding by default.
        setIncludeFontPadding(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        CharSequence text = getText();
        if (TextUtils.isEmpty(text) || LATIN_CHARSET_ENCODER.canEncode(text)) {
            // For latin text align to the default capital letter height.
            text = LATIN_CAPITAL_LETTER;
        }
        getPaint().getTextBounds(text.toString(), 0, text.length(), mTempRect);

        final Paint textPaint = getPaint();
        mTopPaddingOffset = Math.min(getPaddingTop(),
                (int) Math.floor(mTempRect.top - textPaint.ascent()));
        mBottomPaddingOffset = Math.min(getPaddingBottom(),
                (int) Math.floor(textPaint.descent()));

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public int getCompoundPaddingTop() {
        return super.getCompoundPaddingTop() - mTopPaddingOffset;
    }

    @Override
    public int getCompoundPaddingBottom() {
        return super.getCompoundPaddingBottom() - mBottomPaddingOffset;
    }
}
