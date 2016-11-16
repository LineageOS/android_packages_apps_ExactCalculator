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
import android.view.View;
import android.widget.TextView;

/**
 * Contains the logic for animating the recyclerview elements on drag.
 */
public final class DragController {

    // References to views from the Calculator Display.
    private CalculatorFormula mDisplayFormula;
    private CalculatorResult mDisplayResult;
    private View mToolbar;

    private int mFormulaTranslationY;
    private int mFormulaTranslationX;
    private float mFormulaScale;

    private int mResultTranslationY;
    private int mResultTranslationX;

    private boolean mAnimationInitialized;

    public void setDisplayFormula(CalculatorFormula formula) {
        mDisplayFormula = formula;
    }

    public void setDisplayResult(CalculatorResult result) {
        mDisplayResult = result;
    }

    public void setToolbar(View toolbar) {
        mToolbar = toolbar;
    }

    public void animateViews(float yFraction, RecyclerView recyclerView, int itemCount) {
        final HistoryAdapter.ViewHolder vh = (HistoryAdapter.ViewHolder)
                recyclerView.findViewHolderForAdapterPosition(0);
        if (vh != null) {
            final CalculatorFormula formula = vh.getFormula();
            final CalculatorResult result = vh.getResult();
            final TextView date = vh.getDate();

            if (!mAnimationInitialized) {
                // Calculate the scale for the text
                mFormulaScale = (mDisplayFormula.getTextSize() * 1.0f) / formula.getTextSize();

                // Baseline of formula moves by the difference in formula bottom padding.
                mFormulaTranslationY =
                        mDisplayFormula.getPaddingBottom() - formula.getPaddingBottom()
                        + mDisplayResult.getHeight() - result.getHeight();

                // Right border of formula moves by the difference in formula end padding.
                mFormulaTranslationX = mDisplayFormula.getPaddingEnd() - formula.getPaddingEnd();

                // Baseline of result moves by the difference in result bottom padding.
                mResultTranslationY = mDisplayResult.getPaddingBottom() - result.getPaddingBottom();

                mResultTranslationX = mDisplayResult.getPaddingEnd() - result.getPaddingEnd();

                mAnimationInitialized = true;
            }

            if (mAnimationInitialized) {
                formula.setPivotX(formula.getWidth() - formula.getPaddingEnd());
                formula.setPivotY(formula.getHeight() - formula.getPaddingBottom());

                result.setPivotX(result.getWidth() - result.getPaddingEnd());
                result.setPivotY(result.getHeight() - result.getPaddingBottom());

                final float resultTranslationX = (mResultTranslationX * yFraction)
                        - mResultTranslationX;
                result.setTranslationX(resultTranslationX);

                // Scale linearly between -mResultTranslationY and 0.
                final float resultTranslationY =
                        (mResultTranslationY * yFraction) - mResultTranslationY;
                result.setTranslationY(resultTranslationY);

                final float scale = mFormulaScale - (mFormulaScale * yFraction) + yFraction;
                formula.setScaleY(scale);
                formula.setScaleX(scale);

                final float formulaTranslationX = (mFormulaTranslationX * yFraction)
                        - mFormulaTranslationX;
                formula.setTranslationX(formulaTranslationX);

                // Scale linearly between -FormulaTranslationY and 0.
                final float formulaTranslationY =
                        (mFormulaTranslationY * yFraction) - mFormulaTranslationY;
                formula.setTranslationY(formulaTranslationY);

                // We want the date to start out above the visible screen with
                // this distance decreasing as it's pulled down.
                final float dateTranslationY =
                        - mToolbar.getHeight() * (1 - yFraction)
                        + formulaTranslationY
                        - mDisplayFormula.getPaddingTop()
                        + (mDisplayFormula.getPaddingTop() * yFraction);
                date.setTranslationY(dateTranslationY);

                // Translate items above the Current Expression to accommodate the size change.
                // Move up all ViewHolders above the current expression.
                for (int i = recyclerView.getChildCount() - 1; i > 0; --i) {
                    final RecyclerView.ViewHolder vh2 =
                            recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
                    if (vh2 != null) {
                        final View view = vh2.itemView;
                        if (view != null) {
                            view.setTranslationY(dateTranslationY);
                        }
                    }
                }
            }
        }
    }
}
