/*
 * SPDX-FileCopyrightText: 2015 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2

import com.hp.creals.CR
import java.math.BigInteger
import java.util.Objects
import java.util.Random

/**
 * Rational numbers that may turn to null if they get too big.
 * For many operations, if the length of the nuumerator plus the length of the denominator exceeds
 * a maximum size, we simply return null, and rely on our caller do something else.
 * We currently never return null for a pure integer or for a BoundedRational that has just been
 * constructed.
 *
 * We also implement a number of irrational functions.  These return a non-null result only when
 * the result is known to be rational.
 */
class BoundedRational(private val num: BigInteger, private val den: BigInteger) {
    // TODO: Consider returning null for integers.  With some care, large factorials might become
    // much faster.
    // TODO: Maybe eventually make this extend Number?

    constructor(n: BigInteger) : this(n, BigInteger.ONE)

    constructor(n: Long, d: Long) : this(BigInteger.valueOf(n), BigInteger.valueOf(d))

    constructor(n: Long) : this(BigInteger.valueOf(n), BigInteger.valueOf(1))

    /**
     * Convert to String reflecting raw representation.
     * Debug or log messages only, not pretty.
     */
    override fun toString(): String {
        return num.toString() + "/" + den.toString()
    }

    /**
     * Convert to readable String.
     * Intended for output to user.  More expensive, less useful for debugging than
     * toString().  Not internationalized.
     */
    fun toNiceString(): String {
        val nicer = reduce().positiveDen()
        var result = nicer.num.toString()
        if (nicer.den != BigInteger.ONE) {
            result += "/" + nicer.den
        }
        return result
    }

    /**
     * Returns a truncated (rounded towards 0) representation of the result.
     * Includes n digits to the right of the decimal point.
     * @param n result precision, >= 0
     */
    fun toStringTruncated(n: Int): String {
        var digits = num.abs().multiply(BigInteger.TEN.pow(n)).divide(den.abs()).toString()
        var len = digits.length
        if (len < n + 1) {
            digits = StringUtils.repeat('0', n + 1 - len) + digits
            len = n + 1
        }
        return (if (signum() < 0) "-" else "") + digits.substring(0, len - n) + "." +
                digits.substring(len - n)
    }

    /**
     * Return a double approximation.
     * The result is correctly rounded to nearest, with ties rounded away from zero.
     * TODO: Should round ties to even.
     */
    fun doubleValue(): Double {
        val sign = signum()
        if (sign < 0) {
            return -negate(this)!!.doubleValue()
        }
        // We get the mantissa by dividing the numerator by denominator, after
        // suitably prescaling them so that the integral part of the result contains
        // enough bits. We do the prescaling to avoid any precision loss, so the division result
        // is correctly truncated towards zero.
        val apprExp = num.bitLength() - den.bitLength()
        if (apprExp < -1100 || sign == 0) {
            // Bail fast for clearly zero result.
            return 0.0
        }
        val neededPrec = apprExp - 80
        val dividend = if (neededPrec < 0) num.shiftLeft(-neededPrec) else num
        val divisor = if (neededPrec > 0) den.shiftLeft(neededPrec) else den
        val quotient = dividend.divide(divisor)
        val qLength = quotient.bitLength()
        var extraBits = qLength - 53
        var exponent = neededPrec + qLength  // Exponent assuming leading binary point.
        if (exponent >= -1021) {
            // Binary point is actually to right of leading bit.
            --exponent
        } else {
            // We're in the gradual underflow range. Drop more bits.
            extraBits += (-1022 - exponent) + 1
            exponent = -1023
        }
        val bigMantissa =
            quotient.add(BigInteger.ONE.shiftLeft(extraBits - 1)).shiftRight(extraBits)
        if (exponent > 1024) {
            return Double.POSITIVE_INFINITY
        }
        if (exponent > -1023 && bigMantissa.bitLength() != 53
            || exponent <= -1023 && bigMantissa.bitLength() >= 53
        ) {
            throw AssertionError("doubleValue internal error")
        }
        val mantissa = bigMantissa.toLong()
        val bits = (mantissa and ((1L shl 52) - 1)) or ((exponent.toLong() + 1023) shl 52)
        return java.lang.Double.longBitsToDouble(bits)
    }

