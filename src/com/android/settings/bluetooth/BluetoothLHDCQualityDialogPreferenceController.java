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
 * Dialog preference controller to set the Bluetooth A2DP config of LHDCV3/V4 quality
 */
public class BluetoothLHDCQualityDialogPreferenceController extends
        AbstractBluetoothDialogPreferenceController {

    private static final String KEY = "bluetooth_select_a2dp_codec_lhdc_playback_quality";
    private static final String TAG = "BtLhdcAudioQualityCtr";
    private static final int DEFAULT_MASK = 0xC000;
    private static final int DEFAULT_TAG = 0x8000;
    private static final int VALUE_MASK = 0xFF;

    private static final int index_adjust_offset = 1;  //offset by low0

    //DEFAULT_MAX_INDEX: sync with BluetoothLHDCQualityDialogPreference.java::getDefaultIndex()
    private static final int DEFAULT_MAX_INDEX = 7;

    private static final int INDEX_ABR = DEFAULT_MAX_INDEX;
    private static final int INDEX_HIGH = 6;   //900kbps
    private static final int INDEX_MID = 5;    //500kbps
    private static final int INDEX_LOW = 4;    //400kbps
    private static final int INDEX_LOW4 = 3;   //320kbps
    private static final int INDEX_LOW3 = 2;   //256kbps
    private static final int INDEX_LOW2 = 1;   //192kbps
    private static final int INDEX_LOW1 = 0;   //128kbps

    private static final int MAX_BITRATE_MASK = 0xF00;
    private static final int MIN_BITRATE_MASK = 0xF0000;
    private static final int LHDCV3_CAP_V4_LLAC_MASK = 0xF00000;

    private static final int LHDCV3_MAX_BITRATE_INX_MID = 0x100;    //400kbps
    private static final int LHDCV3_MAX_BITRATE_INX_LOW = 0x200;    //500kbps
    private static final int LHDCV3_MAX_BITRATE_INX_HIGH = 0x000;   //900kbps

    private static final int LHDCV3_MIN_BITRATE_ON = 0x10000;

    private static final int LHDCV3_CAP_V4_LLAC = 0x300000;
    private static final int LHDCV3_CAP_V4_ONLY = 0x200000;
    private static final int LHDCV3_CAP_LLAC_ONLY = 0x100000;
    private static final int LHDCV3_CAP_V3_ONLY = 0x000000;

    public BluetoothLHDCQualityDialogPreferenceController(Context context, Lifecycle lifecycle,
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

            final BluetoothCodecConfig currentConfig = getCurrentCodecConfig();

            // LHDCV3/V5 selectable items are filtered by product rules
            if (currentConfig != null) {
                int codecType = currentConfig.getCodecType();

                if (codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV3) {
                    codecSpecific1Value &= ~ DEFAULT_MASK;
                    codecSpecific1Value &= ~ VALUE_MASK;
                    codecSpecific1Value |= DEFAULT_TAG;
                    if (index <= DEFAULT_MAX_INDEX) {
                        codecSpecific1Value |= (index + index_adjust_offset);
                    } else {
                        codecSpecific1Value |= DEFAULT_MAX_INDEX;
                    }
                    mBluetoothA2dpConfigStore.setCodecSpecific1Value(codecSpecific1Value);
                }
            }
        }
    }

    @Override
    protected int getCurrentIndexByConfig(BluetoothCodecConfig config) {
        if (config == null) {
            Log.e(TAG, "Unable to get current config index. Config is null.");
        }
        int index = 0;
        long codecSpecific1Value = config.getCodecSpecific1();
        int codecType = config.getCodecType();
        long featureTag = 0;
        if (codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV3) {
            featureTag = DEFAULT_TAG;
        }
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

        // LHDCV3/V4 selectable items are filtered by product rules
        if (currentConfig != null) {
            int codecType = currentConfig.getCodecType();
            long codecSpecific1Value = currentConfig.getCodecSpecific1();
            int sampleRateValue = currentConfig.getSampleRate();

            if (codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV3) {
                for (int i = 0; i <= DEFAULT_MAX_INDEX; i++) {
                    // UI policy 1: filter bitrate index by LHDCV3/V4 capabilities: V4 and LLAC
                    // cap: V4+LLAC
                    if ((codecSpecific1Value & LHDCV3_CAP_V4_LLAC_MASK) == LHDCV3_CAP_V4_LLAC) {
                        if (sampleRateValue == BluetoothCodecConfig.SAMPLE_RATE_96000) {
                            if ((codecSpecific1Value & MIN_BITRATE_MASK) == LHDCV3_MIN_BITRATE_ON) {
                                // min birate ON: at least available from MID but no ABR
                                if (i < INDEX_MID || i == INDEX_ABR) {
                                    continue;
                                }
                            } else {
                                // min birate OFF: at least available from LOW but no ABR
                                if (i < INDEX_LOW || i == INDEX_ABR) {
                                    continue;
                                }
                            }
                        }
                    }
                    // cap: V4 only
                    if ((codecSpecific1Value & LHDCV3_CAP_V4_LLAC_MASK) == LHDCV3_CAP_V4_ONLY) {
                        if (sampleRateValue == BluetoothCodecConfig.SAMPLE_RATE_96000) {
                            if ((codecSpecific1Value & MIN_BITRATE_MASK) == LHDCV3_MIN_BITRATE_ON) {
                                // min birate ON: at least available from MID but no ABR
                                if (i < INDEX_MID || i == INDEX_ABR) {
                                    continue;
                                }
                            } else {
                                // min birate OFF: at least available from LOW but no ABR
                                if (i < INDEX_LOW || i == INDEX_ABR) {
                                    continue;
                                }
                            }
                        } else {
                            if ((codecSpecific1Value & MIN_BITRATE_MASK) == LHDCV3_MIN_BITRATE_ON) {
                                // min birate ON: at least available from LOW4
                                if (i < INDEX_LOW4) {
                                    continue;
                                }
                            }
                        }
                    }
                    // cap: LLAC only
                    if ((codecSpecific1Value & LHDCV3_CAP_V4_LLAC_MASK) == LHDCV3_CAP_LLAC_ONLY) {
                        // at most available to LOW but include ABR
                        if (i > INDEX_LOW && i != INDEX_ABR) {
                            continue;
                        }
                    }
                    // cap: V3 only
                    if ((codecSpecific1Value & LHDCV3_CAP_V4_LLAC_MASK) == LHDCV3_CAP_V3_ONLY) {
                        if (sampleRateValue == BluetoothCodecConfig.SAMPLE_RATE_96000) {
                            if ((codecSpecific1Value & MIN_BITRATE_MASK) == LHDCV3_MIN_BITRATE_ON) {
                                // min birate ON: at least available from LOW
                                if (i < INDEX_LOW) {
                                    continue;
                                }
                            }
                        } else {
                            if ((codecSpecific1Value & MIN_BITRATE_MASK) == LHDCV3_MIN_BITRATE_ON) {
                                // min birate ON: at least available from LOW4
                                if (i < INDEX_LOW4) {
                                    continue;
                                }
                            }
                        }
                    }

                    // UI policy 2: filter bitrate index by max bitrate
                    if (((codecSpecific1Value & MAX_BITRATE_MASK) == LHDCV3_MAX_BITRATE_INX_LOW) &&
                        (i > INDEX_LOW && i < DEFAULT_MAX_INDEX)) {
                        continue;
                    }
                    if (((codecSpecific1Value & MAX_BITRATE_MASK) == LHDCV3_MAX_BITRATE_INX_MID) && 
                        (i > INDEX_MID && i < DEFAULT_MAX_INDEX)) {
                        continue;
                    }
                    selectableIndex.add(i);
                }
            }
        }
        return selectableIndex;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        // Enable this preference menu when current codec type is LHDC V3/V4. For other cases, disable it.
        final BluetoothCodecConfig currentConfig = getCurrentCodecConfig();
        if (currentConfig != null &&
           (currentConfig.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LHDCV3)) {
            preference.setEnabled(true);
        } else {
            preference.setEnabled(false);
            //preference.setSummary("");
        }
    }

    @Override
    public void onHDAudioEnabled(boolean enabled) {
        mPreference.setEnabled(false);
    }

    @VisibleForTesting
    int convertCfgToBtnIndex(long tag, long index) {
        int ret = 0;
        long tmp = index & DEFAULT_MASK;
        if (tmp == tag) {
            index &= VALUE_MASK;
        } else {
            index = getDefaultIndex();
        }
        ret = (int)(index - index_adjust_offset);
        if(ret < 0)
        {
            ret = 0;
        }
        return ret;
    }
}
