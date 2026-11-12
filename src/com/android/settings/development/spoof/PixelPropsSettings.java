/*
 * Copyright (C) 2026 The PixelOS Project
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

package com.android.settings.development.spoof;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.custom.PixelDeviceRepository;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.development.DeveloperOptionAwareMixin;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable
public class PixelPropsSettings extends DashboardFragment implements DeveloperOptionAwareMixin {
    private static final String TAG = "PixelPropsSettings";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DEVELOPMENT;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.pixel_props_settings;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        ContentResolver cr = requireContext().getContentResolver();
        SwitchPreferenceCompat enabled = findPreference("pi_pp_spoof_switch");
        if (enabled != null) {
            enabled.setOnPreferenceChangeListener((p, value) -> {
                SpoofStore.putInt(cr, false, Settings.Secure.PI_PP_SPOOF,
                        Boolean.TRUE.equals(value) ? 1 : 0);
                stopTargets(cr);
                return true;
            });
        }
        bindModel();
    }

    private void bindModel() {
        ListPreference model = findPreference("pi_pp_model");
        if (model == null) return;
        android.content.Context context = requireContext();
        ContentResolver cr = context.getContentResolver();
        List<String> order = new ArrayList<>(PixelDeviceRepository.pickerOrder(context));
        if (order.isEmpty()) {
            model.setEnabled(false);
            model.setEntries(new String[0]);
            model.setEntryValues(new String[0]);
            model.setSummary(R.string.pixel_props_fetch_catalog);
            return;
        }
        model.setEnabled(true);
        model.setOnPreferenceChangeListener(null);
        String stored = SpoofStore.getString(cr, SpoofStore.PP_MODEL);
        if (stored != null && !stored.isEmpty() && !order.contains(stored)) {
            order.add(stored);
        }
        String[] values = order.toArray(new String[0]);
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            String name = PixelDeviceRepository.modelName(context, values[i]);
            labels[i] = name == null ? values[i] : name;
        }
        model.setEntries(labels);
        model.setEntryValues(values);
        String current = (stored == null || stored.isEmpty())
                ? PixelDeviceRepository.getDefaultPhoneCodename(context)
                : stored;
        if (current == null) current = values[0];
        model.setValue(current);
        model.setSummary(labelFor(current));
        model.setOnPreferenceChangeListener((p, value) -> {
            String codename = String.valueOf(value);
            SpoofStore.putString(cr, SpoofStore.PP_MODEL, codename);
            model.setSummary(labelFor(codename));
            stopTargets(cr);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        SwitchPreferenceCompat enabled = findPreference("pi_pp_spoof_switch");
        if (enabled != null) {
            enabled.setChecked(SpoofStore.getInt(requireContext().getContentResolver(), false,
                    Settings.Secure.PI_PP_SPOOF, 1) != 0);
        }
        bindModel();
    }

    private String labelFor(String codename) {
        String name = PixelDeviceRepository.modelName(requireContext(), codename);
        return name == null ? codename : name;
    }

    private void stopTargets(ContentResolver cr) {
        String raw = SpoofStore.getString(cr, SpoofStore.PP_TARGETS);
        List<String> targets = SpoofStore.readCsv(raw);
        if (targets.isEmpty()) {
            targets = new ArrayList<>(PixelDeviceRepository.DEFAULT_PP_TARGETS);
        }
        SpoofStore.stopPackages(requireContext(), targets.toArray(new String[0]));
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.pixel_props_settings) {
                @Override
                protected boolean isPageSearchEnabled(android.content.Context context) {
                    return DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(context);
                }
            };
}
