/*
 * SPDX-FileCopyrightText: 2006 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import com.google.android.material.button.MaterialButton;

/**
 * A basic Button that vibrates on finger down.
 */
public class HapticButton extends MaterialButton {
    public HapticButton(Context context) {
        super(context);
        initVibration();
    }

    public HapticButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVibration();
    }

    public HapticButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVibration();
    }

    private void initVibration() {
        setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }

            // Passthrough
            return false;
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (getIcon() == null) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.4f);
        }
    }
}
