package com.android.actor.script.dex;

import android.content.Intent;
import android.os.RemoteException;

import com.android.actor.control.ActActivityManager;
import com.android.actor.control.api.Check;
import com.android.internal.ds.IDexActivity;

public class DexActivity extends IDexActivity.Stub {

    @Override
    public boolean checkActiveWindow(String pkgName) throws RemoteException {
        return Check.checkActiveWindow(pkgName);
    }

    @Override
    public boolean checkTopActivity(String activity) throws RemoteException {
        return Check.checkTopActivity(activity);
    }

    @Override
    public boolean isTopActivity(String packageName, String activityName) throws RemoteException {
        return ActActivityManager.getInstance().isTopActivity(packageName, activityName);
    }

    @Override
    public String moveToFront(String packageName) throws RemoteException {
        return ActActivityManager.getInstance().moveToFront(packageName);
    }

    @Override
    public String getCurrentApp() throws RemoteException {
        return ActActivityManager.getInstance().getCurrentApp();
    }

    @Override
    public String getCurrentActivity() throws RemoteException {
        return ActActivityManager.getInstance().getCurrentActivity();
    }

    @Override
    public String forceStopPackage(String packageName) throws RemoteException {
        return ActActivityManager.getInstance().forceStopPackage(packageName);
    }

    @Override
    public String startActivityAndWait(String packageName, String activityName) throws RemoteException {
        return ActActivityManager.getInstance().startActivityAndWait(packageName, activityName);
    }

    @Override
    public String startActivityByIntent(Intent intent) throws RemoteException {
        return ActActivityManager.getInstance().startActivityAndWait(intent);
    }
}
