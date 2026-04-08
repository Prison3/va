package com.android.va.runtime;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import com.android.va.base.AppCallback;
import com.android.va.base.Settings;
import com.android.va.mirror.android.app.BRActivityThread;
import com.android.va.mirror.android.os.BRUserHandle;

import java.io.File;

/**
 * Host (installer) application identity: package name, UID, user id, and base {@link Context}.
 * Also the process-wide {@link Settings} and {@link AppCallback} facade: app-specific behavior
 * comes from the delegate passed to {@link #attach(Context, Settings, AppCallback)}.
 * Filled once during {@link com.android.va.runtime.VRuntime#onAttach}.
 */
public final class VHost extends Settings implements AppCallback {

    private static final VHost INSTANCE = new VHost();

    private Context mContext;
    private int mUid;
    private int mUserId;
    private String mPackageName;
    /** App-supplied settings (optional); overrides are forwarded where applicable. */
    private Settings mDelegate;
    /** App implementation; optional — methods no-op when null (same as {@link AppCallback#EMPTY}). */
    private AppCallback mAppCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private VHost() {
    }

    public static VHost get() {
        return INSTANCE;
    }

    public static Handler getHandler() {
        return INSTANCE.mHandler;
    }

    /**
     * Called from {@link com.android.va.runtime.VRuntime#onAttach} with the host {@code Application} context,
     * optional app-specific {@link Settings} subclass, and {@link AppCallback} implementation.
     */
    public void attach(Context context, Settings delegate, AppCallback appCallback) {
        mContext = context;
        mUid = Process.myUid();
        mPackageName = context.getPackageName();
        mUserId = BRUserHandle.get().myUserId();
        mDelegate = delegate;
        mAppCallback = appCallback;
    }

    @Override
    public void beforeMainLaunchApk(String packageName, int userid) {
        if (mAppCallback != null) {
            mAppCallback.beforeMainLaunchApk(packageName, userid);
        }
    }

    @Override
    public boolean onStoragePermissionNeeded(String packageName, int userId) {
        return mAppCallback != null && mAppCallback.onStoragePermissionNeeded(packageName, userId);
    }

    @Override
    public void beforeMainActivityOnCreate(Activity activity) {
        if (mAppCallback != null) {
            mAppCallback.beforeMainActivityOnCreate(activity);
        }
    }

    @Override
    public void afterMainActivityOnCreate(Activity activity) {
        if (mAppCallback != null) {
            mAppCallback.afterMainActivityOnCreate(activity);
        }
    }

    @Override
    public void beforeCreateApplication(String packageName, String processName, Context context, int userId) {
        if (mAppCallback != null) {
            mAppCallback.beforeCreateApplication(packageName, processName, context, userId);
        }
    }

    @Override
    public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
        if (mAppCallback != null) {
            mAppCallback.beforeApplicationOnCreate(packageName, processName, application, userId);
        }
    }

    @Override
    public void afterApplicationOnCreate(String packageName, String processName, Application application, int userId) {
        if (mAppCallback != null) {
            mAppCallback.afterApplicationOnCreate(packageName, processName, application, userId);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (mAppCallback != null) {
            mAppCallback.onActivityCreated(activity, savedInstanceState);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (mAppCallback != null) {
            mAppCallback.onActivityStarted(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (mAppCallback != null) {
            mAppCallback.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (mAppCallback != null) {
            mAppCallback.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (mAppCallback != null) {
            mAppCallback.onActivityStopped(activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        if (mAppCallback != null) {
            mAppCallback.onActivitySaveInstanceState(activity, outState);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (mAppCallback != null) {
            mAppCallback.onActivityDestroyed(activity);
        }
    }

    @Override
    public void onBeforeCreateApplication(String packageName, String processName, Context context, int userId) {
        if (mAppCallback != null) {
            mAppCallback.onBeforeCreateApplication(packageName, processName, context, userId);
        }
    }

    @Override
    public boolean isEnableDaemonService() {
        return mDelegate != null ? mDelegate.isEnableDaemonService() : super.isEnableDaemonService();
    }

    @Override
    public boolean isEnableLauncherActivity() {
        return mDelegate != null ? mDelegate.isEnableLauncherActivity() : super.isEnableLauncherActivity();
    }

    @Override
    public boolean requestInstallPackage(File file, int userId) {
        return mDelegate != null && mDelegate.requestInstallPackage(file, userId);
    }

    public static String getPackageName() {
        return INSTANCE.mPackageName;
    }

    public static int getUid() {
        return INSTANCE.mUid;
    }

    public static int getUserId() {
        return INSTANCE.mUserId;
    }

    public static Context getContext() {
        return INSTANCE.mContext;
    }

    /** Current process {@link android.app.ActivityThread} instance (host process). */
    public static Object mainThread() {
        return BRActivityThread.get().currentActivityThread();
    }
}
