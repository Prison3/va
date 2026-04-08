package com.android.actor;

import static com.android.actor.device.ActConnManager.USE_4G;
import static com.android.actor.device.ActConnManager.USE_WIFI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.android.actor.control.RocketComponent;
import com.android.actor.device.ActWifiManager;
import com.android.actor.device.BrightnessController;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.LppiManagerConnection;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.ResUtils;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.sms.SmsUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ActBroadcastReceiver extends BroadcastReceiver {


    private final static String TAG = ActBroadcastReceiver.class.getSimpleName();
    public final static String SET_GRPC = "com.android.actor.SET_GRPC";
    private final static String SET_WIFI = "com.android.actor.SET_WIFI";
    private final static String SWITCH_4G_NET = "com.android.actor.SWITCH_4G_NET";
    private final static String SWITCH_WIFI_NET = "com.android.actor.SWITCH_WIFI_NET";
    private final static String LPPI_SWITCH = "com.android.actor.LPPI_SWITCH";
    private final static String ACTOR_CHECK = "com.android.actor.ACTOR_CHECK";
    private final static String SET_MARK = "com.android.actor.SET_MARK";
    private final static String POWER_BUTTON = "com.android.actor.POWER_BUTTON";
    private final static String RESET_MOCK_COUNT = "com.android.actor.RESET_MOCK_COUNT";
    private final static String UPDATE_MEDIA = "com.android.actor.UPDATE_MEDIA";

    public void register(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SET_GRPC);
        filter.addAction(SET_WIFI);
        filter.addAction(SWITCH_4G_NET);
        filter.addAction(SWITCH_WIFI_NET);
        filter.addAction(LPPI_SWITCH);
        filter.addAction(ACTOR_CHECK);
        filter.addAction(SET_MARK);
        filter.addAction(POWER_BUTTON);
        filter.addAction(RESET_MOCK_COUNT);
        filter.addAction(UPDATE_MEDIA);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);

        filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        context.registerReceiver(this, filter);
        Logger.d(TAG, "register ActorReceiver");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d(TAG, "ActBroadcastReceiver onReceived " + intent.getAction());

        switch (intent.getAction()) {
            case SET_GRPC:
                onSettingGRPC(intent);
                break;
            case SET_WIFI:
                onSettingWifi(intent);
                break;
            case SWITCH_4G_NET:
                DeviceInfoManager.getInstance().getConnManager().enableWired(USE_4G);
                break;
            case SWITCH_WIFI_NET:
                DeviceInfoManager.getInstance().getConnManager().enableWired(USE_WIFI);
                break;
            case LPPI_SWITCH:
                onLppiSwitch(intent);
                break;
            case ACTOR_CHECK:
                String result = RocketComponent.checkActor(intent);
                this.setResult(result.isEmpty() ? 1 : 0, result.isEmpty() ? result : "check below:" + result, null);
                break;
            case SET_MARK:
                this.onSetMark(intent);
                break;
            case POWER_BUTTON:
                onPowerButtonPressed();
                break;
            case RESET_MOCK_COUNT:
                SPUtils.putInt(SPUtils.ModuleFile.mocked_device, "mock_count", 0);
                break;
            case UPDATE_MEDIA:
                onUpdateMedia(intent);
                break;
            case Telephony.Sms.Intents.SMS_RECEIVED_ACTION:
                try {
                    onSmsReceive(intent);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                boolean isAirplaneModeOn = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
                Logger.d(TAG, "airplane mode changed to " + isAirplaneModeOn);
                DeviceInfoManager.getInstance().isAirplaneModeOn = isAirplaneModeOn;
                if (!isAirplaneModeOn) {
                    GRPCManager.getInstance().getActorChannel().resetChannel();
                }
            default:
                break;
        }
    }

    private void onUpdateMedia(Intent intent) {
        String path = intent.getStringExtra("path");
        ResUtils.updateMedia(path);
    }

    private void onSettingGRPC(Intent intent) {
        String address = intent.getStringExtra("address");
        if (ActStringUtils.checkProxyPattern(address)) {
            Logger.d(TAG, "using broadcast setting grpc address :" + address);
            GRPCManager.getInstance().getActorChannel().setNodeAddress(address);
            GRPCManager.getInstance().getActorChannel().addNodeAddressToSp(address);
            this.setResult(1, "set node address success", null);
        } else {
            this.setResult(0, "address is empty or invalid", null);
        }
    }

    public void onSmsReceive(Intent intent) throws IOException {
        Bundle bundle = intent.getExtras(); //通过getExtras()方法获取短信内容
        String format = intent.getStringExtra("format");
        long time = System.currentTimeMillis();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus"); //根据pdus关键字获取短信字节数组，数组内的每个元素都是一条短信
            Map<String, String> map = new HashMap<>();
            Logger.d(TAG, bundle.toString());
            for (Object object : pdus) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) object, format); //将字节数组转化为Message对象
                String sender = message.getOriginatingAddress(); //获取短信手机号
                String content = message.getMessageBody(); //获取短信内容
                if (map.containsKey(sender)) {
                    String msg = map.get(sender) + content; // 太长的短信会分割成多个pdus，这里简单认为同一时间，同一个电话号码只发了一条短信；
                    map.put(sender, msg);
                } else {
                    map.put(sender, content);
                }
            }
            for (String sender : map.keySet()) {
                SmsUtil.uploadNode(sender, map.get(sender), time);
                SmsUtil.uploadCloud(sender, map.get(sender), time);
            }
        }
    }


    private void onSettingWifi(Intent intent) {
        String ssid = intent.getStringExtra("ssid");
        String username = intent.getStringExtra("username");
        String password = intent.getStringExtra("password");
        if (ssid == null || ssid.isEmpty()) {
            this.setResult(0, "not set wifi ssid", null);
        } else {
            DeviceInfoManager.getInstance().getConnManager().connect(
                    new ActWifiManager.UserInfo(ssid, username, password));
            this.setResult(1, "try to connect wifi", null);
        }
    }

    private void onLppiSwitch(Intent intent) {
        String pkgName = intent.getStringExtra("pkg");
        boolean on = intent.getBooleanExtra("on", true);
       // ActActivityManager.getInstance().moveToFront(RocketComponent.PKG_LPPI_INSTALLER);
        LppiManagerConnection connection = new LppiManagerConnection();
        connection.switchModule(pkgName, on, (success, reason) -> {
            this.setResult(success ? 1 : 0, reason, null);
            Logger.i(TAG, "switch lppi module " + (on ? "on" : "off") + " " + pkgName + ", result " + success + " " + reason);
        });
    }

    private void onSetMark(Intent intent) {
        int alpha = intent.getIntExtra("alpha", 90);
        if (ActAccessibility.isStart()) {
            ActAccessibility.getInstance().setDarkMask(alpha / 100);
        } else {
            this.setResult(0, "AccessibilityService not start.", null);
        }
    }

    private void onPowerButtonPressed() {
        BrightnessController.instance().acquireScreenReadable();
    }
}
