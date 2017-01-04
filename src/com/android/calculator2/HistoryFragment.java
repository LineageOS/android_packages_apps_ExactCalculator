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
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import java.util.ArrayList;

import static android.support.v7.widget.RecyclerView.SCROLL_STATE_DRAGGING;

public class HistoryFragment extends Fragment {

    public static final String TAG = "HistoryFragment";
    public static final String CLEAR_DIALOG_TAG = "clear";

    private final DragLayout.DragCallback mDragCallback =
            new DragLayout.DragCallback() {
                @Override
                public void onStartDraggingOpen() {
                    // no-op
                }

                @Override
                public void whileDragging(float yFraction) {
                    mDragController.animateViews(yFraction, mRecyclerView);
                }

                @Override
                public boolean shouldCaptureView(View view, int x, int y) {
                    return view.getId() == R.id.history_frame
                            && mDragLayout.isViewUnder(view, x, y)
                            && !mRecyclerView.canScrollVertically(1 /* scrolling down */);
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
    private DragLayout mDragLayout;

    private Evaluator mEvaluator;

    private ArrayList<HistoryItem> mDataSet = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = new HistoryAdapter(mDataSet);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(
                R.layout.fragment_history, container, false /* attachToRoot */);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.history_recycler_view);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == SCROLL_STATE_DRAGGING) {
                    stopActionModeOrContextMenu();
                }
                super.onScrollStateChanged(recyclerView, newState);
            }
        });

        // The size of the RecyclerView is not affected by the adapter's contents.
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);

        final Toolbar toolbar = (Toolbar) view.findViewById(R.id.history_toolbar);
        toolbar.inflateMenu(R.menu.fragment_history);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_clear_history) {
                    final Calculator calculator = (Calculator) getActivity();
                    AlertDialogFragment.showMessageDialog(calculator, "" /* title */,
                            getString(R.string.dialog_clear),
                            getString(R.string.menu_clear_history),
                            CLEAR_DIALOG_TAG);
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

        final Calculator activity = (Calculator) getActivity();

        mAdapter.setEvaluator(Evaluator.getInstance(activity));

        final boolean isResultLayout = activity.isResultLayout();
        final boolean isOneLine = activity.isOneLine();

        mDragLayout = (DragLayout) activity.findViewById(R.id.drag_layout);
        mDragLayout.removeDragCallback(mDragCallback);
        mDragLayout.addDragCallback(mDragCallback);

        mEvaluator = Evaluator.getInstance(activity);

        if (mEvaluator != null) {
            initializeController(isResultLayout, isOneLine);

            final long maxIndex = mEvaluator.getMaxIndex();

            final ArrayList<HistoryItem> newDataSet = new ArrayList<>();

            if (!EvaluatorStateUtils.isDisplayEmpty(mEvaluator) && !isResultLayout) {
                // Add the current expression as the first element in the list (the layout is
                // reversed and we want the current expression to be the last one in the
                // RecyclerView).
                // If we are in the result state, the result will animate to the last history
                // element in the list and there will be no "Current Expression."
                mEvaluator.copyMainToHistory();
                newDataSet.add(new HistoryItem(Evaluator.HISTORY_MAIN_INDEX,
                        System.currentTimeMillis(), mEvaluator.getExprAsSpannable(0)));
            }
            for (long i = 0; i < maxIndex; ++i) {
                newDataSet.add(null);
            }
            final boolean isEmpty = newDataSet.isEmpty();
            mRecyclerView.setBackgroundColor(isEmpty
                    ? ContextCompat.getColor(activity, R.color.empty_history_color)
                    : Color.TRANSPARENT);
            if (isEmpty) {
                newDataSet.add(new HistoryItem());
            }
            mDataSet = newDataSet;
            mAdapter.setDataSet(mDataSet);
            mAdapter.setIsResultLayout(isResultLayout);
            mAdapter.setIsOneLine(activity.isOneLine());
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStart() {
        super.onStart();
        final Calculator activity = (Calculator) getActivity();
        // The orientation may have changed.
        mDragController.initializeAnimation(mRecyclerView,
                activity.isResultLayout(), activity.isOneLine(), mDragLayout.isOpen());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        final View view = getView();
        final int height = getResources().getDisplayMetrics().heightPixels;
        if (enter) {
            if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
                return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height, 0f);
            } else {
                return null;
            }
        } else {
            return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height);
        }
    }

    @Override
    public void onDestroyView() {
        final DragLayout dragLayout = (DragLayout) getActivity().findViewById(R.id.drag_layout);
        if (dragLayout != null) {
            dragLayout.removeDragCallback(mDragCallback);
        }

        // Note that the view is destroyed when the fragment backstack is popped, so
        // these are essentially called when the DragLayout is closed.
        mEvaluator.cancelNonMain();

        super.onDestroyView();
    }

    private void initializeController(boolean isResult, boolean isOneLine) {
        mDragController.setDisplayFormula(
                (CalculatorFormula) getActivity().findViewById(R.id.formula));

        mDragController.setDisplayResult(
                (CalculatorResult) getActivity().findViewById(R.id.result));

        mDragController.setToolbar(getActivity().findViewById(R.id.toolbar));

        mDragController.setEvaluator(mEvaluator);

        mDragController.initializeController(isResult, isOneLine);
    }

    public boolean stopActionModeOrContextMenu() {
        if (mRecyclerView == null) {
            return false;
        }
        for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
            final View view = mRecyclerView.getChildAt(i);
            final HistoryAdapter.ViewHolder viewHolder =
                    (HistoryAdapter.ViewHolder) mRecyclerView.getChildViewHolder(view);
            if (viewHolder != null && viewHolder.getResult() != null
                    && viewHolder.getResult().stopActionModeOrContextMenu()) {
                return true;
            }
        }
        return false;
    }
}
