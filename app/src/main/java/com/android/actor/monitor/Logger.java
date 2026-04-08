package com.android.actor.monitor;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Logger {
    public final static String APP_TAG = "Rocket_Actor";

    public interface Listener {
        void onLog(Keep keep);
    }

    public static class Keep {
        final static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");

        long time;
        char level;
        String tag;
        String msg;

        Keep(long time, char level, String tag, String msg) {
            this.time = time;
            this.level = level;
            this.tag = tag;
            this.msg = msg;
        }

        @NonNull
        @Override
        public String toString() {
            return dateFormat.format(new Date(time)) + " [" + level + "/" + tag + "] " + msg;
        }
    }

    private static Map<String, List<Listener>> sLogListeners = new HashMap<>();
    private static LinkedList<Keep> sLogsToKeep = new LinkedList<>();
    private static final String[] TAGS_TO_KEEP = new String[] {
            "SelfUpdater", "HttpDownloader"
    };
    private static final int MAX_KEEP = 100;

    public static void d(String clazz, String msg) {
        Log.d(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('d', clazz, msg);
    }

    public static void e(String clazz, String msg) {
        Log.e(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('e', clazz, msg);
    }

    public static void e(String clazz, String msg, Throwable e) {
        Log.e(APP_TAG, "[" + clazz + "] " + msg, e);
        notifyListeners('e', clazz, msg + ", " + e);
    }

    public static void w(String clazz, String msg) {
        Log.w(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('w', clazz, msg);
    }

    public static void w(String clazz, String msg, Throwable e) {
        Log.w(APP_TAG, "[" + clazz + "] " + msg, e);
        notifyListeners('w', clazz, msg + ", " + e);
    }

    public static void i(String clazz, String msg) {
        Log.i(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('i', clazz, msg);
    }

    public static void v(String clazz, String msg) {
        Log.v(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('v', clazz, msg);
    }

    public static void d(String clazz, Object msg) {
        Log.d(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('d', clazz, msg.toString());
    }

    public static void e(String clazz, Object msg) {
        Log.e(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('e', clazz, msg.toString());
    }

    public static void w(String clazz, Object msg) {
        Log.w(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('w', clazz, msg.toString());
    }

    public static void i(String clazz, Object msg) {
        Log.i(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('i', clazz, msg.toString());
    }

    public static void v(String clazz, Object msg) {
        Log.v(APP_TAG, "[" + clazz + "] " + msg);
        notifyListeners('v', clazz, msg.toString());
    }

    private static void notifyListeners(char level, String tag, String msg) {
        Keep keep = new Keep(System.currentTimeMillis(), level, tag, msg);
        List<Listener> listeners = null;
        synchronized (Logger.class) {
            if (ArrayUtils.contains(TAGS_TO_KEEP, tag)) {
                sLogsToKeep.add(keep);
                if (sLogsToKeep.size() > MAX_KEEP) {
                    sLogsToKeep.removeFirst();
                }
            }
            List<Listener> _listeners = sLogListeners.get(tag);
            if (_listeners != null) {
                listeners = new ArrayList<>(_listeners);
            }
        }
        if (listeners != null) {
            for (Listener listener : listeners) {
                try {
                    listener.onLog(keep);
                } catch (Throwable e) {
                    Log.e(APP_TAG, "[" + Logger.class.getSimpleName() + "] notify listener exception.", e);
                }
            }
        }
    }

    public static synchronized void addLogListener(Listener listener, String... tags) {
        for (String tag : tags) {
            List<Listener> listeners = sLogListeners.get(tag);
            if (listeners == null) {
                listeners = new ArrayList<>();
                sLogListeners.put(tag, listeners);
            }
            listeners.add(listener);
        }
        for (Keep keep : sLogsToKeep) {
            if (ArrayUtils.contains(tags, keep.tag)) {
                listener.onLog(keep);
            }
        }
    }

    public static synchronized void removeLogListener(Listener listener, String... tags) {
        for (String tag : tags) {
            sLogListeners.get(tag).remove(listener);
        }
    }

    public static void stackTrace(String clazz, Throwable e) {
        if (e instanceof InvocationTargetException) {
            e = ((InvocationTargetException) e).getTargetException();
        }
        Logger.e(clazz, Log.getStackTraceString(e));
    }

    public static void intent(String clazz, Intent intent) {
        intent(clazz, intent, 1);
    }

    public static void intent(String clazz, Intent intent, int deep) {
        String space = StringUtils.repeat(' ', deep);
        if (intent == null) {
            Logger.d(clazz, space + "intent is null.");
            return;
        }
        Logger.d(clazz, space + "intent action: " + intent.getAction());
        Logger.d(clazz, space + "intent package: " + intent.getPackage());
        Logger.d(clazz, space + "intent component: " + (intent.getComponent() != null ? intent.getComponent().toShortString() : ""));
        Logger.d(clazz, space + "intent type: " + intent.getType());
        Logger.d(clazz, space + "intent flag: 0x" + Integer.toHexString(intent.getFlags()));
        Logger.d(clazz, space + "intent data: " + intent.getData());
        if (intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                Object value = intent.getExtras().get(key);
                Logger.d(clazz, space + "intent extra: " + key + " -> " + value
                        + " (" + (value != null ? value.getClass().getName() : "") + ")");
                if (value != null && value.getClass() == Intent.class) {
                    Logger.intent(clazz, (Intent) value, deep + 1);
                }
            }
        }
    }

    public static void bundle(String clazz, Bundle bundle) {
        if (bundle == null) {
            Logger.d(clazz, " bundle is null.");
            return;
        }
        for (String key : bundle.keySet()) {
            Logger.d(clazz, " bundle: " + key + " -> " + bundle.get(key));
        }
    }

    public static void clsFieldValues(String clazz, Object instance) {
        try {
            Logger.d(clazz, "clsFieldValues " + instance);
            List<Field> fields = FieldUtils.getAllFieldsList(instance.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Logger.d(clazz, "  " + field.getName() + ": " + field.get(instance));
            }
        } catch (Throwable e) {
            Logger.e(clazz, "clsFieldValues", e);
        }
    }
}
