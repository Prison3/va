package com.android.actor.grpc.process;

import android.telephony.SmsManager;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.proto.common.SmsMessage;
import com.android.proto.common.SmsSendReq;

public class ProcessSmsSend extends RequestProcess<SmsSendReq> {
    @Override
    public void process(SmsSendReq req) {
        mClientId = req.getClientId();
        if (req.getTo().isEmpty()) {
            reply(ActorMessageBuilder.smsSend(mClientId, false, "to address is empty", null));
            return;
        }
        if (req.getContent().isEmpty()) {
            reply(ActorMessageBuilder.smsSend(mClientId, false, "sms content is empty", null));
            return;
        }
        if (!DeviceInfoManager.getInstance().hasSimCard()) {
            reply(ActorMessageBuilder.smsSend(mClientId, false, "this device has not sim card", null));
            return;
        }
        SmsManager.getDefault().sendTextMessage(req.getTo(), null, req.getContent(), null, null);
        String phone = DeviceInfoManager.getInstance().getPhoneNumber();
        long time = System.currentTimeMillis();
        SmsMessage msg = ActorMessageBuilder.smsMessage(true, phone, req.getTo(), req.getContent(), time);
        reply(ActorMessageBuilder.smsSend(mClientId, true, null, msg)); // currently we do not care about the result of sending sms
    }
}
