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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.math.BigInteger;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.hp.creals.CR;
import com.hp.creals.UnaryCRFunction;
import com.hp.creals.PrecisionOverflowError;
import com.hp.creals.AbortedError;

import android.content.Context;

// A mathematical expression represented as a sequence of "tokens".
// Many tokes are represented by button ids for the corresponding operator.
// Parsed only when we evaluate the expression using the "eval" method.
class CalculatorExpr {
    private ArrayList<Token> mExpr;  // The actual representation
                                     // as a list of tokens.  Constant
                                     // tokens are always nonempty.

    private static enum TokenKind { CONSTANT, OPERATOR, PRE_EVAL };
    private static TokenKind[] tokenKindValues = TokenKind.values();
    private final static BigInteger BIG_MILLION = BigInteger.valueOf(1000000);
    private final static BigInteger BIG_BILLION = BigInteger.valueOf(1000000000);

    private static abstract class Token {
        abstract TokenKind kind();
        abstract void write(DataOutput out) throws IOException;
                // Implementation writes kind as Byte followed by
                // data read by constructor.
        abstract String toString(Context context);
                // We need the context to convert button ids to strings.
    }

    // An operator token
    private static class Operator extends Token {
	final int mId; // We use the button resource id
        Operator(int resId) {
	    mId = resId;
        }
        Operator(DataInput in) throws IOException {
            mId = in.readInt();
        }
        @Override
        void write(DataOutput out) throws IOException {
            out.writeByte(TokenKind.OPERATOR.ordinal());
            out.writeInt(mId);
        }
        @Override
        public String toString(Context context) {
            return KeyMaps.toString(mId, context);
        }
        @Override
        TokenKind kind() { return TokenKind.OPERATOR; }
    }

    // A (possibly incomplete) numerical constant
    private static class Constant extends Token implements Cloneable {
        private boolean mSawDecimal;
        String mWhole;  // part before decimal point
        private String mFraction; // part after decimal point

        Constant() {
            mWhole = "";
            mFraction = "";
	    mSawDecimal = false;
        };

        Constant(DataInput in) throws IOException {
            mWhole = in.readUTF();
            mSawDecimal = in.readBoolean();
            mFraction = in.readUTF();
        }

        @Override
        void write(DataOutput out) throws IOException {
            out.writeByte(TokenKind.CONSTANT.ordinal());
            out.writeUTF(mWhole);
            out.writeBoolean(mSawDecimal);
            out.writeUTF(mFraction);
        }

        // Given a button press, append corresponding digit.
        // We assume id is a digit or decimal point.
        // Just return false if this was the second (or later) decimal point
        // in this constant.
        boolean add(int id) {
            if (id == R.id.dec_point) {
                if (mSawDecimal) return false;
                mSawDecimal = true;
                return true;
            }
            int val = KeyMaps.digVal(id);
            if (mSawDecimal) {
                mFraction += val;
            } else {
                mWhole += val;
            }
            return true;
        }

        // Undo the last add.
        // Assumes the constant is nonempty.
        void delete() {
            if (!mFraction.isEmpty()) {
                mFraction = mFraction.substring(0, mFraction.length() - 1);
            } else if (mSawDecimal) {
                mSawDecimal = false;
            } else {
                mWhole = mWhole.substring(0, mWhole.length() - 1);
            }
        }

        boolean isEmpty() {
            return (mSawDecimal == false && mWhole.isEmpty());
        }

        boolean isInt() {
            return !mSawDecimal || mFraction.isEmpty()
                   || new BigInteger(mFraction).equals(BigInteger.ZERO);
        }

        @Override
        public String toString() {
            String result = mWhole;
            if (mSawDecimal) {
                result += '.';
                result += mFraction;
            }
            return result;
        }

        @Override
        String toString(Context context) {
            return toString();
        }

        @Override
        TokenKind kind() { return TokenKind.CONSTANT; }

        // Override clone to make it public
        @Override
        public Object clone() {
            Constant res = new Constant();
            res.mWhole = mWhole;
            res.mFraction = mFraction;
            res.mSawDecimal = mSawDecimal;
            return res;
        }
    }

