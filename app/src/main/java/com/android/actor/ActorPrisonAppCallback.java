package com.android.actor;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import com.android.actor.monitor.Logger;
import com.android.va.base.AppCallback;

public final class ActorPrisonAppCallback implements AppCallback {

    private static final String TAG = "ActorVA";

    @Override
    public void beforeMainLaunchApk(String packageName, int userid) {
        Logger.d(TAG, "beforeMainLaunchApk pkg=" + packageName + " user=" + userid);
    }

    @Override
    public boolean onStoragePermissionNeeded(String packageName, int userId) {
        Logger.w(TAG, "onStoragePermissionNeeded pkg=" + packageName + " userId=" + userId);
        return false;
    }

    @Override
    public void beforeMainActivityOnCreate(Activity activity) {
    }

    @Override
    public void afterMainActivityOnCreate(Activity activity) {
    }

    @Override
    public void beforeCreateApplication(String packageName, String processName, Context context, int userId) {
        Logger.d(TAG, "beforeCreateApplication pkg=" + packageName + " proc=" + processName + " userId=" + userId);
    }

    @Override
    public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
        Logger.d(TAG, "beforeApplicationOnCreate pkg=" + packageName + " proc=" + processName);
    }

    @Override
    public void afterApplicationOnCreate(String packageName, String processName, Application application, int userId) {
        Logger.d(TAG, "afterApplicationOnCreate pkg=" + packageName + " proc=" + processName + " userId=" + userId);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    @Override
    public void onBeforeCreateApplication(String packageName, String processName, Context context, int userId) {
    }
}
