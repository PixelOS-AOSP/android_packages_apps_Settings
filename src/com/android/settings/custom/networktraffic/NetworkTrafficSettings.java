package com.android.settings.custom.networktraffic;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.custom.preference.SecureSettingMainSwitchPreference;
import com.android.settings.custom.preference.SecureSettingSwitchPreference;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SearchIndexable
public class NetworkTrafficSettings
        extends SettingsPreferenceFragment implements OnPreferenceChangeListener {
    private static final String TAG = "NetworkTrafficSettings";

    private static final String KEY_NET_TRAFFIC_MODE = "network_traffic_mode";
    private static final String KEY_NET_TRAFFIC_AUTOHIDE = "network_traffic_autohide";

    private SecureSettingMainSwitchPreference mNetMonitor;
    private SecureSettingSwitchPreference mThreshold;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.network_traffic_settings);

        final ContentResolver resolver = getActivity().getContentResolver();

        boolean isEnabled =
                Settings.Secure.getIntForUser(
                        resolver, Settings.Secure.NETWORK_TRAFFIC_MODE, 1, UserHandle.USER_CURRENT)
                == 1;
        mNetMonitor = findPreference(KEY_NET_TRAFFIC_MODE);
        mNetMonitor.setChecked(isEnabled);
        mNetMonitor.setOnPreferenceChangeListener(this);

        boolean isThresholdEnabled =
                Settings.Secure.getIntForUser(resolver, Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0,
                        UserHandle.USER_CURRENT)
                == 1;
        mThreshold = findPreference(KEY_NET_TRAFFIC_AUTOHIDE);
        mThreshold.setChecked(isThresholdEnabled);
        mThreshold.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mNetMonitor) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putIntForUser(getActivity().getContentResolver(),
                    Settings.Secure.NETWORK_TRAFFIC_MODE, value ? 1 : 0, UserHandle.USER_CURRENT);
            mNetMonitor.setChecked(value);
            mThreshold.setChecked(value);
            return true;
        } else if (preference == mThreshold) {
            boolean value = (Boolean) objValue;
            Settings.Secure.putIntForUser(getContentResolver(),
                    Settings.Secure.NETWORK_TRAFFIC_AUTOHIDE, value ? 1 : 0,
                    UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.CUSTOM;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.network_traffic_settings);
}
