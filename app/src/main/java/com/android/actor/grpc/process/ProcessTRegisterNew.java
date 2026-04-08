package com.android.actor.grpc.process;

import android.content.Intent;
import android.net.Uri;
import android.util.Pair;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActAccessibility;
import com.android.actor.ActApp;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.device.DeviceNumber;
import com.android.actor.device.NewDeviceGenerator;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.TApp;
import com.android.actor.script.ScriptExecutor;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.temu.A4;
import com.android.proto.common.TRegisterNewReq;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;


public class ProcessTRegisterNew extends RequestProcess<TRegisterNewReq> {
    private final static String TAG = ProcessTRegisterNew.class.getSimpleName();

    @Override
    public void process(TRegisterNewReq req) throws InterruptedException {
        String targetApp = RocketComponent.PKG_CHROMIUM;
        //String mainActivity = "a.test.collect.TWebviewActivity";

        TApp.sTaskId = DeviceNumber.get() + "-" + System.currentTimeMillis();
        String taskId = TApp.sTaskId;
        mClientId = req.getClientId();
        Map<String, String> params = req.getParamsMap();
        String proxy = params.get("proxy");
        String a4 = params.get("a4");
        String country = params.get("country");

        if (StringUtils.isEmpty(proxy)) {
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, "proxy is empty"));
            return;
        }
        if (!StringUtils.isEmpty(a4)) {
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, "a4 must be empty"));
            return;
        }

        if (!Objects.equals(country, "us") && !Objects.equals(country, "uk")) {
            Logger.i(TAG, "country " + country + " not legal in params");
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, "country is invalid"));
        }

        String script = req.getScript();
        Map<String, String> scriptParams = req.getScriptParamsMap();
        if (StringUtils.isEmpty(script)) {
            script = "url:" + TApp.sRegisterNewScript;
        }
        int quota = TApp.sRegisterNewTimeout;

        if (ScriptExecutor.getInstance().isRunning()) {
            Logger.i(TAG, "New register cmd coming, stop running script " + ScriptExecutor.getInstance().geCurrentTaskId());
            ScriptExecutor.getInstance().stopScript();
        }

        JSONObject jParams = new JSONObject();
        if (!StringUtils.isEmpty(a4)) {
            String userAgent = A4.getUserAgent(a4);
            jParams.put("web_user_agent", userAgent);
        }

        BlockingReference<String> blockingReference = new BlockingReference<>();
        NewDeviceGenerator generator = new NewDeviceGenerator(ActApp.getInstance());
        generator.generate(targetApp, 0, null, country, false, false, true, jParams, (status, text) -> {
            if (status == NewDeviceGenerator.MSG_DONE || status == NewDeviceGenerator.MSG_ERROR) {
                boolean success = (status == NewDeviceGenerator.MSG_DONE);
                blockingReference.put(success ? null : text);
            }
        });
        try {
            String result = blockingReference.take();
            if (result != null) {
                Logger.e(TAG, "New device error, " + result);
                reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, result));
                return;
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "Interrupted at new device.", e);
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, e.toString()));
            return;
        }

        Pair<Boolean, String> r = ProxyManager.getInstance().addProxy(targetApp, 0, proxy);
        if (!r.first) {
            Logger.i(TAG, "process: " + r.second);
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, r.second));
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://www.temu.com/login.html"));
        intent.setPackage(targetApp);
        String msg = ActActivityManager.getInstance().startActivityAndWait(intent);
        if (msg != null) {
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, "Failed to start chromium, " + msg));
            return;
        }


        /*try {
            Intent intent = new Intent();
            intent.setClassName(targetApp, mainActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!StringUtils.isEmpty(a4)){
                intent.putExtra("a4", a4);
            }
            ActActivityManager.getInstance().startActivityByIntent(intent);
        }catch (Throwable e){
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, e.toString()));
            return;
        }*/
        // wait chrmomium load url
        Thread.sleep(10000);
        Logger.d(TAG, "process: taskId = " + taskId + ", script " + script + ", quota " + quota);
        if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(script) || !ScriptExecutor.getInstance().isPrefixValid(script)) {
            reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, "Param task_id or lua_script is empty or invalid, please check request."));
        } else {
            if (ActAccessibility.isStart()) {
                if (!ScriptExecutor.getInstance().isRunning()) {
                    ScriptExecutor.getInstance().loadScript(taskId, script, scriptParams);
                    msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                    reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, msg == null, msg));
                } else {
                    String currentTaskId = ScriptExecutor.getInstance().geCurrentTaskId();
                    String currentExecutor = StringUtils.substringBefore(currentTaskId, "-");
                    String newExecutor = StringUtils.substringBefore(taskId, "-");
                    if (!currentExecutor.isEmpty() && currentExecutor.equals(newExecutor)) {
                        ScriptExecutor.getInstance().stopScript();
                        ScriptExecutor.getInstance().loadScript(taskId, script, scriptParams);
                        Logger.d(TAG, "loadScript: " + taskId + ", script " + script + ", quota " + quota);
                        msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                        Logger.d(TAG, "lua ScriptExecutor : " + msg);
                        reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, msg == null,
                                msg + ", task " + currentTaskId + " replaced by " + taskId));
                    } else {
                        reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, "Actor is running lua script now, current task: " + currentTaskId));
                    }
                }
            } else {
                reply(ActorMessageBuilder.t_registerNew(mClientId, taskId, false, "Actor not start accessibility yet"));
            }
        }

    }

    public void runTRegisterScript() {

    }
}
