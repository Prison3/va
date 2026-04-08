package com.android.actor;

import android.os.Process;
import android.widget.Toast;

import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.device.NewStage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.shell.Shell;

import java.io.File;
import java.io.IOException;

public class SettingUp {

    public static final SettingUp instance = new SettingUp();
    private static final int CHECK_INTERVAL = 10 * 1000;
    private static final String TAG = SettingUp.class.getSimpleName();
    private File NO_SETUP_FILE = new File("/data/new_stage/no_setup");

    public boolean isSettingUp() {
        if (NO_SETUP_FILE.exists()) {
            return false;
        }
        return getSettingUpPhase() > 0;
    }

    private int getSettingUpPhase() {
        if (com.topjohnwu.superuser.Shell.isAppGrantedRoot()) {

        }
        if (!NewStage.instance().isReady()) {
            return 1;
        }
        if (Libsu.exec("settings get global heads_up_notifications_enabled").getOut().get(0).equals("1")) {
            return 2;
        }
        if (!ActPackageManager.getInstance().isAppInstalled(RocketComponent.PKG_CHROMIUM)) {
            return 3;
        }
        Libsu.exec("settings put global package_verifier_user_consent -1");
        return 0;
    }

    public void enterCheckLoop() {
        new Thread(() -> {
            try {
                Shell.execRootCmd("svc bluetooth disable; " +
                        "svc nfc disable; " +
                        "svc data disable; " +
                        "settings put system screen_brightness 60; " +
                        "svc power stayon true; " +
                        "settings put global wifi_scan_always_enabled 0; " +
                        "settings put global wifi_wakeup_enabled 0; " +
                        "settings put global wifi_networks_available_notification_on 0; " +
                        "settings put global network_recommendations_enabled 0; " +
                        "settings delete global network_recommendations_package; " +
                        "settings put global captive_portal_mode 0; " +
                        "settings put global captive_portal_detection_enabled 0; " +
                        "input keyevent 164; " +
                        "settings put global mode_ringer 0; " +
                        "settings put global zen_mode_ringer_level 0; " +
                        "settings put secure manual_ringer_toggle_count 1; " +
                        "settings put system volume_music_speaker 0; " +
                        "settings put system time_12_24 24; " +
                        "settings put system accelerometer_rotation 0; " +
                        "settings put system screen_off_timeout 120000; " +
                        "settings put secure install_non_market_apps 1; " +
                        "locksettings set-disabled true; " +
                        "settings put global heads_up_notifications_enabled 0; "
                );
            } catch (IOException e) {
                Logger.e(TAG, "Error to execute setting up cmd.", e);
            }
        }).start();

        ActApp.getMainHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                check();
                ActApp.getMainHandler().postDelayed(this, CHECK_INTERVAL);
            }
        }, CHECK_INTERVAL);
    }

    private void check() {
        Logger.d(TAG, "Check setting up.");
        Toast.makeText(ActApp.getInstance(), "Setting up...", Toast.LENGTH_LONG).show();
        if (!isSettingUp()) {
            Logger.i(TAG, "Setup complete, kill actor.");
            Process.killProcess(Process.myPid());
        } else {
            Logger.v(TAG, "Setting up phase " + getSettingUpPhase());
        }
    }
}
