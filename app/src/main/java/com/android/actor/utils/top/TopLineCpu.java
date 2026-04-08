package com.android.actor.utils.top;

public class TopLineCpu {
    public int cpu;
    public int user;
    public int nice;
    public int sys;
    public int idle;
    public int iow;
    public int irq;
    public int sirq;
    public int host;

    public TopLineCpu(String line) {
        String[] items = line.split("\\s+");
        int offset = 0;
        while (items[offset].isEmpty()) {
            offset++;
        }
        for (int i = offset; i < items.length; i++) {
            String[] words = items[i].split("%");
            switch (words[1]) {
                case "cpu":
                    cpu = Integer.parseInt(words[0]);
                    break;
                case "user":
                    user = Integer.parseInt(words[0]);
                    break;
                case "nice":
                    nice = Integer.parseInt(words[0]);
                    break;
                case "sys":
                    sys = Integer.parseInt(words[0]);
                    break;
                case "idle":
                    idle = Integer.parseInt(words[0]);
                    break;
                case "iow":
                    iow = Integer.parseInt(words[0]);
                    break;
                case "irq":
                    irq = Integer.parseInt(words[0]);
                    break;
                case "sirq":
                    sirq = Integer.parseInt(words[0]);
                    break;
                case "host":
                    host = Integer.parseInt(words[0]);
                    break;
            }
        }
    }

    @Override
    public String toString() {
        return "TopLineCpu{" +
                "cpu=" + cpu +
                ", user=" + user +
                ", nice=" + nice +
                ", sys=" + sys +
                ", idle=" + idle +
                ", iow=" + iow +
                ", irq=" + irq +
                ", sirq=" + sirq +
                ", host=" + host +
                '}';
    }
}
