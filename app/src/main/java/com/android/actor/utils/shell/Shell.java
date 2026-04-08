package com.android.actor.utils.shell;

import com.android.actor.monitor.Logger;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Shell {
    private final static String TAG = Shell.class.getSimpleName();

    // return output
    public static List<String>  execRootCmd(String cmd) throws IOException {
        long start = System.currentTimeMillis();
        List<String> result = new ArrayList<>();
        DataOutputStream dos = null;
        BufferedReader reader = null;
        try {
            Process p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            dos.writeBytes(cmd + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
            String line = null;
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            Logger.e(TAG, e);
        } finally {
            if (dos != null) {
                dos.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
        Logger.i(TAG, "using time: " + (System.currentTimeMillis() - start) + "ms\n" + cmd);
        return result;
    }

    public static List<String> execRootCmd(List<String> cmds) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String cmd : cmds) {
            sb.append(cmd).append("\n");
        }
        return execRootCmd(sb.toString());
    }

    public static LuaTable execRootCmd(LuaTable table) {
        LuaTable result = LuaTable.tableOf();
        try {
            List<String> list = new ArrayList<>();
            for (int i = 1; i < table.length() + 1; i++) {
                list.add(table.get(i).tojstring());
            }
            List<String> ret = execRootCmd(list);
            for (int i = 0; i < ret.size(); i++) {
                result.set(i + 1, CoerceJavaToLua.coerce(ret.get(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, e.toString());
        }
        return result;
    }

    // without return
    public static void execRootCmdSilent(String cmd) {
        long start = System.currentTimeMillis();
        DataOutputStream dos = null;
        try {
            Process p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
            dos.writeBytes(cmd + "\n");
            dos.flush();
            dos.writeBytes("exit\n");
            dos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Logger.i(TAG, "using time: " + (System.currentTimeMillis() - start) + "ms\n" + cmd);
    }

    public static void execRootCmdSilent(List<String> cmds) {
        StringBuilder sb = new StringBuilder();
        for (String cmd : cmds) {
            sb.append(cmd).append("\n");
        }
        execRootCmdSilent(sb.toString());
    }

    public static boolean execRootCmdSilent(LuaTable table) {
        try {
            List<String> list = new ArrayList<>();
            for (int i = 1; i < table.length() + 1; i++) {
                list.add(table.get(i).tojstring());
            }
            execRootCmdSilent(list);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, e.toString());
            return false;
        }
    }

    public static void inputKeyEvent(int keyEvent) {
        execRootCmdSilent("input keyevent " + keyEvent);
    }

    public static void enter() {
        execRootCmdSilent("input keyevent KEYCODE_ENTER");
    }

    public static void swipe(int fromX, int fromY, int toX, int toY, int duration) {
        execRootCmdSilent("input swipe " + fromX + " " + fromY + " " + toX + " " + toY + " " + duration);
    }

    public static void inputText(String text) {
        execRootCmdSilent("input text " + text);
    }
}