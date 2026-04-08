package com.android.actor.device;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.SystemProperties;
import android.text.Html;

import androidx.annotation.NonNull;

import com.android.actor.ActApp;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.monitor.Logger;
import com.android.actor.ui.Dialogs;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BasicInfo {

    private static final String TAG = BasicInfo.class.getSimpleName();

    static class SBuilder {

        StringBuilder builder = new StringBuilder();

        void append(String s) {
            builder.append(s);
        }

        void append(String s, boolean red) {
            if (red) {
                appendRed(s);
            } else {
                append(s);
            }
        }

        void append(char c) {
            if (c == '\n') {
                append("<br/>");
            } else {
                builder.append(c);
            }
        }

        void appendRed(String s) {
            builder.append("<font color='#FF0000'>" + s + "</font>");
        }

        @NonNull
        @Override
        public String toString() {
            return builder.toString();
        }
    }

    public static void show(Activity activity) {
        SBuilder builder = new SBuilder();
        for (String packageName : new String[] {
                RocketComponent.PKG_ALPHA,
                RocketComponent.PKG_TWEBVIEW,
                RocketComponent.PKG_WEBVIEW,
                RocketComponent.PKG_CHROMIUM,
                RocketComponent.PKG_NEW_STAGE,
        }) {
            int versionCode = ActPackageManager.getInstance().getVersionCode(packageName);
            builder.append(packageName + ": " + versionCode);
            builder.append('\n');
        }
        String strROM = SystemProperties.get("ro.build.version.incremental");
       // boolean romOK = Integer.parseInt(strROM) <= 1600699973;
        builder.append("ROM: " + strROM);
        builder.append('\n');
        builder.append('\n');

        List<String> cameraIdList = new ArrayList<>();
        boolean cameraOK = false;
        try {
            CameraManager cameraManager = (CameraManager) ActApp.getInstance().getSystemService(Context.CAMERA_SERVICE);
            String[] idList = cameraManager.getCameraIdList();
            for (String id : idList) {
                cameraManager.getCameraCharacteristics(id);
                cameraIdList.add(id);
            }
            if (idList.length < 2) {
                throw new Exception("Camera id list " + ArrayUtils.toString(idList));
            }
            cameraOK = true;
        } catch (Throwable e) {
            Logger.e(TAG, "Camera broken.", e);
        }
        builder.append("Camera: " + cameraIdList, !cameraOK);
        builder.append('\n');

        try {
            if (FileUtils.readFileToString(new File("/proc/self/maps")).contains("/memfd:/system/framework/arm64/boot.oat (deleted)")) {
                builder.append("/memfd:/system/framework/arm64/boot.oat (deleted)", true);
            } else {
                builder.append("proc maps boot.oat check ok.");
            }
        } catch (Throwable e) {
            builder.append(e.toString(), true);
        }

        builder.append('\n');
        Dialogs.showTextDialog(activity, "Basic info", Html.fromHtml(builder.toString()));
    }
}
