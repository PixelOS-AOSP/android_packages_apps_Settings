/*
 * Copyright (C) 2026 The PixelOS Project
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

package com.android.settings.development.spoof;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.security.trickystore.KeyBoxManager;
import android.security.trickystore.TrickyStoreService;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.android.internal.util.custom.PixelDeviceRepository;
import com.android.settings.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes the spoof settings the framework services already consume.
 * Value formats are part of that contract: keyboxes are Base64 of the XML, and
 * TrickyStore targets are one package per line with a mode suffix.
 */
final class SpoofStore {
    private static final String TAG = "SpoofStore";

    static final String PIF_ENABLED = "spoof_pif_enabled";
    static final String TRICKY_ENABLED = "spoof_trickystore_enabled";
    static final String PIF_LAST_FETCH = "spoof_pif_last_auto_fetch";
    static final String KEYBOX_SOURCE = "spoof_trickystore_keybox_source";
    static final String SOURCE_USER = "user";

    static final String PP_TARGETS = "pi_pp_targets";
    static final String PP_MODEL = "pi_pp_model";

    private static final int MAX_IMPORT_BYTES = 512 * 1024;
    private static final String[] GMS_FAMILY = {
            "com.android.vending",
            "com.google.android.gms.unstable",
            "com.google.android.gms",
            "com.google.android.gms.persistent",
            "com.google.android.rkpdapp",
            "com.google.android.gsf",
            "com.google.android.contactkeys",
            "com.google.android.safetycore",
            "com.google.android.googlequicksearchbox",
    };
    private static final String[] PIF_FINGERPRINT_DERIVED = {
            "BRAND", "PRODUCT", "DEVICE", "ID", "INCREMENTAL", "TYPE", "TAGS",
            "RELEASE", "_canary_month", "_canary_release_date",
    };

    private SpoofStore() {}

    static int getInt(ContentResolver cr, boolean system, String key, int def) {
        return system
                ? Settings.System.getInt(cr, key, def)
                : Settings.Secure.getInt(cr, key, def);
    }

    static void putInt(ContentResolver cr, boolean system, String key, int value) {
        if (system) {
            Settings.System.putInt(cr, key, value);
        } else {
            Settings.Secure.putInt(cr, key, value);
        }
    }

    static String getString(ContentResolver cr, String key) {
        return Settings.Secure.getString(cr, key);
    }

    static void putString(ContentResolver cr, String key, String value) {
        Settings.Secure.putString(cr, key, value);
    }

