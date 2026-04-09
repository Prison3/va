package com.android.actor.device;

import android.content.Context;

import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.topjohnwu.superuser.Shell;

import org.apache.commons.lang3.StringUtils;

public class DeviceNumber {

    private static final String TAG = DeviceNumber.class.getSimpleName();
    private static final String PATH = "/data/new_stage/number";
    /** 与 {@link SPUtils.ModuleFile#config} 中设备编号读写键一致 */
    private static final String SP_KEY_SERIAL = "Serial";
    private static String sNumber;

    static {
        Shell.su("chmod 644 " + PATH + "; chown system:system " + PATH).exec();
    }

    public static String get() {
        if (StringUtils.isEmpty(sNumber)) {
            sNumber = read();
        }
        return sNumber;
    }

    public static boolean has() {
        return !StringUtils.isEmpty(get());
    }

    /**
     * 界面展示用：SP 中未配置（{@link #get()} 为空）时显示英文占位；业务逻辑仍以 {@link #get()} 为准。
     */
    public static String getDisplay() {
        String n = get();
        if (!StringUtils.isEmpty(n)) {
            return n;
        }
        Context ctx = ActApp.getInstance();
        if (ctx != null) {
            return ctx.getString(R.string.device_number_not_configured);
        }
        return "";
    }

    public static String set(String number) {
        try {
            number = number.toUpperCase();
            if (!validate(number)) {
                return "Invalid number format.";
            }
            SPUtils.putString(SPUtils.ModuleFile.config, SP_KEY_SERIAL, number);
            sNumber = null;
            if (!number.equals(read())) {
                return "Write number failure.";
            }
            sNumber = number;
            return null;
        } catch (Throwable e) {
            Logger.e(TAG, "Set number exception.", e);
            return e.toString();
        }
    }

    public static boolean validate(String number) {
        return calcWiredIP(number) != null;
    }

    public static String calcWiredIP() {
        String number = get();
        return calcWiredIP(number);
    }

    private static String calcWiredIP(String number) {
        if (StringUtils.isEmpty(number)) {
            return null;
        }
        char c = number.charAt(0);
        if (c < 'A' || c > 'Y') {
            Logger.e(TAG, "invalid number " + number);
            return null;
        }
        int n;
        try {
            n = Integer.parseInt(number.substring(1));
        } catch (NumberFormatException e) {
            Logger.e(TAG, "invalid number " + number);
            return null;
        }
        if (n < 1 || n > 1000) {
            Logger.e(TAG, "invalid number " + number);
            return null;
        }
        int second = (n - 1) / 100 + (c - 'A') * 10;
        int third = (n - 1) % 100 + 1;
        return "10." + second + '.' + third + ".2";
    }

    public static String calcGateway() {
        String ip = calcWiredIP();
        if (StringUtils.isEmpty(ip)) {
            return null;
        }
        return ip.substring(0, ip.length() - 1) + '1';
    }

    private static String read() {
        try {
            String str = SPUtils.getString(SPUtils.ModuleFile.config, SP_KEY_SERIAL, "");
            if (str != null) {
                str = StringUtils.strip(str, "\r\n\t ");
            }
            Logger.i(TAG, "Number " + str);
            return str != null ? str : "";
        } catch (Throwable e) {
            Logger.w(TAG, "Device number read failed", e);
            return "";
        }
    }

    public static void setAsSerial() {
        String number = get();
        if (!StringUtils.isEmpty(number)) {
            String path = "/config/usb_gadget/g1/strings/0x409/serialnumber";
            try {
                if (!number.equals(Shell.su("cat " + path).exec().getOut().get(0))) {
                    Logger.i(TAG, "Set serial " + number);
                    Shell.Result result = Shell.su("echo -n '" + number + "' > " + path
                            + " && setprop service.adb.tcp.port 15555 && killall adbd").exec();
                    if (result.getCode() != 0) {
                        Logger.e(TAG, "Can't set number as serial, " + result.getOut() + ", " + result.getErr());
                    }
                    GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIRED_OR_WIFI, null);
                }
            } catch (Throwable e) {
                Logger.e(TAG, "Exception on set as serial.", e);
            }
        }
    }

    // L001,L003-L005,A011
    public static boolean isDeviceInArray(String numberStr) {
        if (StringUtils.isEmpty(numberStr)) {
            return false;
        }
        String number = get();
        if (StringUtils.isEmpty(number)) {
            return false;
        }
        try {
            int num = Integer.valueOf(number.substring(1));
            char alpha = number.charAt(0);
            for (String str : numberStr.replace(" ", "").split(",")) {
                if (number.equals(str)) {
                    return true;
                }
                if (!str.contains("-")) {
                    continue;
                }
                String[] parts = StringUtils.split(str, '-');
                if (alpha != parts[0].charAt(0) || alpha != parts[1].charAt(0)) {
                    continue;
                }
                int min = Integer.valueOf(parts[0].substring(1));
                int max = Integer.valueOf(parts[1].substring(1));
                if (num >= min && num <= max) {
                    return true;
                }
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Error parse " + number + " in " + numberStr, e);
        }
        return false;
    }
}
