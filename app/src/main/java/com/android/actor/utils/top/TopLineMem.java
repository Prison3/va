package com.android.actor.utils.top;

public class TopLineMem {
    public String total;
    public String used;
    public String free;
    public String buffers;

    public TopLineMem(String line) {
        String[] items = line.split("[,]?\\s+");
        int offset = 0;
        while (items[offset].isEmpty()) {
            offset++;
        }
        total = items[1 + offset];
        used = items[3 + offset];
        free = items[5 + offset];
        buffers = items[7 + offset];
    }

    @Override
    public String toString() {
        return "TopLineMem{" +
                "total='" + total + '\'' +
                ", used='" + used + '\'' +
                ", free='" + free + '\'' +
                ", buffers='" + buffers + '\'' +
                '}';
    }
}

