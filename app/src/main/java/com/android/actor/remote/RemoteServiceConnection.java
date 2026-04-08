package com.android.actor.remote;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.utils.NetUtils;
import com.android.internal.aidl.service.BaseService;
import com.android.internal.aidl.service.ParcelCreator;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;
import com.android.actor.device.NewStage;
import com.android.actor.monitor.Logger;

public class RemoteServiceConnection {

    private final static String TAG = RemoteServiceConnection.class.getSimpleName();
    private String mApp;
    private int mDexVer;
    private String mDexTag;

    public RemoteServiceConnection(String app) {
        mApp = app;
    }

    public RemoteServiceConnection(String app, JSONObject jInfo) {
        mApp = app;
        if (jInfo != null) {
            mDexVer = jInfo.getIntValue("dex_ver");
            mDexTag = jInfo.getString("dex_tag");
        }
    }

    public String getPackageName() {
        return mApp;
    }

    public int getDexVer() {
        return mDexVer;
    }

    public String getDexTag() {
        return mDexTag;
    }

    public boolean canUse() {
        return mDexVer > 0;
    }

    public ResponseV0 requestAppAction(RequestV0 request) {
        if (ActApp.isSettingUp()) {
            return ParcelCreator.buildFailResponse(BaseService.CODE_SYSTEM_NOT_READY);
        }
        if (mDexVer <= 0) {
            Logger.e(TAG, "No dex info on " + mApp + ", drop action.");
            return ParcelCreator.buildFailResponse(BaseService.CODE_NO_DEX_INFO);
        }
        ResponseV0 resp = NewStage.instance().requestAppAction(request);
        if (resp == null) {
            resp = ParcelCreator.buildFailResponse(BaseService.CODE_APP_ERROR);
        }
        NetUtils.metricCounter("xposed_api", mApp , request.getAction(), String.valueOf(resp.getCode()));
        return resp;
    }

    @NonNull
    @Override
    public String toString() {
        return "[RemoteServiceConnection] " + mApp + ", dexVer " + mDexVer + ", dexTag " + mDexTag;
    }
}
