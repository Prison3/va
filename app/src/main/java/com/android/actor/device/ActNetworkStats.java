package com.android.actor.device;

import android.annotation.SuppressLint;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.telephony.TelephonyManager;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;

import org.apache.commons.io.FileUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Optional;

public class ActNetworkStats {

    private static final String TAG = ActNetworkStats.class.getSimpleName();
    private NetworkStatsManager mNetworkStatsManager;
    private NetworkPolicyManager mNetworkPolicyManager;
    private TelephonyManager mTelephonyManager;
    private ConnectivityManager mConnectivityManager;

    @SuppressLint("WrongConstant")
    public ActNetworkStats() {
        mNetworkStatsManager = (NetworkStatsManager) ActApp.getInstance().getSystemService(Context.NETWORK_STATS_SERVICE);
        mNetworkPolicyManager = (NetworkPolicyManager) ActApp.getInstance().getSystemService("netpolicy");
        mTelephonyManager = (TelephonyManager) ActApp.getInstance().getSystemService(Context.TELEPHONY_SERVICE);
        mConnectivityManager = (ConnectivityManager) ActApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void report() {
        try {
            if (!DeviceNumber.has()) {
                return;
            }
            if (mTelephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
                return;
            }
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(mConnectivityManager.getActiveNetwork());
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return;
            }

            NetworkPolicy[] policies = mNetworkPolicyManager.getNetworkPolicies();
            NetworkPolicy policy = Arrays.stream(policies).filter(p -> p.metered && !p.inferred).findFirst().get();
            int startDay = policy.cycleRule.start.getDayOfMonth();
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_MONTH, startDay);
            long startTime = calendar.getTimeInMillis();
            long currentTime = System.currentTimeMillis();
            if (startTime >= currentTime) {
                calendar.add(Calendar.MONTH, -1);
                startTime = calendar.getTimeInMillis();
            }

            NetworkStats.Bucket bucket = mNetworkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startTime, currentTime);
            long usedBytes = bucket.getRxBytes() + bucket.getTxBytes();
            Logger.d(TAG, "Report mobile data usage, start day " + startDay
                    + ", used " + FileUtils.byteCountToDisplaySize(usedBytes)
                    + ", total " + FileUtils.byteCountToDisplaySize(policy.warningBytes));
            NetUtils.metricGauge("mobile_data_used_percent",
                    null,
                    null,
                    null,
                    (int) (((float) usedBytes / policy.warningBytes) * 100),
                    Optional.empty()
            );
            NetUtils.metricGauge("mobile_data_cycle_past_days",
                    null,
                    null,
                    null,
                    (int) ((currentTime - startTime) / 1000 / 3600 / 24),
                    Optional.empty()
            );
        } catch (Throwable e) {
            Logger.e(TAG, "Error to report mobile data usage.", e);
        }
    }
}
