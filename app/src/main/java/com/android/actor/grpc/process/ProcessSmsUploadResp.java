package com.android.actor.grpc.process;

import com.android.proto.actor.SmsUploadResp;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.sms.SmsUtil;

public class ProcessSmsUploadResp extends RequestProcess<SmsUploadResp> {
    private final static String TAG = ProcessSmsUploadResp.class.getSimpleName();

    @Override
    public void process(SmsUploadResp resp) {
        if (resp.getSuccess()) {
            Logger.d(TAG, "uploadNode success: " + resp.getUuid());
        } else {
            Logger.d(TAG, "uploadNode failed: " + resp.getReason());
            GRPCManager.getInstance().sendNotification("error", "uploadNode sms failed but somehow get return: " + resp.getUuid());
        }
        SmsUtil.onUploaded(resp.getUuid());
    }
}
