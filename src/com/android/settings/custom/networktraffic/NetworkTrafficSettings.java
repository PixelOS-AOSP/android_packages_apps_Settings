/*
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.networktraffic;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.custom.preference.SecureSettingSwitchPreference;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class NetworkTrafficSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener  {

    private static final String TAG = "NetworkTrafficSettings";

    private static final int UNITS_KILOBITS = 0;
    private static final int UNITS_MEGABITS = 1;
    private static final int UNITS_KILOBYTES = 2;
    private static final int UNITS_MEGABYTES = 3;
    private static final int UNITS_AUTOBYTES = 4;

    private static final int SHOW_UNITS_OFF = 0;
    private static final int SHOW_UNITS_ON = 1;
    private static final int SHOW_UNITS_COMPACT = 2;

    private ListPreference mNetTrafficMode;
    private SecureSettingSwitchPreference mNetTrafficAutohide;
    private ListPreference mNetTrafficUnits;
    private ListPreference mNetTrafficShowUnits;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.network_traffic_settings);
        getActivity().setTitle(R.string.network_traffic_settings_title);

        final ContentResolver resolver = getActivity().getContentResolver();

        mNetTrafficMode = findPreference(Settings.Secure.NETWORK_TRAFFIC_MODE);
        mNetTrafficMode.setOnPreferenceChangeListener(this);
        int mode = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_MODE, 0);
        mNetTrafficMode.setValue(String.valueOf(mode));

        mNetTrafficAutohide = findPreference(Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE);
        mNetTrafficAutohide.setOnPreferenceChangeListener(this);

        mNetTrafficUnits = findPreference(Settings.Secure.NETWORK_TRAFFIC_UNITS);
        mNetTrafficUnits.setOnPreferenceChangeListener(this);
        int units = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_UNITS, UNITS_KILOBYTES);
        mNetTrafficUnits.setValue(String.valueOf(units));

        mNetTrafficShowUnits = findPreference(Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS);
        mNetTrafficShowUnits.setOnPreferenceChangeListener(this);
        adjustShowUnitsState(units, resolver);

        updateEnabledStates(mode);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mNetTrafficMode) {
            int mode = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.NETWORK_TRAFFIC_MODE, mode);
            updateEnabledStates(mode);
        } else if (preference == mNetTrafficUnits) {
            int units = Integer.parseInt((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.NETWORK_TRAFFIC_UNITS, units);
            adjustShowUnitsState(units, getActivity().getContentResolver());
        } else if (preference == mNetTrafficShowUnits) {
            int showUnits = Integer.valueOf((String) newValue);
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, showUnits);
        }
        return true;
    }

    private void adjustShowUnitsState(int units, ContentResolver resolver) {
        int showUnits = Settings.Secure.getInt(resolver,
                Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, SHOW_UNITS_ON);
        if (units == UNITS_KILOBYTES || units == UNITS_MEGABYTES) {
            // off, on, compact
            mNetTrafficShowUnits.setEntries(R.array.network_traffic_show_units_entries);
            mNetTrafficShowUnits.setEntryValues(R.array.network_traffic_show_units_values);
        } else {
            boolean putShowUnits = false;
            if (units == UNITS_AUTOBYTES) {
                if (showUnits == SHOW_UNITS_OFF) {
                    showUnits = SHOW_UNITS_COMPACT;
                    putShowUnits = true;
                }
                // on, compact
                mNetTrafficShowUnits.setEntries(R.array.network_traffic_show_units_entries_auto);
                mNetTrafficShowUnits.setEntryValues(R.array.network_traffic_show_units_values_auto);
            } else {
                if (showUnits == SHOW_UNITS_COMPACT) {
                    showUnits = SHOW_UNITS_ON;
                    putShowUnits = true;
                }
                // off, on
                mNetTrafficShowUnits.setEntries(R.array.network_traffic_show_units_entries_bits);
                mNetTrafficShowUnits.setEntryValues(R.array.network_traffic_show_units_values_bits);
            }
            if (putShowUnits)
                Settings.Secure.putInt(resolver,
                        Settings.Secure.NETWORK_TRAFFIC_SHOW_UNITS, showUnits);
        }
        mNetTrafficShowUnits.setValue(String.valueOf(showUnits));
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CUSTOM;
    }

    private void updateEnabledStates(int mode) {
        final boolean enabled = mode != 0;
        mNetTrafficAutohide.setEnabled(enabled);
        mNetTrafficUnits.setEnabled(enabled);
        mNetTrafficShowUnits.setEnabled(enabled);
    }
}
