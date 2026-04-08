package com.android.actor.grpc.process;

import com.android.actor.grpc.ActorMessageBuilder;
import com.android.proto.common.GetInstalledAppReq;


public class ProcessGetInstalledApp extends RequestProcess<GetInstalledAppReq> {
    @Override
    public void process(GetInstalledAppReq req) {
        mClientId = req.getClientId();
        reply(ActorMessageBuilder.installedAppList(mClientId));
    }
}