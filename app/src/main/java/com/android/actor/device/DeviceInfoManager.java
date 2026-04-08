package com.android.actor.device;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;

import com.android.actor.ActAccessibility;
import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.shell.Shell;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class DeviceInfoManager {
    private final static String TAG = DeviceInfoManager.class.getSimpleName();
    private static DeviceInfoManager sInstance;
    private RuntimeHandler mHandler;
    private ActConnManager mConnManager;

    public boolean isAirplaneModeOn;
    public int width = 1080;
    public int height = 1920;
    public int statusBarHeight = 0;
    public int navigationBarHeight = 0;
    private final Thread mCollectorLoopThread = new Thread(() -> {
        Looper.prepare();
        Logger.d(TAG, "Collector looper prepare.");
        mHandler = new RuntimeHandler();
        Looper.loop();
    });

    private DeviceInfoManager() {
        Logger.d(TAG, "DeviceInfoManager init");
        // loop once
        mConnManager = new ActConnManager(ActApp.getInstance());
        mCollectorLoopThread.start();
        Resources res = ActApp.getInstance().getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        width = dm.widthPixels;
        height = dm.heightPixels; // height = screen height - navigationBarHeight
        statusBarHeight = getStatusBarHeight();
        navigationBarHeight = getNavigationBarHeight();
        Logger.i(TAG, "screen info width: " + width + " , height: " + height + ", statusBarHeight: " + statusBarHeight + ", navigationBarHeight: " + navigationBarHeight);
    }

    public int getStatusBarHeight() {
        Resources res = ActApp.getInstance().getResources();
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return res.getDimensionPixelSize(resourceId);
        }
        return 0;
    }


    public int getNavigationBarHeight() {
        Resources res = ActApp.getInstance().getResources();
        int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return res.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public synchronized static DeviceInfoManager getInstance() {
        if (sInstance == null) {
            sInstance = new DeviceInfoManager();
        }
        return sInstance;
    }

    public String getSerial() {
        return "XXX";
        //return optProp("ro.boot.serialno");
    }

    @SuppressLint("MissingPermission")
    public String optProp(String key) {
        String value = SystemProperties.get(key);

        // Serial can't be read by SystemProperties.get(), DetailCollector may collect delayed.
        // Return serial by system api, we have privilege.
        if (StringUtils.isEmpty(value) && ("ro.boot.serialno".equals(key) || "ro.serialno".equals(key))) {
            try {
                value = Build.getSerial();
            } catch (SecurityException e) {
            }
        }

        // default_system_properties don't contains ro.build.new_stage_image, read it from original.
        else if ("ro.build.new_stage_image".equals(key)) {
            value = SystemProperties.get(key);
        }
        return value == null ? "" : value;
    }

    public String optBootVersion() {
        try {
            File file = new File("/proc/new_stage_version");
            if (file.exists()) {
                return FileUtils.readFileToString(file, "UTF-8");
            }
        } catch (Exception e) {
            try {
                return Shell.execRootCmd("cat /proc/new_stage_version").get(0);
            } catch (Exception e1) {
                Logger.e(TAG, e.toString() + ", " + e1.toString(), e1);
            }
        }
        return null;
    }

    void collectRuntime() {
        if (ActAccessibility.getInstance() != null && ActAccessibility.isStart()) {
            Logger.d(TAG, "BatteryInfo: " + ActAccessibility.getInstance().mBatteryReceiver.getBatteryInfo());
            GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_BATTERY_CHANGE, ActAccessibility.getInstance().mBatteryReceiver.getBatteryMsg());
        } else {
            Logger.d(TAG, "BatteryInfo");
        }
    }

    public ActConnManager getConnManager() {
        return mConnManager;
    }

    @SuppressLint({"MissingPermission", "HardwareIds"})
    public String getPhoneNumber() {
        TelephonyManager tm = (TelephonyManager) ActApp.getInstance().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            String phoneNum = tm.getLine1Number();
            if (!StringUtils.isEmpty(phoneNum) && phoneNum.startsWith("+86")) {
                phoneNum = phoneNum.substring(3);
            }
            Logger.d(TAG,"getPhoneNumber " + phoneNum);
            return phoneNum;
        }
        return null;
    }

    public boolean hasSimCard() {
        TelephonyManager tm = (TelephonyManager) ActApp.getInstance().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            return tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT &&
                    tm.getSimState() != TelephonyManager.SIM_STATE_UNKNOWN;
        }
        return false;
    }
}