    static void stopPackages(Context context, String... packages) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am == null) return;
        for (String pkg : packages) {
            if (TextUtils.isEmpty(pkg)) continue;
            try {
                am.forceStopPackage(pkg);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not stop " + pkg, e);
            }
        }
    }

    static void stopGms(Context context) {
        stopPackages(context, GMS_FAMILY);
    }

    /**
     * Play Store keeps the certification result in its own data. Force-stopping
     * it does not drop that cache. The caller has already saved the new
     * fingerprint or keybox; this only asks before wiping Play Store data.
     */
    static void confirmClearPlayStore(Context context, int messageRes) {
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(R.string.pif_wipe_playstore_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.pif_wipe_confirm, (dialog, which) -> {
                    try {
                        context.getPackageManager().clearApplicationUserData(
                                "com.android.vending", null);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Could not clear Play Store data", e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static void resetDefaults(ContentResolver cr) {
        Settings.Secure.putLong(cr, PIF_LAST_FETCH, 0L);
        putString(cr, Settings.Secure.SPOOF_TRICKYSTORE_TARGET,
                TrickyStoreService.DEFAULT_TARGET_LIST);
        putString(cr, Settings.Secure.SPOOF_TRICKYSTORE_PATCH, "");
        putString(cr, Settings.Secure.SPOOF_PIF_CONFIG, "");
    }

    static JSONObject readPif(ContentResolver cr) {
        String raw = getString(cr, Settings.Secure.SPOOF_PIF_CONFIG);
        if (TextUtils.isEmpty(raw)) return new JSONObject();
        try {
            String trimmed = raw.trim();
            if (trimmed.startsWith("{")) return new JSONObject(trimmed);
            return new JSONObject(normalizePif(trimmed));
        } catch (JSONException e) {
            Log.w(TAG, "Unreadable Play Integrity config", e);
            return new JSONObject();
        }
    }

    static void writePif(ContentResolver cr, JSONObject json) throws JSONException {
        putString(cr, Settings.Secure.SPOOF_PIF_CONFIG, json.toString(2));
    }

    /** JSON, or a {@code key=value} prop file, stored as JSON. */
    static String normalizePif(String raw) throws JSONException {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return "{}";
        if (trimmed.startsWith("{")) return trimmed;
        JSONObject json = new JSONObject();
        for (String line : trimmed.split("\n")) {
            String stripped = line.trim();
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("//")) {
                continue;
            }
            int eq = stripped.indexOf('=');
            if (eq <= 0) continue;
            String key = stripped.substring(0, eq).trim();
            String value = stripped.substring(eq + 1).trim();
            int comment = value.indexOf('#');
            if (comment >= 0) value = value.substring(0, comment).trim();
            if (!key.isEmpty()) json.put(key, value);
        }
        return json.toString(2);
    }

    static JSONObject profileConfig(JSONObject existing, PixelDeviceRepository.PixelProfile profile)
            throws JSONException {
        JSONObject out = new JSONObject();
        JSONObject old = existing == null ? new JSONObject() : existing;
        JSONArray names = old.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                if (key.startsWith("spoof") || "verboseLogs".equals(key) || "DEBUG".equals(key)) {
                    out.put(key, old.get(key));
                }
            }
        }
        String brand = profile.brand == null ? "" : profile.brand;
        String manufacturer = brand.isEmpty()
                ? ""
                : brand.substring(0, 1).toUpperCase(Locale.US) + brand.substring(1);
        out.put("MANUFACTURER", manufacturer);
        out.put("BRAND", brand);
        out.put("MODEL", profile.model);
        out.put("PRODUCT", profile.product);
        out.put("DEVICE", profile.device);
        out.put("FINGERPRINT", profile.fingerprint);
        out.put("SECURITY_PATCH", profile.securityPatch);
        out.put("DEVICE_INITIAL_SDK_INT",
                PixelDeviceRepository.getInitialSdkInt(profile.device));
        if (profile.isCanary && profile.securityPatch != null && profile.securityPatch.length() >= 7) {
            out.put("_canary_month", profile.securityPatch.substring(0, 7));
        }
        if (profile.isCanary && profile.releaseDate != null) {
            out.put("_canary_release_date", profile.releaseDate);
        }
        out.put("manually_imported", false);
        return out;
    }

    static void updatePatchIfSimple(ContentResolver cr, String patch) {
        if (patch == null || !patch.matches("\\d{4}-\\d{2}-\\d{2}")) return;
        String existing = getString(cr, Settings.Secure.SPOOF_TRICKYSTORE_PATCH);
        String trimmed = existing == null ? "" : existing.trim();
        if (!trimmed.isEmpty() && !trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) return;
        if (patch.equals(trimmed)) return;
        putString(cr, Settings.Secure.SPOOF_TRICKYSTORE_PATCH, patch);
    }

    static boolean isValidPatch(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return true;
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) return true;
        if (trimmed.matches("YYYY-MM-\\d{2}")) return true;
        for (String line : value.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (t.startsWith("[") && t.endsWith("]")) continue;
            int eq = t.indexOf('=');
            if (eq <= 0) return false;
            String v = t.substring(eq + 1).trim();
            if (v.matches("\\d{4}-\\d{2}-\\d{2}") || v.matches("YYYY-MM-\\d{2}")) continue;
            if (v.equalsIgnoreCase("no") || v.equalsIgnoreCase("prop")) continue;
            return false;
        }
        return true;
    }

    static void putPifField(ContentResolver cr, String key, String value, boolean manual)
            throws JSONException {
        JSONObject json = readPif(cr);
        json.put(key, value);
        if ("FINGERPRINT".equals(key)) {
            for (String derived : PIF_FINGERPRINT_DERIVED) json.remove(derived);
        }
        if (manual) json.put("manually_imported", true);
        writePif(cr, json);
    }

    static boolean flagOn(JSONObject json, String key, boolean def) {
        if (json == null || !json.has(key)) return def;
        Object raw = json.opt(key);
        if (raw instanceof Boolean) return (Boolean) raw;
        String value = json.optString(key, "");
        if (value.isEmpty()) return def;
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /**
     * @return null when the XML can be stored, otherwise a short reason
     */
    static String keyboxProblem(String xml) {
        if (xml == null) return "empty";
        if (!xml.contains("<Key algorithm=\"ecdsa\">")
                && !xml.contains("<Key algorithm=\"rsa\">")) {
            return "No ECDSA or RSA key block";
        }
        if (!xml.contains("<serial>") && !xml.contains("DeviceID")) {
            return "No serial or DeviceID";
        }
        KeyBoxManager manager = new KeyBoxManager();
        if (!manager.parseKeybox(xml) || !manager.hasKeyboxes()) {
            return "Not a valid keybox";
        }
        return null;
    }

    static String encodeKeybox(byte[] raw) {
        return Base64.encodeToString(raw, Base64.NO_WRAP);
    }

    static String decodeKeybox(String stored) {
        if (stored == null) return null;
        String trimmed = stored.trim();
        if (trimmed.startsWith("<")) return trimmed;
        try {
            byte[] decoded = Base64.decode(trimmed, Base64.DEFAULT);
            String xml = new String(decoded, StandardCharsets.UTF_8).trim();
            return xml.startsWith("<") ? xml : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static byte[] readUri(ContentResolver cr, Uri uri) throws IOException {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("unreadable");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > MAX_IMPORT_BYTES) throw new IOException("file too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    static Map<String, String> readTargetMap(String stored) {
        Map<String, String> out = new LinkedHashMap<>();
        if (TextUtils.isEmpty(stored)) return out;
        for (String raw : stored.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String mode = "";
            String pkg = line;
            char last = line.charAt(line.length() - 1);
            if (last == '?' || last == '!' || last == '-') {
                mode = String.valueOf(last);
                pkg = line.substring(0, line.length() - 1).trim();
            }
            if (!pkg.isEmpty()) out.put(pkg, mode);
        }
        return out;
    }

    static String writeTargetMap(Map<String, String> targets) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(entry.getKey());
            if (entry.getValue() != null) sb.append(entry.getValue());
        }
        return sb.toString();
    }

    static List<String> readCsv(String stored) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(stored)) return out;
        for (String part : stored.split(",")) {
            String pkg = part.trim();
            if (!pkg.isEmpty()) out.add(pkg);
        }
        return out;
    }

    static String writeCsv(Iterable<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(value);
        }
        return sb.toString();
    }
}
