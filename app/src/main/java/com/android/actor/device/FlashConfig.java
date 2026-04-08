package com.android.actor.device;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.topjohnwu.superuser.Shell;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class FlashConfig {

    public static final String NO_ACCESSIBILITY = "no_accessibility";
    public static final String NO_SELF_BUILD_WEBVIEW = "no_self_build_webview";
    public static final String LIMITED_EDITION = "limited_edition";

    public static boolean has(String name) {
        return new File("/data/new_stage/" + name).exists();
    }

    public static JSONObject readAsJson(String name) {
        try {
            String content = FileUtils.readFileToString(new File("/data/new_stage/" + name));
            return JSON.parseObject(content);
        } catch (Throwable e) {
            return null;
        }
    }

    public static void remove(String name) {
        Shell.su("rm -f /data/new_stage/" + name).exec();
    }
}
