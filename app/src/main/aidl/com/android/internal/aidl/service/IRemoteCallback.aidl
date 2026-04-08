// IRemoteCallback.aidl
package com.android.internal.aidl.service;

// Declare any non-default types here with import statements

import com.android.internal.aidl.service.ResponseV0;
interface IRemoteCallback {
    void callback(in ResponseV0 result);
}