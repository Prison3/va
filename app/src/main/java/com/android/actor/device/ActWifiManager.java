package com.android.actor.device;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.notification.GlobalNotification;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActWifiManager extends IActConnManager {

    public static boolean sIsConfigWifi = true;

    public static class UserInfo implements IUserInfo {
        private String mSSID;
        private String mUsername;
        private String mPassword;

        public UserInfo() {
        }

        public UserInfo(String ssid, String username, String password) {
            mSSID = ssid;
            mUsername = username;
            mPassword = password;
        }
    }

    private final static String TAG = ActWifiManager.class.getSimpleName();
    private final WifiManager mWifiManager;
    public final static String WIFI_DEFAULT_STORAGE_PATH = "/data/new_stage/wifi";
    private UserInfo mUserInfo = new UserInfo();

    @SuppressLint("WifiManagerPotentialLeak")
    public ActWifiManager(Context context) {
        super(context, "WiFi");
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mConnected = mWifiManager.getConnectionInfo().getBSSID() != null;
        Logger.d(TAG, "WIFI enable: " + mWifiManager.isWifiEnabled() + ", is connect: " + mConnected);
    }

    public String getConnectionInfo() {
        return getSSID() + ": " + getWifiIp();
    }

    @Override
    public String getConnectionDetail() {
        StringBuilder builder = new StringBuilder();
        builder.append("SSID: ");
        builder.append(getSSID());
        builder.append('\n');
        builder.append("IP: ");
        builder.append(getWifiIp());
        builder.append('\n');
        builder.append("gateway: ");
        String gateway = ActStringUtils.intToIp(mWifiManager.getDhcpInfo().gateway);
        builder.append(gateway);
        builder.append('\n');
        return builder.toString();
    }

    public String getWifiIp() {
        return ActStringUtils.intToIp(mWifiManager.getConnectionInfo().getIpAddress());
    }

    public String getSSID() {
        return mWifiManager.getConnectionInfo().getSSID().replace("\"", "");
    }

    @Override
    protected void onEnabled(boolean enabled) {
        if (enabled) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            Logger.d(TAG, "Disable wifi.");
            mContext.unregisterReceiver(mReceiver);
            mWifiManager.setWifiEnabled(false);
        }
    }

    @Override
    protected void onConnected(boolean connected) {
        if (connected) {
            SPUtils.putString(SPUtils.ModuleFile.wifi, "ssid", mUserInfo.mSSID);
            SPUtils.putString(SPUtils.ModuleFile.wifi, "username", mUserInfo.mUsername);
            SPUtils.putString(SPUtils.ModuleFile.wifi, "password", mUserInfo.mPassword);
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Logger.d(TAG, "onReceive: " + action);
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {// wifi打开与否
                int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
                if (wifistate == WifiManager.WIFI_STATE_DISABLED) {
                    Logger.w(TAG, "系统关闭wifi");
                } else if (wifistate == WifiManager.WIFI_STATE_ENABLED) {
                    Logger.i(TAG, "系统开启wifi");
//                    GRPCManager.getInstance().getActorChannel().resetChannel(true);
                }
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                // wifi连接上与否
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                Logger.d(TAG, "--NetworkInfo--" + info);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    Logger.w(TAG, "wifi网络连接断开");
                    setConnected(false);
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    // 获取当前wifi名称
                    Logger.i(TAG, "连接到网络 " + wifiInfo.getSSID());
                    setConnected(true);
                    if (mUserInfo != null) {
                        if (("\"" + mUserInfo.mSSID + "\"").equals(wifiInfo.getSSID())) {
                            Logger.d(TAG, "Current wifi matched config");
                            sIsConfigWifi = true;
                        } else {
                            Logger.w(TAG, "Current wifi not match config");
                            sIsConfigWifi = false;
                        }
                    }
                }
            }
            GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIFI_CHANGE, null);
            GRPCManager.getInstance().getActorChannel().resetChannel();
        }
    };

    public List<String> getScanList() {
        List<String> list = new ArrayList<>();
        @SuppressLint("MissingPermission") List<ScanResult> scanList = mWifiManager.getScanResults();
        for (ScanResult result : scanList) {
            list.add(result.SSID);
        }
        return list;
    }

    private void enableWifi() {
        if (!mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(true);
            Logger.d(TAG, "try enable wifi");
        }
    }

    @Override
    public boolean checkAndReconnect() {
        if (!isEnabled()) {
            return true;
        }
        Logger.d(TAG, "CheckWifi: my SSID: " + mUserInfo.mSSID + ", current SSID: " + getSSID());
        Logger.d(TAG, "WifiInfo: " + getConnectionInfo());
        if (mUserInfo.mSSID == null || mUserInfo.mPassword == null) {
            mUserInfo.mSSID = SPUtils.getString(SPUtils.ModuleFile.wifi, "ssid");
            mUserInfo.mUsername = SPUtils.getString(SPUtils.ModuleFile.wifi, "username");
            mUserInfo.mPassword = SPUtils.getString(SPUtils.ModuleFile.wifi, "password");
            if (mUserInfo.mSSID == null || mUserInfo.mPassword == null) {
                try {
                    Logger.i(TAG, "Not found wifi in sp, try load from: " + WIFI_DEFAULT_STORAGE_PATH);
                    File file = new File(WIFI_DEFAULT_STORAGE_PATH);
                    if (file.exists()) {
                        List<String> lines = FileUtils.readLines(file, "UTF-8");
                        if (lines.size() > 1) {
                            mUserInfo.mSSID = lines.get(0);
                            mUserInfo.mPassword = lines.get(1);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (mUserInfo.mSSID == null || mUserInfo.mPassword == null) {
                        mUserInfo.mSSID = "SHEIN-PTYF";
                        mUserInfo.mPassword = "1QAZ2wsx3edc...";
                    }
                }
            }
        }
        if (!mWifiManager.isWifiEnabled()) {
            enableWifi();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (!mConnected) {
            Logger.d(TAG, "Wifi not connected, try connect");
            connect(mUserInfo);
        }
        return true;
    }

    // 子线程更新
    @Override
    public void connect(IUserInfo userInfo) {
        if (!isEnabled()) {
            return;
        }
        if (userInfo == null) {
            return;
        }
        mUserInfo = (UserInfo) userInfo;
        if (mWifiManager != null) {
            WifiConfiguration config;
            if (ActStringUtils.isEmpty(mUserInfo.mUsername)) {
                config = createWifiInfo(mUserInfo.mSSID, mUserInfo.mPassword);
            } else {
                config = createEnterpriseWifiInfo(mUserInfo.mSSID, mUserInfo.mUsername, mUserInfo.mPassword);
            }
            int netID = mWifiManager.addNetwork(config);
            Logger.d(TAG, "netID " + netID);
            mWifiManager.enableNetwork(netID, true);
        }
    }

    private WifiConfiguration createWifiInfo(String SSID, String password) {
        Logger.i(TAG, "SSID:" + SSID + ",password:" + password);
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + SSID + "\"";
        WifiConfiguration tempConfig = isExists(SSID);
        if (tempConfig != null) {
            mWifiManager.removeNetwork(tempConfig.networkId);
        } else {
            Logger.w(TAG, "IsExsits is null.");
        }
        config.preSharedKey = "\"" + password + "\"";
        config.hiddenSSID = true;
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.status = WifiConfiguration.Status.ENABLED;
        return config;
    }

    private WifiConfiguration createEnterpriseWifiInfo(String SSID, String username, String password) {
        Logger.i(TAG, "SSID:" + SSID + ", username:" + username);
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + SSID + "\"";
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X);
        WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();
        enterpriseConfig.setIdentity(username);
        enterpriseConfig.setPassword(password);
        enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.PEAP);
        enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);
        config.enterpriseConfig = enterpriseConfig;
        return config;
    }

    private WifiConfiguration isExists(String SSID) {
        @SuppressLint("MissingPermission") List<WifiConfiguration> existingConfigs = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                return existingConfig;
            }
        }
        return null;
    }

    public boolean startScan() { return mWifiManager.startScan(); }

    public UserInfo getUserInfo() { return mUserInfo; }
}
