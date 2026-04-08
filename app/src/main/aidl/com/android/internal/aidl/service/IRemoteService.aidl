// IRemoteService.aidl
package com.android.internal.aidl.service;

import com.android.internal.aidl.service.IRemoteCallback;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;
// Declare any non-default types here with import statements
interface IRemoteService {
    boolean ping();
    Map getInfo();
    ResponseV0 invoke(in RequestV0 request);
    void call(in RequestV0 request, IRemoteCallback callback);
}