    fun crValue(): CR {
        return CR.valueOf(num).divide(CR.valueOf(den))
    }

    fun intValue(): Int {
        val reduced = reduce()
        if (reduced.den != BigInteger.ONE) {
            throw ArithmeticException("intValue of non-int")
        }
        return reduced.num.toInt()
    }

    // Approximate number of bits to left of binary point.
    // Negative indicates leading zeroes to the right of binary point.
    fun wholeNumberBits(): Int {
        return if (num.signum() == 0) {
            Int.MIN_VALUE
        } else {
            num.bitLength() - den.bitLength()
        }
    }

    /**
     * Is this number too big for us to continue with rational arithmetic?
     * We return fals for integers on the assumption that we have no better fallback.
     */
    private fun tooBig(): Boolean {
        if (den == BigInteger.ONE) {
            return false
        }
        return (num.bitLength() + den.bitLength() > MAX_SIZE)
    }

    /**
     * Return an equivalent fraction with a positive denominator.
     */
    private fun positiveDen(): BoundedRational {
        if (den.signum() > 0) {
            return this
        }
        return BoundedRational(num.negate(), den.negate())
    }

    /**
     * Return an equivalent fraction in lowest terms.
     * Denominator sign may remain negative.
     */
    private fun reduce(): BoundedRational {
        if (den == BigInteger.ONE) {
            return this  // Optimization only
        }
        val divisor = num.gcd(den)
        return BoundedRational(num.divide(divisor), den.divide(divisor))
    }

    fun compareTo(r: BoundedRational): Int {
        // Compare by multiplying both sides by denominators, invert result if denominator product
        // was negative.
        return num.multiply(r.den).compareTo(r.num.multiply(den)) * den.signum() *
                r.den.signum()
    }

    fun signum(): Int {
        return num.signum() * den.signum()
    }

    override fun hashCode(): Int {
        // Note that this may be too expensive to be useful.
        val reduced = reduce().positiveDen()
        return Objects.hash(reduced.num, reduced.den)
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other is BoundedRational && compareTo(other) == 0
    }

    class ZeroDivisionException : ArithmeticException("Division by zero")

    /**
     * Compute integral power of this, assuming this has been reduced and exp is >= 0.
     */
    private fun rawPow(exp: BigInteger): BoundedRational? {
        if (exp == BigInteger.ONE) {
            return this
        }
        if (exp.and(BigInteger.ONE).toInt() == 1) {
            return rawMultiply(rawPow(exp.subtract(BigInteger.ONE)), this)
        }
        if (exp.signum() == 0) {
            return ONE
        }
        val tmp = rawPow(exp.shiftRight(1))
        if (Thread.interrupted()) {
            throw CR.AbortedException()
        }
        val result = rawMultiply(tmp, tmp)
        if (result == null || result.tooBig()) {
            return null
        }
        return result
    }

    /**
     * Compute an integral power of this.
     */
    fun pow(exp: BigInteger): BoundedRational? {
        val expSign = exp.signum()
        if (expSign == 0) {
            // Questionable if base has undefined or zero value.
            // java.lang.Math.pow() returns 1 anyway, so we do the same.
            return ONE
        }
        if (exp == BigInteger.ONE) {
            return this
        }
        // Reducing once at the beginning means there's no point in reducing later.
        val reduced = reduce().positiveDen()
        // First handle cases in which huge exponents could give compact results.
        if (reduced.den == BigInteger.ONE) {
            if (reduced.num == BigInteger.ZERO) {
                return ZERO
            }
            if (reduced.num == BigInteger.ONE) {
                return ONE
            }
            if (reduced.num == BIG_MINUS_ONE) {
                return if (exp.testBit(0)) {
                    MINUS_ONE
                } else {
                    ONE
                }
            }
        }
        if (exp.bitLength() > 1000) {
            // Stack overflow is likely; a useful rational result is not.
            return null
        }
        return if (expSign < 0) {
            inverse(reduced)!!.rawPow(exp.negate())
        } else {
            reduced.rawPow(exp)
        }
    }

