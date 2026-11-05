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
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.custom.PixelDeviceRepository;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.development.DeveloperOptionAwareMixin;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.settingslib.search.SearchIndexable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

@SearchIndexable
public class PifSettings extends DashboardFragment implements DeveloperOptionAwareMixin {
    private static final String TAG = "PifSettings";
    private static final String[] FLAGS = {
            "spoofBuild", "spoofProps", "spoofProvider", "spoofSignature",
    };
    private static final boolean[] FLAG_DEFAULTS = {true, true, false, false};
    private static final String[] FIELDS = {
            "MODEL", "MANUFACTURER", "BRAND", "PRODUCT", "DEVICE",
            "FINGERPRINT", "SECURITY_PATCH", "ID", "RELEASE", "DEVICE_INITIAL_SDK_INT",
    };

    private final ActivityResultLauncher<String[]> mImport =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImport);
    private boolean mFetching;

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
        return R.xml.pif_settings;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        Preference fetch = findPreference("pif_fetch");
        if (fetch != null) {
            fetch.setOnPreferenceClickListener(p -> {
                fetchProfiles();
                return true;
            });
        }
        Preference importPref = findPreference("pif_import");
        if (importPref != null) {
            importPref.setOnPreferenceClickListener(p -> {
                mImport.launch(new String[] {"application/*", "text/*"});
                return true;
            });
        }
        Preference delete = findPreference("pif_delete");
        if (delete != null) {
            delete.setOnPreferenceClickListener(p -> {
                confirmDelete();
                return true;
            });
        }
        for (String flag : FLAGS) {
            SwitchPreferenceCompat pref = findPreference(flag);
            if (pref == null) continue;
            pref.setOnPreferenceChangeListener((p, value) -> {
                writeFlag(flag, Boolean.TRUE.equals(value));
                return true;
            });
        }
        SwitchPreferenceCompat finger = findPreference("spoofVendingFinger");
        if (finger != null) {
            finger.setOnPreferenceChangeListener((p, value) -> {
                writeRaw("spoofVendingFinger", Boolean.TRUE.equals(value) ? "1" : "0", false);
                return true;
            });
        }
        SwitchPreferenceCompat sdk = findPreference("spoofVendingSdk");
        if (sdk != null) {
            sdk.setOnPreferenceChangeListener((p, value) -> {
                writeRaw("spoofVendingSdk", Boolean.TRUE.equals(value) ? "1" : "0", false);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!isAdded()) return;
        JSONObject config = SpoofStore.readPif(requireContext().getContentResolver());
        boolean hasConfig = config.length() > 0;
        Preference status = findPreference("pif_status");
        if (status != null) {
            String model = config.optString("MODEL", "");
            status.setSummary(model.isEmpty()
                    ? getString(R.string.pif_no_config)
                    : getString(R.string.pif_active_model, model));
        }
        for (int i = 0; i < FLAGS.length; i++) {
            SwitchPreferenceCompat pref = findPreference(FLAGS[i]);
            if (pref == null) continue;
            pref.setEnabled(hasConfig);
            pref.setChecked(SpoofStore.flagOn(config, FLAGS[i], FLAG_DEFAULTS[i]));
        }
        String finger = config.optString("spoofVendingFinger", "0");
        SwitchPreferenceCompat fingerPref = findPreference("spoofVendingFinger");
        if (fingerPref != null) {
            fingerPref.setEnabled(hasConfig);
            boolean custom = !finger.isEmpty() && !"0".equals(finger) && !"1".equals(finger)
                    && !"true".equalsIgnoreCase(finger) && !"false".equalsIgnoreCase(finger);
            fingerPref.setChecked(!"0".equals(finger) && !"false".equalsIgnoreCase(finger)
                    && !finger.isEmpty());
            fingerPref.setSummary(custom
                    ? getString(R.string.pif_vending_finger_custom, finger)
                    : getString(R.string.pif_vending_finger_summary));
        }
        SwitchPreferenceCompat sdk = findPreference("spoofVendingSdk");
        if (sdk != null) {
            sdk.setEnabled(hasConfig);
            String value = config.optString("spoofVendingSdk", "0");
            int parsed = 0;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                parsed = "true".equalsIgnoreCase(value) ? 1 : 0;
            }
            sdk.setChecked(parsed > 0);
        }
        PreferenceCategory fields = findPreference("pif_fields");
        if (fields == null) return;
        fields.removeAll();
        fields.setVisible(hasConfig);
        if (!hasConfig) return;
        for (String key : FIELDS) {
            if (!config.has(key)) continue;
            Preference row = new Preference(requireContext());
            row.setKey("field_" + key);
            row.setTitle(key);
            row.setSummary(config.optString(key, ""));
            row.setPersistent(false);
            row.setOnPreferenceClickListener(p -> {
                editField(key, config.optString(key, ""));
                return true;
            });
            fields.addPreference(row);
        }
    }

    private void writeFlag(String key, boolean on) {
        writeRaw(key, on ? "1" : "0", false);
    }

    private void writeRaw(String key, String value, boolean manual) {
        try {
            SpoofStore.putPifField(requireContext().getContentResolver(), key, value, manual);
            SpoofStore.stopGms(requireContext());
        } catch (JSONException e) {
            toast(getString(R.string.pif_failed, e.getMessage()));
        }
        refresh();
    }

    private void editField(String key, String current) {
        EditText input = new EditText(requireContext());
        input.setText(current);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.pif_edit_value, key))
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        toast(getString(R.string.pif_value_empty));
                        return;
                    }
                    if (("DEVICE_INITIAL_SDK_INT".equals(key) || "SDK_INT".equals(key))
                            && !value.matches("-?\\d+")) {
                        toast(getString(R.string.pif_value_integer));
                        return;
                    }
                    if ("FINGERPRINT".equals(key)
                            && !PixelDeviceRepository.isValidFingerprint(value)) {
                        toast(getString(R.string.pif_invalid_fingerprint));
                        return;
                    }
                    writeRaw(key, value, true);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pif_delete_title)
                .setMessage(R.string.pif_delete_message)
                .setPositiveButton(R.string.pif_delete, (d, w) -> {
                    ContentResolver cr = requireContext().getContentResolver();
                    SpoofStore.putString(cr, Settings.Secure.SPOOF_PIF_CONFIG, null);
                    android.provider.Settings.Secure.putLong(cr, SpoofStore.PIF_LAST_FETCH, 0L);
                    SpoofStore.stopGms(requireContext());
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onImport(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            byte[] raw = SpoofStore.readUri(requireContext().getContentResolver(), uri);
            String normalized = SpoofStore.normalizePif(new String(raw, StandardCharsets.UTF_8));
            JSONObject json = new JSONObject(normalized);
            String fp = json.optString("FINGERPRINT", "");
            if (!fp.isEmpty() && !PixelDeviceRepository.isValidFingerprint(fp)) {
                toast(getString(R.string.pif_invalid_fingerprint));
                return;
            }
            json.put("manually_imported", true);
            ContentResolver cr = requireContext().getContentResolver();
            SpoofStore.writePif(cr, json);
            SpoofStore.updatePatchIfSimple(cr, json.optString("SECURITY_PATCH", ""));
            SpoofStore.stopGms(requireContext());
            toast(getString(R.string.pif_imported));
            SpoofStore.confirmClearPlayStore(requireContext(), R.string.pif_wipe_playstore_message);
            refresh();
        } catch (Exception e) {
            toast(getString(R.string.pif_failed, e.getMessage()));
        }
    }

    private void fetchProfiles() {
        if (mFetching || !isAdded()) return;
        mFetching = true;
        Preference fetch = findPreference("pif_fetch");
        if (fetch != null) fetch.setEnabled(false);
        android.content.Context context = requireContext();
        new Thread(() -> {
            List<PixelDeviceRepository.PixelProfile> profiles = null;
            Exception error = null;
            try {
                profiles = PixelDeviceRepository.getProfiles(context, true);
            } catch (Exception e) {
                error = e;
            }
            List<PixelDeviceRepository.PixelProfile> result = profiles;
            Exception failure = error;
            android.app.Activity activity = getActivity();
            if (activity == null) {
                mFetching = false;
                return;
            }
            activity.runOnUiThread(() -> {
                if (failure != null) {
                    mFetching = false;
                    if (fetch != null) fetch.setEnabled(true);
                    toast(getString(R.string.pif_failed,
                            failure.getMessage() == null ? "error" : failure.getMessage()));
                    return;
                }
                showProfiles(result);
            });
        }, "pif-fetch").start();
    }

    private void showProfiles(List<PixelDeviceRepository.PixelProfile> profiles) {
        mFetching = false;
        Preference fetch = findPreference("pif_fetch");
        if (fetch != null) fetch.setEnabled(true);
        if (!isAdded()) return;
        if (profiles == null || profiles.isEmpty()) {
            toast(getString(R.string.pif_no_devices));
            return;
        }
        String recommended = PixelDeviceRepository.getDefaultPifCodename(profiles);
        String[] labels = new String[profiles.size()];
        int checked = 0;
        for (int i = 0; i < profiles.size(); i++) {
            PixelDeviceRepository.PixelProfile profile = profiles.get(i);
            String label = profile.model == null ? profile.codename : profile.model;
            if (recommended.equals(profile.codename)) {
                label = label + " — " + getString(R.string.pif_recommended);
                checked = i;
            }
            labels[i] = label;
        }
        final int[] choice = {checked};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.pif_select_device)
                .setSingleChoiceItems(labels, checked, (d, which) -> choice[0] = which)
                .setPositiveButton(android.R.string.ok, (d, w) -> applyProfile(profiles.get(choice[0])))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyProfile(PixelDeviceRepository.PixelProfile profile) {
        if (profile == null || !PixelDeviceRepository.isValidFingerprint(profile.fingerprint)) {
            toast(getString(R.string.pif_invalid_fingerprint));
            return;
        }
        try {
            ContentResolver cr = requireContext().getContentResolver();
            JSONObject json = SpoofStore.profileConfig(SpoofStore.readPif(cr), profile);
            SpoofStore.writePif(cr, json);
            SpoofStore.updatePatchIfSimple(cr, profile.securityPatch);
            SpoofStore.stopGms(requireContext());
            toast(getString(R.string.pif_fetched_model, profile.model));
            SpoofStore.confirmClearPlayStore(requireContext(), R.string.pif_wipe_playstore_message);
            refresh();
        } catch (JSONException e) {
            toast(getString(R.string.pif_failed, e.getMessage()));
        }
    }

    private void toast(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.pif_settings) {
                @Override
                protected boolean isPageSearchEnabled(android.content.Context context) {
                    return DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(context);
                }
            };
}
