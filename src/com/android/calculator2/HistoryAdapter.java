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
import java.util.Calendar;
import java.util.List;

/**
 * Adapter for RecyclerView of HistoryItems.
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private static final int EMPTY_VIEW_TYPE = 0;
    private static final int HISTORY_VIEW_TYPE = 1;

    private final List<HistoryItem> mDataSet = new ArrayList<>();
    private String mCurrentExpression;

    public HistoryAdapter(int[] dataset, String currentExpression) {
        mCurrentExpression = currentExpression;
        // Temporary dataset
        final Calendar calendar = Calendar.getInstance();
        for (int i: dataset) {
            calendar.set(2016, 10, i);
            mDataSet.add(new HistoryItem(calendar.getTimeInMillis(), Integer.toString(i) + "+1",
                    Integer.toString(i+1)));
        }
        // Temporary: just testing the empty view placeholder
        mDataSet.add(new HistoryItem());
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
    public void onBindViewHolder(HistoryAdapter.ViewHolder holder, int position) {
        final HistoryItem item = mDataSet.get(position);

        if (item.isEmptyView()) {
            return;
        }
        if (!isCurrentExpressionItem(position)) {
            holder.mDate.setText(item.getDateString());
            holder.mDate.setContentDescription(item.getDateDescription());
        } else {
            holder.mDate.setText(mCurrentExpression);
            holder.mDate.setContentDescription(mCurrentExpression);
        }
        holder.mFormula.setText(item.getFormula());
        holder.mResult.setText(item.getResult());
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.mDate.setContentDescription(null);
        holder.mDate.setText(null);
        holder.mFormula.setText(null);
        holder.mResult.setText(null);

        super.onViewRecycled(holder);
    }

    @Override
    public int getItemViewType(int position) {
        return mDataSet.get(position).isEmptyView() ? EMPTY_VIEW_TYPE : HISTORY_VIEW_TYPE;
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    private boolean isCurrentExpressionItem(int position) {
        return position == mDataSet.size() - 1;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView mDate;
        private CalculatorFormula mFormula;
        private CalculatorResult mResult;

        public ViewHolder(View v, int viewType) {
            super(v);
            if (viewType == EMPTY_VIEW_TYPE) {
                return;
            }
            mDate = (TextView) v.findViewById(R.id.history_date);
            mFormula = (CalculatorFormula) v.findViewById(R.id.history_formula);
            mResult = (CalculatorResult) v.findViewById(R.id.history_result);
        }

        public CalculatorFormula getFormula() {
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