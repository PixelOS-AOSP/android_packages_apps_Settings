/*
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.settings.lineage.health;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.android.internal.lineage.health.HealthInterface;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settingslib.widget.SliderPreference;

public class ChargingLimitPreference extends SliderPreference
        implements Preference.OnPreferenceChangeListener {

    private static final int MIN_LIMIT = 70;
    private static final int MAX_LIMIT = 100;

    private final HealthInterface mHealthInterface;

    public ChargingLimitPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        setPersistent(false);
        setMin(MIN_LIMIT);
        setMax(MAX_LIMIT);
        setSliderIncrement(1);
        setShowSliderValue(true);
        setLabelFormater(value -> Utils.formatPercentage(Math.round(value)));
        setOnPreferenceChangeListener(this);
        setValue(MIN_LIMIT);

        mHealthInterface = HealthInterface.getInstance(context);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        final int chargingLimit = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, (Integer) newValue));
        setSetting(chargingLimit);
        updateValue(chargingLimit);
        return true;
    }

    @Override
    public void setValue(final int value) {
        super.setValue(value);
        updateValue(getValue());
    }

    protected int getSetting() {
        final int storedLimit = mHealthInterface.getLimit();
        final int currentLimit = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, storedLimit));
        if (currentLimit != storedLimit) {
            mHealthInterface.setLimit(currentLimit);
        }
        return currentLimit;
    }

    protected void setSetting(final int chargingLimit) {
        mHealthInterface.setLimit(Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, chargingLimit)));
    }

    private void updateValue(final int value) {
        final String formattedValue = Utils.formatPercentage(value);
        setSummary(getContext().getString(
                R.string.charging_control_limit_current, formattedValue));
        setSliderStateDescription(formattedValue);
    }
}