    companion object {
        private const val MAX_SIZE = 10000 // total, in bits

        /**
         * Produce BoundedRational equal to the given double.
         */
        @JvmStatic
        fun valueOf(x: Double): BoundedRational {
            val l = Math.round(x)
            if (l.toDouble() == x && Math.abs(l) <= 1000) {
                return valueOf(l)
            }
            val allBits = java.lang.Double.doubleToRawLongBits(Math.abs(x))
            var mantissa = (allBits and ((1L shl 52) - 1))
            val biased_exp = (allBits ushr 52).toInt()
            if ((biased_exp and 0x7ff) == 0x7ff) {
                throw ArithmeticException("Infinity or NaN not convertible to BoundedRational")
            }
            val sign: Long = if (x < 0.0) -1 else 1
            var exp = biased_exp - 1075  // 1023 + 52; we treat mantissa as integer.
            if (biased_exp == 0) {
                exp += 1  // Denormal exponent is 1 greater.
            } else {
                mantissa += (1L shl 52)  // Implied leading one.
            }
            var num = BigInteger.valueOf(sign * mantissa)
            var den = BigInteger.ONE
            if (exp >= 0) {
                num = num.shiftLeft(exp)
            } else {
                den = den.shiftLeft(-exp)
            }
            return BoundedRational(num, den)
        }

        /**
         * Produce BoundedRational equal to the given long.
         */
        @JvmStatic
        fun valueOf(x: Long): BoundedRational {
            if (x >= -2 && x <= 10) {
                when (x.toInt()) {
                    -2 -> return MINUS_TWO
                    -1 -> return MINUS_ONE
                    0 -> return ZERO
                    1 -> return ONE
                    2 -> return TWO
                    10 -> return TEN
                }
            }
            return BoundedRational(x)
        }

        private val sReduceRng = Random()

        /**
         * Return a possibly reduced version of r that's not tooBig().
         * Return null if none exists.
         */
        private fun maybeReduce(r: BoundedRational?): BoundedRational? {
            if (r == null) return null
            // Reduce randomly, with 1/16 probability, or if the result is too big.
            if (!r.tooBig() && (sReduceRng.nextInt() and 0xf) != 0) {
                return r
            }
            var result = r.positiveDen()
            result = result.reduce()
            if (!result.tooBig()) {
                return result
            }
            return null
        }

        // We use static methods for arithmetic, so that we can easily handle the null case.  We try
        // to catch domain errors whenever possible, sometimes even when one of the arguments is null,
        // but not relevant.

        /**
         * Returns equivalent BigInteger result if it exists, null if not.
         */
        @JvmStatic
        fun asBigInteger(r: BoundedRational?): BigInteger? {
            if (r == null) {
                return null
            }
            val quotAndRem = r.num.divideAndRemainder(r.den)
            return if (quotAndRem[1].signum() == 0) {
                quotAndRem[0]
            } else {
                null
            }
        }

        @JvmStatic
        fun add(r1: BoundedRational?, r2: BoundedRational?): BoundedRational? {
            if (r1 == null || r2 == null) {
                return null
            }
            val den = r1.den.multiply(r2.den)
            val num = r1.num.multiply(r2.den).add(r2.num.multiply(r1.den))
            return maybeReduce(BoundedRational(num, den))
        }

        /**
         * Return the argument, but with the opposite sign.
         * Returns null only for a null argument.
         */
        @JvmStatic
        fun negate(r: BoundedRational?): BoundedRational? {
            if (r == null) {
                return null
            }
            return BoundedRational(r.num.negate(), r.den)
        }

        /**
         * Return product of r1 and r2 without reducing the result.
         */
        private fun rawMultiply(r1: BoundedRational?, r2: BoundedRational?): BoundedRational? {
            // It's tempting but marginally unsound to reduce 0 * null to 0.  The null could represent
            // an infinite value, for which we failed to throw an exception because it was too big.
            if (r1 == null || r2 == null) {
                return null
            }
            // Optimize the case of our special ONE constant, since that's cheap and somewhat frequent.
            if (r1 === ONE) {
                return r2
            }
            if (r2 === ONE) {
                return r1
            }
            val num = r1.num.multiply(r2.num)
            val den = r1.den.multiply(r2.den)
            return BoundedRational(num, den)
        }

        @JvmStatic
        fun multiply(r1: BoundedRational?, r2: BoundedRational?): BoundedRational? {
            return maybeReduce(rawMultiply(r1, r2))
        }

        /**
         * Return the reciprocal of r (or null if the argument was null).
         */
        @JvmStatic
        fun inverse(r: BoundedRational?): BoundedRational? {
            if (r == null) {
                return null
            }
            if (r.num.signum() == 0) {
                throw ZeroDivisionException()
            }
            return BoundedRational(r.den, r.num)
        }

        @JvmStatic
        fun divide(r1: BoundedRational?, r2: BoundedRational?): BoundedRational? {
            return multiply(r1, inverse(r2))
        }

        @JvmStatic
        fun sqrt(r: BoundedRational?): BoundedRational? {
            // Return non-null if numerator and denominator are small perfect squares.
            if (r == null) {
                return null
            }
            val reduced = r.positiveDen().reduce()
            if (reduced.num.signum() < 0) {
                throw ArithmeticException("sqrt(negative)")
            }
            val num_sqrt = BigInteger.valueOf(Math.round(Math.sqrt(reduced.num.toDouble())))
            if (num_sqrt.multiply(num_sqrt) != reduced.num) {
                return null
            }
            val den_sqrt = BigInteger.valueOf(Math.round(Math.sqrt(reduced.den.toDouble())))
            if (den_sqrt.multiply(den_sqrt) != reduced.den) {
                return null
            }
            return BoundedRational(num_sqrt, den_sqrt)
        }

        @JvmField
        val ZERO = BoundedRational(0)

        @JvmField
        val HALF = BoundedRational(1, 2)

        @JvmField
        val MINUS_HALF = BoundedRational(-1, 2)

        @JvmField
        val THIRD = BoundedRational(1, 3)

        @JvmField
        val QUARTER = BoundedRational(1, 4)

        @JvmField
        val SIXTH = BoundedRational(1, 6)

        @JvmField
        val ONE = BoundedRational(1)

        @JvmField
        val MINUS_ONE = BoundedRational(-1)

        @JvmField
        val TWO = BoundedRational(2)

        @JvmField
        val MINUS_TWO = BoundedRational(-2)

        @JvmField
        val TEN = BoundedRational(10)

        @JvmField
        val TWELVE = BoundedRational(12)
        private val BIG_MINUS_ONE = BigInteger.valueOf(-1)

        @JvmStatic
        fun pow(base: BoundedRational?, exp: BoundedRational?): BoundedRational? {
            if (exp == null) {
                return null
            }
            if (base == null) {
                return null
            }
            val reducedExp = exp.reduce().positiveDen()
            if (reducedExp.den != BigInteger.ONE) {
                return null
            }
            return base.pow(reducedExp.num)
        }

        private val BIG_FIVE = BigInteger.valueOf(5)

        /**
         * Return the number of decimal digits to the right of the decimal point required to represent
         * the argument exactly.
         * Return Integer.MAX_VALUE if that's not possible.  Never returns a value less than zero, even
         * if r is a power of ten.
         */
        @JvmStatic
        fun digitsRequired(r: BoundedRational?): Int {
            if (r == null) {
                return Int.MAX_VALUE
            }
            var powersOfTwo = 0  // Max power of 2 that divides denominator
            var powersOfFive = 0  // Max power of 5 that divides denominator
            // Try the easy case first to speed things up.
            if (r.den == BigInteger.ONE) {
                return 0
            }
            var den = r.reduce().den
            if (den.bitLength() > MAX_SIZE) {
                return Int.MAX_VALUE
            }
            while (!den.testBit(0)) {
                ++powersOfTwo
                den = den.shiftRight(1)
            }
            while (den.mod(BIG_FIVE).signum() == 0) {
                ++powersOfFive
                den = den.divide(BIG_FIVE)
            }
            // If the denominator has a factor of other than 2 or 5 (the divisors of 10), the decimal
            // expansion does not terminate.  Multiplying the fraction by any number of powers of 10
            // will not cancel the demoniator.  (Recall the fraction was in lowest terms to start
            // with.) Otherwise the powers of 10 we need to cancel the denominator is the larger of
            // powersOfTwo and powersOfFive.
            if (den != BigInteger.ONE && den != BIG_MINUS_ONE) {
                return Int.MAX_VALUE
            }
            return Math.max(powersOfTwo, powersOfFive)
        }
    }
}
