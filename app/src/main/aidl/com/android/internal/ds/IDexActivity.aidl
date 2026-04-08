package com.android.internal.ds;

interface IDexActivity {

    boolean checkActiveWindow(String pkgName);

    boolean checkTopActivity(String activity);

    boolean isTopActivity(String packageName, String activityName);

    String moveToFront(String packageName);

    String getCurrentApp();

    String getCurrentActivity();

    String forceStopPackage(String packageName);

    String startActivityAndWait(String packageName, String activityName);

    String startActivityByIntent(in Intent intent);
}