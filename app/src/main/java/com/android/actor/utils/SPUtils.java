package com.android.actor.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.actor.ActApp;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;

public class SPUtils {
    public enum ModuleFile {
        proxy_chain,
        proxy_redsocks,
        config,
        action,
        script,
        wifi,
        lppi_module,
        mocked_device,
        sms_upload,
        proxy_socks,
        proxy_direct_domains,
        proxy_stats,
        ai_models,
        wired_network,
        export_ip,
        browser_accounts,
        app_accounts,
        proxy_blocked_status,
        temu_info,
        daily_info,
        fi,
    }

    public final static Object lock = new Object();

    public static void putString(ModuleFile file, String key, String value) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(key, value);
            editor.commit();
        }
    }

    public static String getString(ModuleFile file, String key, String defaultValue) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            return sp.getString(key, defaultValue);
        }
    }

    public static String getString(ModuleFile file, String key) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            return sp.getString(key, null);
        }
    }

    public static void putStringSet(ModuleFile file, String key, Set<String> value) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putStringSet(key, value);
            editor.commit();
        }
    }

    public static void putInt(ModuleFile file, String key, int value) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt(key, value);
            editor.commit();
        }
    }

    public static void putLong(ModuleFile file, String key, long value) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putLong(key, value);
            editor.commit();
        }
    }

    public static void putBoolean(ModuleFile file, String key, boolean value) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(key, value);
            editor.commit();
        }
    }

    public static void putStringMap(ModuleFile file, Map<String, String> map) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            for (String key : map.keySet()) {
                editor.putString(key, map.get(key));
            }
            editor.commit();
        }
    }

    public static int getInt(ModuleFile file, String key, int defaultValue) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            return sp.getInt(key, defaultValue);
        }
    }

    public static int getInt(ModuleFile file, String key) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            return sp.getInt(key, 0);
        }
    }

    public static long getLong(ModuleFile file, String key) {
        try {
            synchronized (lock) {
                SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
                return sp.getLong(key, 0);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean getBoolean(ModuleFile file, String key) {
        try {
            synchronized (lock) {
                SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
                return sp.getBoolean(key, false);
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, ?> getAll(ModuleFile file) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            return sp.getAll();
        }
    }


    public static void removeKey(ModuleFile file, String key) {
        synchronized (lock) {
            SharedPreferences sp = ActApp.getInstance().getSharedPreferences(file.name(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.remove(key);
            editor.commit();
        }
    }


    public static String getValue(ModuleFile file, String key, String defaultValue) {
        try {
            return getString(file, key);
        } catch (ClassCastException e1) {
            try {
                return String.valueOf(getInt(file, key));
            } catch (ClassCastException e2) {
                try {
                    return String.valueOf(getLong(file, key));
                } catch (ClassCastException e3) {
                    // handle other types if needed
                }
            }
        }
        return defaultValue; // return default value if all parsing attempts fail
    }

}