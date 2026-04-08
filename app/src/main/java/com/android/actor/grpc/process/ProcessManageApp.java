package com.android.actor.grpc.process;

import com.android.actor.ActApp;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageInstaller;
import com.android.actor.control.ActPackageManager;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import com.android.proto.common.ManageAppReq;

public class ProcessManageApp extends RequestProcess<ManageAppReq> {
    private final static String TAG = ProcessManageApp.class.getSimpleName();

    @Override
    public void process(ManageAppReq req) {
        mClientId = req.getClientId();
        String appName = req.getAppName();
        String appCdnUrl = req.getApkCdnUrl();
        String activityName = req.getActivityName();
        switch (req.getCmd()) {
            case "install":
            case "update":
                installApp(appCdnUrl);
                break;
            case "uninstall":
                uninstallApp(appName);
                break;
            case "start":
                startApp(appName, activityName);
                break;
            case "stop":
                stopApp(appName);
                break;
            default:
                reply(ActorMessageBuilder.manageApp(mClientId, false, "unknown cmd: " + req.getCmd()));
                break;
        }
    }

    private void installApp(String url) {
        ActPackageInstaller.instance().installAppUrl(url, new ActPackageInstaller.Listener() {
            @Override
            public void onPreInstall(String packageName, int versionCode) {
                if (packageName.equals(ActApp.getInstance().getPackageName())) {
                    reply(ActorMessageBuilder.manageApp(mClientId, true, ""));
                    Logger.w(TAG, "Reply actor self update at preinstall as success.");
                }
            }

            @Override
            public void postInstall(boolean success, String reason) {
                reply(ActorMessageBuilder.manageApp(mClientId, success, reason));
            }
        });
    }

    private void uninstallApp(String pkgName) {
        if (!ActStringUtils.isEmpty(pkgName)) {
            if (pkgName.equals(ActApp.getInstance().getPackageName())) {
                reply(ActorMessageBuilder.manageApp(mClientId, false, "Should not uninstall actor."));
            } else {
                ActPackageManager.getInstance().uninstallApp(pkgName, (_pkgName, success, reason) -> {
                    reply(ActorMessageBuilder.manageApp(mClientId, success, reason));
                });
            }
        } else {
            reply(ActorMessageBuilder.manageApp(mClientId, false, "pkg_name is empty."));
        }
    }

    private void startApp(String appName, String activityName) {
        if (!ActStringUtils.isEmpty(appName)) {
            ActActivityManager.getInstance().startActivityAndWait(appName, activityName, reason -> {
                String msg = !ActStringUtils.isEmpty(activityName) ? reason : "activityName empty, use default launch activity instead. " + reason;
                reply(ActorMessageBuilder.manageApp(mClientId,
                        ActStringUtils.isEmpty(reason), msg));
            });
        } else {
            reply(ActorMessageBuilder.manageApp(mClientId, false, "app_name is empty."));
        }
    }

    private void stopApp(String pkgName) {
        if (!ActStringUtils.isEmpty(pkgName)) {
            if (pkgName.equals(ActApp.getInstance().getPackageName())) {
                reply(ActorMessageBuilder.manageApp(mClientId, false, "Should not stop actor because grpc disconnect when stoped. Try reboot."));
            } else {
                ActActivityManager.getInstance().forceStopPackage(pkgName, (success, reason) -> {
                    reply(ActorMessageBuilder.manageApp(mClientId, success, reason));
                });
            }
        } else {
            reply(ActorMessageBuilder.manageApp(mClientId, false, "pkg_name is empty."));
        }
    }
}