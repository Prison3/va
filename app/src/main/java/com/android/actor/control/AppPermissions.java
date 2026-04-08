package com.android.actor.control;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.monitor.Logger;

public class AppPermissions {

    private static final String TAG = AppPermissions.class.getSimpleName();
    public static final AppPermissions instance = new AppPermissions();
    private JSONObject mConfig;

    private AppPermissions() {
    }

    public void updateConfig(JSONObject jConfig) {
        if (jConfig == null) {
            jConfig = new JSONObject();
        }
        if (jConfig.equals(mConfig)) {
            return;
        }
        mConfig = jConfig;
        apply();
    }

    private void apply() {
        Logger.d(TAG, "Apply new app allow permissions.");
        mConfig.forEach((packageName, _permissions) -> {
            JSONArray jPermissions = (JSONArray) _permissions;
            ActPackageManager.getInstance().resetRuntimePermissions(packageName, jPermissions.toJavaList(String.class));
        });
    }

    public void apply(String packageName) {
        JSONArray jPermissions = mConfig.getJSONArray(packageName);
        if (jPermissions != null) {
            ActPackageManager.getInstance().resetRuntimePermissions(packageName, jPermissions.toJavaList(String.class));
        } else {
            ActPackageManager.getInstance().revokeRuntimePermissions(packageName);
        }
    }
}
