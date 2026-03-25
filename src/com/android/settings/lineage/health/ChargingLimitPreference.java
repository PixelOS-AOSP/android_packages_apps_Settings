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
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.internal.lineage.health.HealthInterface;
import com.android.settings.R;
import com.android.settings.Utils;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

public class ChargingLimitPreference extends Preference
        implements Slider.OnChangeListener {

    private static final int MIN_LIMIT = 70;
    private static final int MAX_LIMIT = 100;

    private final HealthInterface mHealthInterface;

    private TextView mChargingLimitValue;
    private Slider mChargingLimitSlider;

    public ChargingLimitPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        setLayoutResource(R.layout.preference_charging_limit);
        setSelectable(false);

        mHealthInterface = HealthInterface.getInstance(context);
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        mChargingLimitValue = (TextView) holder.findViewById(R.id.value);

        final Slider slider = (Slider) holder.findViewById(R.id.slider);
        mChargingLimitSlider = slider;
        if (slider == null) {
            return;
        }

        final int currentLimit = getSetting();

        slider.clearOnChangeListeners();
        slider.setValueFrom(MIN_LIMIT);
        slider.setValueTo(MAX_LIMIT);
        slider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
        slider.setLabelFormatter(value -> Utils.formatPercentage(Math.round(value)));
        slider.setEnabled(isEnabled());
        updateSlider(currentLimit);
        slider.addOnChangeListener(this);
    }

    @Override
    public void onValueChange(final Slider slider, final float value, final boolean fromUser) {
        final int chargingLimit = sanitizeLimit(Math.round(value));
        updateValue(chargingLimit);
        slider.setStateDescription(Utils.formatPercentage(chargingLimit));

        if (!fromUser) {
            return;
        }

        setSetting(chargingLimit);
    }

    public void setValue(final int value) {
        updateSlider(sanitizeLimit(value));
    }

    protected int getSetting() {
        final int storedLimit = mHealthInterface.getLimit();
        final int currentLimit = sanitizeLimit(storedLimit);
        if (currentLimit != storedLimit) {
            mHealthInterface.setLimit(currentLimit);
        }
        return currentLimit;
    }

    protected void setSetting(final int chargingLimit) {
        mHealthInterface.setLimit(sanitizeLimit(chargingLimit));
    }

    private void updateSlider(final int value) {
        if (mChargingLimitSlider != null) {
            mChargingLimitSlider.setValue(value);
            mChargingLimitSlider.setStateDescription(Utils.formatPercentage(value));
        }
        updateValue(value);
    }

    private void updateValue(final int value) {
        if (mChargingLimitValue != null) {
            mChargingLimitValue.setText(getContext().getString(
                    R.string.charging_control_limit_current,
                    Utils.formatPercentage(value)));
        }
    }

    static int sanitizeLimit(final int value) {
        return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, value));
    }
}
