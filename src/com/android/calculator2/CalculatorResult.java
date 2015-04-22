/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Color;
import android.net.Uri;
import android.widget.TextView;
import android.widget.OverScroller;
import android.text.Editable;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import android.support.v4.view.ViewCompat;


// A text widget that is "infinitely" scrollable to the right,
// and obtains the text to display via a callback to Logic.
public class CalculatorResult extends TextView {
    final static int MAX_RIGHT_SCROLL = 100000000;
    final static int INVALID = MAX_RIGHT_SCROLL + 10000;
        // A larger value is unlikely to avoid running out of space
    final OverScroller mScroller;
    final GestureDetector mGestureDetector;
    class MyTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            boolean res = mGestureDetector.onTouchEvent(event);
            return res;
        }
    }
    final MyTouchListener mTouchListener = new MyTouchListener();
    private Evaluator mEvaluator;
    private boolean mScrollable = false;
                            // A scrollable result is currently displayed.
    private boolean mValid = false;
                            // The result holds something valid; either a
                            // a number or an error message.
    private int mCurrentPos;// Position of right of display relative
                            // to decimal point, in pixels.
                            // Large positive values mean the decimal
                            // point is scrolled off the left of the
                            // display.  Zero means decimal point is
                            // barely displayed on the right.
    private int mLastPos;   // Position already reflected in display.
    private int mMinPos;    // Maximum position before all digits
                            // digits disappear of the right.
    private Object mWidthLock = new Object();
                            // Protects the next two fields.
    private int mWidthConstraint = -1;
                            // Our total width in pixels.
    private int mCharWidth = 1;
                            // Maximum character width.
                            // For now we pretend that all characters
                            // have this width.
                            // TODO: We're not really using a fixed
                            // width font.  But it appears to be close
                            // enough for the characters we use that
                            // the difference is not noticeable.
    private static final int MAX_WIDTH = 100;
                            // Maximum number of digits displayed

    public CalculatorResult(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroller = new OverScroller(context);
        mGestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2,
                                       float velocityX, float velocityY) {
                    if (!mScroller.isFinished()) {
                        mCurrentPos = mScroller.getFinalX();
                    }
                    mScroller.forceFinished(true);
                    CalculatorResult.this.cancelLongPress();                                        // Ignore scrolls of error string, etc.
                        if (!mScrollable) return true;
                    mScroller.fling(mCurrentPos, 0, - (int) velocityX,
                                    0  /* horizontal only */, mMinPos,
                                    MAX_RIGHT_SCROLL, 0, 0);
                    ViewCompat.postInvalidateOnAnimation(CalculatorResult.this);
                    return true;
                }
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float distanceX, float distanceY) {
                    // TODO: Should we be dealing with any edge effects here?
                    if (!mScroller.isFinished()) {
                        mCurrentPos = mScroller.getFinalX();
                    }
                    mScroller.forceFinished(true);
                    CalculatorResult.this.cancelLongPress();
                    if (!mScrollable) return true;
                    int duration = (int)(e2.getEventTime() - e1.getEventTime());
                    if (duration < 1 || duration > 100) duration = 10;
                    mScroller.startScroll(mCurrentPos, 0, (int)distanceX, 0,
                                          (int)duration);
                    ViewCompat.postInvalidateOnAnimation(CalculatorResult.this);
                    return true;
                }
                @Override
                public void onLongPress(MotionEvent e) {
                    startActionMode(mCopyActionModeCallback);
                }
            });
        setOnTouchListener(mTouchListener);
        setHorizontallyScrolling(false);  // do it ourselves
        setCursorVisible(false);

        // Copy ActionMode is triggered explicitly, not through
        // setCustomSelectionActionModeCallback.
    }

    void setEvaluator(Evaluator evaluator) {
        mEvaluator = evaluator;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        char testChar = KeyMaps.translateResult("5").charAt(0);
        // TODO: Redo on Locale change?  Doesn't seem to matter?
        // We try to determine the maximal size of a digit plus
        // corresponding inter-character space.
        // We assume that "5" has maximal width.  Since any
        // string includes one fewer inter-character space than
        // characters, me measure one that's longer than any real
        // display string, and then divide by the number of characters.
        // This should bound the per-character space we need for any
        // real string.
        StringBuilder sb = new StringBuilder(MAX_WIDTH);
        for (int i = 0; i < MAX_WIDTH; ++i) {
            sb.append(testChar);
        }
        final int newWidthConstraint =
                MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        final int newCharWidth =
                (int)Math.ceil(getPaint().measureText(sb.toString())
                               / MAX_WIDTH);
        synchronized(mWidthLock) {
            mWidthConstraint = newWidthConstraint;
            mCharWidth = newCharWidth;
        }
    }

    // Display a new result, given initial displayed
    // precision and the string representing the whole part of
    // the number to be displayed.
    // We pass the string, instead of just the length, so we have
    // one less place to fix in case we ever decide to
    // correctly use a variable width font.
    void displayResult(int initPrec, String truncatedWholePart) {
        mLastPos = INVALID;
        synchronized(mWidthLock) {
            mCurrentPos = initPrec * mCharWidth;
        }
        mMinPos = - (int) Math.ceil(getPaint().measureText(truncatedWholePart));
        redisplay();
    }

    void displayError(int resourceId) {
        mValid = true;
        mScrollable = false;
        setText(resourceId);
    }

    private final int MAX_COPY_SIZE = 1000000;

    // Return entire result (within reason) up to current displayed precision.
    public String getFullText() {
        if (!mValid) return "";
        if (!mScrollable) return getText().toString();
        int currentCharPos = getCurrentCharPos();
        return KeyMaps.translateResult(
                          mEvaluator.getString(currentCharPos, MAX_COPY_SIZE));
    }

    public boolean fullTextIsExact() {
        BoundedRational rat = mEvaluator.getRational();
        int currentCharPos = getCurrentCharPos();
        if (currentCharPos == -1) {
            // Suppressing decimal point; still showing all
            // integral digits.
            currentCharPos = 0;
        }
        // TODO: Could handle scientific notation cases better;
        // We currently treat those conservatively as approximate.
        return (currentCharPos >= BoundedRational.digitsRequired(rat));
    }

    // May be called asynchronously from non-UI thread.
    int getMaxChars() {
        // We only use 2/3 of the available space, since the
        // left 1/3 of the result is not visible when it is shown
        // in large size.
        int result;
        synchronized(mWidthLock) {
            result = 2 * mWidthConstraint / (3 * mCharWidth);
            // We can apparently finish evaluating before
            // onMeasure in CalculatorEditText has been called, in
            // which case we get 0 or -1 as the width constraint.
        }
        if (result <= 0) {
            // Return something conservatively big, to force sufficient
            // evaluation.
            return MAX_WIDTH;
        } else {
            return result;
        }
    }

    int getCurrentCharPos() {
        synchronized(mWidthLock) {
            return mCurrentPos/mCharWidth;
        }
    }

    void clear() {
        mValid = false;
        setText("");
    }

    void redisplay() {
        int currentCharPos = getCurrentCharPos();
        int maxChars = getMaxChars();
        String result = mEvaluator.getString(currentCharPos, maxChars);
        int epos = result.indexOf('e');
        result = KeyMaps.translateResult(result);
        if (epos > 0 && result.indexOf('.') == -1) {
          // Gray out exponent if used as position indicator
            SpannableString formattedResult = new SpannableString(result);
            formattedResult.setSpan(new ForegroundColorSpan(Color.LTGRAY),
                                    epos, result.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(formattedResult);
        } else {
            setText(result);
        }
        mValid = true;
        mScrollable = true;
    }

    @Override
    public void computeScroll() {
        if (!mScrollable) return;
        if (mScroller.computeScrollOffset()) {
            mCurrentPos = mScroller.getCurrX();
            if (mCurrentPos != mLastPos) {
                mLastPos = mCurrentPos;
                redisplay();
            }
            if (!mScroller.isFinished()) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }
    }

    // Copy support:

    private ActionMode.Callback mCopyActionModeCallback =
                                new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.copy, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case R.id.menu_copy:
                copyContent();
                mode.finish();
                return true;
            default:
                return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    };

    private void setPrimaryClip(ClipData clip) {
        ClipboardManager clipboard = (ClipboardManager) getContext().
                getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(clip);
    }

    private void copyContent() {
        final CharSequence text = getFullText();
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        // We include a tag URI, to allow us to recognize our
        // own results and handle them specially.
        ClipData.Item newItem = new ClipData.Item(text, null,
                                          mEvaluator.capture());
        String[] mimeTypes =
                new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData cd = new ClipData("calculator result",
                                   mimeTypes, newItem);
        clipboard.setPrimaryClip(cd);
        Toast.makeText(getContext(), R.string.text_copied_toast,
                       Toast.LENGTH_SHORT).show();
    }

}
