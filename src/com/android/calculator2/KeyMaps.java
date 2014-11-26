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

import android.content.res.Resources;
import android.content.Context;
import android.view.View;
import java.text.DecimalFormatSymbols;

public class KeyMaps {
    // Map key id to corresponding (internationalized) display string
    public static String toString(int id, Context context) {
        Resources res = context.getResources();
        switch(id) {
        case R.id.const_pi:         return res.getString(R.string.const_pi);
        case R.id.const_e:          return res.getString(R.string.const_e);
        case R.id.op_sqrt:    	    return res.getString(R.string.op_sqrt);
        case R.id.op_fact:          return res.getString(R.string.op_fact);
        case R.id.fun_sin:          return res.getString(R.string.fun_sin)
                                            + res.getString(R.string.lparen);
        case R.id.fun_cos:          return res.getString(R.string.fun_cos) 
                                            + res.getString(R.string.lparen);
        case R.id.fun_tan:          return res.getString(R.string.fun_tan)
                                            + res.getString(R.string.lparen);
        case R.id.fun_arcsin:       return res.getString(R.string.fun_arcsin)
                                            + res.getString(R.string.lparen);
        case R.id.fun_arccos:       return res.getString(R.string.fun_arccos) 
                                            + res.getString(R.string.lparen);
        case R.id.fun_arctan:       return res.getString(R.string.fun_arctan)
                                            + res.getString(R.string.lparen);
        case R.id.fun_ln:           return res.getString(R.string.fun_ln)
                                            + res.getString(R.string.lparen);
        case R.id.fun_log:          return res.getString(R.string.fun_log)
                                            + res.getString(R.string.lparen);
        case R.id.lparen:           return res.getString(R.string.lparen);
        case R.id.rparen:           return res.getString(R.string.rparen);
        case R.id.op_pow:           return res.getString(R.string.op_pow);
        case R.id.op_mul:           return res.getString(R.string.op_mul);
        case R.id.op_div:           return res.getString(R.string.op_div);
        case R.id.op_add:	    return res.getString(R.string.op_add);
        case R.id.op_sub:           return res.getString(R.string.op_sub);
        case R.id.dec_point:        return res.getString(R.string.dec_point);
        case R.id.digit_0:          return res.getString(R.string.digit_0);
        case R.id.digit_1:          return res.getString(R.string.digit_1);
        case R.id.digit_2:          return res.getString(R.string.digit_2);
        case R.id.digit_3:          return res.getString(R.string.digit_3);
        case R.id.digit_4:          return res.getString(R.string.digit_4);
        case R.id.digit_5:          return res.getString(R.string.digit_5);
        case R.id.digit_6:          return res.getString(R.string.digit_6);
        case R.id.digit_7:          return res.getString(R.string.digit_7);
        case R.id.digit_8:          return res.getString(R.string.digit_8);
        case R.id.digit_9:          return res.getString(R.string.digit_9);
        default:                    return "?oops?";
        }
    }

    // Does a button id correspond to a binary operator?
    public static boolean isBinary(int id) {
        switch(id) {
        case R.id.op_pow:
        case R.id.op_mul:
        case R.id.op_div:
        case R.id.op_add:
        case R.id.op_sub:
            return true;
        default:
            return false;
        }
    }

    // Does a button id correspond to a suffix operator?
    public static boolean isSuffix(int id) {
        return id == R.id.op_fact;
    }

    public static final int NOT_DIGIT = 10;

    // Map key id to digit or NOT_DIGIT
    public static int digVal(int id) {
        switch (id) {
        case R.id.digit_0:
            return 0;
        case R.id.digit_1:
            return 1;
        case R.id.digit_2:
            return 2;
        case R.id.digit_3:
            return 3;
        case R.id.digit_4:
            return 4;
        case R.id.digit_5:
            return 5;
        case R.id.digit_6:
            return 6;
        case R.id.digit_7:
            return 7;
        case R.id.digit_8:
            return 8;
        case R.id.digit_9:
            return 9;
        default:
            return NOT_DIGIT;
        }
    }

    // Map digit to corresponding key.  Inverse of above.
    public static int keyForDigVal(int v) {
        switch(v) {
        case 0:
            return R.id.digit_0;
        case 1:
            return R.id.digit_1;
        case 2:
            return R.id.digit_2;
        case 3:
            return R.id.digit_3;
        case 4:
            return R.id.digit_4;
        case 5:
            return R.id.digit_5;
        case 6:
            return R.id.digit_6;
        case 7:
            return R.id.digit_7;
        case 8:
            return R.id.digit_8;
        case 9:
            return R.id.digit_9;
        default:
            return View.NO_ID;
        }
    }

    final static char decimalPt =
                DecimalFormatSymbols.getInstance().getDecimalSeparator();

    // Return the button id corresponding to the supplied character
    // or NO_ID
    // TODO: Should probably also check on characters used as button
    // labels.  But those don't really seem to be internationalized.
    public static int keyForChar(char c) {
        if (Character.isDigit(c)) {
            int i = Character.digit(c, 10);
            return KeyMaps.keyForDigVal(i);
        }
        if (c == decimalPt) return R.id.dec_point;
        switch (c) {
        case '.':
            return R.id.dec_point;
        case '-':
            return R.id.op_sub;
        case '+':
            return R.id.op_add;
        case '*':
            return R.id.op_mul;
        case '/':
            return R.id.op_div;
        case 'e':
            return R.id.const_e;
        case '^':
            return R.id.op_pow;
        default:
            return View.NO_ID;
        }
    }
}
