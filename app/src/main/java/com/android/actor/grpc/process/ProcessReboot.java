package com.android.actor.grpc.process;

import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.shell.Root;
import com.android.actor.utils.shell.Shell;
import com.android.proto.common.RebootReq;

import java.io.IOException;

public class ProcessReboot extends RequestProcess<RebootReq> {
    @Override
    public void process(RebootReq req) {
        mClientId = req.getClientId();

        if (Root.checkRoot()) {
            reply(ActorMessageBuilder.reboot(mClientId, true, null));
        } else {
            reply(ActorMessageBuilder.reboot(mClientId, false, "Not get shell-root permission."));
        }
        Libsu.reboot();
    }
}