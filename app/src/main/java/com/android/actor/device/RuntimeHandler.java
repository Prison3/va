package com.android.actor.device;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Message;
import android.view.Display;

import com.android.actor.ActAccessibility;
import com.android.actor.control.api.InputKeyCode;
import com.android.actor.monitor.Logger;
import com.android.actor.script.dex.DexUpdater;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.TimeFormatUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.shell.Libsu;

import java.util.Optional;


class RuntimeHandler extends Handler {
    private final static String TAG = RuntimeHandler.class.getSimpleName();
    final static int COLLECTOR_HANDLER_START = 123;
    final static int COLLECTOR_HANDLER_STOP = 321;
    final static int RUNTIME_LOOP = 2;
    final static int TRAFFIC_LOOP = 3;
    final static int SCREEN_LOOP = 4;
    final static int CONN_EXTRA_CHECK = 5;
    final static int DS_UPDATE = 6;
    private final static long RUNTIME_DELAY = 60 * 1000;
    private final static long CONN_EXTRA_CHECK_DELAY = 4 * 1000;
    private final static long SCREEN_DELAY = 5 * 60 * 1000;
    private final static long TRAFFIC_DELAY = 5 * 60 * 1000;

    private final static long ProxyReportDelay = 5 * 60 * 1000;
    private static long lastProxyReportTime = 0;
    private final static long AccountReportDelay = 1 * 60 * 1000;

    private static long lastAccountReportTime = 0;

    private ActNetworkStats mActNetworkStats = new ActNetworkStats();

