package com.android.actor.utils.top;

import java.util.List;

public class TopLineItem {
    private final static String TAG = TopLineItem.class.getSimpleName();
    private int pid;
    private String user;
    private String priority; // 优先级
    private int nice; // 负值表示高优先级，正值表示低优先级
    private String virtualMemory; // 虚拟内存
    private String residentMemory; // 常驻内存
    private String shareMemory; // 共享内存大小
    private String status; // （D=不可中断的睡眠状态，R=运行，S=睡眠，T=跟踪/停止，Z=僵尸进程）
    private String cpuPercentage;
    private String memoryPercentage;
    private String timeUsage;// 进程使用的CPU时间总计
    private String args;

    public TopLineItem(String line, List<String> order) {
        String[] items = line.split("\\s+");
        int offset = 0;
        while (items[offset].isEmpty()) {
            offset++;
        }

        // 去掉第一个是[1m的情况
        String first = items[offset].replaceAll("\\x1b\\[[0-9]+m", "");
        if (first.isEmpty()) {
            offset += 1;
        } else {
            items[offset] = first;
        }
        int i = offset;
        for (; i - offset < order.size() && i < items.length; i++) {
            switch (order.get(i - offset)) {
                case "PID":
                    pid = Integer.parseInt(items[i]);
                    break;
                case "USER":
                    user = items[i];
                    break;
                case "PR":
                    priority = items[i];
                    break;
                case "NI":
                    nice = Integer.parseInt(items[i]);
                    break;
                case "VIRT":
                    virtualMemory = items[i];
                    break;
                case "RES":
                    residentMemory = items[i];
                    break;
                case "SHR":
                    shareMemory = items[i];
                    break;
                case "S":
                    status = items[i];
                    break;
                case "CPU":
                    cpuPercentage = items[i];
                    break;
                case "MEM":
                    memoryPercentage = items[i];
                    break;
                case "TIME":
                    timeUsage = items[i];
                    break;
                case "ARGS":
                    args = items[i];
                    break;
            }
        }
        // args 有命令行的空格
        for (int j = i; j < items.length; j++) {
            args += " " + items[j];
        }
    }

    @Override
    public String toString() {
        return "TopLineItem{" +
                "pid=" + pid +
                ", user='" + user + '\'' +
                ", priority=" + priority +
                ", nice=" + nice +
                ", virtualMemory='" + virtualMemory + '\'' +
                ", residentMemory='" + residentMemory + '\'' +
                ", shareMemory='" + shareMemory + '\'' +
                ", status='" + status + '\'' +
                ", cpu='" + cpuPercentage + '\'' +
                ", memoryPercentage='" + memoryPercentage + '\'' +
                ", timeUsage='" + timeUsage + '\'' +
                ", args='" + args + '\'' +
                '}';
    }

    public int getPid() {
        return pid;
    }

    public String getUser() {
        return user;
    }

    public int getPriority() {
        return priority.equals("RT") ? 9999 : Integer.parseInt(priority);
    }

    public int getNice() {
        return nice;
    }

    public String getStatus() {
        return status;
    }

    public int getShareMemory() {
        return parseToMbInt(shareMemory);
    }

    public int getVirtualMemory() {
        return parseToMbInt(virtualMemory);
    }

    public int getResidentMemory() {
        return parseToMbInt(residentMemory);
    }


    public float getMemoryPercentage() {
        return Float.parseFloat(memoryPercentage);
    }

    public float getCpuPercentage() {
        return Float.parseFloat(cpuPercentage);
    }

    public String getTimeUsage() {
        return timeUsage;
    }

    public String getArgs() {
        return args;
    }


    private int parseToMbInt(String str) {
        if (str.length() == 1) {
            return Integer.parseInt(str);
        }
        try {
            int value = (int) Float.parseFloat(str.substring(0, str.length() - 1));
            if (str.endsWith("G")) {
                return value * 1000;
            }
            if (str.endsWith("M")) {
                return value;
            }
            if (str.endsWith("K")) {
                return 0;
            }
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 0;
        }
    }
}
