package com.android.actor.monitor;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.android.actor.utils.shell.Shell;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

public class Logcat {

    private static final String TAG = Logcat.class.getSimpleName();
    private String mDir;
    private _Thread mThread;

    private static final int MSG_KILL = 0;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_KILL: {
                    kill();
                    mHandler.sendEmptyMessageDelayed(MSG_KILL, getNextKillDelay());
                    break;
                }
            }
        }
    };

    public Logcat(String dir) {
        mDir = dir;
        mThread = new _Thread();
        Logger.i(TAG, "Logcat dir " + dir);
    }

    public void startCollect() {
        mThread.start();
        mHandler.sendEmptyMessageDelayed(MSG_KILL, getNextKillDelay());
    }

    private void kill() {
        Logger.d(TAG, "Kill logcat.");
        Shell.execRootCmdSilent("ps -ef | grep logcat | grep actor | awk '{print $2}' | xargs kill");
    }

    private long getNextKillDelay() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 5);
        return calendar.getTimeInMillis() - System.currentTimeMillis();
    }

    private void clearOldLogs() {
        File[] files = new File(mDir).listFiles();
        if (files.length > 10) {
            Arrays.stream(files).sorted((f1, f2) -> f1.lastModified() < f2.lastModified() ? -1 : 1)
                    .limit(files.length - 10)
                    .forEach(f -> f.delete());
        }
    }

    private class _Thread extends Thread {
        @Override
        public void run() {
            setPriority(MIN_PRIORITY);
            kill();
            while (true) {
                clearOldLogs();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {}

                SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd_HH-mm-ss");
                String path = mDir + "/Actor-" + dateFormat.format(new Date()) + ".log";
                Logger.i(TAG, "Logcat path " + path);
                try {
                    Shell.execRootCmd("logcat -f " + path + " -s " +
                            "'Rocket_Actor'," +
                            "'Rocket_AIDL'," +
                            "'new_stage'," +
                            "'cr_new_stage'," +
                            "'twebview'," +
                            "'AndroidRuntime'," +
                            "'ActivityManager'," +
                            "'Zygote'," +
                            "");
                } catch (IOException e) {
                    Logger.e(TAG, "Logcat exception.", e);
                }
            }
        }
    }
}
