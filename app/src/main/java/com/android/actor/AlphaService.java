package com.android.actor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.actor.fi.FIAppWarmup;
import com.android.actor.fi.FIEntryPoint;
import com.android.actor.fi.FIRequest;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;

public class AlphaService extends Service {

    private static final String TAG = "AlphaService";

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.i(TAG, "onCreate.");
        startForegroundService();

        Libsu.exec("killall -9 fi16.3.3");
        FIEntryPoint.getPackages().forEach(packageName -> {
            Libsu.exec("am force-stop " + packageName);
        });
        if (!FIAppWarmup.init(this)) {
            Toast.makeText(this, "Error.", Toast.LENGTH_LONG).show();
        }
        FIEntryPoint.getPackages().forEach(packageName -> {
            FIRequest.get(packageName);
        });
    }

    private void startForegroundService() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(TAG, TAG, NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
        startForeground(3, getNotification());
    }

    private Notification getNotification() {
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(TAG)
                .setContentText(TAG);
        builder.setChannelId(TAG);
        return builder.build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.i(TAG, "onDestroy.");
    }
}
