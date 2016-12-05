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
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

public class DragLayout extends RelativeLayout {

    private static final String TAG = "DragLayout";
    private static final double AUTO_OPEN_SPEED_LIMIT = 800.0;
    private static final String KEY_IS_OPEN = "IS_OPEN";
    private static final String KEY_SUPER_STATE = "SUPER_STATE";

    private FrameLayout mHistoryFrame;
    private ViewDragHelper mDragHelper;

    private final List<DragCallback> mDragCallbacks = new ArrayList<>();
    private CloseCallback mCloseCallback;

    private int mDraggingState = ViewDragHelper.STATE_IDLE;
    private int mDraggingBorder;
    private int mVerticalRange;
    private boolean mIsOpen;

    // Used to determine whether a touch event should be intercepted.
    private float mInitialDownX;
    private float mInitialDownY;

    public DragLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mDragHelper = ViewDragHelper.create(this, 1.0f, new DragHelperCallback());
        mHistoryFrame = (FrameLayout) findViewById(R.id.history_frame);
        super.onFinishInflate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) {
            for (DragCallback c : mDragCallbacks) {
               c.onLayout(t-b);
            }
            if (mIsOpen) {
                setOpen();
            } else {
                setClosed();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int height = 0;
        for (DragCallback c : mDragCallbacks) {
            height += c.getDisplayHeight();
        }
        mVerticalRange = h - height;
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
        final int action = event.getActionMasked();

        // Always handle the case of the touch gesture being complete.
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            // Release the scroll.
            mDragHelper.cancel();
            return false; // Do not intercept touch event, let the child handle it
        }

        final float x = event.getX();
        final float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mInitialDownX = x;
                mInitialDownY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                final float deltaX = Math.abs(x - mInitialDownX);
                final float deltaY = Math.abs(y - mInitialDownY);
                final int slop = mDragHelper.getTouchSlop();
                if (deltaY > slop && deltaY > deltaX) {
                    break;
                } else {
                    return false;
                }
        }
        boolean doDrag = true;
        for (DragCallback c : mDragCallbacks) {
            doDrag &= c.shouldInterceptTouchEvent(event);
        }
        return doDrag && mDragHelper.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mDragHelper.processTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void onStartDragging() {
        for (DragCallback c : mDragCallbacks) {
            c.onStartDraggingOpen();
        }
        mHistoryFrame.setVisibility(VISIBLE);
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
        mHistoryFrame.setVisibility(VISIBLE);
    }

    public void setClosed() {
        mDragHelper.smoothSlideViewTo(mHistoryFrame, 0, 0);
    }

    public void setCloseCallback(CloseCallback callback) {
        mCloseCallback = callback;
    }

    public void addDragCallback(DragCallback callback) {
        mDragCallbacks.add(callback);
    }

    public void removeDragCallback(DragCallback callback) {
        mDragCallbacks.remove(callback);
    }

    /**
     * Callback when the layout is closed.
     * We use this to pop the HistoryFragment off the backstack.
     * We can't use a method in DragCallback because we get ConcurrentModificationExceptions on
     * mDragCallbacks when executePendingTransactions() is called for popping the fragment off the
     * backstack.
     */
    public interface CloseCallback {
        void onClose();
    }

    /**
     * Callbacks for coordinating with the RecyclerView or HistoryFragment.
     */
    public interface DragCallback {
        // Callback when a drag to open begins.
        void onStartDraggingOpen();

        // Animate the RecyclerView text.
        void whileDragging(float yFraction);

        // Whether we should intercept the touch event
        boolean shouldInterceptTouchEvent(MotionEvent event);

        int getDisplayHeight();

        void onLayout(int translation);
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
                    mIsOpen = false;
                    mHistoryFrame.setVisibility(GONE);
                    if (mCloseCallback != null) {
                        mCloseCallback.onClose();
                    }
                } else if (mDraggingBorder == mVerticalRange) {
                    setOpen();
                    mIsOpen = true;
                }
            } else if (state == ViewDragHelper.STATE_DRAGGING && !mIsOpen) {
                onStartDragging();
            }
            mDraggingState = state;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            mDraggingBorder = top;

            // Animate RecyclerView text.
            for (DragCallback c : mDragCallbacks) {
                c.whileDragging(top / (mVerticalRange * 1.0f));
            }
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