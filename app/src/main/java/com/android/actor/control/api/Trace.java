package com.android.actor.control.api;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.android.actor.control.input.ActInputManager;
import com.android.actor.script.lua.LuaScript;
import com.android.actor.monitor.Logger;

import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;

public class Trace {
    private final static String TAG = Trace.class.getSimpleName();
    private final List<Integer> xList = new ArrayList<>();
    private final List<Integer> yList = new ArrayList<>();
    private int fromX;
    private int fromY;
    private int toX;
    private int toY;
    private boolean isDown = false;
    private boolean isUp = false;
    private int offsetX = 0;
    private int offsetY = 0;

    public Trace() {
    }

    public Trace(int ox, int oy) {
        this.offsetX = ox;
        this.offsetY = oy;
    }

    // No support press time, If you want to hold for a while, use dragTo with same points.
    public Trace press(int x, int y) {
        if (!isDown) {
            fromX = x + offsetX;
            fromY = y + offsetY;
            xList.add(x + offsetX);
            yList.add(y + offsetY);
            isDown = true;
            return this;
        } else {
            throw new RuntimeException("Can not press twice.");
        }
    }

    public Trace dragTo(int x, int y) throws RuntimeException {
        if (isDown) {
            xList.add(x + offsetX);
            yList.add(y + offsetY);
        } else {
            throw new RuntimeException("Can not drag without press.");
        }
        return this;
    }

    public Trace release() {
        if (isDown) {
            // in case no drag, use last
            toX = xList.get(xList.size() - 1);
            toY = yList.get(yList.size() - 1);
            isUp = true;
        } else {
            throw new RuntimeException("Can not release without press.");
        }
        return this;
    }

    public void perform() {
        if (isUp) {
            Logger.d(TAG, "swipe " + fromX + "," + fromY + " to " + toX + "," + toY);
            long downTime = SystemClock.uptimeMillis();
            long eventTime = SystemClock.uptimeMillis();
            // down
            MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, fromX, fromY, 0);
            downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            ActInputManager.getInstance().inFrameEventQueue(downEvent, 1);
            // drag
            int i = 0;
            for (; i < xList.size(); i++) {
                eventTime = SystemClock.uptimeMillis() + i * ActInputManager.FRAME_INTERVAL;
                MotionEvent holdEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, xList.get(i), yList.get(i), 0);
                holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);
            }
            // up
            eventTime = SystemClock.uptimeMillis() + i * ActInputManager.FRAME_INTERVAL;
            MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, toX, toY, 0);
            upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            ActInputManager.getInstance().inFrameEventQueue(upEvent, 2);
            Wait.waits((int) (eventTime - downTime));
            Wait.waits(LuaScript.optionTimeSpan);
        } else {
            throw new RuntimeException("Can not perform without release");
        }
    }

    public static void swipeWithList(LuaTable xList, LuaTable yList) throws RuntimeException {
        swipeWithList(xList, yList, 0, 0);
    }

    @SuppressLint("DefaultLocale")
    public static void swipeWithList(LuaTable xList, LuaTable yList, int offsetX, int offsetY) throws RuntimeException {
        int xLen = xList.length();
        int yLen = yList.length();
        int xK = xList.keyCount();
        int yK = yList.keyCount();
        Logger.d(TAG, String.format("swipe xList length: %d keyCount: %d, yList length: %d keyCount: %d", xLen, xK, yLen, yK));
        if (xLen != yLen) {
            throw new RuntimeException("xList or yList length not match");
        }
        if (xK != xLen || yK != yLen) {
            throw new RuntimeException("xList or yList are not int list.");
        }
        if (xLen < 1) {
            throw new RuntimeException("xList or yList is empty.");
        }
        // lua table start from 1
        int i = 1;
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        // down
        MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, xList.get(i).toint() + offsetX, yList.get(i).toint() + offsetY, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(downEvent, 1);
        // drag
        for (; i < xLen; i++) {
            eventTime = SystemClock.uptimeMillis() + i * ActInputManager.FRAME_INTERVAL;
            MotionEvent holdEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, xList.get(i).toint() + offsetX, yList.get(i).toint() + offsetY, 0);
            holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);
        }
        // up
        eventTime = SystemClock.uptimeMillis() + i * ActInputManager.FRAME_INTERVAL;
        MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, xList.get(i).toint() + offsetX, yList.get(i).toint() + offsetY, 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(upEvent, 2);
    }
}
