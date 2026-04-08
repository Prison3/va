package com.android.actor.device;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.android.actor.grpc.ActorAdapter;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.notification.GlobalNotification;

import org.apache.commons.net.util.SubnetUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Act4GManager extends IActConnManager {

    private static final String TAG = Act4GManager.class.getSimpleName();
    private TelephonyManager mTelephonyManager;
    private ConnectivityManager mConnectivityManager;

    public Act4GManager(Context context) {
        super(context, "4G");
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public String getConnectionInfo() {
        Optional<LinkProperties> opt = Arrays.stream(mConnectivityManager.getAllNetworks())
                .filter(network -> {
                    NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
                    return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                })
                .map(network -> mConnectivityManager.getLinkProperties(network))
                .findFirst();
        if (opt.isPresent()) {
            List<LinkAddress> addresses = opt.get().getLinkAddresses();
            if (addresses.size() > 0) {
                return addresses.get(0).getAddress().getHostAddress();
            }
        }
        return null;
    }

    @Override
    public String getConnectionDetail() {
        try {
            Optional<LinkProperties> opt = Arrays.stream(mConnectivityManager.getAllNetworks())
                    .filter(network -> {
                        NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
                        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                    })
                    .map(network -> mConnectivityManager.getLinkProperties(network))
                    .findFirst();
            if (opt.isPresent()) {
                LinkProperties linkProperties = opt.get();
                StringBuilder builder = new StringBuilder();
                List<LinkAddress> addresses = linkProperties.getLinkAddresses();
                if (addresses.size() > 0) {
                    LinkAddress linkAddress = addresses.get(0);
                    String ip = linkAddress.getAddress().getHostAddress();
                    builder.append("IP: ");
                    builder.append(ip);
                    builder.append('\n');

                    int prefixLength = linkAddress.getPrefixLength();
                    SubnetUtils subnetUtils = new SubnetUtils(ip + '/' + prefixLength);
                    String mask = subnetUtils.getInfo().getNetmask();
                    builder.append("mask: ");
                    builder.append(mask);
                    builder.append('\n');

                    RouteInfo routeInfo = linkProperties.getRoutes().stream().filter(RouteInfo::hasGateway).findFirst().get();
                    String gateway = routeInfo.getGateway().getHostAddress();
                    builder.append("gateway: ");
                    builder.append(gateway);
                    builder.append('\n');
                    return builder.toString();
                }
            }
        } catch (Throwable e) {
            Logger.e(TAG, "getConnectionDetail exception.", e);
        }
        return null;
    }

    @Override
    protected void onEnabled(boolean enabled) {
        mTelephonyManager.setDataEnabled(enabled);
        if (enabled) {
            mConnectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build()
                    , mCallback);
        } else {
            mConnectivityManager.unregisterNetworkCallback(mCallback);
            setConnected(false);
        }
    }

    private ConnectivityManager.NetworkCallback mCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
            Logger.d(TAG, "onLinkPropertiesChanged " + network);
            setConnected(true);
            GRPCManager.getInstance().getActorChannel().resetChannel();
        }

        @Override
        public void onLost(@NonNull Network network) {
            Logger.d(TAG, "onLost " + network);
            setConnected(false);
        }
    };

    @Override
    protected void onConnected(boolean connected) {
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_WIFI_CHANGE, null);
    }

    @Override
    public boolean checkAndReconnect() {
        return true;
    }

    @Override
    public void connect(IUserInfo userInfo) {
    }
}
