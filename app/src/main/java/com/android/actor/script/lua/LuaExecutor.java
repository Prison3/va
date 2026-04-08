package com.android.actor.script.lua;

import android.os.Handler;

import com.android.actor.monitor.Logger;
import com.android.actor.script.IScriptExecutor;
import com.android.actor.utils.SPUtils;

import java.util.Map;

public class LuaExecutor  implements  IScriptExecutor {
    private final static String TAG = LuaExecutor.class.getSimpleName();
    private static LuaExecutor sInstance;

    public LuaExecutor() {
        mHandler = new Handler();
        // Running task status should not exist when start up. If a running task exist, it should be failed because incorrect record.
        if (LuaScript.LuaStatus.running.name().equals(SPUtils.getString(SPUtils.ModuleFile.script, "status"))) {
            SPUtils.putString(SPUtils.ModuleFile.script, "status", LuaScript.LuaStatus.failed.name());
        }
    }

//    private synchronized static LuaExecutor getInstance() {
//        if (sInstance == null) {
//            sInstance = new LuaExecutor();
//        }
//        return sInstance;
//    }

    private final Handler mHandler;
    private LuaScript mLuaInst;
    private Thread mLuaThread;

    // for retry
    private String mTaskId;
    private String mScript;
    private Map<String, String> mParams;
    private int mQuota;
    private Runnable mStopRunnable = () -> {
        Logger.w(TAG, "Script reach quota " + mQuota);
        stopScript();
    };

    @Override
    public void loadScript(String taskId, String script, Map<String, String> params) {
        Logger.d(TAG, "Set lua " + taskId);
        mLuaInst = new LuaScript(taskId);
        LuaRunnable runnable = new LuaRunnable(mLuaInst, script, params);
        mLuaThread = new Thread(runnable, "LuaThread");
        mTaskId = taskId;
        mScript = script;
        mParams = params;
    }

    @Override
    public String executeScript(String taskId, int quota) {
        Logger.d(TAG, "Run lua " + taskId);
        mQuota = quota;
        if (mLuaInst != null && mLuaThread != null && geCurrentTaskId().equals(taskId)) {
            mLuaThread.start();
            resetQuota(quota);
            return null;
        } else {
            return "Run lua failed: lua " + mLuaInst + ", thread " + mLuaThread + ", taskId: " + taskId;
        }
    }

    @Override
    public void retry() {
        if (mTaskId != null && mScript != null && !isRunning()) {
            Logger.d(TAG, "retry lua script: " + mTaskId);
            loadScript(mTaskId, mScript, mParams);
            executeScript(mTaskId, mQuota);
        }
    }

    @Override
    public boolean isPrefixValid(String script) {
        return script.startsWith("url:") || script.startsWith("str:") || script.startsWith("debug:");
    }

    @Override
    public boolean isRunning() {
        return mLuaInst != null && mLuaInst.getState() == LuaScript.LuaStatus.running;
    }

    @Override
    public String geCurrentTaskId() {
        return mLuaInst != null ? mLuaInst.getTaskId() : null;
    }

    @Override
    public String getState() {
        if (mLuaInst != null) {
            return mLuaInst.getState().name();
        }
        return LuaScript.LuaStatus.notfound.name();
    }

    @Override
    public long getLastModified() {
        return -1;
    }

    @Override
    public void stopScript() {
        Logger.d(TAG, "Stop lua " + mTaskId);
        if (mLuaInst != null && mLuaThread != null) {
            if (mLuaInst.getState() == LuaScript.LuaStatus.running) {
                try {
                    mLuaInst.globals.isInterrupted = true;
                    mLuaThread.interrupt();
                } catch (Exception e) {
                    // luaj sleep时候的中断
                    e.printStackTrace();
                }
                mLuaInst.setState(LuaScript.LuaStatus.stopped);
            }
        }
    }

    @Override
    public void resetQuota(int quota) {
        mHandler.removeCallbacks(mStopRunnable);
        if (quota > 0) {
            mHandler.postDelayed(mStopRunnable, quota);
        }
    }
}
