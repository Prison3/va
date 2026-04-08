package com.android.actor.grpc.process;

import android.annotation.SuppressLint;
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
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.proto.common.TGetDetailReq;
import org.apache.commons.lang3.StringUtils;
import com.android.actor.utils.ActStringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class ProcessTGetDetail extends RequestProcess<TGetDetailReq> {
    private final static String TAG = ProcessTGetDetail.class.getSimpleName();

    @SuppressLint("DefaultLocale")
    @Override
    public void process(TGetDetailReq req) throws InterruptedException {
        mClientId = req.getClientId();
        String serial = req.getSerial();
        // 任务的参数，如注册账号的代理，需要获取数据的 targetUrl
        Map<String, String> params = req.getParamsMap();
        String targetUrl = params.get("target_url");
        String proxy = params.get("register_proxy");
        String country = params.get("country");
        // lua脚本的参数，taskId
        Map<String, String> scriptParams = req.getScriptParamsMap();
        Map<String, String> MyCopyScriptParams = new HashMap<>(scriptParams);

        Logger.d(TAG, "targetUrl: " + targetUrl + ", proxy: " + proxy + " , country:" + country + ", MyCopyScriptParams: " + MyCopyScriptParams);
        Map<String, String> result = new HashMap<>();

        String checkUrlAndReturnFileName = ActStringUtils.checkUrlPattern(targetUrl, TApp.sHost);
        if (!Objects.equals(MyCopyScriptParams.get("need_shot"), "true")) {
            Logger.i(TAG, "need_shot not found in script_params, set false");
            MyCopyScriptParams.put("need_shot", "false");
        }
        if (!Objects.equals(country, "us") && !Objects.equals(country, "uk")) {
            Logger.i(TAG, "country " + country + " not legal in params");
            result.put("code", "102");
            result.put("status", "country not legal in params");
            reply(ActorMessageBuilder.tGetDetail(mClientId, serial, TApp.sTaskId, result, false));
        }

        if (checkUrlAndReturnFileName.equals("")) {
            String taskId = DeviceNumber.get() + System.currentTimeMillis();
            TApp.sTaskId = taskId;
            Logger.i(TAG, "targetUrl is not match, targetUrl: " + targetUrl);
            result.put("code", "101");
            result.put("status", "targetUrl is not match");
            reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
        } else {
            Logger.i(TAG, "targetUrl is match, targetUrl: " + targetUrl + " ; fileName: " + checkUrlAndReturnFileName);
            if (!CheckTAccountStatus()) {
                TApp.sGetDetailUrl = "";
                String taskId = DeviceNumber.get() + "-TGetDetailRegister-" + System.currentTimeMillis();
                TApp.sTaskId = taskId;
                result.put("note", "last account dead , register a new account");
                // load register script
                if (StringUtils.isEmpty(proxy)) {
                    result.put("code", "301");
                    result.put("status", "register failed because register_proxy is empty");
                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                    return;
                }
                String script = "url:" + TApp.sRegisterNewScript;
                int quota = TApp.sRegisterNewTimeout;
                if (ScriptExecutor.getInstance().isRunning()) {
                    Logger.i(TAG, "New register cmd coming, stop running script " + ScriptExecutor.getInstance().geCurrentTaskId());
                    ScriptExecutor.getInstance().stopScript();
                }
                // 改机
                BlockingReference<String> blockingReference = new BlockingReference<>();
                NewDeviceGenerator generator = new NewDeviceGenerator(ActApp.getInstance());
                generator.generate(RocketComponent.PKG_CHROMIUM, 0, null, country, false, false, true, new JSONObject(), (status, text) -> {
                    if (status == NewDeviceGenerator.MSG_DONE || status == NewDeviceGenerator.MSG_ERROR) {
                        boolean success = (status == NewDeviceGenerator.MSG_DONE);
                        blockingReference.put(success ? null : text);
                    }
                });
                try {
                    String devicemock_result = blockingReference.take();
                    if (devicemock_result != null) {
                        result.put("code", "302");
                        result.put("status", "register failed because device mock error : " + devicemock_result);
                        reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                        return;
                    }
                } catch (InterruptedException e) {
                    result.put("code", "303");
                    result.put("status", "Interupted at device mock , " + e.toString());
                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                    return;
                }
                // 代理
                Pair<Boolean, String> r = ProxyManager.getInstance().addProxy(RocketComponent.PKG_CHROMIUM, 0, proxy);
                if (!r.first) {
                    Logger.i(TAG, "process: " + r.second);
                    result.put("code", "304");
                    result.put("status", "add proxy error :" + r.second);
                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                    return;
                }
                result.put("export_proxy", proxy);
                //加载登录页面
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://www.temu.com/login.html"));
                intent.setPackage(RocketComponent.PKG_CHROMIUM);
                String msg = ActActivityManager.getInstance().startActivityAndWait(intent);
                if (msg != null) {
                    result.put("code", "305");
                    result.put("status", "start chromium error :" + msg);
                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                    return;
                }
                Thread.sleep(10000);
                Logger.d(TAG, "process: taskId = " + taskId + ", script " + script + ", quota " + quota);
                // 加载注册lua脚本
                if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(script) || !ScriptExecutor.getInstance().isPrefixValid(script)) {
                    result.put("code", "306");
                    result.put("status", "Param task_id or lua_script is empty or invalid, please check request");
                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                } else {
                    if (ActAccessibility.isStart()) {
                        if (!ScriptExecutor.getInstance().isRunning()) {
                            ScriptExecutor.getInstance().loadScript(taskId, script, MyCopyScriptParams);
                            msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                            result.put("code", msg == null ? "300" : "307");
                            result.put("status", "register-lua-script status is : " + msg);
                            reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, msg == null));
                        } else {
                            String currentTaskId = ScriptExecutor.getInstance().geCurrentTaskId();
                            String currentExecutor = StringUtils.substringBefore(currentTaskId, "-");
                            String newExecutor = StringUtils.substringBefore(taskId, "-");
                            if (!currentExecutor.isEmpty() && currentExecutor.equals(newExecutor)) {
                                ScriptExecutor.getInstance().stopScript();
                                ScriptExecutor.getInstance().loadScript(taskId, script, MyCopyScriptParams);
                                Logger.d(TAG, String.format("loadScript: %s, script %s, quota %d", taskId, script, quota));
                                msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                                Logger.d(TAG, "lua ScriptExecutor : " + msg);
                                result.put("code", msg == null ? "300" : "308");
                                result.put("status", "register-lua-script status is : " + msg + ", task " + currentTaskId + " replaced by " + taskId);
                                reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, msg == null));
                            } else {
                                result.put("code", "309");
                                result.put("status", "Actor is running lua script now, current task: " + currentTaskId);
                                reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                            }
                        }
                    } else {
                        result.put("code", "310");
                        result.put("status", "Actor not start accessibility yet");
                        reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                    }
                }

            } else {
                // load get detail script
                // no need add proxy and device mock
                TApp.sGetDetailUrl = targetUrl;
                TApp.sShortGetDetailUrl = ActStringUtils.checkUrlPattern(targetUrl, TApp.sHost);
                String taskId = DeviceNumber.get() + "-TGetDetailData-" + System.currentTimeMillis();
                TApp.sTaskId = taskId;
                MyCopyScriptParams.put("taskId", taskId);
                String export_proxy = ProxyManager.getInstance().getProxy(RocketComponent.PKG_CHROMIUM, 0).socksProxy;
                result.put("export_proxy", export_proxy);

                result.put("note", "account alive , get detail data");
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(targetUrl));
                intent.setPackage(RocketComponent.PKG_CHROMIUM);
                String msg = ActActivityManager.getInstance().startActivityAndWait(intent);
                Thread.sleep(10000);
                if (msg != null) {
                    result.put("code", "201");
                    result.put("msg", "failed to start activity: " + msg);
                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                    return;
                } else {
                    result.put("code", "200");
                    result.put("html", "");
                    String script = "url:" + TApp.sGetDetailScript;
                    int quota = TApp.sGetDetailTimeout;
                    if (ScriptExecutor.getInstance().isRunning()) {
                        Logger.i(TAG, "New register cmd coming, stop running script " + ScriptExecutor.getInstance().geCurrentTaskId());
                        ScriptExecutor.getInstance().stopScript();
                    }

                    Logger.d(TAG, "process: taskId = " + taskId + ", script " + script + ", quota " + quota);

                    if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(script) || !ScriptExecutor.getInstance().isPrefixValid(script)) {
                        result.put("code", "201");
                        result.put("status", "Param task_id or lua_script is empty or invalid, please check request");
                        reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                    } else {
                        if (ActAccessibility.isStart()) {
                            if (!ScriptExecutor.getInstance().isRunning()) {
                                ScriptExecutor.getInstance().loadScript(taskId, script, MyCopyScriptParams);
                                msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                                result.put("code", msg == null ? "200" : "202");
                                result.put("status", "get-detail-lua-script status is : " + msg);
                                reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, msg == null));
                            } else {
                                String currentTaskId = ScriptExecutor.getInstance().geCurrentTaskId();
                                String currentExecutor = StringUtils.substringBefore(currentTaskId, "-");
                                String newExecutor = StringUtils.substringBefore(taskId, "-");
                                if (!currentExecutor.isEmpty() && currentExecutor.equals(newExecutor)) {
                                    ScriptExecutor.getInstance().stopScript();
                                    ScriptExecutor.getInstance().loadScript(taskId, script, MyCopyScriptParams);
                                    Logger.d(TAG, String.format("loadScript: %s, script %s, quota %d", taskId, script, quota));
                                    msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                                    Logger.d(TAG, "lua ScriptExecutor : " + msg);
                                    result.put("code", msg == null ? "200" : "203");
                                    result.put("status", "get-detail-lua-script status is : " + msg + ", task " + currentTaskId + " replaced by " + taskId);
                                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, msg == null));
                                } else {
                                    result.put("code", "204");
                                    result.put("status", "Actor is running lua script now, current task: " + currentTaskId);
                                    reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                                }
                            }
                        } else {
                            result.put("code", "205");
                            result.put("status", "Actor not start accessibility yet");
                            reply(ActorMessageBuilder.tGetDetail(mClientId, serial, taskId, result, false));
                        }
                    }

                }
                Logger.d(TAG, "go url: " + targetUrl);

            }
        }

    }

    public boolean CheckTAccountStatus() {
        String t_account_status = SPUtils.getString(SPUtils.ModuleFile.browser_accounts, "temu", "");
        Logger.d(TAG, "temu browser_accounts is " + t_account_status);

        return t_account_status.equals("1");
    }
}