    // Hash maps used to detect duplicate subexpressions when
    // we write out CalculatorExprs and read them back in.
    private static final ThreadLocal<IdentityHashMap<CR,Integer>>outMap =
                new ThreadLocal<IdentityHashMap<CR,Integer>>();
        // Maps expressions to indices on output
    private static final ThreadLocal<HashMap<Integer,PreEval>>inMap =
                new ThreadLocal<HashMap<Integer,PreEval>>();
        // Maps expressions to indices on output
    private static final ThreadLocal<Integer> exprIndex =
                new ThreadLocal<Integer>();

    static void initExprOutput() {
        outMap.set(new IdentityHashMap<CR,Integer>());
        exprIndex.set(Integer.valueOf(0));
    }

    static void initExprInput() {
        inMap.set(new HashMap<Integer,PreEval>());
    }

    // We treat previously evaluated subexpressions as tokens
    // These are inserted when either:
    //    - We continue an expression after evaluating some of it.
    //    - TODO: When we copy/paste expressions.
    // The representation includes three different representations
    // of the expression:
    //  1) The CR value for use in computation.
    //  2) The integer value for use in the computations,
    //     if the expression evaluates to an integer.
    //  3a) The corresponding CalculatorExpr, together with
    //  3b) The context (currently just deg/rad mode) used to evaluate
    //      the expression.
    //  4) A short string representation that is used to
    //     Display the expression.
    //
    // (3) is present only so that we can persist the object.
    // (4) is stored explicitly to avoid waiting for recomputation in the UI
    //       thread.
    private static class PreEval extends Token {
        final CR mValue;
        final BigInteger mIntValue;
        private final CalculatorExpr mExpr;
        private final EvalContext mContext;
        private final String mShortRep;
        PreEval(CR val, BigInteger intVal, CalculatorExpr expr, EvalContext ec,
                String shortRep) {
            mValue = val;
            mIntValue = intVal;
            mExpr = expr;
            mContext = ec;
            mShortRep = shortRep;
        }
        // In writing out PreEvals, we are careful to avoid writing
        // out duplicates.  We assume that two expressions are
        // duplicates if they have the same mVal.  This avoids a
        // potential exponential blow up in certain off cases and
        // redundant evaluation after reading them back in.
        // The parameter hash map maps expressions we've seen
        // before to their index.
        @Override
        void write(DataOutput out) throws IOException {
            out.writeByte(TokenKind.PRE_EVAL.ordinal());
            Integer index = outMap.get().get(mValue);
            if (index == null) {
                int nextIndex = exprIndex.get() + 1;
                exprIndex.set(nextIndex);
                outMap.get().put(mValue, nextIndex);
                out.writeInt(nextIndex);
                mExpr.write(out);
                mContext.write(out);
                out.writeUTF(mShortRep);
            } else {
                // Just write out the index
                out.writeInt(index);
            }
        }
        PreEval(DataInput in) throws IOException {
            int index = in.readInt();
            PreEval prev = inMap.get().get(index);
            if (prev == null) {
                mExpr = new CalculatorExpr(in);
                mContext = new EvalContext(in);
                // Recompute other fields
                // We currently do this in the UI thread, but we
                // only create PreEval expressions that were
                // previously successfully evaluated, and thus
                // don't diverge.  We also only evaluate to a
                // constructive real, which involves substantial
                // work only in fairly contrived circumstances.
                // TODO: Deal better with slow evaluations.
                EvalRet res = mExpr.evalExpr(0,mContext);
                mValue = res.mVal;
                mIntValue = res.mIntVal;
                mShortRep = in.readUTF();
                inMap.get().put(index, this);
            } else {
                mValue = prev.mValue;
                mIntValue = prev.mIntValue;
                mExpr = prev.mExpr;
                mContext = prev.mContext;
                mShortRep = prev.mShortRep;
            }
        }
        @Override
        String toString(Context context) {
            return mShortRep;
        }
        @Override
        TokenKind kind() { return TokenKind.PRE_EVAL; }
    }

    static Token newToken(DataInput in) throws IOException {
        TokenKind kind = tokenKindValues[in.readByte()];
        switch(kind) {
        case CONSTANT:
            return new Constant(in);
        case OPERATOR:
            return new Operator(in);
        case PRE_EVAL:
            return new PreEval(in);
        default: throw new IOException("Bad save file format");
        }
    }

    CalculatorExpr() {
        mExpr = new ArrayList<Token>();
    }

    private CalculatorExpr(ArrayList<Token> expr) {
        mExpr = expr;
    }

    CalculatorExpr(DataInput in) throws IOException {
        mExpr = new ArrayList<Token>();
        int size = in.readInt();
        for (int i = 0; i < size; ++i) {
            mExpr.add(newToken(in));
        }
    }

