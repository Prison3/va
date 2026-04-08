package com.android.actor.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.shell.Shell;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class ActWiredManager extends IActConnManager {

    private static final String TAG = ActWiredManager.class.getSimpleName();
    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED";

    private ConnectivityManager mConnectivityManager;
    private boolean mUsbConnected = false;
    private String mIP;
    private String mGateway;
    private boolean mDnsSet = false;
    private boolean mNtpSet = false;
    private int mFailurePingCount = 0;

    public ActWiredManager(Context context) {
        super(context, "Wired");
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public String getConnectionInfo() {
        if (isConnected()) {
            return mIP;
        }
        return null;
    }

    public String getGateway() {
        if (isConnected()) {
            return mGateway;
        }
        return null;
    }

    @Override
    public String getConnectionDetail() {
        return null;
    }

    @Override
    protected void onEnabled(boolean enabled) {
        setConnected(false);
        if (enabled) {
            Logger.d(TAG, "Enable wired.");
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_USB_STATE);
            filter.addAction(ACTION_TETHER_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            disable();
        }
    }

    public void disable() {
        Logger.d(TAG, "Disable wired.");
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch (Throwable e) {
        }
    }

    @Override
    protected void onConnected(boolean connected) {
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIFI_CHANGE, null);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_STATE.equals(action)) {
                Logger.d(TAG, "USB_STATE");
                Logger.intent(TAG, intent);
                mUsbConnected = intent.getBooleanExtra("connected", false);
                Logger.d(TAG, "mUsbConnected " + mUsbConnected);
                if (!intent.getBooleanExtra("rndis", false)) {
                    Logger.d(TAG, "rndis is false.");
                    setConnected(false);
                }
                if (mUsbConnected) {
                    GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIRED_OR_WIFI, null);
                }
            } else if (ACTION_TETHER_STATE_CHANGED.equals(action)) {
                Logger.d(TAG, "TETHER_STATE_CHANGED");
                Logger.intent(TAG, intent);
            }
        }
    };

    @Override
    public boolean checkAndReconnect() {
        try {
            if (!isEnabled()) {
                return true;
            }
            Logger.d(TAG, "Check wired, mFailurePingCount " + mFailurePingCount);
            if (!mUsbConnected) {
                return true;
            }

            /*if (mFailurePingCount >= 5) {
                Logger.w(TAG, "setUsbTethering to false due to mFailurePingCount reach 5");
                ReflectUtils.callMethod(mConnectivityManager, "setUsbTethering", new Object[] {
                        false
                }, new Class[] {
                        boolean.class
                });
                mFailurePingCount = 0;
                Thread.sleep(3000);
            }*/

            String[] ifaces = (String[]) ReflectUtils.callMethod(mConnectivityManager, "getTetheredIfaces");
            Logger.d(TAG, "getTetheredIfaces " + ArrayUtils.toString(ifaces));
            if (ifaces.length == 0) {
                Libsu.startTethering();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {}

                ifaces = (String[]) ReflectUtils.callMethod(mConnectivityManager, "getTetheredIfaces");
                Logger.d(TAG, "getTetheredIfaces " + ArrayUtils.toString(ifaces));
            }

            if (ifaces.length == 0) {
                Logger.e(TAG, "getTetheredIfaces is empty after startTethering success.");
                setConnected(false);
                return true;
            }
            String iface = ifaces[0];
            mIP = DeviceNumber.calcWiredIP();
            mGateway = DeviceNumber.calcGateway();
            if (StringUtils.isEmpty(mIP) || StringUtils.isEmpty(mGateway)) {
                Logger.e(TAG, "No wired ip or gateway.");
                return true;
            }

            boolean hasIP = false;
            NetworkInterface networkInterface = NetworkInterface.getByName(iface);
            if (networkInterface != null) {
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        String existIP = ((Inet4Address) address).getHostAddress();
                        if (mIP.equals(existIP)) {
                            hasIP = true;
                        }
                    }
                }
            }
            if (!hasIP) {
                Logger.w(TAG, "Wired IP not match, reset tethering.");
                Libsu.stopTethering();
                return false;
            }

            /*if (DeviceInfoManager.getInstance().getConnManager().getType() == ActConnManager.USE_WIFI) {
                String cmd = "ip route add default via " + mGateway + " dev " + iface + " table main proto static";
                Shell.execRootCmdSilent(cmd);
                boolean hasRule = Shell.execRootCmd("ip rule").stream().anyMatch(line -> line.contains("from all lookup main"));
                if (!hasRule) {
                    Shell.execRootCmdSilent("ip rule add to 0.0.0.0/0 table main; ");
                }
            }*/

            if (!mDnsSet) {
                /*setTetherDns(new String[] {
                        "114.114.114.114", "223.5.5.5"
                });*/
                mDnsSet = true;
            }
            if (!mNtpSet) {
                /*NtpClient client = new NtpClient();
                boolean result = client.sync(new String[] {
                        mGateway, "ntp.aliyun.com"
                });
                if (result) {*/
                    mNtpSet = true;
                //}
            }
            if (mNtpSet) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Libsu.exec("killall dnsmasq");
                setConnected(true);
                InetAddress addr = InetAddress.getByName(mGateway);
                if (addr.isReachable(5000)) {
                    mFailurePingCount = 0;
                } else {
                    ++mFailurePingCount;
                }
            } else {
                ++mFailurePingCount;
            }
            return true;
        } catch (Throwable e) {
            Logger.e(TAG, "Failed to check and reconnect wired network.", e);
            return true;
        }
    }

    @Override
    public void connect(IUserInfo userInfo) {
    }
}