    RuntimeHandler() {
        sendEmptyMessage(COLLECTOR_HANDLER_START);
        GlobalNotification.addObserver(GlobalNotification.NOTIFY_WIRED_OR_WIFI, (obs, arg) -> {
            sendEmptyMessage(CONN_EXTRA_CHECK);
        });
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case COLLECTOR_HANDLER_START:
                sendEmptyMessage(RUNTIME_LOOP);
                sendEmptyMessageDelayed(TRAFFIC_LOOP, 1000);
                sendEmptyMessageDelayed(SCREEN_LOOP, 5000);
                //sendEmptyMessageDelayed(DS_UPDATE, 10 * 1000);
                break;

            case COLLECTOR_HANDLER_STOP:
                this.removeMessages(RUNTIME_LOOP);
                this.removeMessages(TRAFFIC_LOOP);
                this.removeMessages(SCREEN_LOOP);
                break;

            case RUNTIME_LOOP: {
                removeMessages(CONN_EXTRA_CHECK);
                boolean pendingCheck = !DeviceInfoManager.getInstance().getConnManager().checkAndReconnect();
                pendingCheck |= !DeviceInfoManager.getInstance().getConnManager().getWired().checkAndReconnect();
                if (pendingCheck) {
                    sendEmptyMessageDelayed(CONN_EXTRA_CHECK, CONN_EXTRA_CHECK_DELAY);
                }
                sendEmptyMessageDelayed(RUNTIME_LOOP, RUNTIME_DELAY);
                DeviceInfoManager.getInstance().collectRuntime();
                // 循环上报
                // 如果距离上次上报时间超过了5分钟，就上报一次
                if (System.currentTimeMillis() - lastProxyReportTime > ProxyReportDelay) {
                    Logger.d(TAG, "since last proxy report time : " + (System.currentTimeMillis() - lastProxyReportTime) / 1000 + "s ," + "go reportProxy");
                    reportProxy("com.einnovation.temu", 0);
                } else {
                    Logger.d(TAG, "since last proxy report time : " + (System.currentTimeMillis() - lastProxyReportTime) / 1000 + "s ," + "not reportProxy");
                }
                if (System.currentTimeMillis() - lastAccountReportTime > AccountReportDelay) {
                    Logger.d(TAG, "since last account report time : " + (System.currentTimeMillis() - lastAccountReportTime) / 1000 + "s ," + "go reportAppAccountStatus");
                    reportAppAccountStatus("com.einnovation.temu");
                } else {
                    Logger.d(TAG, "since last account report time : " + (System.currentTimeMillis() - lastAccountReportTime) / 1000 + "s ," + "not reportAppAccountStatus");
                }
                break;
            }

            case CONN_EXTRA_CHECK: {
                removeMessages(CONN_EXTRA_CHECK);
                boolean pendingCheck = !DeviceInfoManager.getInstance().getConnManager().checkAndReconnect();
                pendingCheck |= !DeviceInfoManager.getInstance().getConnManager().getWired().checkAndReconnect();
                if (pendingCheck) {
                    sendEmptyMessageDelayed(CONN_EXTRA_CHECK, CONN_EXTRA_CHECK_DELAY);
                }
                break;
            }

            case TRAFFIC_LOOP:
                Logger.i(TAG, "Record traffic =====> ");
                mActNetworkStats.report();
                sendEmptyMessageDelayed(TRAFFIC_LOOP, TRAFFIC_DELAY);
                break;

            case SCREEN_LOOP:
                if (ActAccessibility.isStart()) {
                    DisplayManager displayManager = (DisplayManager) ActAccessibility.getInstance().getSystemService(Context.DISPLAY_SERVICE);
                    if (displayManager.getDisplays()[0].getState() == Display.STATE_OFF) {
                        Logger.w(TAG, "Current screen is off, try turn on");
                        InputKeyCode.pressPower();
                    } else {
                        Logger.d(TAG, "Current screen is on.");
                    }
                }
                sendEmptyMessageDelayed(SCREEN_LOOP, SCREEN_DELAY);
                break;

            case DS_UPDATE:
                Logger.i(TAG, "Update ds to newest.");
                DexUpdater.instance.updateAsync(success -> {
                    sendEmptyMessageDelayed(DS_UPDATE, success ? 3600 * 1000 : 300 * 1000);
                });
                break;

            default:
                break;
        }
    }

    public void reportProxy(String pkg, int profile) {
        lastProxyReportTime = System.currentTimeMillis();
        Logger.d(TAG, "lastProxyReportTime : " + lastProxyReportTime + ", getDatetimeStringr :" + TimeFormatUtils.getDatetimeString(lastProxyReportTime));
        Libsu.waitReady(() -> {
            new Thread(() -> {
                Logger.d(TAG, "report proxy pkg : " + pkg + " , profile :" + profile);
                ProxyManager.Proxy proxy = ProxyManager.getInstance().getProxy(pkg, profile);
                Logger.d(TAG, "reportProxy: " + proxy);
                if (proxy != null) {
                    Boolean isOk = ProxyManager.testProxy(proxy);
                    if (isOk) {
                        Logger.d(TAG, "reportProxy: proxy is ok , " + proxy.socksProxy);
                        NetUtils.metricGauge("app_proxy_status", pkg, proxy.socksProxy, "", 1, Optional.empty());
                    } else {
                        Logger.d(TAG, "reportProxy: proxy is not ok , " + proxy.socksProxy);
                        NetUtils.metricGauge("app_proxy_status", pkg, proxy.socksProxy, "", 0, Optional.empty());
                    }
                } else {
                    Logger.d(TAG, "reportProxy: proxy is null");
                }
                // 上报 proxy_detail_in_use
                if (pkg.equals("com.einnovation.temu")) {
                    String proxyDetailInUse = SPUtils.getValue(SPUtils.ModuleFile.temu_info, "proxy_detail_in_use", null);
                    int value = proxyDetailInUse == null ? 1 : Integer.parseInt(proxyDetailInUse);
                    Logger.d(TAG, "reportProxy: proxy_detail_in_use : " + value + " , pkg : " + pkg + "country : " + NewStage.instance().getPackageParameterValue(pkg, "country"));
                    NetUtils.metricGauge("proxy_detail_in_use", pkg, NewStage.instance().getPackageParameterValue(pkg, "country"), "", value, Optional.empty());
                }
                // 上报 该代理 被占用 （被封掉）
//                if (proxyDetailInUse == 1) {
//                    //
//                    NetUtils.reportSpiderProxy(proxy.socksProxy, NetUtils.SpiderProxyStatus.proxy_blocked);
//                } else {
//                    //
//                    NetUtils.reportSpiderProxy(proxy.socksProxy, NetUtils.SpiderProxyStatus.proxy_in_use);
//                }

            }).start();
        });


    }

    public void reportAppAccountStatus(String pkg) {
        lastAccountReportTime = System.currentTimeMillis();
        Logger.d(TAG, "lastAccountReportTime : " + lastAccountReportTime + ", getDatetimeString :" + TimeFormatUtils.getDatetimeString(lastAccountReportTime));
        Libsu.waitReady(() -> {
            new Thread(() -> {
                String accountStatus = SPUtils.getString(SPUtils.ModuleFile.app_accounts, pkg);
                Logger.d(TAG, "report app pkg : " + pkg + " , accountStatus :" + accountStatus + " , pkg :" + pkg + " , country : " + NewStage.instance().getPackageParameterValue(pkg, "country"));
                if (accountStatus != null) {
                    NetUtils.metricGauge("app_account_status", pkg, NewStage.instance().getPackageParameterValue(pkg, "country"), "", Integer.parseInt(accountStatus), Optional.empty());
                } else {
                    Logger.d(TAG, "report app account status : null");
                }
            }).start();
        });

    }
}
