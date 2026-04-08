package com.android.actor.utils.xhs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;
import com.topjohnwu.superuser.nio.ExtendedFile;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DddWatcher extends Thread {

    private static final String TAG = DddWatcher.class.getSimpleName();
    public static final DddWatcher instance = new DddWatcher();
    private Map<Param, String> mParams = new HashMap<>();
    private Set<String> mCheckedNames = new HashSet<>();

    static class Param {
        String x116;
        String x117;

        Param(String x116, String x117) {
            this.x116 = x116;
            this.x117 = x117;
        }

        @NonNull
        @Override
        public String toString() {
            return "x116 " + x116 + "\n x117 " + x117;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return toString().equals(obj.toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }

    private DddWatcher() {
        super(TAG);
    }

    @Override
    public synchronized void start() {
        setPriority(MIN_PRIORITY);
        super.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(10 * 1000);
            } catch (Throwable e) {
            }
            try {
                checkOnce();
            } catch (Throwable e) {
                Logger.e(TAG, "Exception in xhs ddd check.", e);
            }
        }
    }

    private void checkOnce() throws IOException {
        ExtendedFile[] files = Libsu.listFiles("/sdcard/Android/data/com.xingin.xhs");
        if (files == null || files.length == 0) {
            mParams.clear();
            mCheckedNames.clear();
            return;
        }
        for (ExtendedFile file : files) {
            if (!file.getName().startsWith("ddd") || mCheckedNames.contains(file.getName())) {
                continue;
            }
            mCheckedNames.add(file.getName());
            try {
                String content = Libsu.readFileToString(file);
                JSONObject jContent = JSON.parseObject(content);
                String x116 = jContent.getString("x116");
                if (!StringUtils.isEmpty(x116)) {
                    String x117 = jContent.getString("x117");
                    Param param = new Param(x116, x117);
                    if (!mParams.containsKey(param)) {
                        mParams.put(param, file.getName());
                    }
                }
            } catch (JSONException e) {
                Logger.w(TAG, "Invalid json " + file);
            } catch (Throwable e) {
                Logger.e(TAG, "Error to check " + file, e);
            }
        }

        if (mParams.size() > 0) {
            Logger.d(TAG, "--------- xhs ddd start ----------");
            for (Map.Entry<Param, String> entry : mParams.entrySet()) {
                Logger.d(TAG, "xhs " + entry.getValue() + ", " + entry.getKey());
            }
            Logger.d(TAG, "--------- xhs ddd end ----------");
        }
    }
}
