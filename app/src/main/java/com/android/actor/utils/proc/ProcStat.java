package com.android.actor.utils.proc;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProcStat {
    private final static String TAG = ProcStat.class.getSimpleName();
    public CpuStat cpuTotal;
    public final List<CpuStat> cpuCore = new ArrayList<>();
    public String intr; // 给出中断的信息，第一个为自系统启动以来，发生的所有的中断的次数；然后每个数对应一个特定的中断自系统启动以来所发生的次数。
    public long ctxt; // 给出了自系统启动以来CPU发生的上下文交换的次数。
    public long btime; // 从系统启动到现在为止的时间，单位为秒。
    public long processes; // (total_forks) 自系统启动以来所创建的任务的个数目。
    public long procs_running; // 当前运行队列的任务的数目。
    public long procs_blocked; // 当前被阻塞的任务的数目。
    private int cpuCount = 0;

    public ProcStat() throws IOException {
        this(Shell.execRootCmd("cat /proc/stat"));
    }

    public ProcStat(List<String> lines) throws IOException {
        if (lines.size() > 0) {
            cpuTotal = new CpuStat(lines.get(0));
            String line;
            for (int i = 1; i < lines.size(); i++) {
                line = lines.get(i);
                if (line.startsWith("cpu")) {
                    cpuCore.add(new CpuStat(line));
                    cpuCount++;
                } else {
                    break;
                }
            }
            line = lines.get(cpuCount + 1);
            if (line != null) {
                ctxt = Long.parseLong(line.split("\\s+")[1]);
            }
            line = lines.get(cpuCount + 2);
            if (line != null) {
                btime = Long.parseLong(line.split("\\s+")[1]);
            }
            line = lines.get(cpuCount + 3);
            if (line != null) {
                processes = Long.parseLong(line.split("\\s+")[1]);
            }
            line = lines.get(cpuCount + 4);
            if (line != null) {
                procs_running = Long.parseLong(line.split("\\s+")[1]);
            }
            line = lines.get(cpuCount + 5);
            if (line != null) {
                procs_blocked = Long.parseLong(line.split("\\s+")[1]);
            }
        }
        Logger.d(TAG, "ProcStat get " + cpuCount + " core cpu.");
    }

    public int getCpuCount() {
        return cpuCount;
    }
}
