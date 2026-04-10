package com.android.va.hook.content;

import android.os.Bundle;
import android.util.Log;

import com.android.va.utils.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 在 {@code settings} ContentProvider 的 {@code call} 返回后，按「方法名 + 设置项」覆写 Bundle 中的 {@code value}，
 * 用于虚拟环境下的调试或规避部分检测（由 {@link SystemProviderStub} 仅在 authority 为 {@code settings} 时调用）。
 */
public final class SettingsInterception {
    public static final String TAG = SettingsInterception.class.getSimpleName();
    /** SettingsProvider 返回的键名（见 {@code android.provider.Settings.NameValueTable#VALUE}） */
    private static final String BUNDLE_VALUE_KEY = "value";

    private static final String M_GET_GLOBAL = "GET_global";
    private static final String M_GET_SECURE = "GET_secure";
    private static final String M_GET_SYSTEM = "GET_system";

    /** 外层 key 为 {@code call} 的 method（如 {@code GET_secure}），内层为设置项名 */
    private static final Map<String, Map<String, String>> OVERRIDES;

    static {
        Map<String, Map<String, String>> root = new HashMap<>();
        put(root, M_GET_GLOBAL, "adb_enabled", "0");
        put(root, M_GET_GLOBAL, "development_settings_enabled", "0");
        put(root, M_GET_SECURE, "accessibility_enabled", "0");
        put(root, M_GET_SECURE, "airplane_mode_on", "0");
        put(root, M_GET_SECURE, "android_id", "0000000000000000");
        put(root, M_GET_SECURE, "location_mode", "3");
        put(root, M_GET_SYSTEM, "screen_brightness", "89");
        for (Map.Entry<String, Map<String, String>> e : root.entrySet()) {
            e.setValue(Collections.unmodifiableMap(e.getValue()));
        }
        OVERRIDES = Collections.unmodifiableMap(root);
    }

    private static void put(Map<String, Map<String, String>> root, String method, String settingKey, String value) {
        root.computeIfAbsent(method, k -> new HashMap<>()).put(settingKey, value);
    }

    private SettingsInterception() {
    }

    /**
     * 解析 {@code IContentProvider#call(AttributionSource, authority, method, arg, extras)} 五参布局；
     * 若命中表内项则覆写返回 Bundle 的 {@code value}。
     */
    public static void intercept(Object[] args, Bundle result) {
        if (result == null) {
            return;
        }
        String authority = null;
        String method = null;
        String arg = null;
        if (args != null && args.length >= 5) {
            if (args[1] instanceof String) {
                authority = (String) args[1];
            }
            if (args[2] instanceof String) {
                method = (String) args[2];
            }
            if (args[3] instanceof String) {
                arg = (String) args[3];
            }
        }
        if (method == null || arg == null) {
            return;
        }
        Map<String, String> byKey = OVERRIDES.get(method);
        String value = byKey != null ? byKey.get(arg) : null;
        if (value != null) {
            String original = result.getString(BUNDLE_VALUE_KEY);
            result.putString(BUNDLE_VALUE_KEY, value);
            Logger.d(TAG, "authority=" + authority + ", method=" + method + ", arg=" + arg + " from= " + original + " to= " + value);
        }
        //if (Log.isLoggable(TAG, Log.DEBUG)) {
        //ogger.d(TAG, "authority=" + authority + ", method=" + method + ", arg=" + arg + " result=" + result.getString(BUNDLE_VALUE_KEY));
        //}
    }

}
