/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.settings.system

import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure.KEYBOX_TARGET_PACKAGES
import android.util.Log
import co.aospa.settings.core.BaseAppListSettingsFragment
import com.android.settings.R

class HideBootloaderStatusSettings : BaseAppListSettingsFragment() {

    override fun getTitleResId(): Int = R.string.hide_bootloader_status_title

    override fun getInitialCheckedList(): List<String> =
        Settings.Secure.getString(requireContext().contentResolver, KEYBOX_TARGET_PACKAGES)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    override fun onListUpdate(packageName: String, isChecked: Boolean) {
        val newSet = getInitialCheckedList().toMutableSet().apply {
            if (isChecked) {
                add(packageName)
            } else {
                remove(packageName)
            }
        }
        Settings.Secure.putString(
            requireContext().contentResolver,
            KEYBOX_TARGET_PACKAGES,
            newSet.joinToString(",")
        )
    }

    companion object {
        private const val TAG = "HideBootloaderStatusSettings"
    }
}
