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

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.development.DeveloperOptionAwareMixin;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.settingslib.search.SearchIndexable;

import org.json.JSONObject;

@SearchIndexable
public class SpoofingSettings extends DashboardFragment implements DeveloperOptionAwareMixin {
    private static final String TAG = "SpoofingSettings";

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
        return R.xml.spoofing_settings;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        ContentResolver cr = requireContext().getContentResolver();
        bindSystemSwitch(cr, "spoof_trickystore_enabled", SpoofStore.TRICKY_ENABLED);
        bindSystemSwitch(cr, "spoof_pif_enabled", SpoofStore.PIF_ENABLED);
        bindSecureSwitch(cr, "pi_photos_spoof", Settings.Secure.PI_PHOTOS_SPOOF,
                "com.google.android.apps.photos");

        Preference reset = findPreference("spoofing_reset_defaults");
        if (reset != null) {
            reset.setOnPreferenceClickListener(p -> {
                confirmReset();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ContentResolver cr = requireContext().getContentResolver();
        refreshSystemSwitch("spoof_trickystore_enabled", SpoofStore.TRICKY_ENABLED, 1);
        refreshSystemSwitch("spoof_pif_enabled", SpoofStore.PIF_ENABLED, 1);
        refreshSecureSwitch("pi_photos_spoof", Settings.Secure.PI_PHOTOS_SPOOF, 1);
        updateStatus(cr);
    }

    private void updateStatus(ContentResolver cr) {
        PrimarySwitchPreference pif = findPreference("spoof_pif_enabled");
        if (pif != null) {
            JSONObject config = SpoofStore.readPif(cr);
            String model = config.optString("MODEL", "");
            pif.setSummary(model.isEmpty()
                    ? getString(R.string.pif_no_config)
                    : getString(R.string.pif_active_model, model));
        }
        PrimarySwitchPreference tricky = findPreference("spoof_trickystore_enabled");
        if (tricky != null) {
            String keybox = SpoofStore.getString(cr, Settings.Secure.SPOOF_TRICKYSTORE_KEYBOX);
            String source = SpoofStore.getString(cr, SpoofStore.KEYBOX_SOURCE);
            if (keybox == null || keybox.isEmpty()) {
                tricky.setSummary(R.string.ts_no_keybox);
            } else if (SpoofStore.SOURCE_USER.equals(source)) {
                tricky.setSummary(R.string.ts_keybox_user);
            } else {
                tricky.setSummary(R.string.ts_keybox_installed);
            }
        }
    }

    private void bindSystemSwitch(ContentResolver cr, String prefKey, String setting) {
        PrimarySwitchPreference pref = findPreference(prefKey);
        if (pref == null) return;
        pref.setOnPreferenceChangeListener((p, value) -> {
            SpoofStore.putInt(cr, true, setting, Boolean.TRUE.equals(value) ? 1 : 0);
            return true;
        });
    }

    private void bindSecureSwitch(ContentResolver cr, String prefKey, String setting,
            String restartPackage) {
        SwitchPreferenceCompat pref = findPreference(prefKey);
        if (pref == null) return;
        pref.setOnPreferenceChangeListener((p, value) -> {
            boolean on = Boolean.TRUE.equals(value);
            SpoofStore.putInt(cr, false, setting, on ? 1 : 0);
            SpoofStore.stopPackages(requireContext(), restartPackage);
            return true;
        });
    }

    private void refreshSystemSwitch(String prefKey, String setting, int def) {
        PrimarySwitchPreference pref = findPreference(prefKey);
        if (pref == null) return;
        pref.setChecked(SpoofStore.getInt(requireContext().getContentResolver(), true, setting, def)
                != 0);
    }

    private void refreshSecureSwitch(String prefKey, String setting, int def) {
        SwitchPreferenceCompat pref = findPreference(prefKey);
        if (pref == null) return;
        pref.setChecked(SpoofStore.getInt(requireContext().getContentResolver(), false, setting, def)
                != 0);
    }

    private void confirmReset() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spoofing_reset_defaults_dialog_title)
                .setMessage(R.string.spoofing_reset_defaults_dialog_message)
                .setPositiveButton(R.string.spoofing_reset_defaults_confirm, (d, w) -> {
                    SpoofStore.resetDefaults(requireContext().getContentResolver());
                    SpoofStore.stopGms(requireContext());
                    android.widget.Toast.makeText(requireContext(),
                            R.string.spoofing_reset_defaults_done,
                            android.widget.Toast.LENGTH_SHORT).show();
                    updateStatus(requireContext().getContentResolver());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.spoofing_settings) {
                @Override
                protected boolean isPageSearchEnabled(android.content.Context context) {
                    return DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(context);
                }
            };
}
