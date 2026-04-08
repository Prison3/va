package com.android.va.proxy;

import com.android.va.runtime.VHost;

import java.util.Locale;

import com.android.va.runtime.VRuntime;

public class ProxyManifest {
    public static final int FREE_COUNT = 50;

    public static boolean isProxy(String msg) {
        return getBindProvider().equals(msg) || msg.contains("proxy_content_provider_");
    }

    public static String getBindProvider() {
        return VHost.getPackageName() + ".va.SystemCallProvider";
    }

    public static String getProxyAuthorities(int index) {
        return String.format(Locale.CHINA, "%s.proxy_content_provider_%d", VHost.getPackageName(), index);
    }

    public static String getProxyPendingActivity(int index) {
        return String.format(Locale.CHINA, "com.android.va.proxy.ProxyPendingActivity$P%d", index);
    }

    public static String getProxyActivity(int index) {
        return String.format(Locale.CHINA, "com.android.va.proxy.ProxyActivity$P%d", index);
    }

    public static String TransparentProxyActivity(int index) {
        return String.format(Locale.CHINA, "com.android.va.proxy.TransparentProxyActivity$P%d", index);
    }

    public static String getProxyService(int index) {
        return String.format(Locale.CHINA, "com.android.va.proxy.ProxyService$P%d", index);
    }

    public static String getProxyJobService(int index) {
        return String.format(Locale.CHINA, "com.android.va.proxy.ProxyJobService$P%d", index);
    }

    public static String getProxyFileProvider() {
        return VHost.getPackageName() + ".va.FileProvider";
    }

    public static String getProxyReceiver() {
        return VHost.getPackageName() + ".stub_receiver";
    }

    public static String getProcessName(int bPid) {
        return VHost.getPackageName() + ":p" + bPid;
    }
}
