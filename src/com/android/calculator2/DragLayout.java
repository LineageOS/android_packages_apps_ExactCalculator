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
import android.graphics.PointF;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class DragLayout extends RelativeLayout {

    private static final String TAG = "DragLayout";
    private static final double AUTO_OPEN_SPEED_LIMIT = 800.0;
    private static final String KEY_IS_OPEN = "IS_OPEN";
    private static final String KEY_SUPER_STATE = "SUPER_STATE";

    private FrameLayout mHistoryFrame;
    private ViewDragHelper mDragHelper;

    // No concurrency; allow modifications while iterating.
    private final List<DragCallback> mDragCallbacks = new CopyOnWriteArrayList<>();
    private CloseCallback mCloseCallback;

    private final Map<Integer, PointF> mLastMotionPoints = new HashMap<>();
    private final Rect mHitRect = new Rect();

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
        super.onFinishInflate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) {
            for (DragCallback c : mDragCallbacks) {
                c.onLayout(t - b);
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

    private void saveLastMotion(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int actionIndex = event.getActionIndex();
                final int pointerId = event.getPointerId(actionIndex);
                final PointF point = new PointF(event.getX(actionIndex), event.getY(actionIndex));
                mLastMotionPoints.put(pointerId, point);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = event.getPointerCount() - 1; i >= 0; --i) {
                    final int pointerId = event.getPointerId(i);
                    final PointF point = mLastMotionPoints.get(pointerId);
                    if (point != null) {
                        point.set(event.getX(i), event.getY(i));
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int actionIndex = event.getActionIndex();
                final int pointerId = event.getPointerId(actionIndex);
                mLastMotionPoints.remove(pointerId);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mLastMotionPoints.clear();
                break;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        saveLastMotion(event);
        return mDragHelper.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        saveLastMotion(event);
        mDragHelper.processTouchEvent(event);
        return true;
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

    public boolean isViewUnder(View view, int x, int y) {
        view.getHitRect(mHitRect);
        offsetDescendantRectToMyCoords((View) view.getParent(), mHitRect);
        return mHitRect.contains(x, y);
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

        // Whether we should allow the view to be dragged.
        boolean shouldCaptureView(View view, int x, int y);

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
        public boolean tryCaptureView(View view, int pointerId) {
            final PointF point = mLastMotionPoints.get(pointerId);
            if (point == null) {
                return false;
            }

            final int x = (int) point.x;
            final int y = (int) point.y;

            for (DragCallback c : mDragCallbacks) {
                if (!c.shouldCaptureView(view, x, y)) {
                    return false;
                }
            }
            return true;
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
