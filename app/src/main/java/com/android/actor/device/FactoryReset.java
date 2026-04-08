package com.android.actor.device;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;

public class FactoryReset {

    private static final String TAG = FactoryReset.class.getSimpleName();

    public static void execFromConfig(String str) {
        if (str == null) {
            return;
        }
        if (DeviceNumber.isDeviceInArray(str)) {
            Logger.w(TAG, "Device match " + str + ", do factory reset.");
            // https://stackoverflow.com/questions/14685721/how-can-i-do-factory-reset-using-adb-in-android
            Libsu.exec("am broadcast -p \"android\" --receiver-foreground -a android.intent.action.FACTORY_RESET");
        }
    }
}
