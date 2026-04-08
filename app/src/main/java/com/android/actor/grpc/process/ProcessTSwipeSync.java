package com.android.actor.grpc.process;

import com.android.actor.ActAccessibility;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.TApp;
import com.android.actor.script.ScriptExecutor;
import com.android.proto.common.TSwipeSyncReq;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class ProcessTSwipeSync  extends RequestProcess<TSwipeSyncReq>{
    public static final String TAG = ProcessTSwipeSync.class.getSimpleName();

    @Override
    public void process(TSwipeSyncReq req) {
        Logger.i(TAG, "process: " + req.toString());
        String script = req.getScript();
        if (StringUtils.isEmpty(script)) {
            script = "url:" + TApp.sSwipeSyncScript;
        }
        int quota = TApp.sSwipeSyncTimeout;
        mClientId = req.getClientId();
        String taskId = TApp.sTaskId;
        Map<String, String> scriptParams = new HashMap<>();
        Logger.d(TAG, "process: taskId = " + taskId);
        if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(script) || !ScriptExecutor.getInstance().isPrefixValid(script)) {
            reply(ActorMessageBuilder.t_swipeSync(mClientId, taskId, false, "Param task_id or lua_script is empty or invalid, please check request."));
            return;
        }

        if (!ActAccessibility.isStart()) {
            reply(ActorMessageBuilder.t_swipeSync(mClientId, taskId, false, "Actor not start accessibility yet"));
            return;
        }

        if (!ScriptExecutor.getInstance().isRunning()) {
            ScriptExecutor.getInstance().loadScript(taskId, script, scriptParams);
            String msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
            reply(ActorMessageBuilder.t_swipeSync(mClientId, taskId, msg == null, msg));
            return;
        }

        String currentTaskId = ScriptExecutor.getInstance().geCurrentTaskId();
        String currentExecutor = StringUtils.substringBefore(currentTaskId, "-");
        String newExecutor = StringUtils.substringBefore(taskId, "-");
        if (!currentExecutor.isEmpty() && currentExecutor.equals(newExecutor)) {
            ScriptExecutor.getInstance().resetQuota(quota);
            reply(ActorMessageBuilder.t_swipeSync(mClientId, taskId, true, "task " + taskId + " quota reset."));
        } else {
            reply(ActorMessageBuilder.t_swipeSync(mClientId, taskId, false,  "Actor is running lua script now, current task: " + currentTaskId));
        }
    }
}
