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

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;
import android.widget.Toast;

// A text widget that is "infinitely" scrollable to the right,
// and obtains the text to display via a callback to Logic.
public class CalculatorResult extends AlignedTextView {
    static final int MAX_RIGHT_SCROLL = 10000000;
    static final int INVALID = MAX_RIGHT_SCROLL + 10000;
        // A larger value is unlikely to avoid running out of space
    final OverScroller mScroller;
    final GestureDetector mGestureDetector;
    class MyTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return mGestureDetector.onTouchEvent(event);
        }
    }
    final MyTouchListener mTouchListener = new MyTouchListener();
    private Evaluator mEvaluator;
    private boolean mScrollable = false;
                            // A scrollable result is currently displayed.
    private boolean mValid = false;
                            // The result holds something valid; either a a number or an error
                            // message.
    private int mCurrentPos;// Position of right of display relative to decimal point, in pixels.
                            // Large positive values mean the decimal point is scrolled off the
                            // left of the display.  Zero means decimal point is barely displayed
                            // on the right.
    private int mLastPos;   // Position already reflected in display. Pixels.
    private int mMinPos;    // Minimum position before all digits disappear off the right. Pixels.
    private int mMaxPos;    // Maximum position before we start displaying the infinite
                            // sequence of trailing zeroes on the right. Pixels.
    private final Object mWidthLock = new Object();
                            // Protects the next two fields.
    private int mWidthConstraint = -1;
                            // Our total width in pixels.
    private float mCharWidth = 1;
                            // Maximum character width. For now we pretend that all characters
                            // have this width.
                            // TODO: We're not really using a fixed width font.  But it appears
                            // to be close enough for the characters we use that the difference
                            // is not noticeable.
    private static final int MAX_WIDTH = 100;
                            // Maximum number of digits displayed
    private ActionMode mActionMode;
    private final ForegroundColorSpan mExponentColorSpan;

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
                    stopActionMode();
                    CalculatorResult.this.cancelLongPress();
                    // Ignore scrolls of error string, etc.
                    if (!mScrollable) return true;
                    mScroller.fling(mCurrentPos, 0, - (int) velocityX, 0  /* horizontal only */,
                                    mMinPos, mMaxPos, 0, 0);
                    postInvalidateOnAnimation();
                    return true;
                }
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float distanceX, float distanceY) {
                    int distance = (int)distanceX;
                    if (!mScroller.isFinished()) {
                        mCurrentPos = mScroller.getFinalX();
                    }
                    mScroller.forceFinished(true);
                    stopActionMode();
                    CalculatorResult.this.cancelLongPress();
                    if (!mScrollable) return true;
                    if (mCurrentPos + distance < mMinPos) {
                        distance = mMinPos - mCurrentPos;
                    } else if (mCurrentPos + distance > mMaxPos) {
                        distance = mMaxPos - mCurrentPos;
                    }
                    int duration = (int)(e2.getEventTime() - e1.getEventTime());
                    if (duration < 1 || duration > 100) duration = 10;
                    mScroller.startScroll(mCurrentPos, 0, distance, 0, (int)duration);
                    postInvalidateOnAnimation();
                    return true;
                }
                @Override
                public void onLongPress(MotionEvent e) {
                    if (mValid) {
                        mActionMode = startActionMode(mCopyActionModeCallback,
                                ActionMode.TYPE_FLOATING);
                    }
                }
            });
        setOnTouchListener(mTouchListener);
        setHorizontallyScrolling(false);  // do it ourselves
        setCursorVisible(false);
        mExponentColorSpan = new ForegroundColorSpan(
                context.getColor(R.color.display_result_exponent_text_color));

        // Copy ActionMode is triggered explicitly, not through
        // setCustomSelectionActionModeCallback.
    }

    void setEvaluator(Evaluator evaluator) {
        mEvaluator = evaluator;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final TextPaint paint = getPaint();
        final int newWidthConstraint = MeasureSpec.getSize(widthMeasureSpec)
                - (getPaddingLeft() + getPaddingRight())
                - (int) Math.ceil(Layout.getDesiredWidth(KeyMaps.ELLIPSIS, paint));
        final float newCharWidth = Layout.getDesiredWidth("\u2007", paint);
        synchronized(mWidthLock) {
            mWidthConstraint = newWidthConstraint;
            mCharWidth = newCharWidth;
        }
    }

    // Given that the last non-zero digit is at pos, compute the precision we have to ask
    // ask for to actually get the digit at pos displayed.  This is not an identity
    // function, since we may need to drop digits to the right to make room for the exponent.
    private int addExpSpace(int lastDigit) {
        if (lastDigit < getMaxChars() - 1) {
            // The decimal point will be in view when displaying the rightmost digit.
            // no exponent needed.
            // TODO: This will change if we stop scrolling to the left of the decimal
            // point, which might be desirable in the traditional scientific notation case.
            return lastDigit;
        }
        // When the last digit is displayed, the exponent will look like "e-<lastDigit>".
        // The length of that string is the extra precision we need.
        return lastDigit + (int)Math.ceil(Math.log10((double)lastDigit)) + 2;
    }

    // Display a new result, given initial displayed precision, position of the rightmost
    // nonzero digit (or Integer.MAX_VALUE if non-terminating), and the string representing
    // the whole part of the number to be displayed.
    // We pass the string, instead of just the length, so we have one less place to fix in case
    // we ever decide to fully handle a variable width font.
    void displayResult(int initPrec, int leastDigPos, String truncatedWholePart) {
        mLastPos = INVALID;
        synchronized(mWidthLock) {
            mCurrentPos = (int) Math.ceil(initPrec * mCharWidth);
        }
        // Should logically be
        // mMinPos = - (int) Math.ceil(getPaint().measureText(truncatedWholePart)), but
        // we eventually transalate to a character position by dividing by mCharWidth.
        // To avoid rounding issues, we use the analogous computation here.
        mMinPos = - (int) Math.ceil(truncatedWholePart.length() * mCharWidth);
        if (leastDigPos < MAX_RIGHT_SCROLL) {
            mMaxPos = Math.min((int) Math.ceil(addExpSpace(leastDigPos) * mCharWidth),
                    MAX_RIGHT_SCROLL);
        } else {
            mMaxPos = MAX_RIGHT_SCROLL;
        }
        mScrollable = (leastDigPos != (initPrec == -1 ? 0 : initPrec));
                // We assume that initPrec allows most significant digit to be displayed.
                // If there is nothing to the right of initPrec, there is no point in scrolling.
        redisplay();
    }

    void displayError(int resourceId) {
        mValid = true;
        mScrollable = false;
        setText(resourceId);
    }

    private final int MAX_COPY_SIZE = 1000000;

    // Format a result returned by Evaluator.getString() into a single line containing ellipses
    // (if appropriate) and an exponent (if appropriate).  digs is the value that was passed to
    // getString and thus identifies the significance of the rightmost digit.
    // We add two distinct kinds of exponents:
    // 1) If the final result contains the leading digit we use standard scientific notation.
    // 2) If not, we add an exponent corresponding to an interpretation of the final result as
    //    an integer.
    // We add an ellipsis on the left if the result was truncated.
    // We add ellipses and exponents in a way that leaves most digits in the position they
    // would have been in had we not done so.
    // This minimizes jumps as a result of scrolling.  Result is NOT internationalized,
    // uses "e" for exponent.
    public String formatResult(String res, int digs,
                               int maxDigs, boolean truncated,
                               boolean negative) {
        if (truncated) {
            res = KeyMaps.ELLIPSIS + res.substring(1, res.length());
        }
        int decIndex = res.indexOf('.');
        int resLen = res.length();
        if (decIndex == -1 && digs != -1) {
            // No decimal point displayed, and it's not just to the right of the last digit.
            // Add an exponent to let the user track which digits are currently displayed.
            // This is a bit tricky, since the number of displayed digits affects the displayed
            // exponent, which can affect the room we have for mantissa digits.  We occasionally
            // display one digit too few. This is sometimes unavoidable, but we could
            // avoid it in more cases.
            int exp = digs > 0 ? -digs : -digs - 1;
                    // Can be used as TYPE (2) EXPONENT. -1 accounts for decimal point.
            int msd;  // Position of most significant digit in res or indication its outside res.
            boolean hasPoint = false;
            if (truncated) {
                msd = -1;
            } else {
                msd = Evaluator.getMsdPos(res);  // INVALID_MSD is OK
            }
            if (msd < maxDigs - 1 && msd >= 0) {
                // TYPE (1) EXPONENT computation and transformation:
                // Leading digit is in display window. Use standard calculator scientific notation
                // with one digit to the left of the decimal point. Insert decimal point and
                // delete leading zeroes.
                String fraction = res.substring(msd + 1, resLen);
                res = (negative ? "-" : "") + res.substring(msd, msd+1) + "." + fraction;
                exp += resLen - msd - 1;
                // Original exp was correct for decimal point at right of fraction.
                // Adjust by length of fraction.
                resLen = res.length();
                hasPoint = true;
            }
            if (exp != 0 || truncated) {
                // Actually add the exponent of either type:
                String expAsString = Integer.toString(exp);
                int expDigits = expAsString.length();
                int dropDigits = expDigits + 1;
                    // Drop digits even if there is room. Otherwise the scrolling gets jumpy.
                if (dropDigits >= resLen - 1) {
                    dropDigits = Math.max(resLen - 2, 0);
                    // Jumpy is better than no mantissa.
                }
                if (!hasPoint) {
                    // Special handling for TYPE(2) EXPONENT:
                    exp += dropDigits;
                    expAsString = Integer.toString(exp);
                    // Adjust for digits we are about to drop to drop to make room for exponent.
                    // This can affect the room we have for the mantissa. We adjust only for
                    // positive exponents, when it could otherwise result in a truncated
                    // displayed result.
                    if (exp > 0 && expAsString.length() > expDigits) {
                        // ++expDigits; (dead code)
                        ++dropDigits;
                        ++exp;
                        // This cannot increase the length a second time.
                    }
                }
                res = res.substring(0, resLen - dropDigits);
                res = res + "e" + expAsString;
            } // else don't add zero exponent
        }
        return res;
    }

    // Get formatted, but not internationalized, result from
    // mEvaluator.
    private String getFormattedResult(int pos, int maxSize) {
        final boolean truncated[] = new boolean[1];
        final boolean negative[] = new boolean[1];
        final int requested_prec[] = {pos};
        final String raw_res = mEvaluator.getString(requested_prec, maxSize, truncated, negative);
        return formatResult(raw_res, requested_prec[0], maxSize, truncated[0], negative[0]);
   }

    // Return entire result (within reason) up to current displayed precision.
    public String getFullText() {
        if (!mValid) return "";
        if (!mScrollable) return getText().toString();
        int currentCharPos = getCurrentCharPos();
        return KeyMaps.translateResult(getFormattedResult(currentCharPos, MAX_COPY_SIZE));
    }

    public boolean fullTextIsExact() {
        BoundedRational rat = mEvaluator.getRational();
        int currentCharPos = getCurrentCharPos();
        if (currentCharPos == -1) {
            // Suppressing decimal point; still showing all integral digits.
            currentCharPos = 0;
        }
        // TODO: Could handle scientific notation cases better;
        // We currently treat those conservatively as approximate.
        return (currentCharPos >= BoundedRational.digitsRequired(rat));
    }

    /**
     * Return the maximum number of characters that will fit in the result display.
     * May be called asynchronously from non-UI thread.
     */
    int getMaxChars() {
        int result;
        synchronized(mWidthLock) {
            result = (int) Math.floor(mWidthConstraint / mCharWidth);
            // We can apparently finish evaluating before onMeasure in CalculatorText has been
            // called, in which case we get 0 or -1 as the width constraint.
        }
        if (result <= 0) {
            // Return something conservatively big, to force sufficient evaluation.
            return MAX_WIDTH;
        } else {
            // Always allow for the ellipsis character which already accounted for in the width
            // constraint.
            return result + 1;
        }
    }

    /**
     * @return {@code true} if the currently displayed result is scrollable
     */
    public boolean isScrollable() {
        return mScrollable;
    }

    int getCurrentCharPos() {
        synchronized(mWidthLock) {
            return (int) Math.ceil(mCurrentPos / mCharWidth);
        }
    }

    void clear() {
        mValid = false;
        mScrollable = false;
        setText("");
    }

    void redisplay() {
        int currentCharPos = getCurrentCharPos();
        int maxChars = getMaxChars();
        String result = getFormattedResult(currentCharPos, maxChars);
        int epos = result.indexOf('e');
        result = KeyMaps.translateResult(result);
        if (epos > 0 && result.indexOf('.') == -1) {
          // Gray out exponent if used as position indicator
            SpannableString formattedResult = new SpannableString(result);
            formattedResult.setSpan(mExponentColorSpan, epos, result.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(formattedResult);
        } else {
            setText(result);
        }
        mValid = true;
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
                postInvalidateOnAnimation();
            }
        }
    }

    // Copy support:

    private ActionMode.Callback mCopyActionModeCallback = new ActionMode.Callback() {
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
            mActionMode = null;
        }
    };

    public boolean stopActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
            return true;
        }
        return false;
    }

    private void setPrimaryClip(ClipData clip) {
        ClipboardManager clipboard = (ClipboardManager) getContext().
                                               getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(clip);
    }

    private void copyContent() {
        final CharSequence text = getFullText();
        ClipboardManager clipboard =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        // We include a tag URI, to allow us to recognize our own results and handle them
        // specially.
        ClipData.Item newItem = new ClipData.Item(text, null, mEvaluator.capture());
        String[] mimeTypes = new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData cd = new ClipData("calculator result", mimeTypes, newItem);
        clipboard.setPrimaryClip(cd);
        Toast.makeText(getContext(), R.string.text_copied_toast, Toast.LENGTH_SHORT).show();
    }

}
