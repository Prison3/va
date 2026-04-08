package com.android.actor.grpc.process;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActAccessibility;
import com.android.actor.ActApp;
import com.android.actor.control.AccessibilityNodeInfoDumper;
import com.android.actor.control.ActActivityManager;
import com.android.actor.device.BrightnessController;
import com.android.actor.device.NewDeviceGenerator;
import com.android.actor.fi.FIRequest;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.ChromiumRequest;
import com.android.actor.utils.ViewUtils;
import com.android.actor.utils.screen.Screenshot;
import com.android.actor.utils.shell.Shell;
import com.android.proto.nonblocking.WEditorReq;
import com.google.protobuf.ByteString;

import org.apache.commons.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class ProcessWEditor extends RequestProcess<WEditorReq> {

    private static final String TAG = ProcessWEditor.class.getSimpleName();
    private static final int MAX_LOG_BYTES = 40 * 1024 * 1024;
    private static final JSONObject JSON_EMPTY = new JSONObject(0, false);

    @Override
    public void process(WEditorReq req) {
        mClientId = req.getClientId();
        String cmd = req.getCmd();
        JSONObject jBody = JSON.parseObject(req.getBody());
        if (jBody == null) {
            jBody = JSON_EMPTY;
        }
        Logger.d(TAG, "cmd " + cmd);

        try {
            if ("connect".equals(cmd)) {
                connect();
            } else if ("screenshot".equals(cmd)) {
                screenshot();
            } else if ("app_current".equals(cmd)) {
                appCurrent();
            } else if ("dump_hierarchy".equals(cmd)) {
                dumpHierarchy();
            } else if ("window_size".equals(cmd)) {
                windowSize();
            } else if ("log_current".equals(cmd)) {
                logCurrent();
            } else if ("log_history_list".equals(cmd)) {
                logHistoryList();
            } else if ("log_history".equals(cmd)) {
                logHistory(jBody);
            } else if ("put_system_setting".equals(cmd)) {
                putSystemSetting(jBody);
            } else if ("set_brightness".equals(cmd)) {
                setBrightness(jBody);
            } else if ("new_device".equals(cmd)) {
                newDevice(jBody);
            } else if ("chromium_request".equals(cmd)) {
                chromiumRequest(jBody);
            } else if ("fi_request".equals(cmd)) {
                fiRequest(jBody);
            } else {
                replyResp(false, "unknown cmd " + cmd, null);
            }
        } catch (Throwable e) {
            Logger.e(TAG, "process cmd " + cmd + " exception.", e);
            replyResp(false, e.toString(), null);
        }
    }

    private void connect() {
        JSONObject jBody = new JSONObject();
        int pointerLocation = 0;
        /*try {
            pointerLocation = Settings.System.getInt(ActApp.getInstance().getContentResolver(), "pointer_location");
        } catch (Settings.SettingNotFoundException e) {
            Logger.e(TAG, "SettingNotFoundException", e);
        }*/
        jBody.put("pointer_location", pointerLocation);
        replyResp(true, null, jBody);
    }

    private void screenshot() {
        byte[] bytes = Screenshot.takeJPG();
        replyResp(true, null, null, bytes);
    }

    private void appCurrent() {
        JSONObject jBody = new JSONObject();
        jBody.put("package", ActActivityManager.getInstance().getCurrentApp());
        jBody.put("activity", ActActivityManager.getInstance().getCurrentActivity());
        replyResp(true, null, jBody);
    }

    private void dumpHierarchy() {
        JSONObject jBody = new JSONObject();
        AccessibilityNodeInfo root = ActAccessibility.getInstance().getRootInActiveWindow();
        if (root != null) {
            DisplayManager displayManager = (DisplayManager) ActApp.getInstance().getSystemService(Context.DISPLAY_SERVICE);
            Display display = displayManager.getDisplays()[0];
            int rotation = display.getRotation();
            jBody.put("xml", AccessibilityNodeInfoDumper.dumpWindow(root, rotation, ViewUtils.getScreenWidth(), ViewUtils.getScreenHeight()));
            replyResp(true, null, jBody);
        } else {
            replyResp(false, "root is null", null);
        }
    }

    private void windowSize() {
        JSONObject jBody = new JSONObject();
        jBody.put("width", ViewUtils.getScreenWidth());
        jBody.put("height", ViewUtils.getScreenHeight());
        replyResp(true, null, jBody);
    }

    private void logCurrent() throws IOException {
        List<String> _logs = Shell.execRootCmd("logcat -d");
        String logs = String.join("\n", _logs);
        byte[] bytes = logs.getBytes();
        if (bytes.length > MAX_LOG_BYTES) {
            Logger.d(TAG, "limit log to 40M.");
            bytes = compressBytes(bytes, bytes.length - MAX_LOG_BYTES, MAX_LOG_BYTES);
        } else {
            bytes = compressBytes(bytes);
        }
        Logger.d(TAG, "log bytes size " + bytes.length);
        replyResp(true, null, null, bytes);
    }

    private void logHistoryList() {
        File dir = ActApp.getInstance().getExternalFilesDir("log");
        File[] files = dir.listFiles();
        JSONObject jBody = new JSONObject();
        JSONArray jNames = new JSONArray(files.length);
        for (File file : files) {
            JSONObject jFile = new JSONObject();
            jFile.put("name", file.getName());
            jFile.put("size", file.length());
            jNames.add(jFile);
        }
        jBody.put("names", jNames);
        replyResp(true, null, jBody);
    }

    private void logHistory(JSONObject jReqBody) throws IOException {
        String name = jReqBody.getString("name");
        File dir = ActApp.getInstance().getExternalFilesDir("log");
        File file = new File(dir, name);
        if (file.exists()) {
            byte[] bytes = FileUtils.readFileToByteArray(file);
            if (bytes.length > MAX_LOG_BYTES) {
                Logger.d(TAG, "limit log to 40M.");
                bytes = compressBytes(bytes, bytes.length - MAX_LOG_BYTES, MAX_LOG_BYTES);
            } else {
                bytes = compressBytes(bytes);
            }
            Logger.d(TAG, "log bytes size " + bytes.length);
            replyResp(true, null, null, bytes);
        } else {
            replyResp(false, "file not exists.", null);
        }
    }

    private void putSystemSetting(JSONObject jReqBody) {
        boolean success = false;
        for (Map.Entry<String, Object> entry : jReqBody.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            Logger.d(TAG, "Set system setting " + key + " " + value);
            success = Settings.System.putString(ActApp.getInstance().getContentResolver(), key, value);
            if (!success) {
                break;
            }
        }
        replyResp(success, null, null);
    }

    private void setBrightness(JSONObject jReqBody) {
        int timeout = BrightnessController.DEFAULT_TIMEOUT;
        if (jReqBody.containsKey("timeout")) {
            timeout = jReqBody.getIntValue("timeout");
        }
        BrightnessController.instance().acquireScreenReadable(timeout);
        replyResp(true, null, null);
    }

    private void newDevice(JSONObject jReqBody) {
        NewDeviceGenerator generator = new NewDeviceGenerator(ActApp.getInstance());
        String packageName = jReqBody.getString("package_name");
        String model = null;
        if (jReqBody.containsKey("model")) {
            model = jReqBody.getString("model");
        }
        String country = null;
        if (jReqBody.containsKey("country")) {
            country = jReqBody.getString("country");
        }
        boolean randomGPS = false;
        if (jReqBody.containsKey("random_gps")) {
            randomGPS = jReqBody.getBooleanValue("random_gps");
        }
        boolean isModify = true;
        if (jReqBody.containsKey("is_modify")) {
            isModify = jReqBody.getBooleanValue("is_modify");
        }
        boolean useSim = false;
        if (jReqBody.containsKey("use_sim")) {
            useSim = jReqBody.getBooleanValue("use_sim");
        }
        List<String> output = new ArrayList<>();
        generator.generate(packageName, 0, model, country, randomGPS, useSim, isModify, (status, text) -> {
            output.add(text);
            if (status == NewDeviceGenerator.MSG_DONE || status == NewDeviceGenerator.MSG_ERROR) {
                boolean success = (status == NewDeviceGenerator.MSG_DONE);
                String body = String.join("\n", output);
                replyResp(success, success ? null : body, success ? new JSONObject() {{
                    put("output", body);
                }} : null);
            }
        });
    }

    private void chromiumRequest(JSONObject jBody) throws Throwable {
        ChromiumRequest.instance.startRequest(jBody, (success, msg, content) -> {
            replyStringResp(success, msg, content);
        });
    }

    private void fiRequest(JSONObject jBody) throws Throwable {
        String packageName = jBody.getString("package_name");
        FIRequest.get(packageName).startRequest(jBody, (success, str) -> {
            replyStringResp(success, success ? null : str, success ? str : null);
        });
    }

    private byte[] compressBytes(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(output);
        gzip.write(bytes);
        gzip.close();
        return output.toByteArray();
    }

    private byte[] compressBytes(byte[] bytes, int off, int len) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(output);
        gzip.write(bytes, off, len);
        gzip.close();
        return output.toByteArray();
    }

    private void replyResp(boolean success, String reason, JSONObject body) {
        replyStringResp(success, reason, body != null ? body.toString() : null);
    }

    private void replyStringResp(boolean success, String reason, String body) {
        replyStringResp(success, reason, body, null);
    }

    private void replyResp(boolean success, String reason, JSONObject body, byte[] bytes) {
        replyStringResp(success, reason, body != null ? body.toString() : null, bytes);
    }

    private void replyStringResp(boolean success, String reason, String body, byte[] bytes) {
        if (body == null) {
            body = "{}";
        }
        ByteString binary = ByteString.EMPTY;
        if (bytes != null) {
            binary = ByteString.copyFrom(bytes);
        }
        reply(ActorMessageBuilder.weditor(mClientId, success, reason, body, binary));
    }
}
