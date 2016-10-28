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

package com.android.calculator2;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

public class DragLayout extends RelativeLayout {

    private static final double AUTO_OPEN_SPEED_LIMIT = 800.0;
    private static final String KEY_IS_OPEN = "IS_OPEN";
    private static final String KEY_SUPER_STATE = "SUPER_STATE";

    private final Rect mHitRect = new Rect();

    private CalculatorDisplay mCalculatorDisplay;
    private FrameLayout mHistoryFrame;
    private ViewDragHelper mDragHelper;

    private OnDragCallback mOnDragCallback;

    private int mDraggingState = ViewDragHelper.STATE_IDLE;
    private int mDraggingBorder;
    private int mVerticalRange;
    private boolean mIsOpen;

    public DragLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mDragHelper = ViewDragHelper.create(this, 1.0f, new DragHelperCallback());
        mHistoryFrame = (FrameLayout) findViewById(R.id.history_frame);
        mCalculatorDisplay = (CalculatorDisplay) findViewById(R.id.display);
        super.onFinishInflate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) {
            mHistoryFrame.setTranslationY(-(b - t) + mCalculatorDisplay.getBottom());

            if (mIsOpen) {
                setOpen();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mVerticalRange = h - mCalculatorDisplay.getMeasuredHeight();
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState());
        bundle.putBoolean(KEY_IS_OPEN, mIsOpen);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle bundle = (Bundle) state;
            mIsOpen = bundle.getBoolean(KEY_IS_OPEN);
            state = bundle.getParcelable(KEY_SUPER_STATE);
        }
        super.onRestoreInstanceState(state);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return (isDisplayTarget(event) || isHistoryTarget(event))
                && mDragHelper.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isDisplayTarget(event) || isHistoryTarget(event) || isMoving()) {
            mDragHelper.processTouchEvent(event);
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void onStartDragging() {
        mOnDragCallback.onStartDragging();
        mHistoryFrame.setVisibility(VISIBLE);
    }

    private boolean isViewTarget(View view, MotionEvent event) {
        view.getHitRect(mHitRect);
        offsetDescendantRectToMyCoords(view, mHitRect);
        return mHitRect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    private boolean isDisplayTarget(MotionEvent event) {
        return isViewTarget(mCalculatorDisplay, event);
    }

    private boolean isHistoryTarget(MotionEvent event) {
        return isViewTarget(mHistoryFrame, event);
    }

    public boolean isMoving() {
        return mDraggingState == ViewDragHelper.STATE_DRAGGING
                || mDraggingState == ViewDragHelper.STATE_SETTLING;
    }

    public boolean isOpen() {
        return mIsOpen;
    }

    public void setOpen() {
        mDragHelper.smoothSlideViewTo(mHistoryFrame, 0, mVerticalRange);
        mIsOpen = true;
        mHistoryFrame.setVisibility(VISIBLE);
    }

    public void setClosed() {
        mDragHelper.smoothSlideViewTo(mHistoryFrame, 0, 0);
        mIsOpen = false;
        mOnDragCallback.onDragToClose();
        mHistoryFrame.setVisibility(GONE);
    }

    public void setOnDragCallback(OnDragCallback callback) {
        mOnDragCallback = callback;
    }

    public interface OnDragCallback {
        // Callback when a drag in any direction begins.
        void onStartDragging();

        // Callback when a drag is used to close.
        void onDragToClose();
    }

    public class DragHelperCallback extends ViewDragHelper.Callback {
        @Override
        public void onViewDragStateChanged(int state) {
            if (state == mDraggingState) {
                // No change.
                return;
            }
            if ((mDraggingState == ViewDragHelper.STATE_DRAGGING
                    || mDraggingState == ViewDragHelper.STATE_SETTLING)
                    && state == ViewDragHelper.STATE_IDLE) {
                // The view stopped moving.
                if (mDraggingBorder == 0) {
                    setClosed();
                } else if (mDraggingBorder == mVerticalRange) {
                    setOpen();
                }
            }
            if (state == ViewDragHelper.STATE_DRAGGING) {
                onStartDragging();
            }
            mDraggingState = state;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            mDraggingBorder = top;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return mVerticalRange;
        }

        @Override
        public boolean tryCaptureView(View view, int i) {
            return view.getId() == R.id.history_frame;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final int topBound = getPaddingTop();
            final int bottomBound = mVerticalRange;
            return Math.min(Math.max(top, topBound), bottomBound);
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            if (mDraggingBorder == 0) {
                setClosed();
                return;
            }
            if (mDraggingBorder == mVerticalRange) {
                setOpen();
                return;
            }
            boolean settleToOpen = false;
            final float threshold = mVerticalRange / 2;
            if (yvel > AUTO_OPEN_SPEED_LIMIT) {
                // Speed has priority over position.
                settleToOpen = true;
            } else if (yvel < -AUTO_OPEN_SPEED_LIMIT) {
                settleToOpen = false;
            } else if (mDraggingBorder > threshold) {
                settleToOpen = true;
            } else if (mDraggingBorder < threshold) {
                settleToOpen = false;
            }

            if (mDragHelper.settleCapturedViewAt(0, settleToOpen ? mVerticalRange : 0)) {
                ViewCompat.postInvalidateOnAnimation(DragLayout.this);
            }
        }
    }
}