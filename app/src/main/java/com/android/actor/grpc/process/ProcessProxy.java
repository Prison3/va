package com.android.actor.grpc.process;


import android.content.pm.PackageManager;
import android.util.Pair;

import com.android.actor.control.ActPackageManager;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.proto.common.ProxyReq;


public class ProcessProxy extends RequestProcess<ProxyReq> {
    private final static String TAG = ProcessProxy.class.getSimpleName();

    @Override
    public void process(ProxyReq req) {
        mClientId = req.getClientId();
        String app = req.getApp();
        String proxy = req.getProxy();
        if (app == null || app.isEmpty()) {
            reply(ActorMessageBuilder.proxy(mClientId, false, "app is null or empty"));
        } else {
            int uid = 0;
            try {
                uid = ActPackageManager.getInstance().getUidOfPackage(app);
            } catch (PackageManager.NameNotFoundException e) {
                Logger.w(TAG, e.toString());
            } finally {
                if (uid <= 0) {
                    reply(ActorMessageBuilder.proxy(mClientId, false, "cannot get uid of app, is app exists in device?"));
                } else {
                    try {
                        Pair<Boolean, String> r = ProxyManager.getInstance().addProxy(app, 0, proxy);
                        reply(ActorMessageBuilder.proxy(mClientId, r.first, r.second));
                    } catch (Exception e) {
                        Logger.e(TAG, "ProcessProxy exception.", e);
                        reply(ActorMessageBuilder.proxy(mClientId, false, e.toString()));
                    }
                }
            }
        }
    }
}