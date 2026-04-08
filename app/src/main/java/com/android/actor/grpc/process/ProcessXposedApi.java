package com.android.actor.grpc.process;


import android.annotation.SuppressLint;
import android.util.Pair;

import com.alibaba.fastjson.JSON;
import com.android.actor.control.ActActivityManager;
import com.android.actor.device.DeviceNumber;
import com.android.actor.remote.ScriptForAction;
import com.android.actor.script.ScriptExecutor;
import com.android.actor.script.dex.DexUpdater;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.internal.aidl.service.ParcelCreator;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.RemoteServiceConnection;
import com.android.actor.remote.RemoteServiceManager;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.db.XposedContract;
import com.android.proto.common.XposedApiReq;

import org.apache.commons.lang3.RandomUtils;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ProcessXposedApi extends RequestProcess<XposedApiReq> {
    private final static String TAG = ProcessXposedApi.class.getSimpleName();

    @SuppressLint("AuthLeak")
    @Override
    public void process(XposedApiReq req) {
        mClientId = req.getClientId();
        String serial = req.getSerial();
        String app = req.getApp();
        String action = req.getAction();
        String body = JSON.toJSONString(req.getBodyMap());
        Map<String, String> scriptParams = req.getScriptParamsMap();
        Logger.d(TAG, "ProcessXposedApi , app is:" + app + ", action is:" + action + ", body is:" + body + ", scriptParams is:" + scriptParams);
        RemoteServiceConnection conn = RemoteServiceManager.getInstance().getConnection(app);
        if (app == null || action == null || app.isEmpty() || action.isEmpty()) {
            reply(ActorMessageBuilder.xposedApi(mClientId, false, "app or action is null or empty", null, "not define app or action, no proxy"));
        } else if (conn == null) {
            reply(ActorMessageBuilder.xposedApi(mClientId, false, "Device not found conn of: " + app, null, "not conn remote service, no proxy"));
        } else {
            String proxy = ProxyManager.getInstance().getAppProxy(app, 0);
            if (DeviceNumber.get().startsWith("U")) {
                proxy = "http://user01:ujRWJ7bVUPxK2jLrPFng3tf@47.251.53.181:20" + DeviceNumber.get().substring(1);
                Logger.d(TAG, "use US 4G proxy : " + proxy);
            }
            try {
                Pair<String, Long> pair = DexUpdater.instance.update();
                Logger.d(TAG, "Update first, time " + pair.second + ", last run " + ScriptExecutor.getInstance().getLastModified());
                if (ScriptExecutor.getInstance().getLastModified() >= 0
                        && pair.second != ScriptExecutor.getInstance().getLastModified()) {
                    if (ScriptExecutor.getInstance().isRunning()) {
                        ScriptExecutor.getInstance().stopScript();
                    }
                    ActActivityManager.getInstance().forceStopPackage(app);
                    Thread.sleep(RandomUtils.nextInt(3000, 6000));
                }

                if (!app.equals(ActActivityManager.getInstance().getCurrentApp())) {
                    ActActivityManager.getInstance().moveToFront(app);
                    Thread.sleep(RandomUtils.nextInt(7000, 13000));
                }

                String scriptUrl = ScriptForAction.instance.getScriptUrl(app, action);
                if (scriptUrl == null) {
                    Logger.d(TAG, "No script url for action " + app + "/" + action + ", stop script.");
                    throw new Exception("ScriptForAction must be configured for " + app + "/" + action
                            + ", or latest online dex won't be downloaded to local.");
                } else {
                    if (!ScriptExecutor.getInstance().isRunning()) {
                        long endTime = SPUtils.getLong(SPUtils.ModuleFile.script, "endTime");
                        long lastRead = SPUtils.getLong(SPUtils.ModuleFile.script, "lastRead");
                        if (endTime == 0 || lastRead > 0 || System.currentTimeMillis() - endTime > 300 * 1000) {
                            Logger.d(TAG, "Run script for action " + app + "/" + action);
                            Logger.d(TAG, "scriptParams is:" + scriptParams);
                            ScriptExecutor.getInstance().loadScript("script_for_action", scriptUrl, scriptParams);
                            ScriptExecutor.getInstance().executeScript("script_for_action", 300 * 1000);
                        } else {
                            String msg = "Last RunScript result is not read, past " + ((System.currentTimeMillis() - endTime) / 1000) + "s.";
                            reply(ActorMessageBuilder.xposedApi(mClientId, false, msg, null, "not care."));
                            return;
                        }
                    } else {
                        if (!scriptUrl.equals(ScriptExecutor.getInstance().getScript()) || !scriptParams.equals(ScriptExecutor.getInstance().getScriptParams())) {
                            Logger.d(TAG, "Script url changed for action " + app + "/" + action + ", stop script, " +
                                    "old " + ScriptExecutor.getInstance().getScript() + ", new " + scriptUrl);
                            Logger.d(TAG, "Script params changed for action " + app + "/" + action + ", stop script, " +
                                    "old " + ScriptExecutor.getInstance().getScriptParams() + ", new " + scriptParams);
                            reply(ActorMessageBuilder.xposedApi(mClientId, false, "Other script is running.", null, "not care."));
                            return;
                        }
                        Logger.d(TAG, "Reset script quota for action " + app + "/" + action);
                        ScriptExecutor.getInstance().resetQuota(300 * 1000);
                    }
                }

                RequestV0 request = ParcelCreator.buildRequestV0(app, RequestV0.TYPE_ACTION, action, body);
                Logger.d(TAG, "request is :" + request);
                conn = RemoteServiceManager.getInstance().getConnection(app);
                ResponseV0 resp = conn.requestAppAction(request);
                Logger.d(TAG, "response is:" + resp);
                reply(ActorMessageBuilder.xposedApi(mClientId, resp.getSuccess(), resp.getReason(), resp.getBody(), proxy));
                XposedContract.record(app, action, resp.getSuccess());
            } catch (Throwable e) {
                Logger.e(TAG, "ProcessXposedApi " + app + " " + action + " failed: " + e.toString(), e);
                JSONObject exceptionJson = new JSONObject();
                try {
                    exceptionJson.put("serial", serial);
                    exceptionJson.put("app", app);
                    exceptionJson.put("action", action);
                    exceptionJson.put("errorType", "RemoteServiceInvokeError");
                    exceptionJson.put("errorMsg", e.toString());
                    exceptionJson.put("errorStack", ActStringUtils.arrayToString(e.getStackTrace()));
                } catch (Exception e1) {
                }
                reply(ActorMessageBuilder.xposedApi(mClientId, false, "Invoke remote conn exception:" + e.toString(), exceptionJson.toString(), proxy));
            }
        }
    }
}