    void write(DataOutput out) throws IOException {
        int size = mExpr.size();
        out.writeInt(size);
        for (int i = 0; i < size; ++i) {
            mExpr.get(i).write(out);
        }
    }

    private boolean hasTrailingBinary() {
        int s = mExpr.size();
        if (s == 0) return false;
        Token t = mExpr.get(s-1);
        if (!(t instanceof Operator)) return false;
        Operator o = (Operator)t;
        return (KeyMaps.isBinary(o.mId));
    }

    // Append press of button with given id to expression.
    // Returns false and does nothing if this would clearly
    // result in a syntax error.
    boolean add(int id) {
        int s = mExpr.size();
        int d = KeyMaps.digVal(id);
        boolean binary = KeyMaps.isBinary(id);
        if (s == 0 && binary && id != R.id.op_sub) return false;
        if (binary && hasTrailingBinary()
            && (id != R.id.op_sub || isOperator(s-1, R.id.op_sub))) {
            return false;
        }
        boolean isConstPiece = (d != KeyMaps.NOT_DIGIT || id == R.id.dec_point);
        if (isConstPiece) {
            if (s == 0) {
                mExpr.add(new Constant());
                s++;
            } else {
                Token last = mExpr.get(s-1);
                if(!(last instanceof Constant)) {
                    if (!(last instanceof Operator)) {
                        return false;
                    }
                    int lastOp = ((Operator)last).mId;
                    if (lastOp == R.id.const_e || lastOp == R.id.const_pi
                        || lastOp == R.id.op_fact
                        || lastOp == R.id.rparen) {
                        // Constant cannot possibly follow; reject immediately
                        return false;
                    }
                    mExpr.add(new Constant());
                    s++;
                }
            }
            return ((Constant)(mExpr.get(s-1))).add(id);
        } else {
            mExpr.add(new Operator(id));
            return true;
        }
    }

    // Append the contents of the argument expression.
    // It is assumed that the argument expression will not change,
    // and thus its pieces can be reused directly.
    // TODO: We probably only need this for expressions consisting of
    // a single PreEval "token", and may want to check that.
    void append(CalculatorExpr expr2) {
        int s2 = expr2.mExpr.size();
        for (int i = 0; i < s2; ++i) {
            mExpr.add(expr2.mExpr.get(i));
        }
    }

    // Undo the last key addition, if any.
    void delete() {
        int s = mExpr.size();
        if (s == 0) return;
        Token last = mExpr.get(s-1);
        if (last instanceof Constant) {
            Constant c = (Constant)last;
            c.delete();
            if (!c.isEmpty()) return;
        }
        mExpr.remove(s-1);
    }

    void clear() {
        mExpr.clear();
    }

    boolean isEmpty() {
        return mExpr.isEmpty();
    }

    // Returns a logical deep copy of the CalculatorExpr.
    // Operator and PreEval tokens are immutable, and thus
    // aren't really copied.
    public Object clone() {
        CalculatorExpr res = new CalculatorExpr();
        for (Token t: mExpr) {
            if (t instanceof Constant) {
                res.mExpr.add((Token)(((Constant)t).clone()));
            } else {
                res.mExpr.add(t);
            }
        }
        return res;
    }

    // Am I just a constant?
    boolean isConstant() {
        if (mExpr.size() != 1) return false;
        return mExpr.get(0) instanceof Constant;
    }

    // Return a new expression consisting of a single PreEval token
    // representing the current expression.
    // The caller supplies the value, degree mode, and short
    // string representation, which must have been previously computed.
    // Thus this is guaranteed to terminate reasonably quickly.
    CalculatorExpr abbreviate(CR val, BigInteger intVal,
                              boolean dm, String sr) {
        CalculatorExpr result = new CalculatorExpr();
        Token t = new PreEval(val, intVal,
                              new CalculatorExpr(
                                        (ArrayList<Token>)mExpr.clone()),
                              new EvalContext(dm), sr);
        result.mExpr.add(t);
        return result;
    }

    // Internal evaluation functions return an EvalRet triple.
    // We compute integer (BigInteger) results when possible, both as
    // a performance optimization, and to detect errors exactly when we can.
    private class EvalRet {
        int mPos; // Next position (expression index) to be parsed
        final CR mVal; // Constructive Real result of evaluating subexpression
        final BigInteger mIntVal;  // Exact Integer value or null if not integer
        EvalRet(int p, CR v, BigInteger i) {
            mPos = p;
            mVal = v;
            mIntVal = i;
        }
    }

