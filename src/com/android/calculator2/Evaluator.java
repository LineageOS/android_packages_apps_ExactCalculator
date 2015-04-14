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

//
// This implements the calculator evaluation logic.
// An evaluation is started with a call to evaluateAndShowResult().
// This starts an asynchronous computation, which requests display
// of the initial result, when available.  When initial evaluation is
// complete, it calls the calculator onEvaluate() method.
// This occurs in a separate event, and may happen quite a bit
// later.  Once a result has been computed, and before the underlying
// expression is modified, the getString method may be used to produce
// Strings that represent approximations to various precisions.
//
// Actual expressions being evaluated are represented as CalculatorExprs,
// which are just slightly preprocessed sequences of keypresses.
//
// The Evaluator owns the expression being edited and associated
// state needed for evaluating it. It provides functionality for
// saving and restoring this state.  However the current
// CalculatorExpr is exposed to the client, and may be directly modified
// after cancelling any in-progress computations by invoking the
// cancelAll() method.
//
// When evaluation is requested by the user, we invoke the eval
// method on the CalculatorExpr from a background AsyncTask.
// A subsequent getString() callback returns immediately, though it may
// return a result containing placeholder '?' characters.
// In that case we start a background task, which invokes the
// onReevaluate() callback when it completes.
// In both cases, the background task
// computes the appropriate result digits by evaluating
// the constructive real (CR) returned by CalculatorExpr.eval()
// to the required precision.
//
// We cache the best approximation we have already computed.
// We compute generously to allow for
// some scrolling without recomputation and to minimize the chance of
// digits flipping from "0000" to "9999".  The best known
// result approximation is maintained as a string by mCache (and
// in a different format by the CR representation of the result).
// When we are in danger of not having digits to display in response
// to further scrolling, we initiate a background computation to higher
// precision.  If we actually do fall behind, we display placeholder
// characters, e.g. '?', and schedule a display update when the computation
// completes.
// The code is designed to ensure that the error in the displayed
// result (excluding any '?' characters) is always strictly less than 1 in
// the last displayed digit.  Typically we actually display a prefix
// of a result that has this property and additionally is computed to
// a significantly higher precision.  Thus we almost always round correctly
// towards zero.  (Fully correct rounding towards zero is not computable.)

package com.android.calculator2;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;

import com.hp.creals.CR;
import com.hp.creals.PrecisionOverflowError;
import com.hp.creals.AbortedError;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

class Evaluator {
    private final Calculator mCalculator;
    private final CalculatorResult mResult;  // The result display View
    private CalculatorExpr mExpr;      // Current calculator expression
    private CalculatorExpr mSaved;     // Last saved expression.
                                       // Either null or contains a single
                                       // preevaluated node.
    private String mSavedName;         // A hopefully unique name associated
                                       // with mSaved.
    // The following are valid only if an evaluation
    // completed successfully.
        private CR mVal;               // value of mExpr as constructive real
        private BoundedRational mRatVal; // value of mExpr as rational or null
        private int mLastDigs;   // Last digit argument passed to getString()
                                 // for this result, or the initial preferred
                                 // precision.
    private boolean mDegreeMode;       // Currently in degree (not radian) mode
    private final Handler mTimeoutHandler;

    static final char MINUS = '\u2212';

    static final BigInteger BIG_MILLION = BigInteger.valueOf(1000000);


    private final char decimalPt =
                DecimalFormatSymbols.getInstance().getDecimalSeparator();

    private final static int MAX_DIGITS = 100;  // Max digits displayed at once.
    private final static int EXTRA_DIGITS = 20;
                // Extra computed digits to minimize probably we will have
                // to change our minds about digits we already displayed.
                // (The correct digits are technically not computable using our
                // representation:  An off by one error in the last digits
                // can affect earlier ones, even though the display is
                // always within one in the lsd.  This is only visible
                // for results that end in EXTRA_DIGITS 9s or 0s, but are
                // not integers.)
                // We do use these extra digits to display while we are
                // computing the correct answer.  Thus they may be
                // temporarily visible.
   private final static int PRECOMPUTE_DIGITS = 20;
                                // Extra digits computed to minimize
                                // reevaluations during scrolling.

