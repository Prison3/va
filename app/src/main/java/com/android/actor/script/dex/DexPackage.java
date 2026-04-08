package com.android.actor.script.dex;

import android.os.RemoteException;

import com.android.actor.control.ActPackageManager;
import com.android.actor.device.NewStage;
import com.android.internal.ds.IDexPackage;

public class DexPackage extends IDexPackage.Stub {

    @Override
    public String clearApp(String pkgName) throws RemoteException {
        return ActPackageManager.getInstance().clearApp(pkgName, NewStage.instance().getPackageProfileId(pkgName));
    }

    @Override
    public String clearCache(String pkgName) throws RemoteException {
        return ActPackageManager.getInstance().clearCache(pkgName, NewStage.instance().getPackageProfileId(pkgName));
    }
}