    // And take a context argument:
    private static class EvalContext {
        // Memory register contents are not included here,
        // since we now make that an explicit part of the expression
        // If we add any other kinds of evaluation modes, they go here.
        boolean mDegreeMode;
        EvalContext(boolean degreeMode) {
            mDegreeMode = degreeMode;
        }
        EvalContext(DataInput in) throws IOException {
            mDegreeMode = in.readBoolean();
        }
        void write(DataOutput out) throws IOException {
            out.writeBoolean(mDegreeMode);
        }
    }

    private final CR RADIANS_PER_DEGREE = CR.PI.divide(CR.valueOf(180));

    private final CR DEGREES_PER_RADIAN = CR.valueOf(180).divide(CR.PI);

    private CR toRadians(CR x, EvalContext ec) {
        if (ec.mDegreeMode) {
            return x.multiply(RADIANS_PER_DEGREE);
        } else {
            return x;
        }
    }

    private CR fromRadians(CR x, EvalContext ec) {
        if (ec.mDegreeMode) {
            return x.multiply(DEGREES_PER_RADIAN);
        } else {
            return x;
        }
    }

    // The following methods can all throw IndexOutOfBoundsException
    // in the event of a syntax error.  We expect that to be caught in
    // eval below.

    private boolean isOperator(int i, int op) {
        if (i >= mExpr.size()) return false;
        Token t = mExpr.get(i);
        if (!(t instanceof Operator)) return false;
        return ((Operator)(t)).mId == op;
    }

    static class SyntaxError extends Error {
        public SyntaxError() {
            super();
        }
        public SyntaxError(String s) {
            super(s);
        }
    }

    // The following functions all evaluate some kind of expression
    // starting at position i in mExpr in a specified evaluation context.
    // They return both the expression value (as constructive real and,
    // if applicable, as BigInteger) and the position of the next token
    // that was not used as part of the evaluation.
    private EvalRet evalUnary(int i, EvalContext ec)
                    throws ArithmeticException {
        Token t = mExpr.get(i);
        CR value;
        if (t instanceof Constant) {
            Constant c = (Constant)t;
            value = CR.valueOf(c.toString(),10);
            return new EvalRet(i+1, value,
                               c.isInt()? new BigInteger(c.mWhole) : null);
        }
        if (t instanceof PreEval) {
            PreEval p = (PreEval)t;
            return new EvalRet(i+1, p.mValue, p.mIntValue);
        }
        EvalRet argVal;
        switch(((Operator)(t)).mId) {
        case R.id.const_pi:
            return new EvalRet(i+1, CR.PI, null);
        case R.id.const_e:
            return new EvalRet(i+1, CR.valueOf(1).exp(), null);
        case R.id.op_sqrt:
            // Seems to have highest precedence
            // Does not add implicit paren
            argVal = evalUnary(i+1, ec);
            return new EvalRet(argVal.mPos, argVal.mVal.sqrt(), null);
        case R.id.lparen:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            return new EvalRet(argVal.mPos, argVal.mVal, argVal.mIntVal);
        case R.id.fun_sin:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            return new EvalRet(argVal.mPos,
                toRadians(argVal.mVal,ec).sin(), null);
        case R.id.fun_cos:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            return new EvalRet(argVal.mPos,
                toRadians(argVal.mVal,ec).cos(), null);
        case R.id.fun_tan:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            CR argCR = toRadians(argVal.mVal, ec);
            return new EvalRet(argVal.mPos,
                argCR.sin().divide(argCR.cos()), null);
        case R.id.fun_ln:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            return new EvalRet(argVal.mPos, argVal.mVal.ln(), null);
        case R.id.fun_log:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            // FIXME: Heuristically test argument sign
            return new EvalRet(argVal.mPos,
                               argVal.mVal.ln().divide(CR.valueOf(10).ln()),
                               null);
        case R.id.fun_arcsin:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            // FIXME: Heuristically test argument in range
            return new EvalRet(argVal.mPos,
                               fromRadians(UnaryCRFunction
                                   .asinFunction.execute(argVal.mVal),ec),
                               null);
        case R.id.fun_arccos:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            // FIXME: Heuristically test argument in range
            return new EvalRet(argVal.mPos,
                               fromRadians(UnaryCRFunction
                                   .acosFunction.execute(argVal.mVal),ec),
                               null);
        case R.id.fun_arctan:
            argVal = evalExpr(i+1, ec);
            if (isOperator(argVal.mPos, R.id.rparen)) argVal.mPos++;
            return new EvalRet(argVal.mPos,
                               fromRadians(UnaryCRFunction
                                   .atanFunction.execute(argVal.mVal),ec),
                               null);
        default:
            throw new SyntaxError("Unrecognized token in expression");
        }
    }

