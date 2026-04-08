package com.android.actor.script.dex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Pair;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.control.RocketComponent;
import com.android.actor.device.NewStage;
import com.android.actor.monitor.Logger;
import com.android.actor.script.IScriptExecutor;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.internal.ds.IDexCallback;
import com.android.internal.ds.IDexScript;

import java.util.HashMap;
import java.util.Map;

public class DexExecutor implements IScriptExecutor, Runnable {

    private static final String TAG = DexExecutor.class.getSimpleName();
    private BlockingReference<Object> mLoadingBlock;
    private String mTaskId;
    private String mScript;
    private IDexScript mDexScript;
    private long mLastModified;

    private final Handler mHandler;

    public enum Status {
        running,
        success,
        failed,
        stopped,
        notfound,
    }
    private Status mStatus;
    private String mCreateTime;
    private String mFinishTime = null;
    private JSONObject mResult = null;

    private Thread mThread;
    private Map<String, String> mParams;
    private int mQuota = 0;
    private final Runnable mStopRunnable = () -> {
        Logger.w(TAG, "Script reach quota " + mQuota);
        stopScript();
    };

    public DexExecutor() {
        mHandler = new Handler(Looper.myLooper());
        // Running task status should not exist when start up. If a running task exist, it should be failed because incorrect record.
        if (Status.running.name().equals(SPUtils.getString(SPUtils.ModuleFile.script, "status"))) {
            SPUtils.putString(SPUtils.ModuleFile.script, "status", Status.failed.name());
        }
    }

    @Override
    public boolean isPrefixValid(String script) {
        return script.startsWith("dex:");
    }

    public boolean isRunning() {
        return mStatus == Status.running;
    }

    @Override
    public void loadScript(String taskId, String script, Map<String, String> scriptParams) {
        mTaskId = taskId;
        mScript = script.substring(4);
        mParams = scriptParams;
        mThread = new Thread(this);
    }

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Logger.d(TAG, "onServiceConnected, mLoadingBlock " + mLoadingBlock);
            mDexScript = IDexScript.Stub.asInterface(service);
            mLoadingBlock.put(null);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Logger.e(TAG, "onServiceDisconnected");
        }
    };

    @Override
    public String executeScript(String taskId, int quota) {
        if (mLoadingBlock != null) {
            String msg = "Previous task " + mTaskId + " is running.";
            Logger.e(TAG, msg);
            return msg;
        }
        Logger.d(TAG, "executeScript " + taskId);
        mLoadingBlock = new BlockingReference<Object>();
        setState(Status.running);
        mCreateTime = ActStringUtils.getStandardTime();
        mResult = null;
        mQuota = quota;
        resetQuota(quota);
        mThread.start();
        return null;
    }

    @Override
    public void run() {
        try {
            Pair<String, Long> pair = DexUpdater.instance.update();
            mLastModified = pair.second;
            Logger.d(TAG, "Run mLastModified " + mLastModified);
            Intent intent = new Intent("com.android.internal.ds");
            intent.setPackage(RocketComponent.PKG_ALPHA);
            intent.putExtra("path", pair.first);
            ActApp.getInstance().bindService(intent, mConn, Context.BIND_AUTO_CREATE);

            mLoadingBlock.take();
            IDexCallback dexCallback = mDexScript.setup(new DexDriver(), new DexActivity(), new DexPackage(), new DexAI());
            NewStage.instance().registerScriptCallback(dexCallback);
            String result = mDexScript.run(mScript, mParams);
            NewStage.instance().registerScriptCallback(null);
            mResult = JSON.parseObject(result);
            Logger.d(TAG, "result: " + result);
        } catch (Throwable e) {
            Logger.w(TAG, "Exception on setup/run DexScript, " + e);
        }
        try {
            ActApp.getInstance().unbindService(mConn);
        } catch (Throwable e) {
            Logger.e(TAG, "Exception on unbindService, " + e);
        }

        if (mResult == null || mResult.containsKey("exception")) {
            setState(Status.failed);
        } else {
            setState(Status.success);
        }
        mLoadingBlock = null;
    }

    @Override
    public String geCurrentTaskId() {
        return mTaskId;
    }

    @Override
    public void resetQuota(int quota) {
        mHandler.removeCallbacks(mStopRunnable);
        if (quota > 0) {
            mHandler.postDelayed(mStopRunnable, quota);
        }
    }

    @Override
    public void stopScript() {
        Logger.d(TAG, "Stop DexScript " + mTaskId);
        try {
            ActApp.getInstance().unbindService(mConn);
        } catch (Throwable e) {
            Logger.e(TAG, "Exception on unbindService, " + e);
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
    public String getState() {
        return mStatus.name();
    }

    @Override
    public long getLastModified() {
        return mLastModified;
    }

    public void setState(Status status) {
        Logger.i(TAG, "Lua status " + status.name());
        mStatus = status;
        SPUtils.putString(SPUtils.ModuleFile.script, "status", mStatus.name());
        if (mStatus == Status.running) {
            record();
        } else if (mStatus != Status.notfound) {
            mFinishTime = ActStringUtils.getStandardTime();
            record();

            Logger.d(TAG, "Script end, remove lastRead.");
            long time = System.currentTimeMillis();
            SPUtils.putLong(SPUtils.ModuleFile.script, "endTime", time);
            boolean ignoreLastRead = Boolean.parseBoolean(mParams.getOrDefault("_ignore_last_read", "false"));
            if (ignoreLastRead || !"RunScript".equals(mParams.get("_from"))) {
                SPUtils.putLong(SPUtils.ModuleFile.script, "lastRead", 1);
            } else {
                SPUtils.removeKey(SPUtils.ModuleFile.script, "lastRead");
            }
        }
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_TASK_STATUS, null);
    }

    private void record() {
        Map<String, String> map = new HashMap<>();
        map.put("taskId", mTaskId);
        map.put("status", mStatus.name());
        map.put("createTime", mCreateTime);
        map.put("finishTime", mFinishTime);
        if (mResult != null) {
            map.put("record", mResult.getString("log"));
            map.put("result", mResult.getString("result_kvs"));
        }
        SPUtils.putStringMap(SPUtils.ModuleFile.script, map);
    }
}
