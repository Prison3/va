package com.android.actor.device;

import android.content.ComponentName;
import android.content.pm.PackageManager;

import com.android.actor.ActApp;
import com.android.actor.LaunchActivity;
import com.android.actor.control.ActActivityManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;

import java.io.File;
import java.io.IOException;

public class ScrcpyDedicated {

    private static final File CONTROL_FILE = new File(NewStage.PARAMETERS_PATH + "/scrcpy_dedicated");
    private static final String TAG = ScrcpyDedicated.class.getSimpleName();
    public static final ScrcpyDedicated instance = new ScrcpyDedicated();

    private ScrcpyDedicated() {
    }

    public boolean isEnabled() {
        return CONTROL_FILE.exists();
    }

    public void onSwitch() {
        if (!isEnabled()) {
            onEnable();
        } else {
            onDisable();
        }
    }

    private void onEnable() {
        if (!CONTROL_FILE.exists()) {
            try {
                CONTROL_FILE.createNewFile();
            } catch (IOException e) {
                Logger.e(TAG, "Failed to create " + CONTROL_FILE, e);
            }
        }
        ActActivityManager.getInstance().forceStopPackage("com.google.android.as");
        ActActivityManager.getInstance().forceStopPackage("com.google.android.apps.nexuslauncher");
    }

    private void onDisable() {
        if (CONTROL_FILE.exists()) {
            Libsu.exec("rm -f " + CONTROL_FILE.getPath());
        }
        ActActivityManager.getInstance().forceStopPackage("com.google.android.apps.nexuslauncher");
    }
}
