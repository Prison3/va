package com.android.actor.grpc.process;

import com.android.actor.utils.SPUtils;
import com.android.proto.common.GetSpInformationReq;
import com.android.actor.grpc.ActorMessageBuilder;

public class ProcessGetSpInformation extends RequestProcess<GetSpInformationReq> {
    @Override
    public void process(GetSpInformationReq req) {
        mClientId = req.getClientId();
        String getSerial = req.getSerial();
        String moduleName = req.getModuleName();
        String key = req.getKey();
        String value =  SPUtils.getValue(SPUtils.ModuleFile.valueOf(moduleName) , key, "");
        reply(ActorMessageBuilder.getSpInformation(mClientId, getSerial, moduleName, key, value));
    }
}
