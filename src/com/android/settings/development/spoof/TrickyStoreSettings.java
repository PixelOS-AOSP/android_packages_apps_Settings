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
import android.security.trickystore.TrickyStoreService;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.development.DeveloperOptionAwareMixin;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.settingslib.search.SearchIndexable;

import java.nio.charset.StandardCharsets;

@SearchIndexable
public class TrickyStoreSettings extends DashboardFragment implements DeveloperOptionAwareMixin {
    private static final String TAG = "TrickyStoreSettings";

    private final ActivityResultLauncher<String[]> mKeyboxImport =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onKeybox);
    private final ActivityResultLauncher<String[]> mTargetImport =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onTargets);

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
        return R.xml.trickystore_settings;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        click("ts_import_keybox", () ->
                mKeyboxImport.launch(new String[] {"text/*", "application/*"}));
        click("ts_delete_keybox", this::confirmDelete);
        click("ts_import_targets", () ->
                mTargetImport.launch(new String[] {"text/*", "application/*"}));

        EditTextPreference patch = findPreference("ts_patch");
        if (patch != null) {
            patch.setOnBindEditTextListener(edit -> {
                edit.setSingleLine(false);
                edit.setMinLines(3);
            });
            patch.setOnPreferenceChangeListener((p, value) -> {
                String text = value == null ? "" : String.valueOf(value);
                if (!SpoofStore.isValidPatch(text)) {
                    toast(getString(R.string.ts_patch_invalid));
                    return false;
                }
                SpoofStore.putString(requireContext().getContentResolver(),
                        Settings.Secure.SPOOF_TRICKYSTORE_PATCH, text.trim());
                refresh();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void click(String key, Runnable action) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceClickListener(p -> {
                action.run();
                return true;
            });
        }
    }

    private void refresh() {
        if (!isAdded()) return;
        ContentResolver cr = requireContext().getContentResolver();
        Preference status = findPreference("ts_keybox_status");
        String stored = SpoofStore.getString(cr, Settings.Secure.SPOOF_TRICKYSTORE_KEYBOX);
        boolean present = stored != null && !stored.isEmpty();
        if (status != null) {
            if (!present) {
                status.setSummary(R.string.ts_no_keybox);
            } else if (SpoofStore.SOURCE_USER.equals(SpoofStore.getString(cr, SpoofStore.KEYBOX_SOURCE))) {
                status.setSummary(R.string.ts_keybox_user);
            } else {
                status.setSummary(R.string.ts_keybox_installed);
            }
        }
        Preference delete = findPreference("ts_delete_keybox");
        if (delete != null) delete.setEnabled(present);
        String patch = SpoofStore.getString(cr, Settings.Secure.SPOOF_TRICKYSTORE_PATCH);
        EditTextPreference patchPref = findPreference("ts_patch");
        if (patchPref != null) {
            String shown = patch == null ? "" : patch.trim();
            patchPref.setText(patch == null ? "" : patch);
            if (shown.isEmpty()) {
                patchPref.setSummary(R.string.ts_patch_summary_unset);
            } else if (shown.indexOf('\n') >= 0) {
                patchPref.setSummary(R.string.ts_patch_custom);
            } else if (TrickyStoreService.isPatchLevelStale(shown)) {
                patchPref.setSummary(getString(R.string.ts_patch_stale, shown));
            } else {
                patchPref.setSummary(shown);
            }
        }
        Preference targets = findPreference("ts_targets");
        if (targets != null) {
            String list = SpoofStore.getString(cr, Settings.Secure.SPOOF_TRICKYSTORE_TARGET);
            if (list == null) list = TrickyStoreService.DEFAULT_TARGET_LIST;
            int count = SpoofStore.readTargetMap(list).size();
            targets.setSummary(getString(R.string.spoof_target_count, count));
        }
    }

    private void onKeybox(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            byte[] raw = SpoofStore.readUri(requireContext().getContentResolver(), uri);
            String problem = SpoofStore.keyboxProblem(new String(raw, StandardCharsets.UTF_8));
            if (problem != null) {
                toast(getString(R.string.ts_failed, problem));
                return;
            }
            ContentResolver cr = requireContext().getContentResolver();
            SpoofStore.putString(cr, Settings.Secure.SPOOF_TRICKYSTORE_KEYBOX,
                    SpoofStore.encodeKeybox(raw));
            SpoofStore.putString(cr, SpoofStore.KEYBOX_SOURCE, SpoofStore.SOURCE_USER);
            SpoofStore.stopGms(requireContext());
            toast(getString(R.string.ts_keybox_imported));
            SpoofStore.confirmClearPlayStore(requireContext(), R.string.ts_wipe_playstore_message);
            refresh();
        } catch (Exception e) {
            toast(getString(R.string.ts_failed, e.getMessage()));
        }
    }

    private void onTargets(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            byte[] raw = SpoofStore.readUri(requireContext().getContentResolver(), uri);
            String text = new String(raw, StandardCharsets.UTF_8);
            String trimmed = text.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                toast(getString(R.string.ts_failed, "not a target list"));
                return;
            }
            SpoofStore.putString(requireContext().getContentResolver(),
                    Settings.Secure.SPOOF_TRICKYSTORE_TARGET, text);
            toast(getString(R.string.ts_targets_imported));
            refresh();
        } catch (Exception e) {
            toast(getString(R.string.ts_failed, e.getMessage()));
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ts_delete_keybox_title)
                .setMessage(R.string.ts_delete_keybox_message)
                .setPositiveButton(R.string.ts_delete_keybox, (d, w) -> {
                    ContentResolver cr = requireContext().getContentResolver();
                    SpoofStore.putString(cr, Settings.Secure.SPOOF_TRICKYSTORE_KEYBOX, "");
                    SpoofStore.putString(cr, SpoofStore.KEYBOX_SOURCE, "");
                    SpoofStore.stopGms(requireContext());
                    toast(getString(R.string.ts_keybox_deleted));
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.trickystore_settings) {
                @Override
                protected boolean isPageSearchEnabled(android.content.Context context) {
                    return DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(context);
                }
            };
}
