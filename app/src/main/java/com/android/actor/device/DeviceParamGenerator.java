package com.android.actor.device;

import org.apache.commons.lang3.RandomUtils;

import java.util.HashMap;
import java.util.Map;

public class DeviceParamGenerator {

    private static final String ANDROID_ID_CHARS = "0123456789abcdef";

    private static final Map<String, String> MAC_PREFIX = new HashMap<String, String>() {{
        put("Google", "1C:F2:9A");
        put("huawei", "D0:16:B4");
        put("oppo", "70:5E:55");
        put("xiaomi", "E0:CC:F8");
        put("redmi", "A4:45:19");
        put("samsung", "38:9A:F6");
    }};

    public static String generateAndroidId() {
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < 16; ++i) {
            builder.append(ANDROID_ID_CHARS.charAt(RandomUtils.nextInt(0, ANDROID_ID_CHARS.length())));
        }
        return builder.toString();
    }

    public static String generateMacAddr() {
        StringBuilder builder = new StringBuilder(17);
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        return builder.toString().toUpperCase();
    }

    public static String generateMacAddr(String manufacturer) {
        StringBuilder builder = new StringBuilder(17);
        builder.append(MAC_PREFIX.get(manufacturer));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        builder.append(':');
        builder.append(String.format("%02x", RandomUtils.nextInt(0, 0xFF)));
        return builder.toString().toUpperCase();
    }
}
