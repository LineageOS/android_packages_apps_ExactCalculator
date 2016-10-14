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
import android.app.FragmentManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

public class HistoryFragment extends Fragment {

    public static final String TAG = "HistoryFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(
                R.layout.fragment_history, container, false /* attachToRoot */);

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
                final FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                }
            }
        });

        return view;
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        final View view = getView();
        final int height = getResources().getDisplayMetrics().heightPixels;
        if (enter) {
            return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height, 0f);
        } else {
            return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height);
        }
    }

    private void clearHistory() {
        Log.d(TAG, "Dropping history table");
    }
}
