package com.android.actor.control;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.HandlerThread;

import com.android.actor.ActApp;
import com.android.actor.device.NewStage;
import com.android.actor.device.ScrcpyDedicated;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ReflectUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class ActActivityManager {
    private final static String TAG = ActActivityManager.class.getSimpleName();
    private static ActActivityManager sInstance;
    private Context mContext;
    private final ActivityManager mActivityManager;
    private Handler mHandler;

    public static ActActivityManager getInstance() {
        if (sInstance == null) {
            synchronized (ActActivityManager.class) {
                if (sInstance == null) {
                    sInstance = new ActActivityManager(ActApp.getInstance());
                }
            }
        }
        return sInstance;
    }

    private ActActivityManager(Context context) {
        mContext = context;
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        HandlerThread thread = new HandlerThread("ActActivityManager");
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    public boolean hasRunningProcess(String packageName) {
        return mActivityManager.getRunningAppProcesses().stream().anyMatch(
                info -> info.processName.split(":")[0].equals(packageName));
    }

    public boolean hasRunningTask(String packageName) {
        return mActivityManager.getRunningTasks(100).stream().anyMatch(
                task -> task.baseActivity.getPackageName().equals(packageName));
    }

    public boolean isTopActivity(String packageName, String activityName) {
        ComponentName component = mActivityManager.getRunningTasks(1).get(0).topActivity;
        if (component.getPackageName().equals(packageName)) {
            return activityName == null || activityName.equals(component.getClassName());
        }
        return false;
    }

    public String moveToFront(String packageName) {
        if (ActApp.getInstance().getPackageName().equals(packageName)
                && !ActApp.getInstance().getPackageName().equals(getCurrentApp())
                && ScrcpyDedicated.instance.isEnabled()) {
            Logger.i(TAG, "Go launcher.");
            Intent homeIntent = new Intent(Intent.ACTION_MAIN, null);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return startActivityAndWait(homeIntent);
        }

        Logger.i(TAG, "moveToFront " + packageName);
        for (ActivityManager.RunningTaskInfo task : mActivityManager.getRunningTasks(100)) {
            if (task.baseActivity.getPackageName().equals(packageName)) {
                mActivityManager.moveTaskToFront(task.taskId, 0);
                return null;
            }
        }
        Logger.i(TAG, "moveToFront not found " + packageName + ", use start activity.");
        return startActivityAndWait(packageName, null);
    }

    public String getCurrentApp() {
        ComponentName componentName = mActivityManager.getRunningTasks(1).get(0).topActivity;
        return componentName.getPackageName();
    }

    public String getCurrentActivity() {
        ComponentName componentName = mActivityManager.getRunningTasks(1).get(0).topActivity;
        return componentName.getClassName();
    }

    public String forceStopPackage(String packageName) {
        BlockingReference<String> result = new BlockingReference<>();
        forceStopPackage(packageName, (success, reason) -> {
            result.put(success ? null : (StringUtils.isEmpty(reason) ? "Unknown reason." : reason));
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public void forceStopPackage(String packageName, Callback.C2<Boolean, String> callback) {
        Logger.i(TAG, "forceStopPackage " + packageName);
        if (!ActPackageManager.getInstance().isAppInstalled(packageName)) {
            callback.onResult(false, "Package " + packageName + " not exist.");
            return;
        }

        if (packageName.equals(getCurrentApp())) {
            moveToFront(ActApp.getInstance().getPackageName());
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
        try {
            ReflectUtils.callMethod(mActivityManager, "forceStopPackage", packageName);
            callback.onResult(true, "");
        } catch (Throwable e) {
            Logger.e(TAG, "forceStopPackage exception.", e);
            callback.onResult(false, e.toString());
        }
    }

    public String forceStopAllPackage() {
        List<PackageInfo> infos = ActPackageManager.getInstance().getInstalledPackage();
        for (PackageInfo info : infos) {
            String msg = forceStopPackage(info.packageName);
            if (msg != null) {
                return msg;
            }
        }
        return null;
    }

    public String startActivityAndWait(String packageName, String activityName) {
        BlockingReference<String> result = new BlockingReference<>();
        startActivityAndWait(packageName, activityName, (reason) -> {
            result.put(StringUtils.isEmpty(reason) ? null : reason);
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public void startActivityAndWait(String packageName, String activityName, Callback.C1<String> callback) {
        if (!ActPackageManager.getInstance().isAppInstalled(packageName)) {
            callback.onResult("Pacakge " + packageName + " not exist.");
            return;
        }

        Intent intent;
        if (org.apache.commons.lang3.StringUtils.isEmpty(activityName)) {
            Logger.i(TAG, "No activityName provided, use app default.");
            intent = ActPackageManager.getInstance().getLaunchIntentForPackage(packageName);
        } else {
            intent = new Intent();
            intent.setClassName(packageName, activityName);
            intent.setPackage(packageName);
        }
        if (intent == null) {
            callback.onResult("No intent");
        } else {
            startActivityAndWait(intent, callback);
        }
    }

    public String startActivityAndWait(Intent intent) {
        BlockingReference<String> result = new BlockingReference<>();
        startActivityAndWait(intent, (reason) -> {
            result.put(StringUtils.isEmpty(reason) ? null : reason);
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public void startActivityAndWait(Intent intent, Callback.C1<String> callback) {
        Logger.i(TAG, "startActivityAndWait " + intent);
        mHandler.post(() -> {
            String reason = NewStage.instance().startActivityAndWait(intent);
            Logger.i(TAG, "startActivityAndWait result " + reason);
            callback.onResult(reason == null ? "" : reason);
        });
    }

    public void startActivityByIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}