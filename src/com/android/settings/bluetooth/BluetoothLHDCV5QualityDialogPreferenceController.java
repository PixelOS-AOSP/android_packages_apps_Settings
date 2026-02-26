/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.development.bluetooth;

import android.annotation.Nullable;
import android.bluetooth.BluetoothCodecType;
import android.bluetooth.BluetoothCodecConfig;
import android.content.Context;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.development.BluetoothA2dpConfigStore;
import com.android.settingslib.core.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog preference controller to set the Bluetooth A2DP config of LHDCV5 playback quality index
 */
public class BluetoothLHDCV5QualityDialogPreferenceController extends
        AbstractBluetoothDialogPreferenceController {

    private static final String KEY = "bluetooth_select_a2dp_codec_lhdcv5_playback_quality";
    
    /**
     * LHDCV5 playback quality @ codecSpecific1Value description:
     *  0x |        FF         |        FF        |    C0    |       FF      |
     *     | MinBitrate Index  | MaxBitrate Index | CheckTag | Quality Index | 
     */
    private static final String TAG = "BtLhdcV5AudioQualityCtr";
    private static final long LHDCV5_QUALITY_INDEX_MASK = 0xC000;
    private static final long LHDCV5_QUALITY_INDEX_TAG = 0x4000;
    private static final long LHDCV5_QUALITY_INDEX_VALUE_MASK = 0xFF;

    //DEFAULT_MAX_INDEX: sync with BluetoothLHDCV5QualityDialogPreference.java::getDefaultIndex()
    private static final int DEFAULT_MAX_INDEX = 9;

    //followings must sync with packages/modules/Bluetooth/system/stack/a2dp/a2dp_vendor_lhdcv5_constants.h
    private static final int INDEX_ABR        = DEFAULT_MAX_INDEX;
    private static final int INDEX_HIGH1      = 0x08;
    private static final int INDEX_HIGH       = 0x07;
    private static final int INDEX_MID        = 0x06;
    private static final int INDEX_LOW        = 0x05;
    private static final int INDEX_LOW4       = 0x04;
    private static final int INDEX_LOW3       = 0x03;
    private static final int INDEX_LOW2       = 0x02;
    private static final int INDEX_LOW1       = 0x01;
    private static final int INDEX_LOW0       = 0x00;
    private static final long MAX_BITRATE_MASK              = 0xFF0000;
    private static final int  MAX_BITRATE_SHIFT_BIT         = 16;
    private static final long MIN_BITRATE_MASK              = 0xFF000000;
    private static final int  MIN_BITRATE_SHIFT_BIT         = 24;
    private static final long LHDCV5_LOSSLESS_FEATURE       = 0x80;

    public BluetoothLHDCV5QualityDialogPreferenceController(Context context, Lifecycle lifecycle,
                                                      BluetoothA2dpConfigStore store) {
        super(context, lifecycle, store);
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        ((BaseBluetoothDialogPreference) mPreference).setCallback(this);
    }

    @Override
    protected void writeConfigurationValues(final int index) {
        synchronized (mBluetoothA2dpConfigStore) {
            long codecSpecific1Value = mBluetoothA2dpConfigStore.getCodecSpecific1Value();

            codecSpecific1Value &= ~ LHDCV5_QUALITY_INDEX_MASK;
            codecSpecific1Value &= ~ LHDCV5_QUALITY_INDEX_VALUE_MASK;
            codecSpecific1Value |= LHDCV5_QUALITY_INDEX_TAG;
            if (index <= DEFAULT_MAX_INDEX) {
                codecSpecific1Value |= index;
            } else {
                codecSpecific1Value |= DEFAULT_MAX_INDEX;
            }
            mBluetoothA2dpConfigStore.setCodecSpecific1Value(codecSpecific1Value);
        }
    }

    @Override
    protected int getCurrentIndexByConfig(BluetoothCodecConfig config) {
        if (config == null) {
            Log.e(TAG, "Unable to get current config index. Config is null.");
        }
        int index = 0;
        long codecSpecific1Value = config.getCodecSpecific1();
        long featureTag = 0;

        featureTag = LHDCV5_QUALITY_INDEX_TAG;
        index = convertCfgToBtnIndex(featureTag, codecSpecific1Value);

        // make a sync from current to storage while get
        synchronized (mBluetoothA2dpConfigStore) {
            mBluetoothA2dpConfigStore.setCodecSpecific1Value(codecSpecific1Value);
        }

        return index;
    }

    @Override
    public List<Integer> getSelectableIndex() {
        List<Integer> selectableIndex = new ArrayList<>();
        final BluetoothCodecConfig currentConfig = getCurrentCodecConfig();

        // LHDCV5 selectable items are filtered by product rules
        if (currentConfig != null) {
            long codecSpecific1Value = currentConfig.getCodecSpecific1();
            long codecSpecific3Value = currentConfig.getCodecSpecific3();
            int sampleRateValue = currentConfig.getSampleRate();
            int maxBitRateIdx = 0;
            int minBitRateIdx = 0;

            for (int i = 0; i <= DEFAULT_MAX_INDEX; i++) {
                // UI policy: limit selectable index according to max/min bitrate
                // According to peer max bitrate
                maxBitRateIdx = (int)((codecSpecific1Value & MAX_BITRATE_MASK) >> MAX_BITRATE_SHIFT_BIT);
                if (i > maxBitRateIdx && i < DEFAULT_MAX_INDEX) {
                    continue;
                }

                // According to peer min bitrate
                minBitRateIdx = (int)((codecSpecific1Value & MIN_BITRATE_MASK) >> MIN_BITRATE_SHIFT_BIT);
                if (i < minBitRateIdx) {
                    continue;
                }

                // UI policy: lossless can only be enabled in Auto BitRate Mode
                if ((codecSpecific3Value & LHDCV5_LOSSLESS_FEATURE) != 0) {
                    if (i != INDEX_ABR) {
                        continue;
                    }
                }

                selectableIndex.add(i);
            }

        }
        return selectableIndex;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        // Enable this preference menu when current codec type is LHDCV5. For other cases, disable it.
        final BluetoothCodecConfig currentConfig = getCurrentCodecConfig();
        if (currentConfig != null &&
           (currentConfig.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV5)) {
            preference.setEnabled(true);
        } else {
            preference.setEnabled(false);
        }
    }

    @Override
    public void onHDAudioEnabled(boolean enabled) {
        mPreference.setEnabled(false);
    }

    @VisibleForTesting
    int convertCfgToBtnIndex(long tag, long index) {
        int ret = 0;
        long tmp = index & LHDCV5_QUALITY_INDEX_MASK;
        if (tmp == tag) {
            index &= LHDCV5_QUALITY_INDEX_VALUE_MASK;
        } else {
            index = getDefaultIndex();
        }
        ret = (int)index;
        if(ret < 0)
        {
            ret = 0;
        }
        return ret;
    }
}
