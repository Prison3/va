package com.android.internal.os;

import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;

interface IMyActionListener {
    ResponseV0 onActionRequest(in RequestV0 request);
}