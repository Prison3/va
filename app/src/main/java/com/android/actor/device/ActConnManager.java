package com.android.actor.device;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.shell.Shell;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.InetAddress;

public class ActConnManager {

    private static final String TAG = ActConnManager.class.getSimpleName();
    public static final File DISABLE_WIRED_FILE = new File("/data/new_stage/disable_wired");
    private static final int USE_NOT_SET = 0;
    public static final int USE_WIRED = 1;
    public static final int USE_WIFI = 2;
    public static final int USE_4G = 3;

    private IActConnManager mImpl;
    private ActWiredManager mWired;
    private Context mContext;
    private int mIntranetUnreachableCount = 0;
    private int MAX_INTRANET_UNREACHABLE_COUNT = RandomUtils.nextInt(40, 60); // nearly 1h

    public ActConnManager(Context context) {
        mContext = context;
        int useWired = SPUtils.getInt(SPUtils.ModuleFile.wired_network, "wired", USE_NOT_SET);
        if (useWired == USE_NOT_SET) {
            useWired = USE_WIFI;
            Logger.i(TAG, "Use wired false.");
        } else if (useWired == USE_WIRED) {
            Logger.i(TAG, "Use wired due to user saved.");
        } else if (useWired == USE_4G) {
            Logger.i(TAG, "Use 4G due to user saved.");
        } else {
            Logger.i(TAG, "Use wifi due to user saved.");
        }

        if (useWired != USE_WIFI) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wifiManager.setWifiEnabled(false);
        }
        /*if (useWired != USE_WIRED) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            try {
                ReflectUtils.callMethod(connectivityManager, "setUsbTethering", new Object[] {
                        false
                }, new Class[] {
                        boolean.class
                });
            } catch (Throwable e) {
                Logger.e(TAG, "Can't reset setUsbTethering.");
            }
        }*/
        switch (useWired) {
            case USE_WIRED:
                //mImpl = new ActWiredManager(context);
                throw new RuntimeException("Should no wired.");
            case USE_WIFI:
                mImpl = new ActWifiManager(context);
                break;
            case USE_4G:
                mImpl = new Act4GManager(context);
                break;
        }
        mImpl.setEnabled(true);

        mWired = new ActWiredManager(context);
        boolean enabled = SPUtils.getBoolean(SPUtils.ModuleFile.wired_network, "enable_wired");
        if (enabled) {
            mWired.setEnabled(true);
        }
    }

    public String getTypeString() {
        return mImpl.getType();
    }

    public int getType() {
        return mImpl instanceof ActWiredManager ? USE_WIRED : (mImpl instanceof ActWifiManager ? USE_WIFI : USE_4G);
    }

    public String getConnectionInfo() {
        return mImpl.getConnectionInfo();
    }

    public String getConnectionDetail() {
        return mImpl.getConnectionDetail();
    }

    public boolean isConnected() {
        return mImpl.isConnected();
    }

    public ActWiredManager getWired() {
        /*if (mImpl instanceof ActWiredManager) {
            return (ActWiredManager) mImpl;
        }
        return null;*/
        return mWired;
    }

    public ActWifiManager getWifi() {
        if (mImpl instanceof ActWifiManager) {
            return (ActWifiManager) mImpl;
        }
        return null;
    }

    public void enableWired(int wired) {
        if (wired == USE_WIRED && !(mImpl instanceof ActWiredManager)) {
            Logger.i(TAG, "Switch to wired.");
            /*mImpl.setEnabled(false);
            mImpl = new ActWiredManager(mContext);
            mImpl.setEnabled(true);
            SPUtils.putInt(SPUtils.ModuleFile.wired_network, "wired", USE_WIRED);
            GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIRED_OR_WIFI, null);*/
            throw new RuntimeException("Should no wired.");
        } else if (wired == USE_WIFI && !(mImpl instanceof ActWifiManager)) {
            Logger.i(TAG, "Switch to wifi.");
            mImpl.setEnabled(false);
            mImpl = new ActWifiManager(mContext);
            mImpl.setEnabled(true);
            SPUtils.putInt(SPUtils.ModuleFile.wired_network, "wired", USE_WIFI);
            GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIRED_OR_WIFI, null);
        } else if (wired == USE_4G && !(mImpl instanceof Act4GManager)) {
            Logger.i(TAG, "Switch to 4G.");
            mImpl.setEnabled(false);
            mImpl = new Act4GManager(mContext);
            mImpl.setEnabled(true);
            SPUtils.putInt(SPUtils.ModuleFile.wired_network, "wired", USE_4G);
            GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIRED_OR_WIFI, null);
        }
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIFI_CHANGE, null);
    }

    public boolean checkAndReconnect() {
        return mImpl.checkAndReconnect();
    }

    public void checkAndReboot() {
        if (mImpl instanceof ActWiredManager) {
            String reason = null;
            String gateway = ((ActWiredManager) mImpl).getGateway();
            if (gateway != null) {
                try {
                    InetAddress addr = InetAddress.getByName(gateway);
                    if (!addr.isReachable(3000)) {
                        reason = "ping timeout.";
                    }
                } catch (Throwable e) {
                    reason = e.toString();
                }
            } else {
                reason = "No gateway.";
            }
            if (reason != null) {
                ++mIntranetUnreachableCount;
                Logger.d(TAG, "mIntranetUnreachableCount " + mIntranetUnreachableCount + ", reason: " + reason);
                if (mIntranetUnreachableCount >= MAX_INTRANET_UNREACHABLE_COUNT) {
                    Logger.w(TAG, "Reach max intranet count " + mIntranetUnreachableCount + ", reboot.");
                    Libsu.reboot();
                }
            } else {
                if (mIntranetUnreachableCount > 0) {
                    Logger.d(TAG, "Intranet is connected, set mIntranetUnreachableCount to 0.");
                }
                mIntranetUnreachableCount = 0;
            }
        }
    }

    public void connect(IActConnManager.IUserInfo userInfo) {
        mImpl.connect(userInfo);
    }
}