    // Generalized factorial.
    // Compute n * (n - step) * (n - 2 * step) * ...
    // This can be used to compute factorial a bit faster, especially
    // if BigInteger uses sub-quadratic multiplication.
    private static BigInteger genFactorial(long n, long step) {
        if (n > 4 * step) {
            BigInteger prod1 = genFactorial(n, 2 * step);
            BigInteger prod2 = genFactorial(n - step, 2 * step);
            return prod1.multiply(prod2);
        } else {
            BigInteger res = BigInteger.valueOf(n);
            for (long i = n - step; i > 1; i -= step) {
                res = res.multiply(BigInteger.valueOf(i));
            }
            return res;
        }
    }

    // Compute an integral power of a constructive real.
    // Unlike the "general" case using logarithms, this handles a negative
    // base.
    private static CR pow(CR base, BigInteger exp) {
        if (exp.compareTo(BigInteger.ZERO) < 0) {
            return pow(base, exp.negate()).inverse();
        }
        if (exp.equals(BigInteger.ONE)) return base;
        if (exp.and(BigInteger.ONE).intValue() == 1) {
            return pow(base, exp.subtract(BigInteger.ONE)).multiply(base);
        }
        if (exp.equals(BigInteger.ZERO)) {
            return CR.valueOf(1);
        }
        CR tmp = pow(base, exp.shiftRight(1));
        return tmp.multiply(tmp);
    }

    private EvalRet evalFactorial(int i, EvalContext ec) {
        EvalRet tmp = evalUnary(i, ec);
        int cpos = tmp.mPos;
        CR cval = tmp.mVal;
        BigInteger ival = tmp.mIntVal;
        while (isOperator(cpos, R.id.op_fact)) {
            if (ival == null) {
                // Assume it was an integer, but we
                // didn't figure it out.
                // Calculator2 may have used the Gamma function.
                ival = cval.BigIntegerValue();
            }
            if (ival.compareTo(BigInteger.ZERO) <= 0
                || ival.compareTo(BIG_BILLION) > 0) {
                throw new ArithmeticException("Bad factorial argument");
            }
            ival = genFactorial(ival.longValue(), 1);
            ++cpos;
        }
        if (ival != null) cval = CR.valueOf(ival);
        return new EvalRet(cpos, cval, ival);
    }

    private EvalRet evalFactor(int i, EvalContext ec)
                    throws ArithmeticException {
        final EvalRet result1 = evalFactorial(i, ec);
        int cpos = result1.mPos;  // current position
        CR cval = result1.mVal;   // value so far
        BigInteger ival = result1.mIntVal;  // int value so far
        if (isOperator(cpos, R.id.op_pow)) {
            final EvalRet exp = evalSignedFactor(cpos+1, ec);
            cpos = exp.mPos;
            if (ival != null && ival.equals(BigInteger.ONE)) {
                // 1^x = 1
                return new EvalRet(cpos, cval, ival);
            }
            if (exp.mIntVal != null) {
                if (ival != null
                    && exp.mIntVal.compareTo(BigInteger.ZERO) >= 0
                    && exp.mIntVal.compareTo(BIG_MILLION) < 0
                    && ival.abs().compareTo(BIG_MILLION) < 0) {
                    // Use pure integer exponentiation
                    ival = ival.pow(exp.mIntVal.intValue());
                    cval = CR.valueOf(ival);
                } else {
                    // Integer exponent, cval may be negative;
                    // use repeated squaring.  Unsafe to use ln().
                    ival = null;
                    cval = pow(cval, exp.mIntVal);
                }
            } else {
                ival = null;
                cval = cval.ln().multiply(exp.mVal).exp();
            }
        }
        return new EvalRet(cpos, cval, ival);
    }

