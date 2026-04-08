package com.android.actor.control.trace;


import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.android.actor.ActApp;
import com.android.actor.control.input.ActInputManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.FileHelper;
import com.android.actor.utils.ActStringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TraceUtils {
    private final static String TAG = TraceUtils.class.getSimpleName();
    public final static String RECORD_FOLDER = "trace";

    public static void storeToFile(String pkgName, String prefix, EventTrace trace) throws IOException {
        String fileName = prefix + "_" + trace.trackTime;
        File folder = new File(ActApp.getInstance().getFilesDir().getAbsolutePath() + File.separator + RECORD_FOLDER);
        if (!folder.exists()) {
            folder.mkdir();
        }
        File pkgFolder = new File(folder.getAbsolutePath() + File.separator + pkgName);
        if (!pkgFolder.exists()) {
            pkgFolder.mkdir();
        }
        FileHelper.writeToFile(pkgFolder.getAbsolutePath() + File.separator + fileName, trace.getRecordString());
    }

    public static String readRandomTrace(String pkgName, String prefix) throws IOException {
        File folder = new File(ActApp.getInstance().getFilesDir().getAbsolutePath() + File.separator + RECORD_FOLDER + File.separator + pkgName);
        Logger.d(TAG, "readRandomTrace from: " + folder);
        if (!folder.exists()) {
            return null;
        }
        File[] files = folder.listFiles();
        List<File> tempList = new ArrayList<>();
        for (File file : files) {
            if (file.getName().startsWith(prefix)) {
                tempList.add(file);
            }
        }
        Logger.d(TAG, "readRandomTrace get trace count: " + tempList.size());
        if (tempList.size() <= 0) {
            return null;
        }
        Random random = new Random();
        File file = tempList.get(random.nextInt(tempList.size()));
        return FileHelper.readFromFile(file.getPath());
    }

    public static void inputToSwipe(float startX, float startY, String trace) {
        if (!ActStringUtils.isEmpty(trace)) {
            String[] lines = trace.split("\n");
            long downTime = SystemClock.uptimeMillis();
            for (String line : lines) {
                EventPoint p = new EventPoint(line);
                MotionEvent event = p.obtain(downTime, startX, startY);
                if (event != null) {
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    ActInputManager.getInstance().inFrameEventQueue(event, 2);
                }
            }
        }
    }

    public static void inputToSwipe(int startX, int startY, String trace) {
        inputToSwipe((float) startX, (float) startY, trace);
    }
}
