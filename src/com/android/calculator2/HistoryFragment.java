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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import java.util.ArrayList;

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
                    // TODO: only cancel historical evaluations
                    mEvaluator.cancelAll(true);
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

    private Evaluator mEvaluator;

    private ArrayList<HistoryItem> mDataSet = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = new HistoryAdapter((Calculator) getActivity(), mDataSet,
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

        final DragLayout dragLayout = (DragLayout) getActivity().findViewById(R.id.drag_layout);
        dragLayout.removeDragCallback(mDragCallback);
        dragLayout.addDragCallback(mDragCallback);

        final Calculator activity = (Calculator) getActivity();
        final boolean isResultLayout = activity.isResultLayout();

        mEvaluator = Evaluator.getInstance(activity);

        if (mEvaluator != null) {
            initializeController(isResultLayout);

            final long maxIndex = mEvaluator.getMaxIndex();

            final ArrayList<HistoryItem> newDataSet = new ArrayList<>();

            if (!EvaluatorStateUtils.isDisplayEmpty(mEvaluator) && !isResultLayout) {
                // Add the current expression as the first element in the list (the layout is
                // reversed and we want the current expression to be the last one in the
                // recyclerview).
                // If we are in the result state, the result will animate to the last history
                // element in the list and there will be no "Current Expression."
                newDataSet.add(new HistoryItem(Evaluator.MAIN_INDEX, System.currentTimeMillis(),
                        mEvaluator.getExprAsSpannable(0)));
            }
            for (long i = 0; i < maxIndex; ++i) {
                newDataSet.add(null);
            }
            if (maxIndex == 0) {
                newDataSet.add(new HistoryItem());
            }
            mDataSet = newDataSet;
            mAdapter.setDataSet(mDataSet);
            mAdapter.setIsResultLayout(isResultLayout);
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStart() {
        super.onStart();

        // The orientation may have changed.
        mDragController.initializeAnimation(mRecyclerView,
                ((Calculator) getActivity()).isResultLayout());
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

        mEvaluator.cancelAll(true);
        super.onDestroy();
        // FIXME: There are probably better ways to do this. But we can end up cancelling
        // an in-progress evaluation for the main expression that we have to restart.
        ((Calculator)(getActivity())).evaluateInstantIfNecessary();
    }

    private void initializeController(boolean isResult) {
        mDragController.setDisplayFormula(
                (CalculatorFormula) getActivity().findViewById(R.id.formula));

        mDragController.setDisplayResult(
                (CalculatorResult) getActivity().findViewById(R.id.result));

        mDragController.setToolbar(getActivity().findViewById(R.id.toolbar));

        mDragController.setEvaluator(mEvaluator);

        mDragController.initializeController(isResult);
    }

    private void clearHistory() {
        // TODO: Try to preserve the current, saved, and memory expressions. How should we
        // handle expressions to which they refer?
        // FIXME: This should clearly happen on a background thread.
        mEvaluator.clearEverything();
        // TODO: It's not clear what we should really do here. This is an initial hack.
        // May want to make onClearAnimationEnd() private if/when we fix this.
        Calculator calculator = (Calculator) getActivity();
        calculator.onClearAnimationEnd();
        calculator.onBackPressed();
    }
}
