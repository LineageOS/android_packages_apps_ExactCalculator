/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.constraintlayout.motion.widget.MotionLayout
import kotlin.math.abs

class DisplayMotionLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    i: Int = 0,
) : MotionLayout(context, attributeSet, i) {
    private var pointerId = -1
    private var isScrolling = false
    private var previousPoint: PointF? = null
    private var previousEvent: MotionEvent? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var outOfBounds = false

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val hitRect = Rect()

                findViewById<View>(R.id.display).getHitRect(hitRect)
                if (hitRect.contains(motionEvent.x.toInt(), motionEvent.y.toInt())) {
                    previousPoint = PointF(motionEvent.x, motionEvent.y)
                    pointerId = motionEvent.getPointerId(0)
                    isScrolling = false
                    outOfBounds = false
                    saveLastMotion(motionEvent)
                } else {
                    pointerId = -1
                    isScrolling = false
                    outOfBounds = true
                    clearLastMotion()
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP -> {
                isScrolling = false
                outOfBounds = false
                pointerId = -1
                clearLastMotion()
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = motionEvent.findPointerIndex(pointerId)
                if (pointerId != -1 && pointerIndex != -1 && !outOfBounds) {
                    val y = abs(motionEvent.getY(pointerIndex) - previousPoint!!.y)
                    if (y > touchSlop) {
                        isScrolling = true
                        onTouchEvent(previousEvent)
                    }
                }
            }
        }
        if (super.onInterceptTouchEvent(motionEvent)) {
            return true
        }
        return isScrolling && !outOfBounds
    }

    private fun saveLastMotion(motionEvent: MotionEvent) {
        previousEvent?.recycle()
        previousEvent = MotionEvent.obtain(motionEvent)
    }

    private fun clearLastMotion() {
        previousEvent?.recycle()
        previousEvent = null
    }
}
