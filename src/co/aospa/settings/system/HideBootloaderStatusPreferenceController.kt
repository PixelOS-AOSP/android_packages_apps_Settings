/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.settings.system

import android.content.Context
import com.android.internal.util.aospa.PropImitationHooks
import com.android.settings.core.BasePreferenceController

class HideBootloaderStatusPreferenceController(context: Context) :
        BasePreferenceController(context, PREF_KEY) {

    override fun getAvailabilityStatus(): Int =
        if (
            PropImitationHooks.sEnableKeyboxImitation &&
            runCatching { PropImitationHooks.getPihManager()?.keyboxProvider?.hasKeybox() ?: false }
                .getOrDefault(false)
        ) {
            AVAILABLE
        } else {
            CONDITIONALLY_UNAVAILABLE
        }

    companion object {
        private const val TAG = "HideBootloaderStatusPreferenceController"
        private const val PREF_KEY = "hide_bootloader_status_settings"
    }
}
