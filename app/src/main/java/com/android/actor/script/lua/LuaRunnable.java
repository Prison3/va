package com.android.actor.script.lua;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;

import org.luaj.vm2.LuaValue;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class LuaRunnable implements Runnable {
    private final static String TAG = LuaRunnable.class.getSimpleName();
    private final LuaScript mLua;
    private final String mScriptStr;
    private final Map<String, String> mParams;

    public LuaRunnable(LuaScript lua, String script, Map<String, String> scriptParams) {
        mLua = lua;
        mScriptStr = script;
        mParams = scriptParams;
    }

    @Override
    public void run() {
        try {
            LuaValue mScript;
            if (mScriptStr.startsWith("str:")) {
                String scriptStr = mScriptStr.substring(4);
                mScript = mLua.globals.load(scriptStr);
            } else if (mScriptStr.startsWith("debug:")) {
                String scriptStr = mScriptStr.substring(6);
                LuaChecker.parseLuaStream(new ByteArrayInputStream(scriptStr.getBytes()), mLua.logger);
                mScript = mLua.globals.load(scriptStr);
            } else if (mScriptStr.startsWith("url:")) {
                String url = mScriptStr.substring(4);
                mScript = mLua.globals.loadfile(url);
            } else {
                throw new Exception("Invalid script format.");
            }
            for (String key : mParams.keySet()) {
                mLua.globals.set(key, mParams.get(key));
                Logger.i(TAG, "Set lua params: " + key + " " + mParams.get(key));
            }
            mLua.setState(LuaScript.LuaStatus.running);
            mScript.call();
            mLua.setState(LuaScript.LuaStatus.success);
        } catch (Exception e) {
            // luaj 标志位的线程中断
            if (e.getMessage() != null && e.getMessage().contains("OutsideInterrupted")) {
                mLua.setState(LuaScript.LuaStatus.stopped);
            } else {
                Logger.e(TAG, "e", e);
                mLua.logger.i(TAG, "================Exception===============");
                String tailMessage = e.getMessage();
                String[] lines = tailMessage.split("\n");
                if (lines.length > 10) {
                    // supposed to be LuaError
                    String msg = lines[0] + "\n...\n";
                    msg = msg + lines[lines.length - 1].trim().replaceFirst(":", "line ");
                    mLua.logger.e(TAG, msg);
                    for (StackTraceElement element : e.getStackTrace()) {
                        mLua.logger.e(TAG, element.toString());
                    }
                } else {
                    if (tailMessage.length() > 200) {
                        tailMessage = " ..." + tailMessage.substring(tailMessage.length() - 200);
                    }
                    mLua.logger.e(TAG, tailMessage);
                }
                try {
                    Logger.e(TAG, "detailMessage: " + ReflectUtils.getObjectField(e, "detailMessage"));
                } catch (Throwable _e) {
                }
                Throwable cause = e.getCause();
                if (cause != null) {
                    Logger.e(TAG, "Cause:");
                    Logger.stackTrace(TAG, cause);
                }
                mLua.setState(LuaScript.LuaStatus.failed);
            }
        }
    }
}
