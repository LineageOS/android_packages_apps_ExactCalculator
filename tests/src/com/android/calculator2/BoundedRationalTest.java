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

// A test for BoundedRationals package.

package com.android.calculator2;

import com.hp.creals.CR;
import com.hp.creals.UnaryCRFunction;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.math.BigInteger;

public class BoundedRationalTest extends TestCase {
    private static void check(boolean x, String s) {
        if (!x) throw new AssertionFailedError(s);
    }
    final static int TEST_PREC = -100; // 100 bits to the right of
                                       // binary point.
    private static void checkEq(BoundedRational x, CR y, String s) {
        check(x.crValue().compareTo(y, TEST_PREC) == 0, s);
    }
    private static void checkWeakEq(BoundedRational x, CR y, String s) {
        if (x != null) checkEq(x, y, s);
    }

    private final static BoundedRational BR_0 = new BoundedRational(0);
    private final static BoundedRational BR_M1 = new BoundedRational(-1);
    private final static BoundedRational BR_2 = new BoundedRational(2);
    private final static BoundedRational BR_M2 = new BoundedRational(-2);
    private final static BoundedRational BR_15 = new BoundedRational(15);
    private final static BoundedRational BR_390 = new BoundedRational(390);
    private final static BoundedRational BR_M390 = new BoundedRational(-390);
    private final static CR CR_1 = CR.valueOf(1);

    // We assume that x is simple enough that we don't overflow bounds.
    private static void checkBR(BoundedRational x) {
        check(x != null, "test data should not be null");
        CR xAsCR = x.crValue();
        checkEq(BoundedRational.add(x, BoundedRational.ONE), xAsCR.add(CR_1),
                "add 1:" + x);
        checkEq(BoundedRational.subtract(x, BoundedRational.MINUS_THIRTY),
                xAsCR.subtract(CR.valueOf(-30)), "sub -30:" + x);
        checkEq(BoundedRational.multiply(x, BR_15),
                xAsCR.multiply(CR.valueOf(15)), "multiply 15:" + x);
        checkEq(BoundedRational.divide(x, BR_15),
                xAsCR.divide(CR.valueOf(15)), "divide 15:" + x);
        BigInteger big_x = BoundedRational.asBigInteger(x);
        long long_x = (big_x == null? 0 : big_x.longValue());
        if (x.compareTo(BoundedRational.THIRTY) <= 0
                && x.compareTo(BoundedRational.MINUS_THIRTY) >= 0) {
            checkWeakEq(BoundedRational.pow(BR_15, x),
                    CR.valueOf(15).ln().multiply(xAsCR).exp(),
                    "pow(15,x):" + x);
        }
        if (x.signum() > 0) {
            checkWeakEq(BoundedRational.sqrt(x), xAsCR.sqrt(), "sqrt:" + x);
            checkEq(BoundedRational.pow(x, BR_15),
                    xAsCR.ln().multiply(CR.valueOf(15)).exp(),
                    "pow(x,15):" + x);
        }
    }

