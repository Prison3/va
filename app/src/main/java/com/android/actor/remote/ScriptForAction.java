package com.android.actor.remote;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.grpc.process.ProcessAppRegister;
import com.android.actor.monitor.Logger;

import java.util.Map;

public class ScriptForAction {
    private final static String TAG = ScriptForAction.class.getSimpleName();
    public static final ScriptForAction instance = new ScriptForAction();
    private JSONObject mConfig;

    public static String getActionRegister() {
        return "app_init";
    }

    public void updateConfig(JSONObject jConfig) {
        Logger.d(TAG, "updateConfig :" + jConfig);
        mConfig = jConfig;
    }

    public String getScriptUrl(String packageName, String action) {
        if (mConfig != null) {
            JSONObject jApp = mConfig.getJSONObject(packageName);
            if (jApp != null) {
                JSONObject jAction = jApp.getJSONObject(action);
                if (jAction == null) {
                    Logger.d(TAG, "getScriptUrl " + packageName + " " + action + " " + jApp + " " + jAction);
                    return null;
                }
                Logger.d(TAG, "getScriptUrl " + packageName + " " + action + " " + jAction.toString());
                String script = jAction.getString("script");
                Logger.d(TAG, "getScriptUrl " + packageName + " " + action + " " + jAction + " " + script);
                return script;
            }
        }
        return null;
    }

    public int getScriptQuota(String packageName, String action) {
        if (mConfig != null) {
            JSONObject jApp = mConfig.getJSONObject(packageName);
            if (jApp != null) {
                JSONObject jAction = jApp.getJSONObject(action);
                if (jAction == null) {
                    Logger.d(TAG, "getScriptQuota " + packageName + " " + action + " " + jApp + " " + jAction);
                    return 30000;
                }
                Integer quota = jAction.getInteger("quota");
                Logger.d(TAG, "getScriptQuota " + packageName + " " + action + " " + jAction + " " + quota);
                return quota;
            }
        }
        return 30000;
    }

    public boolean checkPackageNameValid(String packageName) {
        if (mConfig != null) {
            JSONObject jUrls = mConfig.getJSONObject(packageName);
            Logger.d(TAG, "checkPackageNameValid " + packageName + " " + jUrls);
            if (jUrls != null) {
                return true;
            }
        }
        return false;
    }
}
