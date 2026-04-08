package com.android.va.utils;

import android.os.IBinder;
import android.os.IInterface;

import com.android.va.mirror.android.app.BRApplicationThreadNative;
import com.android.va.mirror.android.app.BRIApplicationThreadOreoStub;

public class ApplicationThreadCompat {

    public static IInterface asInterface(IBinder binder) {
        if (BuildCompat.isOreo()) {
            return BRIApplicationThreadOreoStub.get().asInterface(binder);
        }
        return BRApplicationThreadNative.get().asInterface(binder);
    }
}