    public void testBR() {
        BoundedRational b = new BoundedRational(4,-6);
        check(b.toString().equals("4/-6"), "toString(4/-6)");
        check(b.toNiceString().equals("-2/3"), "toNiceString(4/-6)");
        check(b.toStringTruncated(1).equals("-0.6"), "(4/-6).toStringT(1)");
        check(BR_15.toStringTruncated(0).equals("15."), "15.toStringT(1)");
        check(BR_0.toStringTruncated(2).equals("0.00"), "0.toStringT(2)");
        checkEq(BR_0, CR.valueOf(0), "0");
        checkEq(BR_390, CR.valueOf(390), "390");
        checkEq(BR_15, CR.valueOf(15), "15");
        checkEq(BR_M390, CR.valueOf(-390), "-390");
        checkEq(BR_M1, CR.valueOf(-1), "-1");
        checkEq(BR_2, CR.valueOf(2), "2");
        checkEq(BR_M2, CR.valueOf(-2), "-2");
        check(BR_0.signum() == 0, "signum(0)");
        check(BR_M1.signum() == -1, "signum(-1)");
        check(BR_2.signum() == 1, "signum(2)");
        check(BoundedRational.asBigInteger(BR_390).intValue() == 390, "390.asBigInteger()");
        check(BoundedRational.asBigInteger(BoundedRational.HALF) == null, "1/2.asBigInteger()");
        check(BoundedRational.asBigInteger(BoundedRational.MINUS_HALF) == null,
                "-1/2.asBigInteger()");
        check(BoundedRational.asBigInteger(new BoundedRational(15, -5)).intValue() == -3,
                "-15/5.asBigInteger()");
        check(BoundedRational.digitsRequired(BoundedRational.ZERO) == 0, "digitsRequired(0)");
        check(BoundedRational.digitsRequired(BoundedRational.HALF) == 1, "digitsRequired(1/2)");
        check(BoundedRational.digitsRequired(BoundedRational.MINUS_HALF) == 1,
                "digitsRequired(-1/2)");
        check(BoundedRational.digitsRequired(new BoundedRational(1,-2)) == 1,
                "digitsRequired(1/-2)");
        // We check values that include all interesting degree values.
        BoundedRational r = BR_M390;
        while (!r.equals(BR_390)) {
            check(r != null, "loop counter overflowed!");
            checkBR(r);
            r = BoundedRational.add(r, BR_15);
        }
        checkBR(BoundedRational.HALF);
        checkBR(BoundedRational.MINUS_HALF);
        checkBR(BoundedRational.ONE);
        checkBR(BoundedRational.MINUS_ONE);
        checkBR(new BoundedRational(1000));
        checkBR(new BoundedRational(100));
        checkBR(new BoundedRational(4,9));
        check(BoundedRational.sqrt(new BoundedRational(4,9)) != null,
              "sqrt(4/9) is null");
        checkBR(BoundedRational.negate(new BoundedRational(4,9)));
        checkBR(new BoundedRational(5,9));
        checkBR(new BoundedRational(5,10));
        checkBR(new BoundedRational(5,10));
        checkBR(new BoundedRational(4,13));
        checkBR(new BoundedRational(36));
        checkBR(BoundedRational.negate(new BoundedRational(36)));
        check(BoundedRational.pow(null, BR_15) == null, "pow(null, 15)");
        check(BoundedRational.multiply(null, BR_15) == null, "multiply(null, 15)");
        check(BoundedRational.multiply(BR_15, null) == null, "multiply(15, null)");
        check(BoundedRational.multiply(null, null) == null, "multiply(null, null)");
        check(BoundedRational.divide(BR_15, null) == null, "divide(15, null)");
        check(BoundedRational.add(null, BR_15) == null, "add(null, 15)");
        check(BoundedRational.add(BR_15, null) == null, "add(15, null)");
        check(BoundedRational.add(null, null) == null, "add(null, null)");
        check(BoundedRational.subtract(null, BR_15) == null, "subtract(null, 15)");
    }

    public void testBRexceptions() {
        try {
            BoundedRational.divide(BR_390, BoundedRational.ZERO);
            check(false, "390/0");
        } catch (ArithmeticException ignored) {}
        try {
            BoundedRational.sqrt(BR_M1);
            check(false, "sqrt(-1)");
        } catch (ArithmeticException ignored) {}
    }

    public void testBROverflow() {
        BoundedRational sum = new BoundedRational(0);
        long i;
        for (i = 1; i < 4000; ++i) {
             sum = BoundedRational.add(sum,
                        BoundedRational.inverse(new BoundedRational(i)));
             if (sum == null) break;
        }
        // With MAX_SIZE = 10000, we seem to overflow at 3488.
        check(i > 3000, "Harmonic series overflowed at " + i);
        check(i < 4000, "Harmonic series didn't overflow");
        BoundedRational veryBig = BoundedRational.pow(BoundedRational.TEN,
                new BoundedRational(5000));
        check(BoundedRational.divide(veryBig,
                BoundedRational.divide(veryBig, veryBig)).equals(veryBig), "big / 1");
        // Very large integers should not be mapped to null.
        check(BoundedRational.divide(veryBig, BoundedRational.TEN) != null, "very big integer");
        // The following assumes MAX_SIZE = 10000.
        // Very large non-integers should be mapped to null.
        check(BoundedRational.divide(veryBig, new BoundedRational(7)) == null,
                "very big fraction");
    }
}
