package com.android.actor.utils.top;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TopLineTitle {
    public List<String> order = new ArrayList<>();

    public TopLineTitle(String line) {
        // 有ascii 27的字符，用字母反向匹配
        String[] items = line.split("[^A-Z]+");
        int offset = 0;
        while (items[offset].isEmpty()) {
            offset++;
        }
        for (int i = offset; i < items.length; i++) {
            order.add(items[i]);
        }
    }

    @Override
    public String toString() {
        return "TopLineTitle{" +
                "order=" + Arrays.toString(order.toArray()) +
                '}';
    }
}
