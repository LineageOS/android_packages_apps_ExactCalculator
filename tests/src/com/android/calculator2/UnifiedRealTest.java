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

// A test for UnifiedReal package.

package com.android.calculator2;

import com.hp.creals.CR;
import com.hp.creals.UnaryCRFunction;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.math.BigInteger;

public class UnifiedRealTest extends TestCase {
    private static void check(boolean x, String s) {
        if (!x) throw new AssertionFailedError(s);
    }
    final static int TEST_PREC = -100; // 100 bits to the right of
                                       // binary point.
    private static void checkEq(UnifiedReal x, CR y, String s) {
        check(x.crValue().compareTo(y, TEST_PREC) == 0, s);
    }

    private final static UnaryCRFunction ASIN = UnaryCRFunction.asinFunction;
    private final static UnaryCRFunction ACOS = UnaryCRFunction.acosFunction;
    private final static UnaryCRFunction ATAN = UnaryCRFunction.atanFunction;
    private final static UnaryCRFunction TAN = UnaryCRFunction.tanFunction;
    private final static CR CR_1 = CR.ONE;

    private final static CR RADIANS_PER_DEGREE = CR.PI.divide(CR.valueOf(180));
    private final static CR DEGREES_PER_RADIAN = CR.valueOf(180).divide(CR.PI);
    private final static CR LN10 = CR.valueOf(10).ln();

    private final static UnifiedReal UR_30 = new UnifiedReal(30);
    private final static UnifiedReal UR_MINUS30 = new UnifiedReal(-30);
    private final static UnifiedReal UR_15 = new UnifiedReal(15);
    private final static UnifiedReal UR_MINUS15 = new UnifiedReal(-15);

    private static CR toRadians(CR x) {
        return x.multiply(RADIANS_PER_DEGREE);
    }

    private static CR fromRadians(CR x) {
        return x.multiply(DEGREES_PER_RADIAN);
    }

    private static UnifiedReal toRadians(UnifiedReal x) {
        return x.multiply(UnifiedReal.RADIANS_PER_DEGREE);
    }

    private static UnifiedReal fromRadians(UnifiedReal x) {
        return x.divide(UnifiedReal.RADIANS_PER_DEGREE);
    }

    // We assume that x is simple enough that we don't overflow bounds.
    private static void checkUR(UnifiedReal x) {
        CR xAsCr = x.crValue();
        checkEq(x.add(UnifiedReal.ONE), xAsCr.add(CR_1), "add 1:" + x);
        checkEq(x.subtract(UR_MINUS30), xAsCr.subtract(CR.valueOf(-30)), "sub -30:" + x);
        checkEq(x.multiply(UR_15), xAsCr.multiply(CR.valueOf(15)), "multiply 15:" + x);
        checkEq(x.divide(UR_15), xAsCr.divide(CR.valueOf(15)), "divide 15:" + x);
        checkEq(x.sin(), xAsCr.sin(), "sin:" + x);
        checkEq(x.cos(), xAsCr.cos(), "cos:" + x);
        if (x.cos().definitelyNonZero()) {
            checkEq(x.tan(), TAN.execute(xAsCr), "tan:" + x);
        }
        checkEq(toRadians(x).sin(), toRadians(xAsCr).sin(), "degree sin:" + x);
        checkEq(toRadians(x).cos(), toRadians(xAsCr).cos(), "degree cos:" + x);
        BigInteger big_x = x.bigIntegerValue();
        long long_x = (big_x == null? 0 : big_x.longValue());
        try {
            checkEq(toRadians(x).tan(), TAN.execute(toRadians(xAsCr)), "degree tan:" + x);
            check((long_x - 90) % 180 != 0, "missed undefined tan: " + x);
        } catch (ArithmeticException ignored) {
            check((long_x - 90) % 180 == 0, "exception on defined tan: " + x + " " + ignored);
        }
        if (x.compareTo(UR_30) <= 0 && x.compareTo(UR_MINUS30) >= 0) {
            checkEq(x.exp(), xAsCr.exp(), "exp:" + x);
            checkEq(UR_15.pow(x), CR.valueOf(15).ln().multiply(xAsCr).exp(), "pow(15,x):" + x);             }
        if (x.compareTo(UnifiedReal.ONE) <= 0
                && x.compareTo(UnifiedReal.ONE.negate()) >= 0) {
            checkEq(x.asin(), ASIN.execute(xAsCr), "asin:" + x);
            checkEq(x.acos(), ACOS.execute(xAsCr), "acos:" + x);
            checkEq(fromRadians(x.asin()), fromRadians(ASIN.execute(xAsCr)), "degree asin:" + x);
            checkEq(fromRadians(x.acos()), fromRadians(ACOS.execute(xAsCr)), "degree acos:" + x);
        }
        checkEq(x.atan(), ATAN.execute(xAsCr), "atan:" + x);
        if (x.signum() > 0) {
            checkEq(x.ln(), xAsCr.ln(), "ln:" + x);
            checkEq(x.sqrt(), xAsCr.sqrt(), "sqrt:" + x);
            checkEq(x.pow(UR_15), xAsCr.ln().multiply(CR.valueOf(15)).exp(), "pow(x,15):" + x);
        }
    }

