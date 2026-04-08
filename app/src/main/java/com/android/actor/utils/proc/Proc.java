package com.android.actor.utils.proc;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.shell.Shell;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Proc {
    private final static String TAG = Proc.class.getSimpleName();

    public static long getTotalCpuTime() {
        try {
            ProcStat procStat = new ProcStat();
            if (procStat.getCpuCount() > 0) {
                return procStat.cpuTotal.getTotalTime();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static long getPidCpuTime(int pid) {
        try {
            PidStat pidStat = new PidStat(pid);
            return pidStat.getProcessCpuTime();
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static float getPidUsage(long totalTimeStart, long totalTimeEnd, long pidTimeStart, long pidTimeEnd) {
        if (totalTimeEnd - totalTimeStart != 0) {
            return (pidTimeEnd - pidTimeStart) * 100.0f / (totalTimeEnd - totalTimeStart);
        } else {
            return 0.0f;
        }
    }

    public static List<Integer> getPidList() {
        List<Integer> list = new ArrayList<>();
        try {
            List<String> lines = Shell.execRootCmd("ls /proc/");
            if (lines.size() > 0) {
                for (String pid : lines) {
                    if (!ActStringUtils.isPositiveNumeric(pid)) {
                        break;
                    }
                    list.add(Integer.valueOf(pid));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static HashMap<Integer, Long> getPidTimeMap() {
        HashMap<Integer, Long> pidMap = new HashMap<>();
        List<Integer> pidList = getPidList();
        Logger.i(TAG, "getPidTimeMap get pid counts: " + pidList.size());
        for (int pid : pidList) {
            pidMap.put(pid, getPidCpuTime(pid));
        }
        return pidMap;
    }

    public static HashMap<Integer, Long> readPid() {
        HashMap<Integer, Long> pidTimeMap = new HashMap<>();
        try {
            List<Integer> pidList = getPidList();
            DataOutputStream dos = null;
            DataInputStream dis = null;
            Process p = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(p.getOutputStream());
            dis = new DataInputStream(p.getInputStream());

            for (int pid : pidList) {
                dos.writeBytes("cat /proc/" + pid + "/stat\n");
            }
            dos.writeBytes("exit\n");
            dos.flush();
            for (int pid : pidList) {
                String pidString = dis.readLine();
                Logger.i(TAG, "cat /proc/" + pid + "/stat: " + pidString);
                if (pidString != null) {
                    PidStat pidStat = new PidStat(pidString);
                    pidTimeMap.put(pid, pidStat.getProcessCpuTime());
                }
            }
            p.waitFor();
            dis.close();
            dos.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return pidTimeMap;
    }
}
