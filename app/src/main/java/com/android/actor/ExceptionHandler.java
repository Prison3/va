package com.android.actor;

import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class ExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = ExceptionHandler.class.getSimpleName();

    public static void setup() {
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        Logger.e(TAG, "uncaughtException " + t, e);
        String msg = t.toString() + '\n' + Log.getStackTraceString(e);

        if (t == Looper.getMainLooper().getThread()) {
            Logger.e(TAG, "Main thread crash, kill.");

            // Hard coded exception file path for main thread, many methods won't work if main thread crashes,
            // Do your things lightly and ASAP.
            File file = new File("/data/data/com.android.actor/exception_main");
            try {
                FileUtils.writeStringToFile(file, msg);
            } catch (IOException ioException) {
                Logger.e(TAG, "Can't write main thread exception.", ioException);
            }

            Process.killProcess(Process.myPid());
        } else {
            GRPCManager.getInstance().sendNotification("error", msg);
        }
    }

    public static void checkLastMainThreadCrash() {
        File file = new File("/data/data/com.android.actor/exception_main");
        if (!file.exists()) {
            return;
        }
        try {
            Logger.w(TAG, "Last time main thread crash, report.");
            String msg = FileUtils.readFileToString(file);
            GRPCManager.getInstance().prepared(() -> {
                GRPCManager.getInstance().sendNotification("error", msg);
            });
            file.delete();
        } catch (IOException e) {
            Logger.e(TAG, "Can't read " + file, e);
        }
    }
}
