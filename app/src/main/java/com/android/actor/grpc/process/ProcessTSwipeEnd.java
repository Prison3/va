package com.android.actor.grpc.process;

import com.android.actor.ActApp;
import com.android.actor.control.ActActivityManager;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.TApp;
import com.android.actor.script.ScriptExecutor;
import com.android.proto.common.TSwipeEndReq;

import org.apache.commons.lang3.StringUtils;

public class ProcessTSwipeEnd extends RequestProcess<TSwipeEndReq>{
    public static final String TAG = ProcessTSwipeEnd.class.getSimpleName();

    @Override
    public void process(TSwipeEndReq req) {
        Logger.d(TAG, "process: " + req.toString());
        mClientId = req.getClientId();
        String taskId = TApp.sTaskId;
        String currentTask = ScriptExecutor.getInstance().geCurrentTaskId();
        if (StringUtils.isEmpty(taskId)) {
            reply(ActorMessageBuilder.stopScript(mClientId, taskId, true, null));
        } else if (taskId.equals(currentTask)) {
            if (ScriptExecutor.getInstance().isRunning()) {
                ScriptExecutor.getInstance().stopScript();
                if (ScriptExecutor.getInstance().isRunning()) {
                    reply(ActorMessageBuilder.stopScript(mClientId, taskId, false, "Task is still running, current status: " + ScriptExecutor.getInstance().getState()));
                } else {
                    Logger.v(TAG, "Bring actor to front at StopScript.");
                    ActActivityManager.getInstance().moveToFront(ActApp.getInstance().getPackageName());
                    reply(ActorMessageBuilder.stopScript(mClientId, taskId, true, null));
                }
            } else {
                reply(ActorMessageBuilder.stopScript(mClientId, taskId, true, "Task is not running, current status: " + ScriptExecutor.getInstance().getState()));
            }
        } else {
            reply(ActorMessageBuilder.stopScript(mClientId, taskId, false, "Task not found, current task: " + currentTask));
        }
    }
}
