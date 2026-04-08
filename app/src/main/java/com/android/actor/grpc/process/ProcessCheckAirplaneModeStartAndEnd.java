package com.android.actor.grpc.process;

import com.alibaba.fastjson.JSON;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.shell.Shell;
import com.android.proto.common.AirplaneModeStartAndEndReq;
import com.android.proto.common.CheckAirplaneModeStartAndEndReq;

import static com.android.actor.device.ActConnManager.USE_4G;


public class ProcessCheckAirplaneModeStartAndEnd extends RequestProcess<CheckAirplaneModeStartAndEndReq> {
    // 也可用来获取当前设备的当前 ip 地址
    @Override
    public void process(CheckAirplaneModeStartAndEndReq req) throws InterruptedException {
        mClientId = req.getClientId();

        String resp = NetUtils.httpGet(NetUtils.GET_IP_URL);
        SPUtils.putString(SPUtils.ModuleFile.export_ip, "export_ip_after_switching", JSON.parseObject(resp).getString("ip"));
        reply(ActorMessageBuilder.checkAirplaneModeStartAndEnd(mClientId, true, req.getSerial(), SPUtils.getString(SPUtils.ModuleFile.export_ip, "export_ip"), resp));

    }
}
