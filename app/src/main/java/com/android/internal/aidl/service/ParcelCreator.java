package com.android.internal.aidl.service;

import android.system.ErrnoException;
import android.util.Log;


public class ParcelCreator {
    public final static String TAG = "new_stage_aidl";

    public static RequestV0 buildRequestV0(String app, int type, String action, String body) {
        RequestV0 requestV0 = new RequestV0();
        requestV0.setApp(checkNull(app));
        requestV0.setType(type);
        requestV0.setAction(checkNull(action));
        requestV0.setBody(checkNull(body));
        return requestV0;
    }

    public static ResponseV0 buildResponseV0(ResultBody body) {
        ResponseV0 responseV0 = new ResponseV0();
        if (body == null) {
            body = new ResultBody();
        }
        responseV0.setSuccess(true);
        responseV0.setCode(body.getCode().getCode());
        responseV0.setReason(body.getCode().getReason());
        try {
            responseV0.setBody(body.toString());
        } catch (ErrnoException e) {
            Log.e(TAG, e.toString(), e);
            responseV0.setSuccess(false);
            responseV0.setReason("aidl errno exception which should not appear. contact with developer.");
        }
        return responseV0;
    }

    public static ResponseV0 buildFailResponse(ICode code) {
        ResultBody result = new ResultBody(code);
        return ParcelCreator.buildResponseV0(result);
    }

    public static String checkNull(String str) {
        return str == null ? "" : str;
    }

}
