package com.android.actor.grpc.process;

import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.device.NewStage;
import com.android.proto.common.DeviceUnmockReq;
import org.apache.commons.lang3.StringUtils;

import static com.android.actor.grpc.ActorMessageBuilder.deviceUnmock;

public class ProcessDeviceUnmock extends RequestProcess<DeviceUnmockReq> {
    @Override
    public void process(DeviceUnmockReq req) {
        mClientId = req.getClientId();
        if (NewStage.instance().isReady()) {
            if (StringUtils.isEmpty(req.getAppName())) {
                reply(deviceUnmock(mClientId, false, "App name not set."));
                return;
            }
            if (!ActPackageManager.getInstance().isAppInstalled(req.getAppName())) {
                reply(deviceUnmock(mClientId, false, "App " + req.getAppName() + " not installed."));
                return;
            }
            String resetMsg = NewStage.instance().reset(req.getAppName(), 0);
            if (resetMsg == null) {
                ActActivityManager.getInstance().forceStopPackage(req.getAppName());
            }
            reply(deviceUnmock(mClientId, resetMsg == null, resetMsg == null ? "" : "Internal error."));
        } else {
            reply(deviceUnmock(mClientId, false, "Device mock module is not ready."));
        }
    }
}
