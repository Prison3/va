package com.android.actor.grpc.process;

import android.annotation.SuppressLint;
import com.android.actor.monitor.Logger;
import com.android.actor.script.ScriptExecutor;
import com.android.actor.script.lua.LuaScript;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.utils.FileHelper;
import com.android.actor.utils.SPUtils;
import com.android.proto.common.RunScriptCheckingReq;

import java.io.IOException;

public class ProcessRunScriptChecking extends RequestProcess<RunScriptCheckingReq> {
    private static final String TAG = ProcessRunScriptChecking.class.getSimpleName();

    @SuppressLint("SdCardPath")
    @Override
    public void process(RunScriptCheckingReq req) throws IOException {
        mClientId = req.getClientId();
        String taskId = SPUtils.getString(SPUtils.ModuleFile.script, "taskId");
        boolean match = taskId != null && taskId.equals(req.getTaskId());
        String data = FileHelper.readFromFile(ScriptExecutor.FILE_PATH_SCRIPT_RECORD);
//        Logger.d(TAG, "process: match = " + match);
//        Logger.d(TAG, "script_record_info_list:" + data);

        String status = SPUtils.getString(SPUtils.ModuleFile.script, "status");
        reply(ActorMessageBuilder.runScriptChecking(
                mClientId,
                req.getTaskId(),
                match ? status : LuaScript.LuaStatus.notfound.name(),
                match ? SPUtils.getString(SPUtils.ModuleFile.script, "record") : null,
                match ? SPUtils.getString(SPUtils.ModuleFile.script, "result") : null,
                match ? data : null));

        if (match) {
            if (LuaScript.LuaStatus.success.name().equals(status)
                    || LuaScript.LuaStatus.failed.name().equals(status)
                    || LuaScript.LuaStatus.stopped.name().equals(status)) {
                if (SPUtils.getLong(SPUtils.ModuleFile.script, "lastRead") == 0) {
                    long time = System.currentTimeMillis();
                    Logger.d(TAG, "Script checking with end, put lastRead " + time);
                    SPUtils.putLong(SPUtils.ModuleFile.script, "lastRead", time);
                }
            }
        }
    }
}
