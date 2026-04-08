package com.android.actor.remote;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ActStringUtils;
import com.android.internal.aidl.lppi.ILppiModule;

import java.util.ArrayList;
import java.util.List;

public class LppiManagerConnection implements ServiceConnection {
    private final static String TAG = LppiManagerConnection.class.getSimpleName();
    public ILppiModule module;
    private final Object mLock = new Object();

    public void bind() {
        Logger.d(TAG, "bind LppiManage.");
        Intent intent = new Intent();
        // the same content as defined at LppiFramework project.
//        intent.setComponent(new ComponentName(RocketComponent.PKG_LPPI_INSTALLER,
//                "com.android.internal.aidl.lppi.LppiModuleService"));
        ActApp.getInstance().bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    public void unbind() {
        ActApp.getInstance().unbindService(this);
    }

    public void switchModule(String pkgName, boolean on, Callback.C2<Boolean, String> callback) {
        Logger.d(TAG, "switch lppi module " + pkgName + " " + on);
        new Thread(() -> {
            try {
                synchronized (mLock) {
                    mLock.wait(1000);
                }
                if (module == null) {
                    bind();
                }
                for (int i = 0; i < 10; i++) {
                    if (module == null) {
                        synchronized (mLock) {
                            mLock.wait(500);
                        }
                    } else {
                        module.switchModule(pkgName, on);
                        List<String> list = module.getList();
                        Logger.d(TAG, "get module list: " + ActStringUtils.listToString(list));
                        if (list == null) {
                            callback.onResult(false, "Cannot get module list.");
                        } else {
                            if (list.contains(pkgName)) {
                                if (on) {
                                    callback.onResult(true, "switch module on success.");
                                } else {
                                    callback.onResult(false, "switch module off but still open.");
                                }
                            } else {
                                if (on) {
                                    callback.onResult(false, "switch module on but still close.");
                                } else {
                                    callback.onResult(true, "switch module off success.");
                                }
                            }
                        }
                        return;
                    }
                }
                callback.onResult(false, "cannot bind service after 5s. please start the lppi manager app first.");
            } catch (Exception e) {
                Logger.e(TAG, e.toString(), e);
                callback.onResult(false, "Meet exception: " + e.toString());
            }
        }).start();
    }

    public List<String> getModuleList() {
        List<String> ret = null;
        try {
            if (module != null) {
                ret = (List<String>) module.getList();
            }
        } catch (RemoteException e) {
            Logger.e(TAG, e.toString(), e);
        }
        return ret == null ? new ArrayList<>() : ret;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Logger.i(TAG, "Lppi Manager onServiceConnected");
        module = ILppiModule.Stub.asInterface(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.i(TAG, "Lppi Manager onServiceDisconnected");
        module = null;
    }

    @Override
    public void onBindingDied(ComponentName name) {
        Logger.i(TAG, "Lppi Manager onBindingDied");
    }

    @Override
    public void onNullBinding(ComponentName name) {
        Logger.i(TAG, "Lppi Manager onBindingDied");
    }
}
