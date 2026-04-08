package com.android.actor.control.api;


import com.android.actor.ActAccessibility;
import com.android.actor.control.ActActivityManager;


public class Check {
    public static boolean checkActiveWindow(String pkgName) {
        if (ActAccessibility.getInstance() != null && ActAccessibility.isStart()) {
            return ActAccessibility.getInstance().getRootInActiveWindow().getPackageName().equals(pkgName);
        }
        return false;
    }

    public static boolean checkTopActivity(String activity) {
        String current = ActActivityManager.getInstance().getCurrentActivity();
        return activity.equals(current);
    }
}
