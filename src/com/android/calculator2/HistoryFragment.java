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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

public class HistoryFragment extends Fragment {

    public static final String TAG = "HistoryFragment";

    private final DragLayout.DragCallback mDragCallback =
            new DragLayout.DragCallback() {
                @Override
                public void onStartDragging() {
                    // no-op
                }

                @Override
                public void whileDragging(float yFraction) {
                    mDragController.animateViews(yFraction, mRecyclerView, mAdapter.getItemCount());
                }

                @Override
                public void onClosed() {
                    mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
                }

                @Override
                public boolean allowDrag(MotionEvent event) {
                    // Do not allow drag if the recycler view can move down more
                    return !mRecyclerView.canScrollVertically(1);
                }

                @Override
                public boolean shouldInterceptTouchEvent(MotionEvent event) {
                    return true;
                }

                @Override
                public int getDisplayHeight() {
                    return 0;
                }

                @Override
                public void onLayout(int translation) {
                    // no-op
                }
            };

    private final DragController mDragController = new DragController();

    private RecyclerView mRecyclerView;
    private HistoryAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Temporary data
        final int[] testArray = {7};
        mAdapter = new HistoryAdapter(testArray,
                getContext().getResources().getString(R.string.title_current_expression));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(
                R.layout.fragment_history, container, false /* attachToRoot */);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.history_recycler_view);

        // The size of the RecyclerView is not affected by the adapter's contents.
        mRecyclerView.setAdapter(mAdapter);

        final Toolbar toolbar = (Toolbar) view.findViewById(R.id.history_toolbar);
        toolbar.inflateMenu(R.menu.fragment_history);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_clear_history) {
                    clearHistory();
                    return true;
                }
                return onOptionsItemSelected(item);
            }
        });
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initializeController();
        final DragLayout dragLayout = (DragLayout) getActivity().findViewById(R.id.drag_layout);
        dragLayout.removeDragCallback(mDragCallback);
        dragLayout.addDragCallback(mDragCallback);
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        final View view = getView();
        final int height = getResources().getDisplayMetrics().heightPixels;
        if (!enter) {
            return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height);
        } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
            return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height, 0f);
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        final DragLayout dragLayout = (DragLayout) getActivity().findViewById(R.id.drag_layout);
        if (dragLayout != null) {
            dragLayout.removeDragCallback(mDragCallback);
        }
        super.onDestroy();
    }

    private void initializeController() {
        mDragController.setDisplayFormula(
                (CalculatorFormula) getActivity().findViewById(R.id.formula));

        mDragController.setDisplayResult(
                (CalculatorResult) getActivity().findViewById(R.id.result));

        mDragController.setToolbar(getActivity().findViewById(R.id.toolbar));

        // Initialize the current expression element to dimensions that match the display to avoid
        // flickering and scrolling when elements expand on drag start.
        mDragController.animateViews(1.0f, mRecyclerView, mAdapter.getItemCount());
    }

    private void clearHistory() {
        Log.d(TAG, "Dropping history table");
    }
}
