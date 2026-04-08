package com.android.actor.script.dex;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.android.actor.R;
import com.android.actor.control.RocketComponent;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;

import dalvik.system.PathClassLoader;

public class DexScriptService extends Service {

    private static final String TAG = DexScriptService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d(TAG, "onCreate.");
        startForegroundService();
    }

    @Nullable
    @SuppressLint("PrivateApi")
    @Override
    public IBinder onBind(Intent intent) {
        try {
            String path = intent.getStringExtra("path");
            PackageInfo info = getPackageManager().getPackageArchiveInfo(path, 0);
            Logger.i(TAG, "Dex path " + path + ", version " + info.versionName);
            ClassLoader classLoader = new PathClassLoader(path, ClassLoader.getSystemClassLoader());
            Class<?> cls = classLoader.loadClass("com.android.ds.Main");
            return (IBinder) ReflectUtils.callStaticMethod(cls, "run");
        } catch (Throwable e) {
            Logger.e(TAG, "Error to load DS.", e);
            return null;
        }
    }

    private void startForegroundService() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String notificationName = "DexScriptService";
        NotificationChannel channel = new NotificationChannel("DexScriptService", notificationName, NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
        startForeground(2, getNotification());
    }

    private Notification getNotification() {
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("DexScriptService")
                .setContentText("DexScriptService is running.");
        builder.setChannelId("DexScriptService");
        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.d(TAG, "onDestroy.");
        System.exit(0);
    }
}