    public void testUR() {
        UnifiedReal b = new UnifiedReal(new BoundedRational(4,-6));
        check(b.toString().equals("4/-6*1.0000000000"), "toString(4/-6)");
        check(b.toNiceString().equals("-2/3"), "toNiceString(4/-6)");
        check(b.toStringTruncated(1).equals("-0.6"), "(4/-6).toString(1)");
        check(UR_15.toStringTruncated(0).equals("15."), "15.toString(1)");
        check(UnifiedReal.ZERO.toStringTruncated(2).equals("0.00"), "0.toString(2)");
        checkEq(UnifiedReal.ZERO, CR.valueOf(0), "0");
        checkEq(new UnifiedReal(390), CR.valueOf(390), "390");
        checkEq(UR_15, CR.valueOf(15), "15");
        checkEq(new UnifiedReal(390).negate(), CR.valueOf(-390), "-390");
        checkEq(UnifiedReal.ONE.negate(), CR.valueOf(-1), "-1");
        checkEq(new UnifiedReal(2), CR.valueOf(2), "2");
        checkEq(new UnifiedReal(-2), CR.valueOf(-2), "-2");
        check(UnifiedReal.ZERO.signum() == 0, "signum(0)");
        check(UnifiedReal.ZERO.definitelyZero(), "definitelyZero(0)");
        check(!UnifiedReal.ZERO.definitelyNonZero(), "definitelyNonZero(0)");
        check(!UnifiedReal.PI.definitelyZero(), "definitelyZero(pi)");
        check(UnifiedReal.PI.definitelyNonZero(), "definitelyNonZero(pi)");
        check(UnifiedReal.ONE.negate().signum() == -1, "signum(-1)");
        check(new UnifiedReal(2).signum() == 1, "signum(2)");
        check(UnifiedReal.E.signum() == 1, "signum(e)");
        check(new UnifiedReal(400).bigIntegerValue().intValue() == 400, "400.bigIntegerValue()");
        check(UnifiedReal.HALF.bigIntegerValue() == null, "1/2.bigIntegerValue()");
        check(UnifiedReal.HALF.negate().bigIntegerValue() == null, "-1/2.bigIntegerValue()");
        check(new UnifiedReal(new BoundedRational(15, -5)).bigIntegerValue().intValue() == -3,
                "-15/5.asBigInteger()");
        check(UnifiedReal.ZERO.digitsRequired() == 0, "digitsRequired(0)");
        check(UnifiedReal.HALF.digitsRequired() == 1, "digitsRequired(1)");
        check(UnifiedReal.HALF.negate().digitsRequired() == 1, "digitsRequired(-1)");
        check(UnifiedReal.ONE.divide(new UnifiedReal(-2)).digitsRequired() == 1,
                "digitsRequired(-2)");
        check(UnifiedReal.ZERO.fact().definitelyEquals(UnifiedReal.ONE), "0!");
        check(UnifiedReal.ONE.fact().definitelyEquals(UnifiedReal.ONE), "1!");
        check(UnifiedReal.TWO.fact().definitelyEquals(UnifiedReal.TWO), "2!");
        check(new UnifiedReal(15).fact().definitelyEquals(new UnifiedReal(1307674368000L)), "15!");
        check(UnifiedReal.ONE.exactlyDisplayable(), "1 displayable");
        check(UnifiedReal.PI.exactlyDisplayable(), "PI displayable");
        check(UnifiedReal.E.exactlyDisplayable(), "E displayable");
        check(UnifiedReal.E.divide(UnifiedReal.E).exactlyDisplayable(), "E/E displayable");
        check(!UnifiedReal.E.divide(UnifiedReal.PI).exactlyDisplayable(), "!E/PI displayable");
        UnifiedReal r = new UnifiedReal(9).multiply(new UnifiedReal(3).sqrt()).ln();
        checkEq(r, CR.valueOf(9).multiply(CR.valueOf(3).sqrt()).ln(), "ln(9sqrt(3))");
        check(r.exactlyDisplayable(), "5/2log3");
        checkEq(r.exp(), CR.valueOf(9).multiply(CR.valueOf(3).sqrt()), "9sqrt(3)");
        check(r.exp().exactlyDisplayable(), "9sqrt(3)");
        check(!UnifiedReal.E.divide(UnifiedReal.PI).definitelyEquals(
                UnifiedReal.E.divide(UnifiedReal.PI)), "E/PI = E/PI not testable");
        check(new UnifiedReal(32).sqrt().definitelyEquals(
                (new UnifiedReal(2).sqrt().multiply(new UnifiedReal(4)))), "sqrt(32)");
        check(new UnifiedReal(32).ln().divide(UnifiedReal.TWO.ln())
                .definitelyEquals(new UnifiedReal(5)), "ln(32)");
        check(new UnifiedReal(10).sqrt().multiply(UnifiedReal.TEN.sqrt())
                .definitelyEquals(UnifiedReal.TEN), "sqrt(10)^2");
        check(UnifiedReal.ZERO.leadingBinaryZeroes() == Integer.MAX_VALUE, "0.leadingBinaryZeros");
        check(new UnifiedReal(new BoundedRational(7,1024)).leadingBinaryZeroes() >= 8,
                "fract.leadingBinaryZeros");
        UnifiedReal tmp = UnifiedReal.TEN.pow(new UnifiedReal(-1000));
        int tmp2 = tmp.leadingBinaryZeroes();
        check(tmp2 >= 3320 && tmp2 < 4000, "leadingBinaryZeroes(10^-1000)");
        tmp2 = tmp.multiply(UnifiedReal.PI).leadingBinaryZeroes();
        check(tmp2 >= 3319 && tmp2 < 4000, "leadingBinaryZeroes(pix10^-1000)");
        // We check values that include all interesting degree values.
        r = new UnifiedReal(-390);
        int i = 0;
        while (!r.definitelyEquals(new UnifiedReal(390))) {
            check(i++ < 100, "int loop counter arithmetic failed!");
            if (i > 100) {
                break;
            }
            checkUR(r);
            r = r.add(new UnifiedReal(15));
        }
        r = UnifiedReal.PI.multiply(new UnifiedReal(-3));
        final UnifiedReal limit = r.negate();
        final UnifiedReal increment = UnifiedReal.PI.divide(new UnifiedReal(24));
        i = 0;
        while (!r.definitelyEquals(limit)) {
            check(i++ < 200, "transcendental loop counter arithmetic failed!");
            if (i > 100) {
                break;
            }
            checkUR(r);
            r = r.add(increment);
        }
        checkUR(UnifiedReal.HALF);
        checkUR(UnifiedReal.MINUS_HALF);
        checkUR(UnifiedReal.ONE);
        checkUR(UnifiedReal.MINUS_ONE);
        checkUR(new UnifiedReal(1000));
        checkUR(new UnifiedReal(100));
        checkUR(new UnifiedReal(new BoundedRational(4,9)));
        check(new UnifiedReal(new BoundedRational(4,9)).sqrt().definitelyEquals(
                UnifiedReal.TWO.divide(new UnifiedReal(3))), "sqrt(4/9)");
        checkUR(new UnifiedReal(new BoundedRational(4,9)).negate());
        checkUR(new UnifiedReal(new BoundedRational(5,9)));
        checkUR(new UnifiedReal(new BoundedRational(5,10)));
        checkUR(new UnifiedReal(new BoundedRational(5,10)));
        checkUR(new UnifiedReal(new BoundedRational(4,13)));
        checkUR(new UnifiedReal(36));
        checkUR(new UnifiedReal(36).negate());
    }