    private EvalRet evalSignedFactor(int i, EvalContext ec)
                    throws ArithmeticException {
        final boolean negative = isOperator(i, R.id.op_sub);
        int cpos = negative? i + 1 : i;
        EvalRet tmp = evalFactor(cpos, ec);
        cpos = tmp.mPos;
        CR cval = negative? tmp.mVal.negate() : tmp.mVal;
        BigInteger ival = (negative && tmp.mIntVal != null)?
                              tmp.mIntVal.negate()
                              : tmp.mIntVal;
        return new EvalRet(cpos, cval, ival);
    }

    private boolean canStartFactor(int i) {
        if (i >= mExpr.size()) return false;
        Token t = mExpr.get(i);
        if (!(t instanceof Operator)) return true;
        int id = ((Operator)(t)).mId;
        if (KeyMaps.isBinary(id)) return false;
        switch (id) {
            case R.id.op_fact:
            case R.id.rparen:
                return false;
            default:
                return true;
        }
    }

    private EvalRet evalTerm(int i, EvalContext ec)
                    throws ArithmeticException {
        EvalRet tmp = evalSignedFactor(i, ec);
        boolean is_mul = false;
        boolean is_div = false;
        int cpos = tmp.mPos;   // Current position in expression.
        CR cval = tmp.mVal;    // Current value.
        BigInteger ival = tmp.mIntVal;
        while ((is_mul = isOperator(cpos, R.id.op_mul))
               || (is_div = isOperator(cpos, R.id.op_div))
               || canStartFactor(cpos)) {
            if (is_mul || is_div) ++cpos;
            tmp = evalTerm(cpos, ec);
            if (is_div) {
                if (ival != null && tmp.mIntVal != null
                    && ival.mod(tmp.mIntVal) == BigInteger.ZERO) {
                    ival = ival.divide(tmp.mIntVal);
                    cval = CR.valueOf(ival);
                } else {
                    cval = cval.divide(tmp.mVal);
                    ival = null;
                }
            } else {
                if (ival != null && tmp.mIntVal != null) {
                    ival = ival.multiply(tmp.mIntVal);
                    cval = CR.valueOf(ival);
                } else {
                    cval = cval.multiply(tmp.mVal);
                    ival = null;
                }
            }
            cpos = tmp.mPos;
            is_mul = is_div = false;
        }
        return new EvalRet(cpos, cval, ival);
    }

    private EvalRet evalExpr(int i, EvalContext ec) throws ArithmeticException {
        EvalRet tmp = evalTerm(i, ec);
        boolean is_plus;
        int cpos = tmp.mPos;
        CR cval = tmp.mVal;
        BigInteger ival = tmp.mIntVal;
        while ((is_plus = isOperator(cpos, R.id.op_add))
               || isOperator(cpos, R.id.op_sub)) {
            tmp = evalTerm(cpos+1, ec);
            if (is_plus) {
                if (ival != null && tmp.mIntVal != null) {
                    ival = ival.add(tmp.mIntVal);
                    cval = CR.valueOf(ival);
                } else {
                    cval = cval.add(tmp.mVal);
                    ival = null;
                }
            } else {
                if (ival != null && tmp.mIntVal != null) {
                    ival = ival.subtract(tmp.mIntVal);
                    cval = CR.valueOf(ival);
                } else {
                    cval = cval.subtract(tmp.mVal);
                    ival = null;
                }
            }
            cpos = tmp.mPos;
        }
        return new EvalRet(cpos, cval, ival);
    }

    // Externally visible evaluation result.
    public class EvalResult {
        EvalResult (CR val, BigInteger intVal) {
            mVal = val;
            mIntVal = intVal;
        }
        final CR mVal;
        final BigInteger mIntVal;
    }

    // Evaluate the entire expression, returning null in the event
    // of an error.
    // Not called from the UI thread, but should not be called
    // concurrently with modifications to the expression.
    EvalResult eval(boolean degreeMode) throws SyntaxError,
                        ArithmeticException, PrecisionOverflowError
    {
        try {
            EvalContext ec = new EvalContext(degreeMode);
            EvalRet res = evalExpr(0, ec);
            if (res.mPos != mExpr.size()) return null;
            return new EvalResult(res.mVal, res.mIntVal);
        } catch (IndexOutOfBoundsException e) {
            throw new SyntaxError("Unexpected expression end");
        }
    }

    // Produce a string representation of the expression itself
    String toString(Context context) {
        StringBuilder sb = new StringBuilder();
        for (Token t: mExpr) {
            sb.append(t.toString(context));
        }
        return sb.toString();
    }
}
