package com.android.actor.grpc.process;

import com.android.actor.control.ActPackageManager;
import com.android.actor.device.NewStage;
import com.android.proto.common.GetDeviceMockParamsReq;

import org.apache.commons.lang3.StringUtils;

import static com.android.actor.grpc.ActorMessageBuilder.getDeviceMockParams;

public class ProcessGetDeviceMockParams extends RequestProcess<GetDeviceMockParamsReq> {
    @Override
    public void process(GetDeviceMockParamsReq req) {
        mClientId = req.getClientId();
        if (NewStage.instance().isReady()) {
            if (StringUtils.isEmpty(req.getAppName())) {
                reply(getDeviceMockParams(mClientId, false, null, "App name not set."));
                return;
            }
            if (!ActPackageManager.getInstance().isAppInstalled(req.getAppName())) {
                reply(getDeviceMockParams(mClientId, false, null, "App " + req.getAppName() + " not installed."));
                return;
            }
            String parameters = NewStage.instance().getPackageParameters(req.getAppName());
            if (parameters == null) {
                reply(getDeviceMockParams(mClientId, false, null, "App " + req.getAppName() + " not mock."));
            } else {
                reply(getDeviceMockParams(mClientId, true, parameters, null));
            }
        } else {
            reply(getDeviceMockParams(mClientId, false, null, "Device mock module is not ready."));
        }
    }
}
