package com.android.actor.utils.shell;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.android.actor.device.ActTethering;
import com.android.actor.monitor.Logger;
import com.android.internal.ILibsuService;
import com.topjohnwu.superuser.ipc.RootService;
import com.topjohnwu.superuser.nio.FileSystemManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

public class LibsuService extends RootService {

    private static final String TAG = LibsuService.class.getSimpleName();

    class TestIPC extends ILibsuService.Stub {
        @Override
        public int getPid() {
            return Process.myPid();
        }

        @Override
        public int getUid() {
            return Process.myUid();
        }

        @Override
        public String getUUID() {
            return uuid;
        }

        @Override
        public IBinder getFileSystemService() {
            return FileSystemManager.getService();
        }

        @Override
        public int getPathUid(String path) throws RemoteException {
            try {
                if (new File(path).exists()) {
                    return (int) Files.getAttribute(Paths.get(path), "unix:uid");
                }
                return -1;
            } catch (Throwable e) {
                Logger.e(TAG, "Error to get path uid.", e);
                return -1;
            }
        }

        @Override
        public void startTethering() throws RemoteException {
            ActTethering.startTethering(LibsuService.this);
        }

        @Override
        public void stopTethering() throws RemoteException {
            ActTethering.stopTethering(LibsuService.this);
        }
    }

    private final String uuid = UUID.randomUUID().toString();

    @Override
    public void onCreate() {
        Logger.d(TAG, "onCreate, " + uuid);
    }

    @Override
    public void onRebind(@NonNull Intent intent) {
        // This callback will be called when we are reusing a previously started root process
        Logger.d(TAG, "onRebind, daemon process reused");
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Logger.d(TAG, "onBind");
        return new TestIPC();
    }

    @Override
    public boolean onUnbind(@NonNull Intent intent) {
        Logger.d(TAG, "onUnbind, client process unbound");
        // Return true here so onRebind will be called
        return true;
    }

    @Override
    public void onDestroy() {
        Logger.d(TAG, "onDestroy");
    }
}
