package com.android.settings.security;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settings.R;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.google.android.material.button.MaterialButton;

public class DuressPasswordMainActivity extends DuressPasswordActivity {
    private static final String TAG = DuressPasswordMainActivity.class.getSimpleName();
    private static final String KEY_USER_CREDENTIAL = "user_credential";
    private static final String KEY_HAS_ASKED_FOR_USER_CREDENTIALS = "asked_for_user_credentials";

    private MaterialButton addButton;
    private MaterialButton updateButton;
    private MaterialButton deleteButton;
    private LockscreenCredential userCredential;
    private boolean askedForUserCredentials;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.duress_pwd_pref_title);
        setContentView(R.layout.duress_password_main);

        addButton = requireViewById(R.id.duress_password_add);
        updateButton = requireViewById(R.id.duress_password_update);
        deleteButton = requireViewById(R.id.duress_password_delete);

        addButton.setOnClickListener(v -> launchSetup(R.string.duress_pwd_action_add));
        updateButton.setOnClickListener(v -> launchSetup(R.string.duress_pwd_action_update));
        deleteButton.setOnClickListener(v -> confirmDelete());

        if (savedInstanceState != null) {
            userCredential = savedInstanceState.getParcelable(KEY_USER_CREDENTIAL, LockscreenCredential.class);
            askedForUserCredentials = savedInstanceState.getBoolean(
                    KEY_HAS_ASKED_FOR_USER_CREDENTIALS);
        }
    }

    @Override
    protected boolean hasUserCredential() {
        return userCredential != null && !userCredential.isNone();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_USER_CREDENTIAL, userCredential);
        outState.putBoolean(KEY_HAS_ASKED_FOR_USER_CREDENTIALS, askedForUserCredentials);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (userCredential == null) {
            if (!getLockPatternUtils().isSecure(getUserId())) {
                userCredential = LockscreenCredential.createNone();
            } else if (!askedForUserCredentials) {
                var b = new ChooseLockSettingsHelper.Builder(this);
                b.setRequestCode(REQ_CODE_OBTAIN_USER_CREDENTIALS);
                b.setReturnCredentials(true);
                b.setForegroundOnly(true);
                b.show();
                askedForUserCredentials = true;
            }
        }

        updateActionList();
    }

    private void updateActionList() {
        if (userCredential == null) {
            Log.d(TAG, "no userCredential, skipping updateActionList");
            return;
        }

        boolean hasDuressCredentials = getLockPatternUtils().hasDuressCredentials(userCredential);
        addButton.setVisibility(hasDuressCredentials ? android.view.View.GONE
                : android.view.View.VISIBLE);
        updateButton.setVisibility(hasDuressCredentials ? android.view.View.VISIBLE
                : android.view.View.GONE);
        deleteButton.setVisibility(hasDuressCredentials ? android.view.View.VISIBLE
                : android.view.View.GONE);
    }

    private void launchSetup(int title) {
        var intent = new Intent(this, DuressPasswordSetupActivity.class);
        intent.putExtra(DuressPasswordSetupActivity.EXTRA_TITLE_TEXT, title);
        intent.putExtra(DuressPasswordSetupActivity.EXTRA_USER_CREDENTIAL, userCredential);
        allowNextOnStop = true;
        startActivityForResult(intent, REQ_CODE_SETUP);
    }

    private void confirmDelete() {
        var builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.duress_pwd_action_delete_confirmation);
        builder.setMessage(R.string.duress_pwd_delete_confirmation_summary);
        builder.setPositiveButton(R.string.duress_pwd_delete_button, (dialog, which) -> {
            LockPatternUtils lockPatternUtils = getLockPatternUtils();
            try {
                lockPatternUtils.deleteDuressCredentials(userCredential);
            } catch (Exception e) {
                Log.e(TAG, "deleteDuressCredentials failed", e);

                var errorDialog = new AlertDialog.Builder(this);
                errorDialog.setMessage(getString(R.string.duress_pwd_delete_error, e.toString()));
                errorDialog.setNeutralButton(R.string.duress_pwd_error_dialog_dismiss, null);
                errorDialog.show();
                return;
            }
            updateActionList();
        });
        builder.setNegativeButton(R.string.duress_pwd_cancel_button, null);
        builder.show();
    }

    static final int REQ_CODE_OBTAIN_USER_CREDENTIALS = 1;
    static final int REQ_CODE_SETUP = 2;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_SETUP) {
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
            return;
        }

        if (requestCode != REQ_CODE_OBTAIN_USER_CREDENTIALS) {
            throw new IllegalStateException(Integer.toString(resultCode));
        }

        Log.d(TAG, "onActivityResult, requestCode: " + requestCode + ", resultCode: " + resultCode);

        if (resultCode != RESULT_OK) {
            finish();
            return;
        }

        if (data == null) {
            throw new IllegalStateException("data == null");
        }

        var credential = data.getParcelableExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, LockscreenCredential.class);
        if (credential == null) {
            throw new IllegalStateException("no returned credential");
        }
        userCredential = credential;
    }
}
