package com.android.actor.utils.proc;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Shell;

import java.io.IOException;
import java.util.HashMap;

public class PidStat {
    private final static String TAG = PidStat.class.getSimpleName();
    private final static HashMap<Integer, PidStat> PID_MAP = new HashMap<>();

    public String pid; //进程(包括轻量级进程，即线程)号
    public String comm; //应用程序或命令的名字
    public String task_state; //任务的状态，R:runnign, S:sleeping (TASK_INTERRUPTIBLE), D:disk sleep (TASK_UNINTERRUPTIBLE), T: stopped, T:tracing stop,Z:zombie, X:dead
    public String ppid; //父进程ID
    public String pgid; //线程组号
    public String sid; //c该任务所在的会话组ID
    public String tty_nr; //(pts/3)该任务的tty终端的设备号，INT（34817/256）=主设备号，（34817-主设备号）=次设备号
    public String tty_pgrp; //终端的进程组号，当前运行在该任务所在终端的前台任务(包括shell 应用程序)的PID。
    public String task_flags; //进程标志位，查看该任务的特性
    public String min_flt; //该任务不需要从硬盘拷数据而发生的缺页（次缺页）的次数
    public String cmin_flt; //累计的该任务的所有的waited-for进程曾经发生的次缺页的次数目
    public String maj_flt; //该任务需要从硬盘拷数据而发生的缺页（主缺页）的次数
    public String cmaj_flt; //累计的该任务的所有的waited-for进程曾经发生的主缺页的次数目
    public String utime = "0"; //该任务在用户态运行的时间，单位为jiffies
    public String stime = "0"; //该任务在核心态运行的时间，单位为jiffies
    public String cutime = "0"; //累计的该任务的所有的waited-for进程曾经在用户态运行的时间，单位为jiffies
    public String cstime = "0"; //累计的该任务的所有的waited-for进程曾经在核心态运行的时间，单位为jiffies
    public String priority; //任务的动态优先级
    public String nice; //任务的静态优先级
    public String num_threads; //该任务所在的线程组里线程的个数
    public String it_real_value; //由于计时间隔导致的下一个 SIGALRM 发送进程的时延，以 jiffy 为单位.
    public String start_time; //该任务启动的时间，单位为jiffies
    public String vsize; //（page）该任务的虚拟地址空间大小
    public String rss; //(page) 该任务当前驻留物理地址空间的大小
    public String rlim; //（bytes）该任务能驻留物理地址空间的最大值
    public String start_code; //该任务在虚拟地址空间的代码段的起始地址
    public String end_code; //该任务在虚拟地址空间的代码段的结束地址
    public String start_stack; //该任务在虚拟地址空间的栈的结束地址
    public String kstkesp; //esp(32 位堆栈指针) 的当前值, 与在进程的内核堆栈页得到的一致.
    public String kstkeip; //指向将要执行的指令的指针, EIP(32 位指令指针)的当前值.
    public String pendingsig; //待处理信号的位图，记录发送给进程的普通信号
    public String block_sig; //阻塞信号的位图
    public String sigign; //忽略的信号的位图
    public String sigcatch; //被俘获的信号的位图
    public String wchan; //如果该进程是睡眠状态，该值给出调度的调用点
    public String nswap; //被swapped的页数，当前没用
    public String cnswap; //所有子进程被swapped的页数的和，当前没用
    public String exit_signal; //该进程结束时，向父进程所发送的信号
    public String task_cpu; //运行在哪个CPU上
    public String task_rt_priority; //实时进程的相对优先级别
    public String task_policy; //进程的调度策略，0=非实时进程，1=FIFO实时进程；2=RR实时进程

    public PidStat(int pid) throws IOException {
        this(Shell.execRootCmd("cat /proc/" + pid + "/stat").get(0));
    }

    public PidStat(String line) {
        String[] toks = line.split("\\s+");
        try {
            pid = toks[0];
            comm = toks[1];
            task_state = toks[2];
            ppid = toks[3];
            pgid = toks[4];
            sid = toks[5];
            tty_nr = toks[6];
            tty_pgrp = toks[7];
            task_flags = toks[8];
            min_flt = toks[9];
            cmin_flt = toks[10];
            maj_flt = toks[11];
            cmaj_flt = toks[12];
            utime = toks[13];
            stime = toks[14];
            cutime = toks[15];
            cstime = toks[16];
            priority = toks[17];
            nice = toks[18];
            num_threads = toks[19];
            it_real_value = toks[20];
            start_time = toks[21];
            vsize = toks[22];
            rss = toks[23];
            rlim = toks[24];
            start_code = toks[25];
            end_code = toks[26];
            start_stack = toks[27];
            kstkesp = toks[28];
            kstkeip = toks[29];
            pendingsig = toks[30];
            block_sig = toks[31];
            sigign = toks[32];
            sigcatch = toks[33];
            wchan = toks[34];
            nswap = toks[35];
            cnswap = toks[36];
            exit_signal = toks[37];
            task_cpu = toks[38];
            task_rt_priority = toks[39];
            task_policy = toks[40];
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e(TAG, "Read info out of range");
        }
        PID_MAP.put(Integer.parseInt(pid), this);
    }

    public long getProcessCpuTime() {
        return Long.parseLong(utime) + Long.parseLong(stime) + Long.parseLong(cutime) + Long.parseLong(cstime);
    }

    public static String getPidName(int pid) {
        PidStat info = PID_MAP.get(pid);
        return info != null ? info.comm : null;
    }

    public static PidStat getPidInfo(int pid) {
        return PID_MAP.get(pid);
    }

    @Override
    public String toString() {
        return "pid=" + pid + ", " + comm + " ppid=" + ppid + ", priority=" + priority;
    }
}
