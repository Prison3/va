package com.android.actor.script;

import java.util.Map;

public interface IScriptExecutor {

    boolean isPrefixValid(String script);

    boolean isRunning();

    void loadScript(String taskId, String script, Map<String, String> scriptParams);

    String executeScript(String taskId, int quota);

    String geCurrentTaskId();

    void resetQuota(int quota);

    void stopScript();

    void retry();

    String getState();

    long getLastModified();
}
