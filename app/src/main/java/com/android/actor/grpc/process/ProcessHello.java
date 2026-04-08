package com.android.actor.grpc.process;

import com.android.actor.grpc.ActorMessageBuilder;
import com.android.proto.common.HelloReq;

public class ProcessHello extends RequestProcess<HelloReq> {
    @Override
    public void process(HelloReq req) {
        reply(ActorMessageBuilder.hello());
    }
}
