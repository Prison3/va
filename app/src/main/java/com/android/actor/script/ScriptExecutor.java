package com.android.actor.script;

import android.annotation.SuppressLint;
import com.android.actor.monitor.Logger;
import com.android.actor.script.dex.DexExecutor;
import com.android.actor.script.lua.LuaExecutor;
import com.android.actor.utils.FileHelper;

import java.io.IOException;
import java.util.Map;

public class ScriptExecutor {
    private static final String TAG = ScriptExecutor.class.getSimpleName();
    public static final String FILE_PATH_SCRIPT_RECORD = "/data/data/com.android.actor/shared_prefs/script_record_info_list";

    private static Wrapper sExecutor = new Wrapper();

    public static Wrapper getInstance() {
        return sExecutor;
    }

    public static class Wrapper implements IScriptExecutor {

        private IScriptExecutor mLua = new LuaExecutor();
        private IScriptExecutor mDex = new DexExecutor();
        private IScriptExecutor mCurrent;
        private String mScript;

        private Map<String, String> mScriptParams;

        private IScriptExecutor getCurrent() {
            return mCurrent != null ? mCurrent : mLua;
        }

        @Override
        public boolean isPrefixValid(String script) {
            return mDex.isPrefixValid(script) || mLua.isPrefixValid(script);
        }

        @Override
        public boolean isRunning() {
            if (mCurrent == null) {
                return mLua.isRunning() || mDex.isRunning();
            }
            return mCurrent.isRunning();
        }

        @SuppressLint("SdCardPath")
        @Override
        public void loadScript(String taskId, String script, Map<String, String> scriptParams) {
            Logger.d(TAG, "loadScript: " + script);
            try {
                Logger.d(TAG, "loadScript: clear script record info list.");
                FileHelper.writeToFile(FILE_PATH_SCRIPT_RECORD, "");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mScript = script;
            mScriptParams = scriptParams;
            if (script.startsWith("dex:")) {
                mCurrent = mDex;
            } else {
                mCurrent = mLua;
            }
            mCurrent.loadScript(taskId, script, scriptParams);
        }

        public String getScript() {
            return mScript;
        }

        public Map<String, String> getScriptParams() {
            return mScriptParams;
        }

        @Override
        public String executeScript(String taskId, int quota) {
            return getCurrent().executeScript(taskId, quota);
        }

        @Override
        public String geCurrentTaskId() {
            return getCurrent().geCurrentTaskId();
        }

        @Override
        public void resetQuota(int quota) {
            getCurrent().resetQuota(quota);
        }

        @Override
        public void stopScript() {
            getCurrent().stopScript();
        }

        @Override
        public void retry() {
            getCurrent().retry();
        }

        @Override
        public String getState() {
            return getCurrent().getState();
        }

        @Override
        public long getLastModified() {
            return getCurrent().getLastModified();
        }
    }
}
