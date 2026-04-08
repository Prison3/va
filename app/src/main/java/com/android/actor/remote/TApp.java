package com.android.actor.remote;

import android.annotation.SuppressLint;

public class TApp {
    public static String sHost = "www.temu.com";

    @SuppressLint("SdCardPath")
    public static String path = String.format("/sdcard/Android/data/org.chromium.chrome/html/%s/", TApp.sHost);

    public static String sRegisterNewScript;
    public static int sRegisterNewTimeout;
    public static String sSwipeSyncScript;
    public static int sSwipeSyncTimeout;

    public static String sTaskId;

    public static String sGetDetailUrl;
    public static String sShortGetDetailUrl;

    public static String sGetDetailScript;
    public static int sGetDetailTimeout;

}
