package com.android.settings.safetycenter.ui;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.security.DuressPasswordMainActivity;

public class DuressPasswordPrefController extends BasePreferenceController {

    public DuressPasswordPrefController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getUser().isSystem() ? AVAILABLE : DISABLED_FOR_USER;
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return super.handlePreferenceTreeClick(preference);
        }
        mContext.startActivity(new Intent(mContext, DuressPasswordMainActivity.class));
        return true;
    }
}
