package com.android.actor.utils.notification;

import java.util.HashMap;

public class GlobalNotification {
    private final static HashMap<Integer, ActObservable> NOTIFICATION_MAP = new HashMap<>();

    public final static int NOTIFY_GRPC_CONNECT = 0;
    public final static int NOTIFY_GRPC_MSG = 1;
    public final static int NOTIFY_GRPC_SET_ADDRESS = 2;
    public final static int NOTIFY_PROXY_CHANGE = 20;
    public final static int NOTIFY_TASK_STATUS = 30;
    public final static int NOTIFY_WIFI_CHANGE = 40;
    public final static int NOTIFY_WIRED_OR_WIFI = 41;
    public final static int NOTIFY_BATTERY_CHANGE = 60;

    public static void addObserver(int code, ActObserver observer) {
        if (!NOTIFICATION_MAP.containsKey(code)) {
            ActObservable observable = new ActObservable(code, observer);
            NOTIFICATION_MAP.put(code, observable);
        }
    }

    public static void removeObserver(int code) {
        NOTIFICATION_MAP.remove(code);
    }

    public static void notifyObserver(int code) {
        notifyObserver(code, null);
    }

    public static void notifyObserver(int code, Object arg) {
        ActObservable observable = NOTIFICATION_MAP.get(code);
        if (observable != null) {
            observable.notifyObservers(arg);
        }
    }
}