    // We cache the result as a string to accelerate scrolling.
    // The cache is filled in by the UI thread, but this may
    // happen asynchronously, much later than the request.
    private String mCache;       // Current best known result, which includes
    private int mCacheDigs = 0;  // mCacheDigs digits to the right of the
                                 // decimal point.  Always positive.
                                 // mCache is valid when non-null
                                 // unless the expression has been
                                 // changed since the last evaluation call.
    private int mCacheDigsReq;  // Number of digits that have been
                                // requested.  Only touched by UI
                                // thread.
    private final int INVALID_MSD = Integer.MIN_VALUE;
    private int mMsd = INVALID_MSD;  // Position of most significant digit
                                     // in current cached result, if determined.
                                     // This is just the index in mCache
                                     // holding the msd.
    private final int MAX_MSD_PREC = 100;
                             // The largest number of digits to the right
                             // of the decimal point to which we will
                             // evaluate to compute proper scientific
                             // notation for values close to zero.

    private AsyncReevaluator mCurrentReevaluator;
        // The one and only un-cancelled and currently running reevaluator.
        // Touched only by UI thread.

    private AsyncDisplayResult mEvaluator;
        // Currently running expression evaluator, if any.

    Evaluator(Calculator calculator,
              CalculatorResult resultDisplay) {
        mCalculator = calculator;
        mResult = resultDisplay;
        mExpr = new CalculatorExpr();
        mSaved = new CalculatorExpr();
        mSavedName = "none";
        mTimeoutHandler = new Handler();
        mDegreeMode = false;  // Remain compatible with previous versions.
    }

    // Result of asynchronous reevaluation
    class ReevalResult {
        ReevalResult(String s, int p) {
            mNewCache = s;
            mNewCacheDigs = p;
        }
        final String mNewCache;
        final int mNewCacheDigs;
    }

    // Compute new cache contents accurate to prec digits to the right
    // of the decimal point.  Ensure that redisplay() is called after
    // doing so.  If the evaluation fails for reasons other than a
    // timeout, ensure that DisplayError() is called.
    class AsyncReevaluator extends AsyncTask<Integer, Void, ReevalResult> {
        @Override
        protected ReevalResult doInBackground(Integer... prec) {
            try {
                int eval_prec = prec[0].intValue();
                return new ReevalResult(mVal.toString(eval_prec), eval_prec);
            } catch(ArithmeticException e) {
                return null;
            } catch(PrecisionOverflowError e) {
                return null;
            } catch(AbortedError e) {
                // Should only happen if the task was cancelled,
                // in which case we don't look at the result.
                return null;
            }
        }
        @Override
        protected void onPostExecute(ReevalResult result) {
            if (result == null) {
                // This should only be possible in the extremely rare
                // case of encountering a domain error while reevaluating.
                mCalculator.onError(R.string.error_nan);
            } else {
                if (result.mNewCacheDigs < mCacheDigs) {
                    throw new Error("Unexpected onPostExecute timing");
                }
                mCache = result.mNewCache;
                mCacheDigs = result.mNewCacheDigs;
                mCalculator.onReevaluate();
            }
            mCurrentReevaluator = null;
        }
        // On cancellation we do nothing; invoker should have
        // left no trace of us.
    }

    // Result of initial asynchronous computation
    private static class InitialResult {
        InitialResult(CR val, BoundedRational ratVal, String s, int p, int idp) {
            mErrorResourceId = Calculator.INVALID_RES_ID;
            mVal = val;
            mRatVal = ratVal;
            mNewCache = s;
            mNewCacheDigs = p;
            mInitDisplayPrec = idp;
        }
        InitialResult(int errorResourceId) {
            mErrorResourceId = errorResourceId;
            mVal = CR.valueOf(0);
            mRatVal = BoundedRational.ZERO;
            mNewCache = "BAD";
            mNewCacheDigs = 0;
            mInitDisplayPrec = 0;
        }
        boolean isError() {
            return mErrorResourceId != Calculator.INVALID_RES_ID;
        }
        final int mErrorResourceId;
        final CR mVal;
        final BoundedRational mRatVal;
        final String mNewCache;       // Null iff it can't be computed.
        final int mNewCacheDigs;
        final int mInitDisplayPrec;
    }

