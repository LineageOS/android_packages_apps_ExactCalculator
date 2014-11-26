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

// TODO: Fix evaluation interface so the evaluator returns entire
//       result, and display can properly handle variable width font.
// TODO: Fix font handling and scaling in result display.
// TODO: Fix placement of inverse trig buttons.
// TODO: Add Degree/Radian switch and display.
// TODO: Handle physical keyboard correctly.
// TODO: Fix internationalization, including result.
// TODO: Check and fix accessability issues.
// TODO: Support pasting of at least full result.  (Rounding?)
// TODO: Copy/paste in formula.

package com.android.calculator2;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroupOverlay;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import com.android.calculator2.CalculatorEditText.OnTextSizeChangeListener;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.text.DecimalFormatSymbols;  // TODO: May eventually not need this here.

public class Calculator extends Activity
        implements OnTextSizeChangeListener, OnLongClickListener, OnMenuItemClickListener {

    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;

    private enum CalculatorState {
        INPUT,          // Result and formula both visible, no evaluation requested,
                        // Though result may be visible on bottom line.
        EVALUATE,       // Both visible, evaluation requested, evaluation/animation incomplete.
        INIT,           // Very temporary state used as alternative to EVALUATE
                        // during reinitialization.  Do not animate on completion.
        ANIMATE,        // Result computed, animation to enlarge result window in progress.
        RESULT,         // Result displayed, formula invisible.
                        // If we are in RESULT state, the formula was evaluated without
                        // error to initial precision.
        ERROR           // Error displayed: Formula visible, result shows error message.
                        // Display similar to INPUT state.
    }
    // Normal transition sequence is
    // INPUT -> EVALUATE -> ANIMATE -> RESULT (or ERROR) -> INPUT
    // A RESULT -> ERROR transition is possible in rare corner cases, in which
    // a higher precision evaluation exposes an error.  This is possible, since we
    // initially evaluate assuming we were given a well-defined problem.  If we
    // were actually asked to compute sqrt(<extremely tiny negative number>) we produce 0
    // unless we are asked for enough precision that we can distinguish the argument from zero.
    // TODO: Consider further heuristics to reduce the chance of observing this?
    //       It already seems to be observable only in contrived cases.
    // ANIMATE, ERROR, and RESULT are translated to an INIT state if the application
    // is restarted in that state.  This leads us to recompute and redisplay the result
    // ASAP.
    // TODO: Possibly save a bit more information, e.g. its initial display string
    // or most significant digit position, to speed up restart.

    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            setState(CalculatorState.INPUT);
            mEvaluator.evaluateAndShowResult();
        }
    };

    private final OnKeyListener mFormulaOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        mCurrentButton = mEqualButton;
                        onEquals();
                    }
                    // ignore all other actions
                    return true;
            }
            return false;
        }
    };

    private static final String NAME = Calculator.class.getName();
    private static final String KEY_DISPLAY_STATE = NAME + "_display_state";
    private static final String KEY_EVAL_STATE = NAME + "_eval_state";
                // Associated value is a byte array holding both mCalculatorState
                // and the (much more complex) evaluator state.

    private CalculatorState mCurrentState;
    private Evaluator mEvaluator;

    private View mDisplayView;
    private CalculatorEditText mFormulaEditText;
    private CalculatorResult mResult;
    private ViewPager mPadViewPager;
    private View mDeleteButton;
    private View mEqualButton;
    private View mClearButton;
    private View mOverflowMenuButton;

    private View mCurrentButton;
    private Animator mCurrentAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        mDisplayView = findViewById(R.id.display);
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
        mResult = (CalculatorResult) findViewById(R.id.result);
        mPadViewPager = (ViewPager) findViewById(R.id.pad_pager);
        mDeleteButton = findViewById(R.id.del);
        mClearButton = findViewById(R.id.clr);
        mEqualButton = findViewById(R.id.pad_numeric).findViewById(R.id.eq);
        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }
        mOverflowMenuButton = findViewById(R.id.overflow_menu);

        mEvaluator = new Evaluator(this, mResult);
        mResult.setEvaluator(mEvaluator);

        if (savedInstanceState != null) {
            setState(CalculatorState.values()[
                savedInstanceState.getInt(KEY_DISPLAY_STATE,
                                          CalculatorState.INPUT.ordinal())]);
            byte[] state =
                    savedInstanceState.getByteArray(KEY_EVAL_STATE);
            if (state != null) {
                try (ObjectInput in = new ObjectInputStream(new ByteArrayInputStream(state))) {
                    mEvaluator.restoreInstanceState(in);
                } catch (Throwable ignored) {
                    // When in doubt, revert to clean state
                    mCurrentState = CalculatorState.INPUT;
                    mEvaluator.clear();
                }
            }
        }
        mFormulaEditText.addTextChangedListener(mFormulaTextWatcher);
        mFormulaEditText.setOnKeyListener(mFormulaOnKeyListener);
        mFormulaEditText.setOnTextSizeChangeListener(this);
        mDeleteButton.setOnLongClickListener(this);
        if (mCurrentState == CalculatorState.EVALUATE) {
            // Odd case.  Evaluation probably took a long time.  Let user ask for it again
            mCurrentState = CalculatorState.INPUT;
            // TODO: This can happen if the user rotates the screen.
            // Is this rotate-to-abort behavior correct?  Revisit after experimentation.
        }
        if (mCurrentState != CalculatorState.INPUT) {
            setState(CalculatorState.INIT);
            mEvaluator.evaluateAndShowResult();
            mEvaluator.requireResult();
        } else {
            redisplayFormula();
            mEvaluator.evaluateAndShowResult();
        }
        // TODO: We're currently not saving and restoring scroll position.
        //       We probably should.  Details may require care to deal with:
        //         - new display size
        //         - slow recomputation if we've scrolled far.
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // If there's an animation in progress, cancel it first to ensure our state is up-to-date.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        super.onSaveInstanceState(outState);
        outState.putInt(KEY_DISPLAY_STATE, mCurrentState.ordinal());
        ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        try (ObjectOutput out = new ObjectOutputStream(byteArrayStream)) {
            mEvaluator.saveInstanceState(out);
        } catch (IOException e) {
            // Impossible; No IO involved.
            throw new AssertionError("Impossible IO exception", e);
        }
        outState.putByteArray(KEY_EVAL_STATE, byteArrayStream.toByteArray());
    }

    private void setState(CalculatorState state) {
        if (mCurrentState != state) {
            if (state == CalculatorState.INPUT) {
                restoreDisplayPositions();
            }
            mCurrentState = state;

            if (mCurrentState == CalculatorState.RESULT
                    || mCurrentState == CalculatorState.ERROR) {
                mDeleteButton.setVisibility(View.GONE);
                mClearButton.setVisibility(View.VISIBLE);
            } else {
                mDeleteButton.setVisibility(View.VISIBLE);
                mClearButton.setVisibility(View.GONE);
            }

            if (mCurrentState == CalculatorState.ERROR) {
                final int errorColor = getResources().getColor(R.color.calculator_error_color);
                mFormulaEditText.setTextColor(errorColor);
                mResult.setTextColor(errorColor);
                getWindow().setStatusBarColor(errorColor);
            } else {
                mFormulaEditText.setTextColor(
                        getResources().getColor(R.color.display_formula_text_color));
                mResult.setTextColor(
                        getResources().getColor(R.color.display_result_text_color));
                getWindow().setStatusBarColor(
                        getResources().getColor(R.color.calculator_accent_color));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mPadViewPager == null || mPadViewPager.getCurrentItem() == 0) {
            // If the user is currently looking at the first pad (or the pad is not paged),
            // allow the system to handle the Back button.
            super.onBackPressed();
        } else {
            // Otherwise, select the previous pad.
            mPadViewPager.setCurrentItem(mPadViewPager.getCurrentItem() - 1);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        // If there's an animation in progress, cancel it so the user interaction can be handled
        // immediately.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }
    }


    public void onButtonClick(View view) {
        mCurrentButton = view;
        int id = view.getId();

        // Always cancel in-progress evaluation.
        // If we were waiting for the result, do nothing else.
        mEvaluator.cancelAll();
        if (mCurrentState == CalculatorState.EVALUATE
                || mCurrentState == CalculatorState.ANIMATE) {
            onCancelled();
            return;
        }
        switch (id) {
            case R.id.overflow_menu:
                PopupMenu menu = constructPopupMenu();
                if (menu != null) {
                    menu.show();
                }
                break;
            case R.id.eq:
                onEquals();
                break;
            case R.id.del:
                onDelete();
                break;
            case R.id.clr:
                onClear();
                break;
            default:
                if (mCurrentState == CalculatorState.ERROR) {
                    setState(CalculatorState.INPUT);
                }
                if (mCurrentState == CalculatorState.RESULT) {
                    if (KeyMaps.isBinary(id) || KeyMaps.isSuffix(id)) {
                        mEvaluator.collapse();
                    } else {
                        mEvaluator.clear();
                    }
                }
                if (!mEvaluator.append(id)) {
                    // TODO: Some user visible feedback?
                }
                // TODO: Could do this more incrementally.
                redisplayFormula();
                setState(CalculatorState.INPUT);
                mResult.clear();
                mEvaluator.evaluateAndShowResult();
                break;
        }
    }

    void redisplayFormula() {
        mFormulaEditText.setText(mEvaluator.getExpr().toString(this));
    }

    @Override
    public boolean onLongClick(View view) {
        mCurrentButton = view;

        if (view.getId() == R.id.del) {
            onClear();
            return true;
        }
        return false;
    }

    // Initial evaluation completed successfully.  Initiate display.
    public void onEvaluate(int initDisplayPrec, String truncatedWholeNumber) {
        if (mCurrentState == CalculatorState.INPUT) {
            // Just update small result display.
            mResult.displayResult(initDisplayPrec, truncatedWholeNumber);
        } else { // in EVALUATE or INIT state
            mResult.displayResult(initDisplayPrec, truncatedWholeNumber);
            onResult(mCurrentState != CalculatorState.INIT);
            setState(CalculatorState.RESULT);
        }
    }

    public void onCancelled() {
        // We should be in EVALUATE state.
        // Display is still in input state.
        setState(CalculatorState.INPUT);
    }

    // Reevaluation completed; ask result to redisplay current value.
    public void onReevaluate()
    {
        mResult.redisplay();
    }

    @Override
    public void onTextSizeChanged(final TextView textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) {
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX = (1.0f - textScale) *
                (textView.getWidth() / 2.0f - textView.getPaddingEnd());
        final float translationY = (1.0f - textScale) *
                (textView.getHeight() / 2.0f - textView.getPaddingBottom());

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(textView, View.SCALE_X, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.SCALE_Y, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, translationX, 0.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, translationY, 0.0f));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    private void onEquals() {
        if (mCurrentState == CalculatorState.INPUT) {
            setState(CalculatorState.EVALUATE);
            mEvaluator.requireResult();
        }
    }

    private void onDelete() {
        // Delete works like backspace; remove the last character from the expression.
        mEvaluator.cancelAll();
        mEvaluator.getExpr().delete();
        redisplayFormula();
        mResult.clear();
        mEvaluator.evaluateAndShowResult();
    }

    private void reveal(View sourceView, int colorRes, AnimatorListener listener) {
        final ViewGroupOverlay groupOverlay =
                (ViewGroupOverlay) getWindow().getDecorView().getOverlay();

        final Rect displayRect = new Rect();
        mDisplayView.getGlobalVisibleRect(displayRect);

        // Make reveal cover the display and status bar.
        final View revealView = new View(this);
        revealView.setBottom(displayRect.bottom);
        revealView.setLeft(displayRect.left);
        revealView.setRight(displayRect.right);
        revealView.setBackgroundColor(getResources().getColor(colorRes));
        groupOverlay.add(revealView);

        final int[] clearLocation = new int[2];
        sourceView.getLocationInWindow(clearLocation);
        clearLocation[0] += sourceView.getWidth() / 2;
        clearLocation[1] += sourceView.getHeight() / 2;

        final int revealCenterX = clearLocation[0] - revealView.getLeft();
        final int revealCenterY = clearLocation[1] - revealView.getTop();

        final double x1_2 = Math.pow(revealView.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(revealView.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(revealView.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

        final Animator revealAnimator =
                ViewAnimationUtils.createCircularReveal(revealView,
                        revealCenterX, revealCenterY, 0.0f, revealRadius);
        revealAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_longAnimTime));
        revealAnimator.addListener(listener);

        final Animator alphaAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        alphaAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_mediumAnimTime));

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(revealAnimator).before(alphaAnimator);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                groupOverlay.remove(revealView);
                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }

    private void onClear() {
        if (mEvaluator.getExpr().isEmpty()) {
            return;
        }
        mResult.clear();
        mEvaluator.clear();
        reveal(mCurrentButton, R.color.calculator_accent_color, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                redisplayFormula();
            }
        });
    }

    // Evaluation encountered en error.  Display the error.
    void onError(final int errorResourceId) {
        if (mCurrentState != CalculatorState.EVALUATE) {
            // Only animate error on evaluate.
            return;
        }

        setState(CalculatorState.ANIMATE);
        reveal(mCurrentButton, R.color.calculator_error_color, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setState(CalculatorState.ERROR);
                mResult.displayError(errorResourceId);
            }
        });
    }


    // Animate movement of result into the top formula slot.
    // Result window now remains translated in the top slot while the result is displayed.
    // (We convert it back to formula use only when the user provides new input.)
    // Historical note: In the Lollipop version, this invisibly and instantaeously moved
    // formula and result displays back at the end of the animation.  We no longer do that,
    // so that we can continue to properly support scrolling of the result.
    // We assume the result already contains the text to be expanded.
    private void onResult(boolean animate) {
        // Calculate the values needed to perform the scale and translation animations,
        // accounting for how the scale will affect the final position of the text.
        // We want to fix the character size in the display to avoid weird effects
        // when we scroll.
        final float resultScale =
                mFormulaEditText.getVariableTextSize(mResult.getText().toString())
                                                     / mResult.getTextSize() - 0.1f;
        // FIXME:  This doesn't work correctly.  The -0.1 is a fudge factor to
        // improve things slightly.  Remove when fixed.
        final float resultTranslationX = (1.0f - resultScale) *
                (mResult.getWidth() / 2.0f - mResult.getPaddingEnd());
        final float resultTranslationY = (1.0f - resultScale) *
                (mResult.getHeight() / 2.0f - mResult.getPaddingBottom()) +
                (mFormulaEditText.getBottom() - mResult.getBottom()) +
                (mResult.getPaddingBottom() - mFormulaEditText.getPaddingBottom());
        final float formulaTranslationY = -mFormulaEditText.getBottom();

        // TODO: Reintroduce textColorAnimator?
        //       The initial and final colors seemed to be the same in L.
        //       With the new model, the result logically changes back to a formula
        //       only when we switch back to INPUT state, so it's unclear that animating
        //       a color change here makes sense.
        if (animate) {
            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(mResult, View.SCALE_X, resultScale),
                    ObjectAnimator.ofFloat(mResult, View.SCALE_Y, resultScale),
                    ObjectAnimator.ofFloat(mResult, View.TRANSLATION_X, resultTranslationX),
                    ObjectAnimator.ofFloat(mResult, View.TRANSLATION_Y, resultTranslationY),
                    ObjectAnimator.ofFloat(mFormulaEditText, View.TRANSLATION_Y,
                                           formulaTranslationY));
            animatorSet.setDuration(
                    getResources().getInteger(android.R.integer.config_longAnimTime));
            animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    // Result should already be displayed; no need to do anything.
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    setState(CalculatorState.RESULT);
                    mCurrentAnimator = null;
                }
            });

            mCurrentAnimator = animatorSet;
            animatorSet.start();
        } else /* No animation desired; get there fast, e.g. when restarting */ {
            mResult.setScaleX(resultScale);
            mResult.setScaleY(resultScale);
            mResult.setTranslationX(resultTranslationX);
            mResult.setTranslationY(resultTranslationY);
            mFormulaEditText.setTranslationY(formulaTranslationY);
        }
    }

    // Restore positions of the formula and result displays back to their original,
    // pre-animation state.
    private void restoreDisplayPositions() {
        // Clear result.
        mResult.setText("");
        // Reset all of the values modified during the animation.
        mResult.setScaleX(1.0f);
        mResult.setScaleY(1.0f);
        mResult.setTranslationX(0.0f);
        mResult.setTranslationY(0.0f);
        mFormulaEditText.setTranslationY(0.0f);

        mFormulaEditText.requestFocus();
     }

    // Overflow menu handling.
    private PopupMenu constructPopupMenu() {
        final PopupMenu popupMenu = new PopupMenu(this, mOverflowMenuButton);
        mOverflowMenuButton.setOnTouchListener(popupMenu.getDragToOpenListener());
        final Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.menu);
        popupMenu.setOnMenuItemClickListener(this);
        onPrepareOptionsMenu(menu);
        return popupMenu;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_help:
                displayHelpMessage();
                return true;
            case R.id.menu_about:
                displayAboutPage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void displayHelpMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (mPadViewPager != null) {
            builder.setMessage(getResources().getString(R.string.help_message)
                               + getResources().getString(R.string.help_pager));
        } else {
            builder.setMessage(R.string.help_message);
        }
        builder.setNegativeButton(R.string.dismiss,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) { }
                    })
               .show();
    }

    private void displayAboutPage() {
        WebView wv = new WebView(this);
        wv.loadUrl("file:///android_asset/about.txt");
        new AlertDialog.Builder(this)
                .setView(wv)
                .setNegativeButton(R.string.dismiss,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) { }
                    })
                .show();
    }

    // TODO: Probably delete the following method and all of its callers before release.
    //       Definitely delete most of its callers.
    private static final String LOG_TAG = "Calculator";

    static void log(String message) {
        Log.v(LOG_TAG, message);
    }

    // EVERYTHING BELOW HERE was preserved from the KitKat version of the
    // calculator, since we expect to need it again once functionality is a bit more
    // more complete.  But it has not yet been wired in correctly, and
    // IS CURRENTLY UNUSED.

    // Is s a valid constant?
    // TODO: Possibly generalize to scientific notation, hexadecimal, etc.
    static boolean isConstant(CharSequence s) {
        boolean sawDecimal = false;
        boolean sawDigit = false;
        final char decimalPt = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        int len = s.length();
        int i = 0;
        while (i < len && Character.isWhitespace(s.charAt(i))) ++i;
        if (i < len && s.charAt(i) == '-') ++i;
        for (; i < len; ++i) {
            char c = s.charAt(i);
            if (c == '.' || c == decimalPt) {
                if (sawDecimal) return false;
                sawDecimal = true;
            } else if (Character.isDigit(c)) {
                sawDigit = true;
            } else {
                break;
            }
        }
        while (i < len && Character.isWhitespace(s.charAt(i))) ++i;
        return i == len && sawDigit;
    }

    // Paste a valid character sequence representing a constant.
    void paste(CharSequence s) {
        mEvaluator.cancelAll();
        if (mCurrentState == CalculatorState.RESULT) {
            mEvaluator.clear();
        }
        int len = s.length();
        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                mEvaluator.append(KeyMaps.keyForChar(c));
            }
        }
    }

}
