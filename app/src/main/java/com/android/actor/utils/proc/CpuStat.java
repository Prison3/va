package com.android.actor.utils.proc;


import com.android.actor.monitor.Logger;

public class CpuStat {
    private final static String TAG = CpuStat.class.getSimpleName();
    public String name;
    public long user; // 从系统启动开始累计到当前时刻，用户态的CPU时间（单位：jiffies） ，不包含 nice值为负进程。1jiffies=0.01秒
    public long nice; // 从系统启动开始累计到当前时刻，核心时间（单位：jiffies）
    public long system;  // 从系统启动开始累计到当前时刻，nice值为负的进程所占用的CPU时间（单位：jiffies）
    public long idle; // 从系统启动开始累计到当前时刻，除硬盘IO等待时间以外其它等待时间（单位：jiffies）
    public long iowait; // 从系统启动开始累计到当前时刻，硬盘IO等待时间（单位：jiffies）
    public long irq; // 从系统启动开始累计到当前时刻，硬中断时间（单位：jiffies）
    public long softirq; // 从系统启动开始累计到当前时刻，软中断时间（单位：jiffies）
//    public long steal;  // 虚拟化环境中运行其他操作系统上花费的时间（since Linux 2.6.11）
//    public long guest; // 操作系统运行虚拟CPU花费的时间(since Linux 2.6.24)
//    public long guest_nice; // 运行一个带nice值的guest花费的时间(since Linux 2.6.33)

    public CpuStat(String line) {
        Logger.d(TAG, "init: " + line);
        String[] cpuLine = line.split("\\s+");
        name = cpuLine[0];
        user = Long.parseLong(cpuLine[1]);
        nice = Long.parseLong(cpuLine[2]);
        system = Long.parseLong(cpuLine[3]);
        idle = Long.parseLong(cpuLine[4]);
        iowait = Long.parseLong(cpuLine[5]);
        irq = Long.parseLong(cpuLine[6]);
        softirq = Long.parseLong(cpuLine[7]);
//        steal = Long.parseLong(cpuLine[8]);
//        guest = Long.parseLong(cpuLine[9]);
//        guest_nice = Long.parseLong(cpuLine[10]);
    }

    public long getTotalTime() {
        return user + nice + system + idle + iowait + irq + softirq;
    }
}
