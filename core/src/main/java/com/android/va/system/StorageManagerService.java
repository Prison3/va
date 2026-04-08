package com.android.va.system;

import com.android.va.runtime.VHost;

import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.StorageVolume;

import java.io.File;

import com.android.va.mirror.android.os.storage.BRStorageManager;
import com.android.va.mirror.android.os.storage.BRStorageVolume;
import com.android.va.base.PrisonCore;
import com.android.va.runtime.VEnvironment;
import com.android.va.base.FileProvider;
import com.android.va.proxy.ProxyManifest;
import com.android.va.utils.BuildCompat;

public class StorageManagerService extends IStorageManagerService.Stub implements ISystemService {
    private static final StorageManagerService sService = new StorageManagerService();

    public static StorageManagerService get() {
        return sService;
    }

    public StorageManagerService() {
    }

    @Override
    public StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId) throws RemoteException {
        if (BRStorageManager.get().getVolumeList(0, 0) == null) {
            return null;
        }
        try {
            StorageVolume[] storageVolumes = BRStorageManager.get().getVolumeList(VUserHandle.getUserId(Process.myUid()), 0);
            if (storageVolumes == null)
                return null;
            for (StorageVolume storageVolume : storageVolumes) {
                BRStorageVolume.get(storageVolume)._set_mPath(VEnvironment.getExternalUserDir(userId));
                if (BuildCompat.isPie()) {
                    BRStorageVolume.get(storageVolume)._set_mInternalPath(VEnvironment.getExternalUserDir(userId));
                }
            }
            return storageVolumes;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Uri getUriForFile(String file) throws RemoteException {
        return FileProvider.getUriForFile(VHost.getContext(), ProxyManifest.getProxyFileProvider(), new File(file));
    }

    @Override
    public void systemReady() {

    }
}
