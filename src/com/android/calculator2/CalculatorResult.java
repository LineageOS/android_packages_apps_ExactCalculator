/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Color;
import android.widget.OverScroller;
import android.view.GestureDetector;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.text.Editable;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import android.support.v4.view.ViewCompat;


// A text widget that is "infinitely" scrollable to the right,
// and obtains the text to display via a callback to Logic.
public class CalculatorResult extends CalculatorEditText {
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
    private int mCurrentPos;// Position of right of display relative
                            // to decimal point, in pixels.
                            // Large positive values mean the decimal
                            // point is scrolled off the left of the
                            // display.  Zero means decimal point is
                            // barely displayed on the right.
    private int mLastPos;   // Position already reflected in display.
    private int mMinPos;    // Maximum position before all digits
                            // digits disappear of the right.
    private int mCharWidth; // Use monospaced font for now.
                            // This shouldn't be much harder with a variable
                            // width font, except it may be even less smooth
        // FIXME: This is not really a fixed width font anymore.
    private Paint mPaint;   // Paint object matching display.

    public CalculatorResult(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroller = new OverScroller(context);
        mGestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
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
            });
        setOnTouchListener(mTouchListener);
        setHorizontallyScrolling(false);  // do it ourselves
        setCursorVisible(false);
        setTypeface(Typeface.MONOSPACE);
        mPaint = getPaint();
        mCharWidth = (int) mPaint.measureText("5");
    }

    void setEvaluator(Evaluator evaluator) {
        mEvaluator = evaluator;
    }

    // Display a new result, given initial displayed
    // precision and the string representing the whole part of
    // the number to be displayed.
    // We pass the string, instead of just the length, so we have
    // one less place to fix in case we ever decide to use a variable
    // width font.
    void displayResult(int initPrec, String truncatedWholePart) {
        mLastPos = INVALID;
        mCurrentPos = initPrec * mCharWidth;
        mMinPos = - (int) Math.ceil(mPaint.measureText(truncatedWholePart));
        redisplay();
    }

    // May be called from non-UI thread, but after initialization.
    int getCharWidth() {
        return mCharWidth;
    }

    void displayError(int resourceId) {
        mScrollable = false;
        setText(resourceId);
    }

    // Return entire result (within reason) up to current displayed precision.
    public CharSequence getFullText() {
        if (!mScrollable) return getText();
        int currentCharPos = mCurrentPos/mCharWidth;
        return mEvaluator.getString(currentCharPos, 1000000);
    }

    int getMaxChars() {
        int result = getWidthConstraint() / mCharWidth;
        // FIXME: We can apparently finish evaluating before 
        // onMeasure in CalculatorEditText has been called, in
        // which case we get 0 or -1 as the width constraint.
        // Perhaps guess conservatively here and reevaluate
        // in InitialResult.onPostExecute?
        if (result <= 0) {
            return 8;
        } else {
            return result;
        }
    }

    void clear() {
        setText("");
    }

    void redisplay() {
        int currentCharPos = mCurrentPos/mCharWidth;
        int maxChars = getMaxChars();
        String result = mEvaluator.getString(currentCharPos, maxChars);
        int epos = result.indexOf('e');
        // TODO: Internationalization for decimal point?
        if (epos > 0 && result.indexOf('.') == -1) {
          // Gray out exponent if used as position indicator
            SpannableString formattedResult = new SpannableString(result);
            formattedResult.setSpan(new ForegroundColorSpan(Color.GRAY),
                                    epos, result.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(formattedResult);
        } else {
            setText(result);
        }
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

}
