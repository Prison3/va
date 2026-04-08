package com.android.actor.utils.top;

public class TopLineSwap {
    public String total;
    public String used;
    public String free;
    public String cached;

    public TopLineSwap(String line) {
        String[] items = line.split("[,]?\\s+");
        int offset = 0;
        while (items[offset].isEmpty()) {
            offset++;
        }
        total = items[1 + offset];
        used = items[3 + offset];
        free = items[5 + offset];
        cached = items[7 + offset];
    }

    @Override
    public String toString() {
        return "TopLineSwap{" +
                "total='" + total + '\'' +
                ", used='" + used + '\'' +
                ", free='" + free + '\'' +
                ", cached='" + cached + '\'' +
                '}';
    }
}
