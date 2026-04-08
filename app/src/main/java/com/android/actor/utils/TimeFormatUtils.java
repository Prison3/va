package com.android.actor.utils;

import android.annotation.SuppressLint;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


@SuppressLint("SimpleDateFormat")
public class TimeFormatUtils {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    public static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        TimeZone timeZone = TimeZone.getTimeZone("Asia/Shanghai");

        // 使用指定时区创建 SimpleDateFormat 对象
        DATE_FORMAT.setTimeZone(timeZone);
        DATETIME_FORMAT.setTimeZone(timeZone);
    }


    public static String getCurrentDateStr() {
        // 设置时区为中国上海
        long currentTimeMillis = System.currentTimeMillis();
        Date date = new Date(currentTimeMillis);
        return getDateString(date);
    }

    public static String getDateString(Date date) {
        return DATE_FORMAT.format(date);
    }

    public static String getDatetimeString(int time) {
        return DATETIME_FORMAT.format(new Date((long) time * 1000));
    }

    public static String getDatetimeString(long time) {
        return DATETIME_FORMAT.format(new Date(time));
    }

}
