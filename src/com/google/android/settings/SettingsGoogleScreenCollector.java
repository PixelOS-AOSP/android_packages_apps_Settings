package com.google.android.settings;

import com.android.settingslib.metadata.FixedArrayMap;
import com.android.settingslib.metadata.PreferenceScreenMetadataFactory;

import com.google.android.settings.fuelgauge.batterysaver.AdaptiveBatteryScreen;
import com.google.android.settings.fuelgauge.batterysaver.BatterySaverGoogleScreen;
import com.google.android.settings.fuelgauge.batterysaver.BatterySaverScheduleScreen;

public abstract class SettingsGoogleScreenCollector {

    public static FixedArrayMap get() {
        return new FixedArrayMap(
                3,
                (obj) ->
                        SettingsGoogleScreenCollector.init((FixedArrayMap.OrderedInitializer) obj));
    }

    private static void init(FixedArrayMap.OrderedInitializer initializer) {
        initializer.put(
                "adaptive_battery_entry",
                (PreferenceScreenMetadataFactory) AdaptiveBatteryScreen::new);
        initializer.put(
                "battery_saver_schedule",
                (PreferenceScreenMetadataFactory) context -> new BatterySaverScheduleScreen());
        initializer.put(
                "battery_saver_screen",
                (PreferenceScreenMetadataFactory) context -> new BatterySaverGoogleScreen());
    }
}
