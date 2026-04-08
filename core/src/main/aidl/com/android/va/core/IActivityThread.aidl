// IActivityThread.aidl
package com.android.va.core;

// Declare any non-default types here with import statements

import android.os.IBinder;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import com.android.va.model.ReceiverData;

interface IActivityThread {
    IBinder getActivityThread();
    void bindApplication();
    void restartJobService(String selfId);
    IBinder acquireContentProviderClient(in ProviderInfo providerInfo);

    IBinder peekService(in Intent intent);
    void stopService(in Intent componentName);

    void finishActivity(IBinder token);
    void handleNewIntent(IBinder token, in Intent intent);

    void scheduleReceiver(in ReceiverData data);
}
