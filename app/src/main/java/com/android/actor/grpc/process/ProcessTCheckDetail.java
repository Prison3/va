package com.android.actor.grpc.process;

import com.android.actor.control.RocketComponent;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.TApp;
import com.android.actor.script.lua.LuaScript;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.temu.A4Watcher;
import com.android.proto.common.TCheckDetailReq;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ProcessTCheckDetail extends RequestProcess<TCheckDetailReq> {
    private final static String TAG = ProcessTCheckDetail.class.getSimpleName();

    @Override
    public void process(TCheckDetailReq req) throws InterruptedException {
        mClientId = req.getClientId();
        String serial = req.getSerial();
        Map<String, String> result = new HashMap<>();
        String proxy = ProxyManager.getInstance().getProxy(RocketComponent.PKG_CHROMIUM, 0).socksProxy;
        result.put("export_proxy", proxy);
        String taskId = SPUtils.getString(SPUtils.ModuleFile.script, "taskId");
        Logger.d(TAG, "process taskId " + taskId);
        boolean match = taskId != null && taskId.equals(TApp.sTaskId);
        String status = match ? SPUtils.getString(SPUtils.ModuleFile.script, "status") : LuaScript.LuaStatus.notfound.name();
        String script_result = match ? SPUtils.getString(SPUtils.ModuleFile.script, "result") : "";
        Logger.d(TAG, "match " + match + " status " + status + " result " + script_result);
        Logger.d(TAG, "taskId " + taskId);
        assert taskId != null;
        if (taskId.contains("TGetDetailRegister")) {
            result.put("Lua-Action", "TGetDetailRegister");
            Logger.d(TAG, "register reply , read a4 from t_result");
            String registerInformation = "";
            if (LuaScript.LuaStatus.success.name().equals(status)) {
                try {
                    registerInformation = Libsu.readFileToString(A4Watcher.DIR_PATH + "/t_result");
                    result.put("registerInformation", registerInformation);
                    result.put("code", "300");
                    result.put("msg", "success");
                } catch (Throwable e) {
                    status = LuaScript.LuaStatus.failed.name();
                }
            }
            Logger.d(TAG, "script_result : " + script_result);
        } else if (taskId.contains("TGetDetailData")) {
            result.put("Lua-Action", "TGetDetailData");
            if (LuaScript.LuaStatus.success.name().equals(status)) {
                String TGetDetailDataPATH = TApp.path + TApp.sShortGetDetailUrl;
                result.put("sGetDetailUrl", TApp.sGetDetailUrl);
                Logger.d(TAG, String.format("get detail data from %s", TGetDetailDataPATH));
                try {
                    String detailData = Libsu.readFileToString(TGetDetailDataPATH);
                    Pattern pattern = Pattern.compile("window.rawData=(\\{.*?\\});");
                    Matcher matcher = pattern.matcher(detailData);
                    if (matcher.find()) {
                        detailData = matcher.group(1);
                        result.put("TGetDetailData", detailData);
                        result.put("code", String.valueOf(200));
                        result.put("msg", "success");
                    } else {
                        Logger.e(TAG, "can't find window.rawData");
                        result.put("code", String.valueOf(201));
                        result.put("msg", "can't find window.rawData");
                        result.put("TGetDetailData", "can't find window.rawData");
                    }
                } catch (Throwable e) {
                    Logger.e(TAG, "lua success but TGetDetailData can't be read.", e);
                    status = LuaScript.LuaStatus.failed.name();
                }
            }
        }
        Logger.d(TAG, "script_result : " + script_result);
        Logger.d(TAG, "result : " + result);
        reply(ActorMessageBuilder.tCheckDetail(
                mClientId, serial, TApp.sTaskId,
                status, match ? SPUtils.getString(SPUtils.ModuleFile.script, "record") : null,
                script_result, result));
    }
}
