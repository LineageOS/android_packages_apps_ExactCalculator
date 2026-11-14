/*
 * SPDX-FileCopyrightText: 2016 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2

/**
 * Some helpful methods operating on strings.
 */
object StringUtils {

    /**
     * Return a string with n copies of c.
     */
    @JvmStatic
    fun repeat(c: Char, n: Int): String {
        val result = StringBuilder()
        for (i in 0 until n) {
            result.append(c)
        }
        return result.toString()
    }

    /**
     * Return a copy of the supplied string with commas added every three digits.
     * The substring indicated by the supplied range is assumed to contain only
     * a whole number, with no decimal point.
     * Inserting a digit separator every 3 digits appears to be
     * at least somewhat acceptable, though not necessarily preferred, everywhere.
     * The grouping separator in the result is NOT localized.
     */
    @JvmStatic
    fun addCommas(s: String, begin: Int, end: Int): String {
        // Resist the temptation to use Java's NumberFormat, which converts to long or double
        // and hence doesn't handle very large numbers.
        val result = StringBuilder()
        var current = begin
        while (current < end && (s[current] == '-' || s[current] == ' ')) {
            ++current
        }
        result.append(s, begin, current)
        while (current < end) {
            result.append(s[current])
            ++current
            if ((end - current) % 3 == 0 && end != current) {
                result.append(',')
            }
        }
        return result.toString()
    }

    /**
     * Ignoring all occurrences of c in both strings, check whether old is a prefix of new.
     * If so, return the remaining subsequence of whole. If not, return null.
     */
    @JvmStatic
    fun getExtensionIgnoring(whole: CharSequence, prefix: CharSequence, c: Char): CharSequence? {
        var wIndex = 0
        var pIndex = 0
        val wLen = whole.length
        val pLen = prefix.length
        while (true) {
            while (pIndex < pLen && prefix[pIndex] == c) {
                ++pIndex
            }
            while (wIndex < wLen && whole[wIndex] == c) {
                ++wIndex
            }
            if (pIndex == pLen) {
                break
            }
            if (wIndex == wLen || whole[wIndex] != prefix[pIndex]) {
                return null
            }
            ++pIndex
            ++wIndex
        }
        while (wIndex < wLen && whole[wIndex] == c) {
            ++wIndex
        }
        return whole.subSequence(wIndex, wLen)
    }
}
