package com.android.actor.grpc.process;

import android.os.Handler;
import android.os.Process;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.grpc.ActorAdapter;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.shell.Shell;
import com.android.proto.common.AirplaneModeStartAndEndReq;
import com.android.actor.grpc.ActorMessageBuilder;
import okhttp3.OkHttpClient;

import static com.android.actor.device.ActConnManager.USE_4G;
import static com.android.actor.device.ActConnManager.USE_WIFI;


public class ProcessAirplaneModeStartAndEnd extends RequestProcess<AirplaneModeStartAndEndReq> {
    @Override
    public void process(AirplaneModeStartAndEndReq req) throws InterruptedException {
        mClientId = req.getClientId();
        String resp = NetUtils.httpGet(NetUtils.GET_IP_URL);
        SPUtils.putString(SPUtils.ModuleFile.export_ip, "export_ip", resp);
        DeviceInfoManager.getInstance().getConnManager().enableWired(USE_4G);
        reply(ActorMessageBuilder.airplaneModeStartAndEnd(mClientId, true, req.getSerial(), resp));
        Shell.execRootCmdSilent("settings put global airplane_mode_on 1");
        Thread.sleep(100);
        Shell.execRootCmdSilent("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
        Thread.sleep(100);
        Shell.execRootCmdSilent("settings put global airplane_mode_on 0");
        Thread.sleep(100);
        Shell.execRootCmdSilent("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
        Thread.sleep(100);
        Logger.d("ProcessAirplaneModeStartAndEnd", "process end");
    }
}
