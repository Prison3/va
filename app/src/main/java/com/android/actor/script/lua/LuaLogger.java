package com.android.actor.script.lua;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import org.luaj.vm2.LuaTable;

import java.util.LinkedList;
import java.util.Queue;


public class LuaLogger {
    private final static String TAG = LuaLogger.class.getSimpleName();
    private final Queue<String> mLogCollector = new LinkedList<>();
    private final static String PRE = "Lua_";
    private int length = 0;

    public enum Level {
        D,
        E,
        W,
        I,
        V,
    }

    //  =============== logger in lua ===============
    public void d(String msg) {
        d(PRE, msg);
    }

    public void e(String msg) {
        e(PRE, msg);
    }

    public void e(String msg, Throwable e) {
        e(PRE, msg, e);
    }

    public void w(String msg) {
        w(PRE, msg);
    }

    public void i(String msg) {
        i(PRE, msg);
    }

    public void v(String msg) {
        v(PRE, msg);
    }

    // =============== logger in java ===============
    public void d(String pre, String msg) {
        msg = pre + ": " + msg;
        Logger.d(TAG, msg);
        record(Level.D, msg);
    }

    public void e(String pre, String msg) {
        msg = pre + ": " + msg;
        Logger.e(TAG, msg);
        record(Level.E, msg);
    }

    public void e(String pre, String msg, Throwable e) {
        msg = pre + ": " + msg;
        Logger.e(TAG, msg, e);
        record(Level.E, msg);
    }

    public void w(String pre, String msg) {
        msg = pre + ": " + msg;
        Logger.w(TAG, msg);
        record(Level.W, msg);
    }

    public void i(String pre, String msg) {
        msg = pre + ": " + msg;
        Logger.i(TAG, msg);
        record(Level.I, msg);
    }

    public void v(String pre, String msg) {
        msg = pre + ": " + msg;
        Logger.v(TAG, msg);
        record(Level.V, msg);
    }

    //   =============== logger table as json ===============
    public void t(LuaTable table) {
        t("", table);
    }

    public void t(String pre, LuaTable table) {
        try {
            d(PRE + "table", pre + " " + LuaUtils.tableToJsonStr(table));
        } catch (Exception e) {
            w(PRE + "table", pre + " parse json fail: " + e.toString());
            d(PRE + "table", pre + " " + (table == null ? null : table.tojstring()));
        }
    }

    private void record(Level level, String msg) {
        synchronized (mLogCollector) {
            if (length < 500) {
                mLogCollector.offer(String.format("%s/%s/%s", level.name(), ActStringUtils.getStandardTime(), msg));
                length++;
            } else {
                mLogCollector.poll();
                mLogCollector.offer(String.format("%s/%s/%s", level.name(), ActStringUtils.getStandardTime(), msg));
            }
        }
    }

    public String flushRecord() {
        synchronized (mLogCollector) {
            StringBuilder sb = new StringBuilder();
            while (true) {
                String msg = mLogCollector.poll();
                if (msg == null) {
                    break;
                } else {
                    sb.append(msg).append("\n");
                }
            }
            return sb.toString();
        }
    }

    public String getRecordLogs() {
        synchronized (mLogCollector) {
            StringBuilder sb = new StringBuilder();
            for (String msg : mLogCollector) {
                sb.append(msg).append("\n");
            }
            return sb.toString();
        }
    }
}