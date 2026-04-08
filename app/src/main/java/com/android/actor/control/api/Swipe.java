package com.android.actor.control.api;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.android.actor.control.input.ActInputManager;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NDK;

import java.util.Random;

public class Swipe {
    private final static String TAG = Swipe.class.getSimpleName();
    /**
     * Swipe from (fromX, fromY) to (toX, toY) with duration.
     * If duration <= 0, use DEFAULT_SWIPE_DURATION.
     */
    private final static long DEFAULT_SWIPE_DURATION = 2000;
    private final static Rect SCREEN_RECT = new Rect(); // full screen
    public final static Rect RECT = new Rect(); // exclude status_bar and navigation_bar

    public final static int TYPE_ACCELERATE_DECELERATE = 0;
    public final static int TYPE_ACCELERATE = 1;
    public final static int TYPE_DECELERATE = 2;
    public final static int TYPE_LINER = 3;

    public final static int DEFAULT_SWIPE_COUNT = 30;
    public final static int DEFAULT_SWIPE_TYPE = TYPE_ACCELERATE_DECELERATE;

    static {
        SCREEN_RECT.bottom = DeviceInfoManager.getInstance().height;
        SCREEN_RECT.right = DeviceInfoManager.getInstance().width;
        if (SCREEN_RECT.bottom == 0 || SCREEN_RECT.right == 0) {
            SCREEN_RECT.bottom = 1920;
            SCREEN_RECT.right = 1080;
        }
        RECT.right = SCREEN_RECT.right;
        RECT.top = DeviceInfoManager.getInstance().statusBarHeight;
        RECT.bottom = SCREEN_RECT.bottom - DeviceInfoManager.getInstance().navigationBarHeight;
    }

    /**
     * ========================== base function ==========================
     */
    public static int getLinerPoint(int from, int to, int index, int total) {
        return index * (to - from) / total + from;
    }

    public static float[] interpolate(float from, float to, int count, int type) {
        switch (type) {
            case TYPE_ACCELERATE:
                return NDK.interpolateAccelerate(from, to, count);
            case TYPE_DECELERATE:
                return NDK.interpolateDecelerate(from, to, count);
            case TYPE_ACCELERATE_DECELERATE:
                return NDK.interpolateAccelerateDecelerate(from, to, count);
            default:
                return NDK.interpolateLiner(from, to, count);
        }
    }

    public static void swipe(int fromX, int fromY, int toX, int toY, int count, Random random, boolean vertical, int type) {
        Logger.d(TAG, "swipe " + fromX + "," + fromY + " to " + toX + "," + toY + ", move count " + count);
        float cx = (float) (toX + fromX) / 2 + (toX - fromX) * (vertical ? 2 : 0.7f) * (random.nextFloat() - 0.5f); // allow over rect in direction move
        float cy = (float) (toY + fromY) / 2 + (toY - fromY) * (vertical ? 0.7f : 2) * (random.nextFloat() - 0.5f);
        cx = Math.max(0, Math.min(cx, RECT.right));
        cy = Math.max(0, Math.min(cy, RECT.bottom));
        float[] xl = vertical ? NDK.interpolateBezierCurveTwo((float) fromX, cx, (float) toX, count)
                : interpolate((float) fromX, (float) toX, count, type);
        float[] yl = vertical ? interpolate((float) fromY, (float) toY, count, type)
                : NDK.interpolateBezierCurveTwo((float) fromY, cy, (float) toY, count);
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        // down
        MotionEvent downEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_DOWN, fromX, fromY);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(downEvent, 1);

