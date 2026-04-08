package com.android.actor.utils.top;

public class TopLineTasks {
    public int total;
    public int running;
    public int sleeping;
    public int stopped;
    public int zombie;


    public TopLineTasks(String line) {
        String[] items = line.split("[,]?\\s+");
        int offset = 0;
        while (items[offset].isEmpty()) {
            offset++;
        }
        total = Integer.parseInt(items[1 + offset]);
        running = Integer.parseInt(items[3 + offset]);
        sleeping = Integer.parseInt(items[5 + offset]);
        stopped = Integer.parseInt(items[7 + offset]);
        zombie = Integer.parseInt(items[9 + offset]);
    }

    @Override
    public String toString() {
        return "TopLineTasks{" +
                "total=" + total +
                ", running=" + running +
                ", sleeping=" + sleeping +
                ", stopped=" + stopped +
                ", zombie=" + zombie +
                '}';
    }
}
