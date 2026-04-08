package com.android.actor.grpc.process;

import android.util.Pair;
import com.android.actor.ActAccessibility;
import com.android.actor.control.ActActivityManager;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.monitor.Logger;
import com.android.actor.script.ScriptExecutor;
import com.android.actor.script.dex.DexUpdater;
import com.android.actor.utils.SPUtils;
import com.android.proto.common.RunScriptReq;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class ProcessRunScript extends RequestProcess<RunScriptReq> {
    private static final String TAG = ProcessRunScript.class.getSimpleName();

    @Override
    public void process(RunScriptReq req) {
        mClientId = req.getClientId();
        String taskId = req.getTaskId();
        String scriptUrl = req.getLuaScript();
        Map<String, String> scriptParams = req.getScriptParamsMap();
        int quota = req.getQuota();
        if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(scriptUrl)
                || !ScriptExecutor.getInstance().isPrefixValid(scriptUrl)) {
            reply(ActorMessageBuilder.runScript(mClientId, taskId, false,
                    "Param task_id or lua_script is empty or invalid, please check request."));
        } else {
            if (ActAccessibility.isStart()) {
                try {
                    Pair<String, Long> pair = DexUpdater.instance.update();
                    Logger.d(TAG, "Update first, time " + pair.second + ", last run " + ScriptExecutor.getInstance().getLastModified());
                    if (ScriptExecutor.getInstance().getLastModified() >= 0
                            && pair.second != ScriptExecutor.getInstance().getLastModified()) {
                        if (ScriptExecutor.getInstance().isRunning()) {
                            ScriptExecutor.getInstance().stopScript();
                        }
                        boolean stopPackage = Boolean.parseBoolean(scriptParams.getOrDefault("_stop_package", "true"));
                        Logger.d(TAG, "_stop_package " + stopPackage);
                        if (stopPackage) {
                            ActActivityManager.getInstance().forceStopAllPackage();
                        }
                        Thread.sleep(RandomUtils.nextInt(3000, 6000));
                    }

                    //如果当前没有运行脚本
                    if (!ScriptExecutor.getInstance().isRunning()) {
                        boolean ignoreLastRead = Boolean.parseBoolean(scriptParams.getOrDefault("_ignore_last_read", "false"));
                        Logger.d(TAG, "ignoreLastRead " + ignoreLastRead);
                        long endTime = SPUtils.getLong(SPUtils.ModuleFile.script, "endTime");
                        long lastRead = SPUtils.getLong(SPUtils.ModuleFile.script, "lastRead");

                        if (ignoreLastRead || endTime == 0 || lastRead > 0 || System.currentTimeMillis() - endTime > 300 * 1000) {
                            Logger.d(TAG, "Run scriptUrl " + scriptUrl + "taskId is:" + taskId + " with scriptParams " + scriptParams);
                            Map<String, String> params = new HashMap<>(scriptParams);
                            params.put("_from", "RunScript");
                            ScriptExecutor.getInstance().loadScript(taskId, scriptUrl, params);
                            String msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                            reply(ActorMessageBuilder.runScript(mClientId, taskId, msg == null, msg));
                        } else {
                            String msg = "Last result is not read, past " + ((System.currentTimeMillis() - endTime) / 1000) + "s.";
                            Logger.d(TAG, msg);
                            reply(ActorMessageBuilder.runScript(mClientId, taskId, false, msg));
                        }
                    } else {
                        String olderTaskId = ScriptExecutor.getInstance().geCurrentTaskId();

                        // 如果当前正在运行的脚本和请求的脚本不一致，停止当前脚本，运行新脚本
                        if (!scriptUrl.equals(ScriptExecutor.getInstance().getScript()) || !scriptParams.equals(ScriptExecutor.getInstance().getScriptParams())) {
                            /*Logger.d(TAG, "scriptUrl or scriptParams is not equal, stop scriptUrl and run new scriptUrl");
                            if (ScriptExecutor.getInstance().isRunning()) {
                                ScriptExecutor.getInstance().stopScript();
                                Thread.sleep(RandomUtils.nextInt(3000, 6000));
                            }
                            Logger.d(TAG, "Run scriptUrl " + scriptUrl + "taskId is:" + taskId + " with scriptParams " + scriptParams);
                            ScriptExecutor.getInstance().loadScript(taskId, scriptUrl, scriptParams);
                            String newTaskId = ScriptExecutor.getInstance().geCurrentTaskId();
                            String msg = ScriptExecutor.getInstance().executeScript(taskId, quota);
                            Logger.d(TAG,"olderTaskId is:" + olderTaskId + ", newTaskId is:" + newTaskId);
                            reply(ActorMessageBuilder.runScript(mClientId, taskId, msg == null, msg + ", task " + olderTaskId + " is replaced by " + newTaskId));*/

                            String msg = "one task is running, no replace, olderTaskId " + olderTaskId;
                            Logger.d(TAG, msg);
                            reply(ActorMessageBuilder.runScript(mClientId, taskId, false, msg));
                        } else {
                            // 如果当前正在运行的脚本和请求的脚本一致，不做处理
                            Logger.d(TAG, "scriptUrl and scriptParams is equal, do nothing");
                            ScriptExecutor.getInstance().resetQuota(quota);
                            reply(ActorMessageBuilder.runScript(mClientId, taskId, true, "same script is running , run the older script"));
                        }
                    }
                } catch (Throwable e) {
                    Logger.e(TAG, "RunScript exception.", e);
                    reply(ActorMessageBuilder.runScript(mClientId, taskId, false, "Invoke remote conn exception:" + e.getMessage()));
                }
            } else {
                reply(ActorMessageBuilder.runScript(mClientId, taskId, false, "Actor not start accessibility yet"));
            }
        }
    }
}