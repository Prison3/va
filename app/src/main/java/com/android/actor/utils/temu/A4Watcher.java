package com.android.actor.utils.temu;

import android.webkit.WebSettings;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;
import com.topjohnwu.superuser.nio.ExtendedFile;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class A4Watcher {

    private static final String TAG = A4Watcher.class.getSimpleName();
    public static final String DIR_PATH = "/sdcard/Android/data/org.chromium.chrome";

    public static boolean check() {
        ExtendedFile dir = Libsu.fs().getFile(DIR_PATH);
        if (!dir.exists()) {
            return false;
        }

        String[] names = dir.list();
        for (String name : names) {
            if (name.startsWith("a4.")) {
                try {
                    ExtendedFile file = Libsu.fs().getFile(DIR_PATH + "/" + name);
                    String content = Libsu.readFileToString(file);
                    JSONObject jBody = JSON.parseObject(content);
                    String data = jBody.getString("data");
                    Logger.d(TAG, "data " + data);
                    String[] lines = A4.Decode(data);

                    boolean hasMoveData = false;
                    for (String line : lines) {
                        Logger.d(TAG, "line " + line);
                        if (line.endsWith("moveData")) {
                            Logger.i(TAG, "Got moveData from " + name);
                            hasMoveData = true;
                            if (!file.renameTo(new File(DIR_PATH + "/a4"))) {
                                throw new RuntimeException("Can't rename " + file);
                            }
                            break;
                        }
                    }
                    if (!hasMoveData) {
                        Logger.v(TAG, "Delete " + name);
                        if (!file.delete()) {
                            throw new RuntimeException("Can't delete " + file);
                        }
                    }
                } catch (Throwable e) {
                    Logger.e(TAG, "Error to read " + name, e);
                }
            }
        }
        if (Libsu.fs().getFile(DIR_PATH + "/a4").exists()) {
            combineTResult();
            return true;
        }
        return false;
    }

    public static String getA4() {
        try {
            return Libsu.readFileToString(DIR_PATH + "/a4");
        } catch (Throwable e) {
            throw new RuntimeException("Can't read a4.");
        }
    }

    public static String getCookies() {
        try {
            return Libsu.readFileToString(DIR_PATH + "/cookies");
        } catch (Throwable e) {
            throw new RuntimeException("Can't read cookies.");
        }
    }

    private static void combineTResult() {
        try {
            String a4 = getA4();
            String cookies = getCookies();
            if (a4 == null || cookies == null) {
                return;
            }
            JSONObject jBody = new JSONObject();
            jBody.put("a4", a4);
            jBody.put("cookie", cookies);

            int uid = ActPackageManager.getInstance().getUidOfPackage(RocketComponent.PKG_CHROMIUM);
            File file = new File("/data/new_stage/" + uid + "/device_param_list.json");
            String ua = null;
            if (file.exists()) {
                String content = FileUtils.readFileToString(file);
                JSONObject jContent = JSON.parseObject(content);
                ua = jContent.getString("web_user_agent_desktop");
            }
            if (ua == null) {
                Logger.w(TAG, "No mock? use real ua instead.");
                ua = WebSettings.getDefaultUserAgent(ActApp.getInstance());
            }
            jBody.put("user_agent", ua);
            Libsu.writeStringToFile(DIR_PATH + "/t_result", jBody.toString());
        } catch (Throwable e) {
            Logger.e(TAG, "Exception at combile t_result.", e);
        }
    }
}
