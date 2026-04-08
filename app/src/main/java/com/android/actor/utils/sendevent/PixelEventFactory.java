package com.android.actor.utils.sendevent;

import android.graphics.Rect;

import com.android.actor.ActAccessibility;
import com.android.actor.monitor.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.android.actor.utils.sendevent.LinuxEvent.*;

public class PixelEventFactory {
    private static final String TAG = PixelEventFactory.class.getSimpleName();
    private final static String SCREEN_EVENT_DEVICE = "event2";
    private static final Random mRandom = new Random();

    public static List<String> getClickEvent(int finger_index, int x, int y, int counts) {
        List<String> cmds = new ArrayList<>();
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_TRACKING_ID, finger_index));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_POSITION_X, x));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_POSITION_Y, y));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_SYN, SYN_REPORT, 0));
        int press = mRandom.nextInt(30) + 50;
        for (int i = 0; i < counts; i++) {
            cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_PRESSURE, press + i));
            cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_SYN, SYN_REPORT, 0));
        }
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_TRACKING_ID, -1));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_SYN, SYN_REPORT, 0));
        return cmds;
    }

    public static List<String> getTraceEvent(int[] xTrace, int[] yTrace) throws Exception {
        if (xTrace.length != yTrace.length) {
            throw new Exception("X trace not match y trace");
        }
        List<String> cmds = new ArrayList<>();
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_TRACKING_ID, 0));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_POSITION_X, xTrace[0]));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_POSITION_Y, yTrace[0]));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_SYN, SYN_REPORT, 0));
        for (int i = 0; i < xTrace.length; i++) {
            cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_POSITION_X, xTrace[i]));
            cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_POSITION_Y, yTrace[i]));
            cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_SYN, SYN_REPORT, 0));
        }
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_ABS, ABS_MT_TRACKING_ID, -1));
        cmds.add(sendEventStr(SCREEN_EVENT_DEVICE, EV_SYN, SYN_REPORT, 0));
        return cmds;
    }

    public static List<String> getClickEvent(int x, int y) {
        return getClickEvent(0, x, y, mRandom.nextInt(3) + 2);
    }

    public static List<String> getLongClickEvent(int x, int y) {
        return getClickEvent(0, x, y, mRandom.nextInt(10) + 10);
    }

    public static List<String> getSwipeUpEvent() throws Exception {
        Rect rect = new Rect();
        if (ActAccessibility.isStart()) {
            ActAccessibility.getInstance().getRootInActiveWindow().getBoundsInScreen(rect);
        } else {
            rect.right = 1080;
            rect.bottom = 1920;
        }
        rect.left = 0;
        rect.top = 0;
        int midX = (rect.left + rect.right) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;
        int fromX = (int) (midX + rangeX * 0.1f * (mRandom.nextFloat() - 0.5f));
        int toX = (int) (midX + rangeX * 0.1f * (mRandom.nextFloat() - 0.5f));
        int fromY = (int) (rect.bottom - rangeY * 0.2f - rangeY * 0.1 * (mRandom.nextFloat() - 0.5f));
        int toY = (int) (rect.top + rangeY * 0.2f + rangeY * 0.1 * (mRandom.nextFloat() - 0.5f));
        Logger.d(TAG, "getSwipeUpEvent fromY " + fromY + ", toY " + toY);

        int count = mRandom.nextInt(10) + 10;
        int[] xTrace = new int[count];
        int[] yTrace = new int[count];
        for (int i = 0; i < count; i++) {
            xTrace[i] = getAcceleratePoint(fromX, toX, i, count - 1);
            yTrace[i] = getAcceleratePoint(fromY, toY, i, count - 1);
        }
        return getTraceEvent(xTrace, yTrace);
    }

    public static List<String> getSwipeDownEvent() throws Exception {
        Rect rect = new Rect();
        if (ActAccessibility.isStart()) {
            ActAccessibility.getInstance().getRootInActiveWindow().getBoundsInScreen(rect);
        } else {
            rect.right = 1080;
            rect.bottom = 1920;
        }
        rect.left = 0;
        rect.top = 0;
        int midX = (rect.left + rect.right) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;
        int fromX = (int) (midX + rangeX * 0.1f * (mRandom.nextFloat() - 0.5f));
        int toX = (int) (midX + rangeX * 0.1f * (mRandom.nextFloat() - 0.5f));
        int fromY = (int) (rect.top + rangeY * 0.2f + rangeY * 0.1 * (mRandom.nextFloat() - 0.5f));
        int toY = (int) (rect.bottom - rangeY * 0.2f - rangeY * 0.1 * (mRandom.nextFloat() - 0.5f));
        Logger.d(TAG, "getSwipeDownEvent fromY " + fromY + ", toY " + toY);

        int count = mRandom.nextInt(10) + 10;
        int[] xTrace = new int[count];
        int[] yTrace = new int[count];
        for (int i = 0; i < count; i++) {
            xTrace[i] = getAcceleratePoint(fromX, toX, i, count - 1);
            yTrace[i] = getAcceleratePoint(fromY, toY, i, count - 1);
        }
        return getTraceEvent(xTrace, yTrace);
    }
    public static List<String> getSwipeLeftEvent() throws Exception {
        Rect rect = new Rect();
        if (ActAccessibility.isStart()) {
            ActAccessibility.getInstance().getRootInActiveWindow().getBoundsInScreen(rect);
        } else {
            rect.right = 1080;
            rect.bottom = 1920;
        }
        rect.left = 0;
        rect.top = 0;
        int midY = (rect.bottom + rect.top) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;
        int fromY = (int) (midY + rangeY * 0.1f * (mRandom.nextFloat() - 0.5f));
        int toY = (int) (midY + rangeY * 0.1f * (mRandom.nextFloat() - 0.5f));
        int fromX = (int) (rect.right - rangeX * 0.3f - rangeX * 0.2 * (mRandom.nextFloat() - 0.5f));
        int toX = (int) (rect.left + rangeX * 0.3f + rangeX * 0.2 * (mRandom.nextFloat() - 0.5f));
        int count = mRandom.nextInt(10) + 10;
        int[] xTrace = new int[count];
        int[] yTrace = new int[count];
        for (int i = 0; i < count; i++) {
            xTrace[i] = getAcceleratePoint(fromX, toX, i, count - 1);
            yTrace[i] = getAcceleratePoint(fromY, toY, i, count - 1);
        }
        return getTraceEvent(xTrace, yTrace);
    }
    public static List<String> getSwipeRightEvent() throws Exception {
        Rect rect = new Rect();
        if (ActAccessibility.isStart()) {
            ActAccessibility.getInstance().getRootInActiveWindow().getBoundsInScreen(rect);
        } else {
            rect.right = 1080;
            rect.bottom = 1920;
        }
        rect.left = 0;
        rect.top = 0;
        int midY = (rect.bottom + rect.top) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;
        int fromY = (int) (midY + rangeY * 0.1f * (mRandom.nextFloat() - 0.5f));
        int toY = (int) (midY + rangeY * 0.1f * (mRandom.nextFloat() - 0.5f));
        int fromX = (int) (rect.left + rangeX * 0.3f + rangeX * 0.2 * (mRandom.nextFloat() - 0.5f));
        int toX = (int) (rect.right - rangeX * 0.3f - rangeX * 0.2 * (mRandom.nextFloat() - 0.5f));
        int count = mRandom.nextInt(10) + 10;
        int[] xTrace = new int[count];
        int[] yTrace = new int[count];
        for (int i = 0; i < count; i++) {
            xTrace[i] = getAcceleratePoint(fromX, toX, i, count - 1);
            yTrace[i] = getAcceleratePoint(fromY, toY, i, count - 1);
        }
        return getTraceEvent(xTrace, yTrace);
    }

    // 二元插值，x = 0开始时， y = i^2 / n^2 * (y2 - y1) + y1
    public static int getAcceleratePoint(int from, int to, int index, int total) {
        return index * index * (to - from) / total / total + from;
    }
}
