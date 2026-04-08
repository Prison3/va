package com.android.actor.grpc.process;

import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;

import java.io.IOException;

public abstract class RequestProcess<R extends com.google.protobuf.Message> {
    protected String mClientId;

    public abstract void process(R req) throws InterruptedException, IOException;

    public void reply(com.google.protobuf.Message msg) {
        Logger.d("RequestProcess", "reply " + msg);
        GRPCManager.getInstance().sendMessageInActorQueue(msg);
    }
}