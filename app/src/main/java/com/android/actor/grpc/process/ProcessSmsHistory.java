package com.android.actor.grpc.process;

import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.sms.SmsUtil;
import com.android.proto.common.SmsHistoryReq;
import com.android.proto.common.SmsMessage;

import java.util.List;

public class ProcessSmsHistory extends RequestProcess<SmsHistoryReq> {
    private static final String TAG = ProcessSmsHistory.class.getSimpleName();

    @Override
    public void process(SmsHistoryReq req) {
        mClientId = req.getClientId();
        try {
            int count = SmsUtil.getSmsTotalCount(req.getFilter());
            List<SmsMessage> smsList = SmsUtil.readHistory(req.getFilter(), req.getPage(), req.getSize());
            reply(ActorMessageBuilder.smsHistory(mClientId, count, true, null, smsList));
        } catch (Exception e) {
            Logger.e(TAG, "SmsUtil.readHistory error: " + e.toString(), e);
            reply(ActorMessageBuilder.smsHistory(mClientId, 0, false, e.toString(), null));
        }
    }
}
