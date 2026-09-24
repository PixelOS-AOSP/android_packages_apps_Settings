/*
 * Copyright (C) 2024 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.display;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RefreshRateUtils {

    private static final String TAG = "RefreshRateUtils";

    static final int DEFAULT_REFRESH_RATE = 60;
    static final int DEFAULT_MIN_REFRESH_RATE = 0; // matches fwb. should be 60 though?

    private Context mContext;
    private List<Integer> mRefreshRates;
    private int mMinRefreshRate, mMaxRefreshRate;

    RefreshRateUtils(Context context) {
        mContext = context;
        mRefreshRates = getRefreshRates();
        mMinRefreshRate = Collections.min(mRefreshRates);
        mMaxRefreshRate = Collections.max(mRefreshRates);
    }

    List<Integer> getRefreshRates() {
        return Arrays.stream(mContext.getDisplay().getSupportedModes())
                .map(m -> Math.round(m.getRefreshRate()))
                .sorted().distinct().collect(Collectors.toList());
    }

    boolean isHighRefreshRateAvailable() {
        return mRefreshRates.stream()
                .filter(r -> r > DEFAULT_REFRESH_RATE)
                .count() > 0;
    }

    private int roundToNearestRefreshRate(int refreshRate, boolean floor) {
        if (mRefreshRates.contains(refreshRate)) return refreshRate;
        int findRefreshRate = mMinRefreshRate;
        for (Integer knownRefreshRate : mRefreshRates) {
            if (!floor) findRefreshRate = knownRefreshRate;
            if (knownRefreshRate > refreshRate) break;
            if (floor) findRefreshRate = knownRefreshRate;
        }
        return findRefreshRate;
    }

    private float getDefaultPeakRefreshRate() {
        return (float) mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultPeakRefreshRate);
    }

    private int getPeakRefreshRate() {
        final int peakRefreshRate = Math.round(Settings.System.getFloat(
                mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, getDefaultPeakRefreshRate()));
        return peakRefreshRate < mMinRefreshRate ? mMaxRefreshRate
                : roundToNearestRefreshRate(peakRefreshRate, true);
    }

    private void setPeakRefreshRate(int refreshRate) {
        Settings.System.putFloat(mContext.getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, (float) refreshRate);
    }

    private int getMinRefreshRate() {
        final int minRefreshRate = Math.round(Settings.System.getFloat(
                mContext.getContentResolver(), Settings.System.MIN_REFRESH_RATE,
                (float) DEFAULT_MIN_REFRESH_RATE));
        return minRefreshRate == DEFAULT_MIN_REFRESH_RATE ? DEFAULT_MIN_REFRESH_RATE
                : roundToNearestRefreshRate(minRefreshRate, false);
    }

    private void setMinRefreshRate(int refreshRate) {
        Settings.System.putFloat(mContext.getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, (float) refreshRate);
    }

    int getCurrentRefreshRate() {
        return Math.max(getMinRefreshRate(), getPeakRefreshRate());
    }

    void setCurrentRefreshRate(int refreshRate) {
        setPeakRefreshRate(refreshRate);
        setMinRefreshRate(isVrrEnabled() ? DEFAULT_MIN_REFRESH_RATE : refreshRate);
        setPreferredPhysicalRefreshRate(refreshRate);
    }

    private void setPreferredPhysicalRefreshRate(int refreshRate) {
        Display display = mContext.getDisplay();
        if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
            return;
        }
        Display.Mode currentMode = display.getMode();
        Display.Mode[] physicalModes = Arrays.stream(display.getSupportedModes())
                .filter(mode -> mode.getSfModeId() != Display.Mode.INVALID_MODE_ID
                        && mode.getParentModeId() == Display.Mode.INVALID_MODE_ID
                        && mode.getPhysicalWidth() == currentMode.getPhysicalWidth()
                        && mode.getPhysicalHeight() == currentMode.getPhysicalHeight())
                .toArray(Display.Mode[]::new);
        boolean hasMultipleBases = Arrays.stream(physicalModes)
                .mapToInt(mode -> Math.round(mode.getRefreshRate()))
                .distinct().count() > 1;
        boolean hasPhysicalMode = Arrays.stream(physicalModes)
                .anyMatch(mode -> Math.round(mode.getRefreshRate()) == refreshRate);
        Settings.System.putFloat(mContext.getContentResolver(),
                Settings.System.USER_PREFERRED_PHYSICAL_REFRESH_RATE,
                display.hasArrSupport() && hasMultipleBases && hasPhysicalMode
                        ? refreshRate : 0f);
    }

    boolean isVrrPossible() {
        return getCurrentRefreshRate() > DEFAULT_REFRESH_RATE;
    }

    boolean isVrrEnabled() {
        return getMinRefreshRate() <= DEFAULT_MIN_REFRESH_RATE;
    }

    void setVrrEnabled(boolean enable) {
        setMinRefreshRate(enable ? DEFAULT_MIN_REFRESH_RATE : getCurrentRefreshRate());
    }
}