        int i = 0;
        for (; i < count; i++) {
            eventTime = SystemClock.uptimeMillis() + i * ActInputManager.FRAME_INTERVAL + random.nextInt(8) - 4;
            MotionEvent holdEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_MOVE, xl[i] + random.nextFloat(), yl[i] + random.nextFloat());
            holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);
        }

        eventTime += ActInputManager.FRAME_INTERVAL;
        MotionEvent holdEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_MOVE, toX, toY);
        holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);

        MotionEvent upEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_UP, toX, toY);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(upEvent, 2);
        Logger.d(TAG, "eventTime - downTime " + (eventTime - downTime));
        Wait.waits((int) (eventTime - downTime));
    }

    /**
     * ========================== two direction ==========================
     */
    public static void swipeVertical(int fromX, int fromY, int toX, int toY, int count, int type) {
        Random random = new Random();
        count = random.nextInt(10) + count;
        swipe(fromX, fromY, toX, toY, count, random, true, type);
    }

    public static void swipeHorizontal(int fromX, int fromY, int toX, int toY, int count, int type) {
        Random random = new Random();
        count = random.nextInt(10) + count;
        swipe(fromX, fromY, toX, toY, count, random, false, type);
    }

    /**
     * ========================== swipe exact ==========================
     */
    public static void swipeExact(int fromX, int fromY, int toX, int toY, int count) {
        Logger.d(TAG, "swipeExact " + fromX + "," + fromY + " to " + toX + "," + toY + ", move count " + count);
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        // down
        MotionEvent downEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_DOWN, fromX, fromY);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(downEvent, 1);

        eventTime += ActInputManager.FRAME_INTERVAL;
        MotionEvent holdEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_MOVE, fromX, fromY);
        holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);

        for (int i = 0; i < count; i++) {
            eventTime += ActInputManager.FRAME_INTERVAL;
            int x = getLinerPoint(fromX, toX, i, count);
            int y = getLinerPoint(fromY, toY, i, count);
            holdEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y);
            holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);
        }

        for (int i = 0; i < 3; i++) {
            eventTime += ActInputManager.FRAME_INTERVAL;
            holdEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_MOVE, toX, toY);
            holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);
        }

        MotionEvent upEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_UP, toX, toY);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(upEvent, 2);
        Logger.d(TAG, "eventTime - downTime " + (eventTime - downTime));
        Wait.waits((int) (eventTime - downTime));
    }

    public static void swipeUpExact(Rect rect) {
        Logger.d(TAG, "swipeUpExact " + rect);
        Random random = new Random();

        int midX = (rect.left + rect.right) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;

        int fromX = (int) (midX + rangeX * 0.2f * (random.nextFloat() - 0.5f));
        int toX = fromX;
        int fromY = (int) (rect.bottom - rangeY * 0.2f - rangeY * 0.2 * (random.nextFloat() - 0.5f));
        int toY = (int) (rect.top + rangeY * 0.2f + rangeY * 0.2 * (random.nextFloat() - 0.5f));
        swipeExact(fromX, fromY, toX, toY, 60);
    }

    /**
     * ========================== swipe up ==========================
     */
    public static void swipeUp() {
        swipeUp(RECT, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeUp(Rect rect) {
        swipeUp(rect, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeUp(int type) {
        swipeUp(RECT, type);
    }

    public static void swipeUp(Rect rect, int type) {
        Logger.d(TAG, "swipeUp " + rect);
        Random random = new Random();

        int midX = (rect.left + rect.right) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;

        int fromX = (int) (midX + rangeX * 0.15f * (random.nextFloat() - 0.5f));
        int toX = (int) (midX + rangeX * 0.15f * (random.nextFloat() - 0.5f));
        int fromY = (int) (rect.bottom - rangeY * 0.2f - rangeY * 0.2 * (random.nextFloat() - 0.5f));
        int toY = (int) (rect.top + rangeY * 0.2f + rangeY * 0.2 * (random.nextFloat() - 0.5f));
        swipeVertical(fromX, fromY, toX, toY, DEFAULT_SWIPE_COUNT, type);
    }

    /**
     * ========================== swipe down ==========================
     */
    public static void swipeDown() {
        swipeDown(RECT, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeDown(Rect rect) {
        swipeDown(rect, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeDown(int type) {
        swipeDown(RECT, type);
    }

    public static void swipeDown(Rect rect, int type) {
        Logger.d(TAG, "swipeDown " + rect);
        Random random = new Random();

        int midX = (rect.left + rect.right) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;

        int fromX = (int) (midX + rangeX * 0.15f * (random.nextFloat() - 0.5f));
        int toX = (int) (midX + rangeX * 0.15f * (random.nextFloat() - 0.5f));
        int fromY = (int) (rect.top + rangeY * 0.2f + rangeY * 0.2 * (random.nextFloat() - 0.5f));
        int toY = (int) (rect.bottom - rangeY * 0.2f - rangeY * 0.2 * (random.nextFloat() - 0.5f));
        swipeVertical(fromX, fromY, toX, toY, DEFAULT_SWIPE_COUNT, type);
    }

    public static void swipeDownFromTop(int type) {
        Logger.d(TAG, "swipeDownFromTop");
        Random random = new Random();

        int midX = (SCREEN_RECT.left + SCREEN_RECT.right) / 2;
        int rangeX = SCREEN_RECT.right - SCREEN_RECT.left;
        int rangeY = SCREEN_RECT.bottom - SCREEN_RECT.top;

        int fromX = (int) (midX + rangeX * 0.15f * (random.nextFloat() - 0.5f));
        int toX = (int) (midX + rangeX * 0.15f * (random.nextFloat() - 0.5f));
        int fromY = (int) (DeviceInfoManager.getInstance().statusBarHeight == 0 ?
                RECT.top + rangeY * 0.03 * random.nextFloat() :
                DeviceInfoManager.getInstance().statusBarHeight * random.nextFloat());
        int toY = (int) (RECT.bottom - rangeY * 0.2f - rangeY * 0.2 * (random.nextFloat() - 0.5f));
        swipeVertical(fromX, fromY, toX, toY, DEFAULT_SWIPE_COUNT, type);
    }

    /**
     * ========================== swipe left ==========================
     */
    public static void swipeLeft() {
        swipeLeft(RECT, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeLeft(Rect rect) {
        swipeLeft(rect, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeLeft(int type) {
        swipeLeft(RECT, type);
    }

    public static void swipeLeft(Rect rect, int type) {
        Logger.d(TAG, "swipeLeft");
        Random random = new Random();

        int midY = (rect.bottom + rect.top) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;

        // 手机很长，垂直方向随机比例降低
        int fromY = (int) (midY + rangeY * 0.05f * (random.nextFloat() - 0.5f));
        int toY = (int) (midY + rangeY * 0.05f * (random.nextFloat() - 0.5f));
        int fromX = (int) (rect.right - rangeX * 0.2f - rangeX * 0.2 * (random.nextFloat() - 0.5f));
        int toX = (int) (rect.left + rangeX * 0.2f + rangeX * 0.2 * (random.nextFloat() - 0.5f));
        swipeHorizontal(fromX, fromY, toX, toY, DEFAULT_SWIPE_COUNT, type);
    }

    /**
     * ========================== swipe right ==========================
     */
    public static void swipeRight() {
        swipeRight(RECT, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeRight(Rect rect) {
        swipeRight(rect, DEFAULT_SWIPE_TYPE);
    }

    public static void swipeRight(int type) {
        swipeRight(RECT, type);
    }

    public static void swipeRight(Rect rect, int type) {
        Logger.d(TAG, "swipeRight");
        Random random = new Random();

        int midY = (rect.bottom + rect.top) / 2;
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;

        int fromY = (int) (midY + rangeY * 0.05f * (random.nextFloat() - 0.5f));
        int toY = (int) (midY + rangeY * 0.05f * (random.nextFloat() - 0.5f));
        int fromX = (int) (rect.left + rangeX * 0.2f + rangeX * 0.2 * (random.nextFloat() - 0.5f));
        int toX = (int) (rect.right - rangeX * 0.2f - rangeX * 0.2 * (random.nextFloat() - 0.5f));
        swipeHorizontal(fromX, fromY, toX, toY, DEFAULT_SWIPE_COUNT, type);
    }
}