package com.android.actor.device;

import com.android.actor.utils.Callback;
import com.android.actor.utils.shell.Shell;

public class BatteryController implements Callback.C0 {

    private static final String CHARGE_SWITCH = "/sys/class/power_supply/battery/charging_enabled";
    private static final String CHARGE_ON = "1";
    private static final String CHARGE_OFF = "0";
    private static final int MAX_LEVEL = 70;
    private static final int MIN_LEVEL = 50;
    private final BatteryChangeReceiver mReceiver;

    public BatteryController(BatteryChangeReceiver receiver) {
        mReceiver = receiver;
    }

    public void start() {
        //mReceiver.addListener(this);
        //onResult(); // charge level is 0 at very beginning, I don't care, it will be corrected at next change.
    }

    @Override
    public void onResult() {
        int level = mReceiver.getBatteryPercentage();
        if (level < MIN_LEVEL) {
            startCharge();
        } else if (level > MAX_LEVEL) {
            stopCharge();
        }
    }

    private void startCharge() {
        setCharge(CHARGE_ON);
    }

    private void stopCharge() {
        setCharge(CHARGE_OFF);
    }

    private void setCharge(String value) {
        Shell.execRootCmdSilent("echo " + value + " > " + CHARGE_SWITCH);
    }
}
