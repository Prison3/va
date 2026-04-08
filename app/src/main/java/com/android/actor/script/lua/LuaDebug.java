package com.android.actor.script.lua;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ActStringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * test stage
 */
public class LuaDebug {
    private final static String TAG = LuaDebug.class.getSimpleName();
    private static LuaDebug sInstance;

    private LuaDebug() {
    }

    public static LuaDebug getInstance() {
        if (sInstance == null) {
            sInstance = new LuaDebug();
        }
        return sInstance;
    }

    private LuaScript mScript;
    private final List<String> mCmdLines = new ArrayList<>();
    private boolean isStart = false;

    public void init() {
        Logger.d(TAG, "Debug init.");
        reset();
        isStart = true;
        mScript = new LuaScript("debug_input");
    }

    public void reset() {
        Logger.d(TAG, "Debug reset.");
        mCmdLines.clear();
        if (mScript != null) {
            mScript.logger.flushRecord();
        }
        isStart = false;
    }

    // use this to execute shell input line by line
    public void execute(String cmd, Callback.C3<Boolean, String, String> callback) {
        Logger.d(TAG, "Debug execute cmd: " + cmd);
        if (!isStart || mScript == null) {
            callback.onResult(false, "please call start before run cmds", null);
            return;
        }
        try {
            mScript.globals.load(cmd).call();
            mCmdLines.add(cmd);
            if (callback != null) {
                callback.onResult(true, null, mScript.logger.flushRecord());
            }
        } catch (Exception e) {
            e.printStackTrace();
            mScript.logger.e(TAG, e.toString(), e);
            if (callback != null) {
                callback.onResult(false, e.toString(), mScript.logger.flushRecord());
            }
        }
    }

    public String getLastInput() {
        return mCmdLines.size() > 0 ? mCmdLines.get(mCmdLines.size() - 1) : null;
    }

    public String getScript() {
        return ActStringUtils.listToString(mCmdLines);
    }

    public void test() {
        init();
        execute("a='hello world'\nb=1\nc=1", null);
        execute("b=b+1\nb:log()", null); // program error will not throw unless error appear
        execute("c=c+1\ne.f", null); // syntax error throw when loaded
        execute("g_log:d('a:'..a)\ng_log:d('b:'..b)\ng_log:d('c:'..c)", null);
    }
}
