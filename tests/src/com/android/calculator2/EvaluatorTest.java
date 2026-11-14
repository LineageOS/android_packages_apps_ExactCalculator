/*
 * SPDX-FileCopyrightText: 2015 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/**
 * A test for a few static methods in Evaluator.
 * The most interesting one is for unflipZeroes(), which we don't know how to test with
 * real calculator input.
 */
public class EvaluatorTest extends TestCase {
    private static void check(boolean x, String s) {
        if (!x) throw new AssertionFailedError(s);
    }
    public void testUnflipZeroes() {
        check(Evaluator.unflipZeroes("9.99", 2, "9.998", 3).equals("9.998"), "test 1");
        check(Evaluator.unflipZeroes("9.99", 2, "10.0000", 4).equals("9.9999"), "test 2");
        check(Evaluator.unflipZeroes("0.99", 2, "1.00000", 5).equals("0.99999"), "test 3");
        check(Evaluator.unflipZeroes("0.99", 2, "1.00", 2).equals("0.99"), "test 4");
        check(Evaluator.unflipZeroes("10.00", 2, "9.9999", 4).equals("9.9999"), "test 5");
        check(Evaluator.unflipZeroes("-10.00", 2, "-9.9999", 4).equals("-9.9999"), "test 6");
        check(Evaluator.unflipZeroes("-0.99", 2, "-1.00000000000000", 14)
                .equals("-0.99999999999999"), "test 7");
        check(Evaluator.unflipZeroes("12349.99", 2, "12350.00000", 5).equals("12349.99999"),
                "test 8");
        check(Evaluator.unflipZeroes("123.4999", 4, "123.5000000", 7).equals("123.4999999"),
                "test 9");
    }

    public void testGetMsdIndexOf() {
        check(Evaluator.getMsdIndexOf("-0.0234") == 4, "getMsdIndexOf(-0.0234)");
        check(Evaluator.getMsdIndexOf("23.45") == 0, "getMsdIndexOf(23.45)");
        check(Evaluator.getMsdIndexOf("-0.01") == Evaluator.INVALID_MSD, "getMsdIndexOf(-0.01)");
    }
}
