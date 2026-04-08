package com.android.actor.control;

import android.content.Intent;
import com.android.actor.BuildConfig;
import com.android.actor.ActApp;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.grpc.ActorAdapter;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.TimeFormatUtils;
import com.android.actor.utils.shell.Root;
import com.android.actor.utils.shell.Shell;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class RocketComponent {
    private final static String TAG = RocketComponent.class.getSimpleName();
    public final static String PKG_ALPHA = BuildConfig.APPLICATION_ID;
    public final static String PKG_NEW_STAGE = "com.android.newstage";
    public final static String PKG_MAGISK = "com.snda.wifilocating";
    public final static String PKG_TWEAKS_FRAMEWORK = "com.android.internal.aidl";
    public final static String PKG_TWEAKS_RELEASE_PKG = "com.android.internal.dex";
    public final static String PKG_TWEBVIEW = "com.android.twebview";
    public final static String PKG_WEBVIEW = "com.android.webview";
    public final static String PKG_GOOGLE_WEBVIEW = "com.google.android.webview";
    public final static String PKG_CHROMIUM = "org.chromium.chrome";
    public final static String PKG_DS = "com.android.ds";

    public static final String GMS = "com.google.android.gms";
    public static final String GSF = "com.google.android.gsf";
    public static final String GOOGLE = "com.google.android.googlequicksearchbox";
    public static final String GOOGLE_PLAY = "com.android.vending";
    public static final String CHROME = "com.android.chrome";

    public final static String PKG_JUST_TRUST_ME = "just.trust.me";

    public final static String SERVICE_LPPI_AIDL_SERVICE = "com.android.internal.aidl.service";

    public static String[] getPkgList() {
        return new String[]{
                PKG_ALPHA,
                PKG_MAGISK,
                PKG_NEW_STAGE,
                PKG_TWEAKS_FRAMEWORK,
                PKG_TWEAKS_RELEASE_PKG,
                PKG_JUST_TRUST_ME,
        };
    }

    public static boolean isRocketComponent(String pkgName) {
        for (String pkg : getPkgList()) {
            if (pkg.equals(pkgName)) {
                return true;
            }
        }
        return false;
    }

    public static String checkActor(Intent intent) {
        String reason = "\n";
        // 1. magisk, default check
        if (!ActPackageManager.getInstance().isAppInstalled(RocketComponent.PKG_MAGISK)) {
            reason += "--> magisk is not install;\n";
        }
        if (!Root.acquireRoot()) {
            reason += "--> agent not get root;\n";
        }
        // 2. lppi, default ch
        if (!ActPackageManager.getInstance().isAppInstalled(RocketComponent.PKG_TWEAKS_FRAMEWORK)) {
            reason += "--> not install Waghal_AIDL;\n";
        }
        try {
            if (!Shell.execRootCmd("cat /data/user_de/0/com.moji.mjweather/conf/enabled_modules.list").contains(RocketComponent.PKG_TWEAKS_FRAMEWORK)) {
                reason += "--> AIDL_framework(" + ") not enable in LppiInstaller;\n";
            }
        } catch (IOException e) {
            Logger.e(TAG, e.toString(), e);
            reason += "--> check aidl enable failed: " + e.toString() + ";\n";
        }
        if (!ActPackageManager.getInstance().isAppInstalled(RocketComponent.PKG_NEW_STAGE)) {
            reason += "--> not install new_stage apk;\n";
        }
        // 3. new stage and rom, --es new_stage_version 1.4 --es boot_version 1.2 --es rom_version 1.5
        String newStageVersion = intent.getStringExtra("new_stage_version");
        if (newStageVersion == null || newStageVersion.isEmpty()) {
            newStageVersion = "1.4";
        }
        String currentNewStageVersion = ActPackageManager.getInstance().getPackageInfo(RocketComponent.PKG_NEW_STAGE).versionName;
        if (!newStageVersion.equals(currentNewStageVersion)) {
            reason += "--> new_stage_version(" + newStageVersion + ") not match current(" + currentNewStageVersion + ");\n";
        }

        String bootVersion = intent.getStringExtra("boot_version");
        if (bootVersion == null || bootVersion.isEmpty()) {
            bootVersion = "1.2";
        }
        String currentBootVersion = DeviceInfoManager.getInstance().optBootVersion();
        if (!bootVersion.equals(currentBootVersion)) {
            reason += "--> boot_version(" + currentBootVersion + ") not match current(" + currentBootVersion + ");\n";
        }

        String romVersion = intent.getStringExtra("rom_version");
        if (romVersion == null || romVersion.isEmpty()) {
            romVersion = "1.5";
        }
        String currentRomVersion = DeviceInfoManager.getInstance().optProp("ro.build.new_stage_image");
        if (!romVersion.equals(currentRomVersion)) {
            reason += "--> rom_version(" + romVersion + ") not match current(" + currentRomVersion + ");\n";
        }

        // 4. system time zone, lang, default check
        String timeZone = TimeZone.getDefault().getDisplayName();
        if (!timeZone.equals("中国标准时间")) {
            reason += "--> timeZone(中国标准时间) not match current(" + timeZone + ");\n";
        }

        Locale locale = ActApp.getInstance().getResources().getConfiguration().locale;
        String language = locale.getLanguage() + "_" + locale.getCountry();
        if (!language.equals("zh_CN")) {
            reason += "--> language(zh) not match current(" + language + ");\n";
        }

        Calendar calendar = Calendar.getInstance();
        if (calendar.get(Calendar.YEAR) < 2021) {
            reason += "--> calendar_year(>=2021) not match current(" + TimeFormatUtils.getDateString(calendar.getTime()) + ");\n";
        }
        Logger.e(TAG, calendar.toString());

        // 5. wifi or grpc, --es wifi_ssid CMCC --es grpc_addr 192.168.2.1:8100
        String wifi = intent.getStringExtra("ssid");
        /*if (wifi != null && !wifi.isEmpty()) {
            String currentWifi = DeviceInfoManager.getInstance().getConnManager().getSSID();
            if (!wifi.equals(currentWifi)) {
                reason += "--> wifi_ssid(" + wifi + ") not match current(" + currentWifi + ");\n";
            }
        }*/

        String grpc = intent.getStringExtra("address");
        if (grpc != null && !grpc.isEmpty()) {
            String currentGrpc = ActorAdapter.DEFAULT_RETRY_ADDRESS;
            if (!grpc.equals(currentGrpc)) {
                reason += "--> grpc_addr(" + grpc + ") not match current(" + currentGrpc + ");\n";
            }
        }
        // return
        return reason.substring(0, reason.length() - 1);
    }
}