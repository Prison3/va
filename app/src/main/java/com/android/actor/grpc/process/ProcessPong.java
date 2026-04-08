package com.android.actor.grpc.process;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActPackageInstaller;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.AppPermissions;
import com.android.actor.device.FactoryReset;
import com.android.actor.device.NewStage;
import com.android.actor.models.ModelConfig;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.ScriptForAction;
import com.android.actor.remote.TApp;
import com.android.actor.script.dex.DexUpdater;
import com.android.actor.utils.downloader.DeviceFilesUpdater;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.proxy.V2ray;
import com.android.proto.actor.Pong;
import com.android.actor.control.SelfUpdater;
import com.android.actor.grpc.GRPCManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class ProcessPong extends RequestProcess<Pong> {

    private final static String TAG = ProcessPong.class.getSimpleName();
    private static final File ONLINE_APPS_FILE = new File(NewStage.PARAMETERS_PATH + "/online_apps.json");
    private static final File ONLINE_APPS_TMP_FILE = new File(NewStage.PARAMETERS_PATH + "/online_apps.json.tmp");

    @Override
    public void process(Pong req) {
        // actor->node without clientId
        GRPCManager.getInstance().getActorChannel().onPong();

        // self update
        String romUrl = req.getRomUrl();
        if (StringUtils.isEmpty(romUrl)) {
            SelfUpdater.instance().apkUpdate(req.getApkUpdateUrl());
        } else {
            //SelfUpdater.instance().otaUpdate(req.getApkUpdateUrl(), romUrl);
        }

        //processAppConfig(req.getAppConfig());
    }

    private void processAppConfig(String content) {
        if (StringUtils.isEmpty(content)) {
            return;
        }
        Logger.d(TAG, "processAppConfig " + content);
        JSONObject jContent = JSON.parseObject(content);

        FactoryReset.execFromConfig(jContent.getString("factory_reset"));
        JSONObject jAppsToInstall = jContent.getJSONObject("apps_to_install");
        ActPackageInstaller.instance().installFromConfig(jAppsToInstall);

        JSONObject jTApp = jContent.getJSONObject("tapp");
        TApp.sRegisterNewScript = jTApp.getString("register_new_script");
        TApp.sRegisterNewTimeout = jTApp.getIntValue("register_new_timeout");
        TApp.sSwipeSyncScript = jTApp.getString("swipe_sync_script");
        TApp.sSwipeSyncTimeout = jTApp.getIntValue("swipe_sync_timeout");
        TApp.sGetDetailScript = jTApp.getString("get_detail_script");
        TApp.sGetDetailTimeout = jTApp.getIntValue("get_detail_timeout");

        ScriptForAction.instance.updateConfig(jContent.getJSONObject("script_for_action"));
        DexUpdater.instance.setUrl(
                jContent.getString("dex_script"),
                jContent.getString("dex_script_test"),
                jContent.getString("dex_script_test_devices"),
                jContent.getJSONArray("dex_script_branches")
        );

        V2ray.setParentSocks(jContent.getString("v2ray_parent_socks"));
        ProxyManager.getInstance().updateDirectDomains(jContent.getJSONObject("proxy_direct_domains"));

        AppPermissions.instance.updateConfig(jContent.getJSONObject("app_allow_permissions"));

        JSONObject aiModels = jContent.getJSONObject("ai_models");
        Logger.d(TAG, "processAppConfig aiModels " + aiModels);
        ModelConfig.instance.updateConfig(aiModels);

        try {
            JSONArray jApps = jContent.getJSONArray("apps");
            if (jApps != null) {
                FileUtils.writeStringToFile(ONLINE_APPS_TMP_FILE, jApps.toString());
                ONLINE_APPS_TMP_FILE.setReadable(true, false);
                if (!ONLINE_APPS_TMP_FILE.renameTo(ONLINE_APPS_FILE)) {
                    throw new Exception("Can't rename " + ONLINE_APPS_TMP_FILE + " to " + ONLINE_APPS_FILE);
                }
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Error to update " + ONLINE_APPS_FILE, e);
        }

        if (ActPackageManager.getInstance().isAppInstalled("com.xingin.xhs")) {
            DeviceFilesUpdater.instance().update(jContent.getString("device_files"));
        }
    }
}
