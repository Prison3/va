package com.android.actor.grpc.process;

import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.device.NewStage;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.proto.common.DeviceMockReq;

import org.apache.commons.lang3.StringUtils;

public class ProcessDeviceMock extends RequestProcess<DeviceMockReq> {
    @Override
    public void process(DeviceMockReq req) {
        mClientId = req.getClientId();
        if (NewStage.instance().isReady()) {
            if (StringUtils.isEmpty(req.getAppName())) {
                reply(ActorMessageBuilder.deviceMock(mClientId, false, "App name not set."));
                return;
            }
            if (!ActPackageManager.getInstance().isAppInstalled(req.getAppName())) {
                reply(ActorMessageBuilder.deviceMock(mClientId, false, "App " + req.getAppName() + " not installed."));
                return;
            }
            String msg = NewStage.instance().modify(req.getAppName(), 0, req.getParams());
            if (msg == null) {
                ActActivityManager.getInstance().forceStopPackage(req.getAppName());
            }
            reply(ActorMessageBuilder.deviceMock(mClientId, Boolean.parseBoolean(msg), msg == null ? "" : "Parameter error."));
        } else {
            reply(ActorMessageBuilder.deviceMock(mClientId, false, "Device mock module is not ready."));
        }
    }
}