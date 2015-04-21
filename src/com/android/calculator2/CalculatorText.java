/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Parcelable;
import android.text.method.ScrollingMovementMethod;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * TextView adapted for Calculator display.
 */

public class CalculatorText extends TextView implements View.OnLongClickListener{


    private final ActionMode.Callback mPasteActionModeCallback =
            new ActionMode.Callback() {
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case R.id.menu_paste:
                pasteContent();
                mode.finish();
                return true;
            default:
                return false;
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.paste, menu);
                return true;
            }
            // Prevents the selection action mode on double tap.
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
    };

    private PasteListener mPasteListener;

    public void setPasteListener(PasteListener pasteListener) {
        mPasteListener = pasteListener;
    }

    private void pasteContent() {
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        ClipData cd = clipboard.getPrimaryClip();
        ClipData.Item item = cd.getItemAt(0);
        // TODO: Should we handle multiple selections?
        Uri uri = item.getUri();
        if (uri == null || !mPasteListener.paste(uri)) {
            mPasteListener.paste(item.coerceToText(getContext()).toString());
        }
    }

    private final float mMaximumTextSize;
    private final float mMinimumTextSize;
    private final float mStepTextSize;

    // Temporary objects for use in layout methods.
    private final Paint mTempPaint = new TextPaint();
    private final Rect mTempRect = new Rect();

    private int mWidthConstraint = -1;
    private OnTextSizeChangeListener mOnTextSizeChangeListener;

    public CalculatorText(Context context) {
        this(context, null);
    }

    public CalculatorText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CalculatorText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CalculatorText, defStyle, 0);
        mMaximumTextSize = a.getDimension(
                R.styleable.CalculatorText_maxTextSize, getTextSize());
        mMinimumTextSize = a.getDimension(
                R.styleable.CalculatorText_minTextSize, getTextSize());
        mStepTextSize = a.getDimension(R.styleable.CalculatorText_stepTextSize,
                (mMaximumTextSize - mMinimumTextSize) / 3);

        a.recycle();

        // Paste ActionMode is triggered explicitly, not through
        // setCustomSelectionActionModeCallback.
        setOnLongClickListener(this);

        // Enable scrolling
        setMovementMethod(ScrollingMovementMethod.getInstance());

        setTextSize(TypedValue.COMPLEX_UNIT_PX, mMaximumTextSize);
        setMinHeight(getLineHeight() + getCompoundPaddingBottom() + getCompoundPaddingTop());
    }

    @Override
    public boolean onLongClick(View v) {
        startActionMode(mPasteActionModeCallback);
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mWidthConstraint =
                MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getVariableTextSize(getText().toString()));
    }

    public int getWidthConstraint() { return mWidthConstraint; }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);

        final int textLength = text.length();
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getVariableTextSize(text.toString()));
    }

    @Override
    public void setTextSize(int unit, float size) {
        final float oldTextSize = getTextSize();
        super.setTextSize(unit, size);

        if (mOnTextSizeChangeListener != null && getTextSize() != oldTextSize) {
            mOnTextSizeChangeListener.onTextSizeChanged(this, oldTextSize);
        }
    }

    public void setOnTextSizeChangeListener(OnTextSizeChangeListener listener) {
        mOnTextSizeChangeListener = listener;
    }

    public float getVariableTextSize(String text) {
        if (mWidthConstraint < 0 || mMaximumTextSize <= mMinimumTextSize) {
            // Not measured, bail early.
            return getTextSize();
        }

        // Capture current paint state.
        mTempPaint.set(getPaint());

        // Step through increasing text sizes until the text would no longer fit.
        float lastFitTextSize = mMinimumTextSize;
        while (lastFitTextSize < mMaximumTextSize) {
            final float nextSize = Math.min(lastFitTextSize + mStepTextSize, mMaximumTextSize);
            mTempPaint.setTextSize(nextSize);
            if (mTempPaint.measureText(text) > mWidthConstraint) {
                break;
            } else {
                lastFitTextSize = nextSize;
            }
        }

        return lastFitTextSize;
    }

    @Override
    public int getCompoundPaddingTop() {
        // Measure the top padding from the capital letter height of the text instead of the top,
        // but don't remove more than the available top padding otherwise clipping may occur.
        getPaint().getTextBounds("H", 0, 1, mTempRect);

        final FontMetricsInt fontMetrics = getPaint().getFontMetricsInt();
        final int paddingOffset = -(fontMetrics.ascent + mTempRect.height());
        return super.getCompoundPaddingTop() - Math.min(getPaddingTop(), paddingOffset);
    }

    @Override
    public int getCompoundPaddingBottom() {
        // Measure the bottom padding from the baseline of the text instead of the bottom, but don't
        // remove more than the available bottom padding otherwise clipping may occur.
        final FontMetricsInt fontMetrics = getPaint().getFontMetricsInt();
        return super.getCompoundPaddingBottom() - Math.min(getPaddingBottom(), fontMetrics.descent);
    }

    public interface OnTextSizeChangeListener {
        void onTextSizeChanged(TextView textView, float oldSize);
    }

    public interface PasteListener {
        void paste(String s);
        boolean paste(Uri u);
    }
}
