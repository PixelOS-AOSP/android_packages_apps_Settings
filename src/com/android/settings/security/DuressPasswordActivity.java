package com.android.settings.security;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.core.SettingsBaseActivity;
import com.android.settings.overlay.FeatureFactory;

public class DuressPasswordActivity extends SettingsBaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        setResult(RESULT_OK);
    }

    protected boolean allowNextOnStop;

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && hasUserCredential()) {
            if (allowNextOnStop) {
                allowNextOnStop = false;
            } else {
                // require user credential to be re-entered after activity is backgrounded
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    protected boolean hasUserCredential() {
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            /** @see com.android.settings.password.ConfirmDeviceCredentialBaseActivity#onDestroy */
            getMainThreadHandler().postDelayed(() -> {
                System.gc();
                System.runFinalization();
                System.gc();
            }, 5000);
        }
    }

    LockPatternUtils getLockPatternUtils() {
        return FeatureFactory.getFeatureFactory()
                .getSecurityFeatureProvider()
                .getLockPatternUtils(this);
    }
}