    private void displayCancelledMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mCalculator);
        builder.setMessage(R.string.cancelled);
        builder.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) { }
            });
        builder.create();
    }

    private final long MAX_TIMEOUT = 60000;
                                   // Milliseconds.
                                   // Longer is unlikely to help unless
                                   // we get more heap space.
    private long mTimeout = 2000;  // Timeout for requested evaluations,
                                   // in milliseconds.
                                   // This is currently not saved and restored
                                   // with the state; we initially
                                   // reenable the timeout when the
                                   // calculator is restarted.
                                   // We'll call that a feature; others
                                   // might argue it's a bug.
    private final long mQuickTimeout = 50;
                                   // Timeout for unrequested, speculative
                                   // evaluations, in milliseconds.

    private void displayTimeoutMessage() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mCalculator);
        builder.setMessage(R.string.timeout);
        builder.setNegativeButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) { }
            });
        builder.setPositiveButton(R.string.ok_remove_timeout,
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    mTimeout = MAX_TIMEOUT;
                }
            });
        builder.create().show();
    }

    final Runnable mTimeoutRunnable = new Runnable () {
        public void run () {
            if (cancelAll()) {
                displayTimeoutMessage();
            }
        }
    };

    final Runnable mQuickTimeoutRunnable = new Runnable () {
        public void run () {
            // Quietly cancel; we didn't really need it.
            cancelAll();
        }
    };

    // Compute initial cache contents and result when we're good and ready.
    // We leave the expression display up, with scrolling
    // disabled, until this computation completes.
    // Can result in an error display if something goes wrong.
    // By default we set a timeout to catch runaway computations.
    class AsyncDisplayResult extends AsyncTask<Void, Void, InitialResult> {
        private boolean mDm;  // degrees
        private boolean mRequired; // Result was requested by user.
        AsyncDisplayResult(boolean dm, boolean required) {
            mDm = dm;
            mRequired = required;
        }
        @Override
        protected void onPreExecute() {
            long timeout = mRequired? mTimeout : mQuickTimeout;
            if (timeout != 0) {
                mTimeoutHandler.postDelayed(
                    (mRequired? mTimeoutRunnable : mQuickTimeoutRunnable),
                    timeout);
            }
        }
        @Override
        protected InitialResult doInBackground(Void... nothing) {
            try {
                CalculatorExpr.EvalResult res = mExpr.eval(mDm);
                if (res == null) return null;
                int prec = 3;  // Enough for short representation
                String initCache = res.mVal.toString(prec);
                int msd = getMsdPos(initCache);
                if (BoundedRational.asBigInteger(res.mRatVal) == null
                        && msd == INVALID_MSD) {
                    prec = MAX_MSD_PREC;
                    initCache = res.mVal.toString(prec);
                    msd = getMsdPos(initCache);
                }
                int initDisplayPrec =
                        getPreferredPrec(initCache, msd,
                             BoundedRational.digitsRequired(res.mRatVal));
                int newPrec = initDisplayPrec + EXTRA_DIGITS;
                if (newPrec > prec) {
                    prec = newPrec;
                    initCache = res.mVal.toString(prec);
                }
                return new InitialResult(res.mVal, res.mRatVal,
                                         initCache, prec, initDisplayPrec);
            } catch (CalculatorExpr.SyntaxError e) {
                return new InitialResult(R.string.error_syntax);
            } catch(ArithmeticException e) {
                return new InitialResult(R.string.error_nan);
            } catch(PrecisionOverflowError e) {
                // Extremely unlikely unless we're actually dividing by
                // zero or the like.
                return new InitialResult(R.string.error_overflow);
            } catch(AbortedError e) {
                return new InitialResult(R.string.error_aborted);
            }
        }
        @Override
        protected void onPostExecute(InitialResult result) {
            mEvaluator = null;
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            if (result.isError()) {
                mCalculator.onError(result.mErrorResourceId);
                return;
            }
            mVal = result.mVal;
            mRatVal = result.mRatVal;
            mCache = result.mNewCache;
            mCacheDigs = result.mNewCacheDigs;
            mLastDigs = result.mInitDisplayPrec;
            int dotPos = mCache.indexOf('.');
            String truncatedWholePart = mCache.substring(0, dotPos);
            // Recheck display precision; it may change, since
            // display dimensions may have been unknow the first time.
            // In that case the initial evaluation precision should have
            // been conservative.
            // TODO: Could optimize by remembering display size and
            // checking for change.
            int init_prec = result.mInitDisplayPrec;
            int msd = getMsdPos(mCache);
            int new_init_prec = getPreferredPrec(mCache, msd,
                                BoundedRational.digitsRequired(mRatVal));
            if (new_init_prec < init_prec) {
                init_prec = new_init_prec;
            } else {
                // They should be equal.  But nothing horrible should
                // happen if they're not. e.g. because
                // CalculatorResult.MAX_WIDTH was too small.
            }
            mCalculator.onEvaluate(init_prec,truncatedWholePart);
        }
        @Override
        protected void onCancelled(InitialResult result) {
            mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
            displayCancelledMessage();
            mCalculator.onCancelled();
            // Just drop the evaluation; Leave expression displayed.
            return;
        }
    }


    // Start an evaluation to prec, and ensure that the
    // display is redrawn when it completes.
    private void ensureCachePrec(int prec) {
        if (mCache != null && mCacheDigs >= prec
                || mCacheDigsReq >= prec) return;
        if (mCurrentReevaluator != null) {
            // Ensure we only have one evaluation running at a time.
            mCurrentReevaluator.cancel(true);
            mCurrentReevaluator = null;
        }
        mCurrentReevaluator = new AsyncReevaluator();
        mCacheDigsReq = prec + PRECOMPUTE_DIGITS;
        mCurrentReevaluator.execute(mCacheDigsReq);
    }

    // Retrieve the preferred precision for the currently
    // displayed result, given the number of characters we
    // have room for and the current string approximation for
    // the result.
    // lastDigit is the position of the last digit on the right
    // or Integer.MAX_VALUE.
    // May be called in non-UI thread.
    int getPreferredPrec(String cache, int msd, int lastDigit) {
        int lineLength = mResult.getMaxChars();
        int wholeSize = cache.indexOf('.');
        // Don't display decimal point if result is an integer.
        if (lastDigit == 0) lastDigit = -1;
        if (lastDigit != Integer.MAX_VALUE
                && ((wholeSize <= lineLength && lastDigit == 0)
                    || wholeSize + lastDigit + 1 /* d.p. */ <= lineLength)) {
            // Prefer to display as integer, without decimal point
            if (lastDigit == 0) return -1;
            return lastDigit;
        }
        if (msd > wholeSize && msd <= wholeSize + 4) {
            // Display number without scientific notation.
            // Treat leading zero as msd.
            msd = wholeSize - 1;
        }
        return Math.min(msd, MAX_MSD_PREC) - wholeSize + lineLength - 2;
    }

    // Get a short representation of the value represented by
    // the string cache (presumed to contain at least 5 characters)
    // and possibly the exact integer i.
    private String getShortString(String cache, BigInteger i) {
        // The result is internationalized; we only display it.
        String res;
        boolean need_ellipsis = false;

        if (i != null && i.abs().compareTo(BIG_MILLION) < 0) {
            res = i.toString();
        } else {
            res = cache.substring(0,5);
            // Avoid a trailing period; doesn't work with ellipsis
            if (res.charAt(3) != '.') {
                res = res.substring(0,4);
            }
            // TODO: Don't do this in the unlikely case this is the
            // full representation.
            need_ellipsis = true;
        }
        res = KeyMaps.translateResult(res);
        if (need_ellipsis) {
            res += mCalculator.getResources()
                              .getString(R.string.ellipsis);
        }
        return res;
    }

    // Return the most significant digit position in the given string
    // or INVALID_MSD.
    private int getMsdPos(String s) {
        int len = s.length();
        int nonzeroPos = -1;
        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if (c != '-' && c != '.' && c != '0') {
                nonzeroPos = i;
                break;
            }
        }
        if (nonzeroPos >= 0 &&
            (nonzeroPos < len - 1 || s.charAt(nonzeroPos) != '1')) {
                return nonzeroPos;
        } else {
            // Unknown, or could change on reevaluation
            return INVALID_MSD;
        }

    }

    // Return most significant digit position in the cache, if determined,
    // INVALID_MSD ow.
    // If unknown, and we've computed less than DESIRED_PREC,
    // schedule reevaluation and redisplay, with higher precision.
    int getMsd() {
        if (mMsd != INVALID_MSD) return mMsd;
        if (mRatVal != null && mRatVal.signum() == 0) {
            return INVALID_MSD;  // None exists
        }
        int res = INVALID_MSD;
        if (mCache != null) {
            res = getMsdPos(mCache);
        }
        if (res == INVALID_MSD && mEvaluator == null
            && mCurrentReevaluator == null && mCacheDigs < MAX_MSD_PREC) {
            // We assert that mCache is not null, since there is no
            // evaluator running.
            ensureCachePrec(MAX_MSD_PREC);
            // Could reevaluate more incrementally, but we suspect that if
            // we have to reevaluate at all, the result is probably zero.
        }
        return res;
    }

    // Return a string with n placeholder characters.
    private String getPadding(int n) {
        StringBuilder padding = new StringBuilder();
        char something =
            mCalculator.getResources()
                       .getString(R.string.guessed_digit).charAt(0);
        for (int i = 0; i < n; ++i) {
            padding.append(something);
        }
        return padding.toString();
    }

    // Return the number of zero characters at the beginning of s
    private int leadingZeroes(String s) {
        int res = 0;
        int len = s.length();
        for (res = 0; res < len && s.charAt(res) == '0'; ++res) {}
        return res;
    }

    // TODO: The following should be refactored, particularly since
    // maxDigs should probably depend on the width of characters in
    // the result.
    // And we should try to at leas factor out the code to add the exponent.
    //
    // Return result to digs digits to the right of the decimal point
    // (minus any space occupied by exponent included in the result).
    // The result should be no longer than maxDigs.
    // The result is returned immediately, based on the
    // current cache contents, but it may contain question
    // marks for unknown digits.  It may also use uncertain
    // digits within EXTRA_DIGITS.  If either of those occurred,
    // schedule a reevaluation and redisplay operation.
    // digs may be negative to only retrieve digits to the left
    // of the decimal point.  (digs = 0 means we include
    // the decimal point, but nothing to the right.  Digs = -1
    // means we drop the decimal point and start at the ones
    // position.  Should not be invoked if mVal is null.
    String getString(int digs, int maxDigs) {
        mLastDigs = digs;
        // Make sure we eventually get a complete answer
            ensureCachePrec(digs + EXTRA_DIGITS);
            if (mCache == null) {
                // Nothing to do now; seems to happen on rare occasion
                // with weird user input timing; will be fixed later.
                return getPadding(1);
            }
        // Compute an appropriate substring of mCache.
        // We avoid returning a huge string to minimize string
        // allocation during scrolling.
        // Pad as needed.
            boolean truncated = false;  // Leading digits dropped.
            int len = mCache.length();
            // Don't scroll left past leftmost digit in mCache.
                int integralDigits = len - mCacheDigs;
                                // includes 1 for dec. pt
                if (mCache.charAt(0) == '-') --integralDigits;
                if (digs < -integralDigits + 1) digs = -integralDigits + 1;
            int offset = mCacheDigs - digs; // trailing digits to drop
            int deficit = 0;  // The number of digits we're short
            if (offset < 0) {
                offset = 0;
                deficit = Math.min(digs - mCacheDigs, maxDigs);
            }
            int endIndx = len - offset;
            if (endIndx < 1) return " ";
            int startIndx = (endIndx + deficit <= maxDigs) ?
                                0
                                : endIndx + deficit - maxDigs;
            String res;
            if (startIndx != 0) {
                truncated = true;
            }
            res = mCache.substring(startIndx, endIndx);
            if (deficit > 0) {
                res = res + getPadding(deficit);
                // Since we always compute past the decimal point,
                // this never fills in the spot where the decimal point
                // should go, and the rest of this can treat the
                // made-up symbols as though they were digits.
            }
        // Include exponent if necessary.
        // Replace least significant digits as necessary.
            if (res.indexOf('.') == -1 && digs != 1) {
                // No decimal point displayed, and it's not just
                // to the right of the last digit.
                // Add an exponent to let the user track which
                // digits are currently displayed.
                // This is a bit tricky, since the number of displayed
                // digits affects the displayed exponent, which can
                // affect the room we have for mantissa digits.
                // We occasionally display one digit too few.
                // This is sometimes unavoidable, but we could
                // avoid it in more cases.
                int exp = (digs > 0)? -digs : -digs - 1;
                        // accounts for decimal point
                int msd = getMsd();
                boolean hasPoint = false;
                final int minFractionDigits = 6;
                if (msd < endIndx - minFractionDigits && msd >= startIndx) {
                    // Leading digit is in display window
                    // Use standard calculator scientific notation
                    // with one digit to the left of the decimal point.
                    // Insert decimal point and delete leading zeroes.
                        int hasMinus = mCache.charAt(0) == '-'? 1 : 0;
                        int resLen = res.length();
                        int resZeroes = leadingZeroes(res);
                        String fraction =
                              res.substring(msd+1 - startIndx,
                                            resLen - 1 - hasMinus);
                        res = (hasMinus != 0? "-" : "")
                              + mCache.substring(msd, msd+1) + "."
                              + fraction;
                    exp += resLen - resZeroes - 1 - hasMinus;
                        // Decimal point moved across original res, except for
                        // leading digit and zeroes, and possibly minus sign.
                    truncated = false; // in spite of dropping leading 0s
                    hasPoint = true;
                }
                if (exp != 0 || truncated) {
                    String expAsString = Integer.toString(exp);
                    int expDigits = expAsString.length();
                    int resLen = res.length();
                    int dropDigits = resLen + expDigits + 1 - maxDigs;
                    if (dropDigits < 0) {
                        dropDigits = 0;
                    } else {
                        if (!hasPoint) {
                            exp += dropDigits;
                                // Adjust for digits we are about to drop
                                // to drop to make room for exponent.
                            // This can affect the room we have for the
                            // mantissa. We adjust only for positive exponents,
                            // when it could otherwise result in a truncated
                            // displayed result.
                            if (exp > 0 && dropDigits > 0 &&
                                Integer.toString(exp).length() > expDigits) {
                                // ++expDigits; (dead code)
                                ++dropDigits;
                                ++exp;
                                // This cannot increase the length a second time.
                            }
                        }
                        res = res.substring(0, resLen - dropDigits);
                    }
                    res = res + "e" + exp;
                } // else don't add zero exponent
            }
            if (truncated) {
                res = mCalculator.getResources().getString(R.string.ellipsis)
                       + res.substring(1, res.length());
            }
            return res;
    }

    // Return rational representation of current result, if any.
    public BoundedRational getRational() {
        return mRatVal;
    }

    private void clearCache() {
        mCache = null;
        mCacheDigs = mCacheDigsReq = 0;
        mMsd = INVALID_MSD;
    }

    void clear() {
        mExpr.clear();
        clearCache();
    }

    // Begin evaluation of result and display when ready
    void evaluateAndShowResult() {
        if (mEvaluator == null) {
            clearCache();
            mEvaluator = new AsyncDisplayResult(mDegreeMode, false);
            mEvaluator.execute();
        } // else already in progress
    }

    // Ensure that we either display a result or complain.
    // Does not invalidate a previously computed cache.
    void requireResult() {
        if (mCache == null) {
            // Restart evaluator in requested mode, i.e. with
            // longer timeout.
            cancelAll();
            mEvaluator = new AsyncDisplayResult(mDegreeMode, true);
            mEvaluator.execute();
        } else {
            // Notify immediately.
            int dotPos = mCache.indexOf('.');
            String truncatedWholePart = mCache.substring(0, dotPos);
            mCalculator.onEvaluate(mLastDigs,truncatedWholePart);
        }
    }

    // Cancel all current background tasks.
    // Return true if we cancelled an initial evaluation,
    // leaving the expression displayed.
    boolean cancelAll() {
        if (mCurrentReevaluator != null) {
            mCurrentReevaluator.cancel(true);
            mCacheDigsReq = mCacheDigs;
            // Backgound computation touches only constructive reals.
            // OK not to wait.
            mCurrentReevaluator = null;
        }
        if (mEvaluator != null) {
            mEvaluator.cancel(true);
            // There seems to be no good way to wait for cancellation
            // to complete, and the evaluation continues to look at
            // mExpr, which we will again modify.
            // Give ourselves a new copy to work on instead.
            mExpr = (CalculatorExpr)mExpr.clone();
            // Approximation of constructive reals should be thread-safe,
            // so we can let that continue until it notices the cancellation.
            mEvaluator = null;
            return true;
        }
        return false;
    }

    void restoreInstanceState(DataInput in) {
        try {
            CalculatorExpr.initExprInput();
            mDegreeMode = in.readBoolean();
            mExpr = new CalculatorExpr(in);
            mSavedName = in.readUTF();
            mSaved = new CalculatorExpr(in);
        } catch (IOException e) {
            Log.v("Calculator", "Exception while restoring:\n" + e);
        }
    }

    void saveInstanceState(DataOutput out) {
        try {
            CalculatorExpr.initExprOutput();
            out.writeBoolean(mDegreeMode);
            mExpr.write(out);
            out.writeUTF(mSavedName);
            mSaved.write(out);
        } catch (IOException e) {
            Log.v("Calculator", "Exception while saving state:\n" + e);
        }
    }

    // Append a button press to the current expression.
    // Return false if we rejected the addition due to obvious
    // syntax issues, and the expression is unchanged.
    // Return true otherwise.
    boolean append(int id) {
        return mExpr.add(id);
    }

    void setDegreeMode(boolean degrees) {
        mDegreeMode = degrees;
    }

    boolean getDegreeMode() {
        return mDegreeMode;
    }

    // Abbreviate the current expression to a pre-evaluated
    // expression node, which will display as a short number.
    // This should not be called unless the expression was
    // previously evaluated and produced a non-error result.
    // Pre-evaluated expressions can never represent an
    // expression for which evaluation to a constructive real
    // diverges.  Subsequent re-evaluation will also not diverge,
    // though it may generate errors of various kinds.
    // E.g. sqrt(-10^-1000)
    void collapse () {
        BigInteger intVal = BoundedRational.asBigInteger(mRatVal);
        CalculatorExpr abbrvExpr = mExpr.abbreviate(
                                      mVal, mRatVal, mDegreeMode,
                                      getShortString(mCache, intVal));
        clear();
        mExpr.append(abbrvExpr);
    }

    // Same as above, but put result in mSaved, leaving mExpr alone.
    // Return false if result is unavailable.
    boolean collapseToSaved() {
        if (mCache == null) return false;
        BigInteger intVal = BoundedRational.asBigInteger(mRatVal);
        CalculatorExpr abbrvExpr = mExpr.abbreviate(
                                      mVal, mRatVal, mDegreeMode,
                                      getShortString(mCache, intVal));
        mSaved.clear();
        mSaved.append(abbrvExpr);
        return true;
    }

    Uri uriForSaved() {
        return new Uri.Builder().scheme("tag")
                                .encodedOpaquePart(mSavedName)
                                .build();
    }

    // Collapse the current expression to mSaved and return a URI
    // describing this particular result, so that we can refer to it
    // later.
    Uri capture() {
        if (!collapseToSaved()) return null;
        // Generate a new (entirely private) URI for this result.
        // Attempt to conform to RFC4151, though it's unclear it matters.
        Date date = new Date();
        TimeZone tz = TimeZone.getDefault();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setTimeZone(tz);
        String isoDate = df.format(new Date());
        mSavedName = "calculator2.android.com," + isoDate + ":"
                     + (new Random().nextInt() & 0x3fffffff);
        Uri tag = uriForSaved();
        return tag;
    }

    boolean isLastSaved(Uri uri) {
        return uri.equals(uriForSaved());
    }

    void addSaved() {
        mExpr.append(mSaved);
    }

    // Retrieve the main expression being edited.
    // It is the callee's reponsibility to call cancelAll to cancel
    // ongoing concurrent computations before modifying the result.
    // TODO: Perhaps add functionality so we can keep this private?
    CalculatorExpr getExpr() {
        return mExpr;
    }

}