    public void testFunctionsOnSmall() {
        // This checks some of the special cases we should handle semi-symbolically.
        UnifiedReal small = new UnifiedReal(2).pow(new UnifiedReal(-1000));
        UnifiedReal small2 = new UnifiedReal(-1000).exp();
        for (int i = 0; i <= 10; i++) {
            UnifiedReal r = new UnifiedReal(i);
            UnifiedReal sqrt = r.sqrt();
            if (i > 1 && i != 4 && i != 9) {
                check(sqrt.definitelyIrrational() && !sqrt.definitelyRational(), "sqrt !rational");
            } else {
                check(!sqrt.definitelyIrrational() && sqrt.definitelyRational(), "sqrt rational");
            }
            check(sqrt.definitelyAlgebraic() && !sqrt.definitelyTranscendental(), "sqrt algenraic");
            check(sqrt.multiply(sqrt).definitelyEquals(r), "sqrt " + i);
            check(!sqrt.multiply(sqrt).definitelyEquals(r.add(small)), "sqrt small " + i);
            check(!sqrt.multiply(sqrt).definitelyEquals(r.add(small2)), "sqrt small2 " + i);
            if (i > 0) {
                UnifiedReal log = r.ln();
                check(log.exp().definitelyEquals(r), "log " + i);
                if (i > 1) {
                    check(log.definitelyTranscendental(), "log transcendental");
                    check(!log.definitelyAlgebraic(), "log !algebraic");
                    check(!log.definitelyRational(), "log !rational");
                    check(log.definitelyIrrational(), "log !rational again");
                } else {
                    check(log.definitelyRational(), "log rational");
                }
                check(r.pow(r).ln().definitelyEquals(r.multiply(r.ln())), "ln(r^r)");
            }
        }
    }

    public void testURexceptions() {
        try {
            UnifiedReal.MINUS_ONE.ln();
            check(false, "ln(-1)");
        } catch (ArithmeticException ignored) {}
        try {
            UnifiedReal.MINUS_ONE.sqrt();
            check(false, "sqrt(-1)");
        } catch (ArithmeticException ignored) {}
        try {
            new UnifiedReal(-2).asin();
            check(false, "asin(-2)");
        } catch (ArithmeticException ignored) {}
        try {
            new UnifiedReal(-2).acos();
            check(false, "acos(-2)");
        } catch (ArithmeticException ignored) {}
    }

}
