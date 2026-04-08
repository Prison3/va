package com.android.actor.device;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BatteryChangeReceiver extends BroadcastReceiver {
    private static final String TAG = BatteryChangeReceiver.class.getSimpleName();
    private final HashMap<String, String> mBatteryInfo = new HashMap<>();
    private final static String BATTERY_STATUS = "batteryStatus"; // 电池状态
    private final static String BATTERY_HEALTH = "batteryHealth"; // 电池的健康程度
    private final static String BATTERY_PRESENT = "batteryPresent"; // 电池是否存在
    private final static String BATTERY_PLUGGED = "batteryPlugged"; // 电池充电方式
    private final static String BATTERY_VOLTAGE = "batteryVoltage"; // 电池电压
    private final static String BATTERY_TECHNOLOGY = "batteryTechnology"; // 电池的技术
    private final static String BATTERY_CURRENT = "batteryCurrent"; // 电池当前电量
    private final static String BATTERY_TOTAL = "batteryTotal"; // 电池总电量
    private final static String BATTERY_TEMPERATURE = "batteryTemperature"; // 电池温度
    private String mBatteryMsg;

    private List<Callback.C0> mListeners = new ArrayList<>();
    private int mBatteryPercentage;
    private int mBatteryTemperature;

    @SuppressLint("DefaultLocale")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BATTERY_LOW)) {
            Logger.w(TAG, "System battery low.");
            GRPCManager.getInstance().sendNotification("warning", "battery low.");
            return;
        }

        //获取电池状态
        int batteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        switch (batteryStatus) {
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
                mBatteryInfo.put(BATTERY_STATUS, "BATTERY_STATUS_UNKNOWN");
                break;
            case BatteryManager.BATTERY_STATUS_CHARGING:
                mBatteryInfo.put(BATTERY_STATUS, "BATTERY_STATUS_CHARGING");
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                mBatteryInfo.put(BATTERY_STATUS, "BATTERY_STATUS_DISCHARGING");
                break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                mBatteryInfo.put(BATTERY_STATUS, "BATTERY_STATUS_NOT_CHARGING");
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                mBatteryInfo.put(BATTERY_STATUS, "BATTERY_STATUS_FULL");
                break;

            default:
                break;
        }

        //获取电池的健康程度
        int batteryHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        switch (batteryHealth) {
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
                mBatteryInfo.put(BATTERY_HEALTH, "BATTERY_HEALTH_UNKNOWN");
                break;
            case BatteryManager.BATTERY_HEALTH_GOOD:
                mBatteryInfo.put(BATTERY_HEALTH, "BATTERY_HEALTH_GOOD");
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                mBatteryInfo.put(BATTERY_HEALTH, "BATTERY_HEALTH_OVERHEAT");
                break;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                mBatteryInfo.put(BATTERY_HEALTH, "BATTERY_HEALTH_DEAD");
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                mBatteryInfo.put(BATTERY_HEALTH, "BATTERY_HEALTH_OVER_VOLTAGE");
                break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                mBatteryInfo.put(BATTERY_HEALTH, "BATTERY_HEALTH_UNSPECIFIED_FAILURE");
                break;
            case BatteryManager.BATTERY_HEALTH_COLD:
                mBatteryInfo.put(BATTERY_HEALTH, "BATTERY_HEALTH_COLD");
                break;
            default:
                break;
        }

        //获取电池是否存在
        boolean batteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false);
        mBatteryInfo.put(BATTERY_PRESENT, String.valueOf(batteryPresent));

        //获取电池的当前电量和总电量
        int batteryCurrent = intent.getExtras().getInt("level");// 获得当前电量
        int batteryTotal = intent.getExtras().getInt("scale");// 获得总电量
        mBatteryPercentage = batteryCurrent * 100 / batteryTotal;
        mBatteryInfo.put(BATTERY_CURRENT, String.valueOf(batteryCurrent));
        mBatteryInfo.put(BATTERY_TOTAL, String.valueOf(batteryTotal));

        //获取电池充电方式
        int batteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        switch (batteryPlugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                mBatteryInfo.put(BATTERY_PLUGGED, "BATTERY_PLUGGED_AC");//交流电
                break;
            case BatteryManager.BATTERY_PLUGGED_USB:
                mBatteryInfo.put(BATTERY_PLUGGED, "BATTERY_PLUGGED_USB");//USB充电
                break;
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                mBatteryInfo.put(BATTERY_PLUGGED, "BATTERY_PLUGGED_WIRELESS");//无线充电
                break;
            default:
                break;
        }

        //获取电池的电压
        int batteryVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        mBatteryInfo.put(BATTERY_VOLTAGE, String.valueOf(batteryVoltage));

        //获取电池的温度
        mBatteryTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        mBatteryInfo.put(BATTERY_TEMPERATURE, String.valueOf(mBatteryTemperature));

        //获取电池的技术
        String batteryTechnology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        mBatteryInfo.put(BATTERY_TECHNOLOGY, batteryTechnology);

        mBatteryMsg = String.format("%s  %s, %.1f°C",
                mBatteryInfo.get(BATTERY_STATUS).replace("STATUS_", "").toLowerCase(),
                mBatteryPercentage + "%",
                (float) mBatteryTemperature / 10);

        mListeners.forEach(Callback.C0::onResult);
    }

    public HashMap<String, String> getBatteryInfo() {
        return mBatteryInfo;
    }

    public String getBatteryMsg(){
        return mBatteryMsg;
    }

    public int getBatteryPercentage() {
        return mBatteryPercentage;
    }

    public int getBatteryTemperature() {
        return mBatteryTemperature;
    }

    public void addListener(Callback.C0 listener) {
        mListeners.add(listener);
    }
}
