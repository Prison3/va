package com.android.va.runtime;

import android.net.Uri;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import com.android.va.system.ServiceManager;
import com.android.va.system.IStorageManagerService;

public class VStorageManager extends VManager<IStorageManagerService> {
    private static final VStorageManager sVStorageManager = new VStorageManager();

    public static VStorageManager get() {
        return sVStorageManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.STORAGE_MANAGER;
    }

    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) {
        try {
            return getService().getVolumeList(uid, packageName, flags, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new StorageVolume[]{};
    }

    public Uri getUriForFile(String file) {
        try {
            return getService().getUriForFile(file);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }
}
