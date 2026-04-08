package com.android.actor.grpc.process;

import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.TApp;
import com.android.actor.script.lua.LuaScript;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.temu.A4Watcher;
import com.android.proto.common.TCheckRegisterReq;

import java.io.IOException;

public class ProcessTCheckRegister extends RequestProcess<TCheckRegisterReq> {

    private static final String TAG = ProcessTCheckRegister.class.getSimpleName();

    @Override
    public void process(TCheckRegisterReq req) {
        mClientId = req.getClientId();
        String taskId = SPUtils.getString(SPUtils.ModuleFile.script, "taskId");
        Logger.d(TAG, "process taskId " + taskId);
        boolean match = taskId != null && taskId.equals(TApp.sTaskId);
        String status = match ? SPUtils.getString(SPUtils.ModuleFile.script, "status") : LuaScript.LuaStatus.notfound.name();
        String result = match ? SPUtils.getString(SPUtils.ModuleFile.script, "result") : "";
        Logger.d(TAG, "match " + match + " status " + status + " result " + result);
        String registerInformation = "";
        if (LuaScript.LuaStatus.success.name().equals(status)) {
            try {
                registerInformation = Libsu.readFileToString(A4Watcher.DIR_PATH + "/t_result");
            } catch (Throwable e) {
                Logger.e(TAG, "lua success but t_result can't be read.", e);
                status = LuaScript.LuaStatus.failed.name();
            }
        }

        Logger.d(TAG, "result " + result);
        reply(ActorMessageBuilder.t_checkRegister(
                mClientId,
                TApp.sTaskId,
                status,
                match ? SPUtils.getString(SPUtils.ModuleFile.script, "record") : null,
                result, registerInformation));
    }
}
