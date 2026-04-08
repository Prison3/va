package com.android.actor.grpc.process;

import android.util.Pair;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActAccessibility;
import com.android.actor.ActApp;
import com.android.actor.device.DeviceNumber;
import com.android.actor.device.NewDeviceGenerator;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.ScriptForAction;
import com.android.actor.script.ScriptExecutor;
import com.android.actor.script.lua.LuaExecutor;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.proto.common.AppRegisterReq;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

public class ProcessAppRegister extends RequestProcess<AppRegisterReq> {
    private final static String TAG = ProcessAppRegister.class.getSimpleName();


    @Override
    public void process(AppRegisterReq req) throws InterruptedException {
        mClientId = req.getClientId();

        String pkgName = req.getPkgName();
        String proxy = req.getProxy();
        String country = req.getCountry();
        Map<String, String> scriptParams = req.getScriptParamsMap();

        String taskId = DeviceNumber.get() + "-" + System.currentTimeMillis();

        if (!country.equals("us") && !country.equals("uk")) {
            reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, "country is invalid"));
            return;
        }

        Logger.d(TAG,"country: " + country);
        if (StringUtils.isEmpty(proxy) && !ActStringUtils.checkProxyPattern(proxy)) {
            reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, "proxy is invalid"));
            return;
        }
        if (!ScriptForAction.instance.checkPackageNameValid(pkgName)) {
            reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, "pkg_name is invalid"));
            return;
        }

        String app_account = SPUtils.getString(SPUtils.ModuleFile.app_accounts, pkgName, "0");

        if (Objects.equals(app_account, "1")) {
            reply(ActorMessageBuilder.appRegister(mClientId, "", true, "go on request, no app init"));
            return;
        }


        String script = ScriptForAction.instance.getScriptUrl(pkgName, ScriptForAction.getActionRegister());
        if (StringUtils.isEmpty(script)) {
            reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, "register script is not set in etcd"));
        }

        if (ScriptExecutor.getInstance().isRunning()) {
            Logger.i(TAG, "New register cmd coming, stop running script " + ScriptExecutor.getInstance().geCurrentTaskId());
            ScriptExecutor.getInstance().stopScript();
        }

        BlockingReference<String> blockingReference = new BlockingReference<>();
        NewDeviceGenerator generator = new NewDeviceGenerator(ActApp.getInstance());
        generator.generate(pkgName, 0, null, country, false, false, true, new JSONObject(), (status, text) -> {
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
        Pair<Boolean, String> r = ProxyManager.getInstance().addProxy(pkgName, 0, proxy);
        if (!r.first) {
            Logger.i(TAG, "process: " + r.second);
            reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, r.second));
            return;
        }
        int quota = ScriptForAction.instance.getScriptQuota(pkgName, ScriptForAction.getActionRegister());
        Logger.d(TAG, "process: taskId = " + taskId + ", script " + script + ", quota " + quota);
        if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(script) || !ScriptExecutor.getInstance().isPrefixValid(script)) {
            reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, "taskId or script is invalid"));
            return;
        } else {
            if (ActAccessibility.isStart()) {
                if (!ScriptExecutor.getInstance().isRunning()) {
                    ScriptExecutor.getInstance().loadScript(taskId, script, scriptParams);
                    String msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                    reply(ActorMessageBuilder.appRegister(mClientId, taskId, msg == null, msg));
                } else {
                    String currentTaskId = ScriptExecutor.getInstance().geCurrentTaskId();
                    String currentExecutor = StringUtils.substringBefore(currentTaskId, "-");
                    String newExecutor = StringUtils.substringBefore(taskId, "-");
                    if (!currentExecutor.isEmpty() && currentExecutor.equals(newExecutor)) {
                        ScriptExecutor.getInstance().stopScript();
                        ScriptExecutor.getInstance().loadScript(taskId, script, scriptParams);
                        Logger.d(TAG, "loadScript: " + taskId + ", script " + script + ", quota " + quota);
                        String msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                        Logger.d(TAG, "lua ScriptExecutor : " + msg);
                        reply(ActorMessageBuilder.appRegister(mClientId, taskId, msg == null, msg));
                    } else {
                        reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, "script is running"));
                    }
                    reply(ActorMessageBuilder.appRegister(mClientId, taskId, false, "Actor not start accessibility yet"));
                }
            }
        }

    }
}
