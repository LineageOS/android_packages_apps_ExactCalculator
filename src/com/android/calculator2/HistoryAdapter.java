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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for RecyclerView of HistoryItems.
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private static final int EMPTY_VIEW_TYPE = 0;
    private static final int HISTORY_VIEW_TYPE = 1;

    private final Evaluator mEvaluator;
    /* Text/accessibility descriptor for the current expression item. */
    private final String mCurrentExpressionDescription;

    private List<HistoryItem> mDataSet;

    private boolean mIsResultLayout;

    public HistoryAdapter(Calculator calculator, ArrayList<HistoryItem> dataSet,
            String currentExpressionDescription) {
        mEvaluator = Evaluator.getInstance(calculator);
        mDataSet = dataSet;
        mCurrentExpressionDescription = currentExpressionDescription;
        setHasStableIds(true);
    }

    @Override
    public HistoryAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View v;
        if (viewType == HISTORY_VIEW_TYPE) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.history_item, parent, false);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.empty_history_view, parent, false);
        }
        return new ViewHolder(v, viewType);
    }

    @Override
    public void onBindViewHolder(final HistoryAdapter.ViewHolder holder, int position) {
        final HistoryItem item = mDataSet.get(position);

        if (item.isEmptyView()) {
            return;
        }

        holder.mFormula.setText(item.getFormula());
        // Note: HistoryItems that are not the current expression will always have interesting ops.
        holder.mResult.setEvaluator(mEvaluator, item.getEvaluatorIndex());
        if (item.getEvaluatorIndex() == Evaluator.HISTORY_MAIN_INDEX) {
            holder.mDate.setText(mCurrentExpressionDescription);
            holder.mDate.setContentDescription(mCurrentExpressionDescription);
        } else {
            holder.mDate.setText(item.getDateString());
        }
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        if (holder.getItemViewType() == EMPTY_VIEW_TYPE) {
            return;
        }
        mEvaluator.cancel(holder.getItemId(), true);

        holder.mDate.setContentDescription(null);
        holder.mDate.setText(null);
        holder.mFormula.setText(null);
        holder.mResult.setText(null);

        super.onViewRecycled(holder);
    }

    @Override
    public long getItemId(int position) {
        return mDataSet.get(position).getEvaluatorIndex();
    }

    @Override
    public int getItemViewType(int position) {
        HistoryItem item = mDataSet.get(position);

        // Continue to lazy-fill the data set
        if (item == null) {
            final int evaluatorIndex = getEvaluatorIndex(position);
            item = new HistoryItem(evaluatorIndex, mEvaluator.getTimeStamp(evaluatorIndex),
                    mEvaluator.getExprAsSpannable(evaluatorIndex));
            mDataSet.set(position, item);
        }
        return item.isEmptyView() ? EMPTY_VIEW_TYPE : HISTORY_VIEW_TYPE;
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    public void setDataSet(ArrayList<HistoryItem> dataSet) {
        mDataSet = dataSet;
    }

    private int getEvaluatorIndex(int position) {
        if (EvaluatorStateUtils.isDisplayEmpty(mEvaluator) || mIsResultLayout) {
            return (int) mEvaluator.getMaxIndex() - position;
        } else {
            // Account for the additional "Current Expression" with the +1.
            return (int) mEvaluator.getMaxIndex() - position + 1;
        }
    }

    public void setIsResultLayout(boolean isResult) {
        mIsResultLayout = isResult;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mDate;
        private AlignedTextView mFormula;
        private CalculatorResult mResult;

        public ViewHolder(View v, int viewType) {
            super(v);
            if (viewType == EMPTY_VIEW_TYPE) {
                return;
            }
            mDate = (TextView) v.findViewById(R.id.history_date);
            mFormula = (AlignedTextView) v.findViewById(R.id.history_formula);
            mResult = (CalculatorResult) v.findViewById(R.id.history_result);
        }

        public AlignedTextView getFormula() {
            return mFormula;
        }

        public CalculatorResult getResult() {
            return mResult;
        }

        public TextView getDate() {
            return mDate;
        }
    }
}
