package com.android.actor.control.api;


import android.graphics.Point;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.android.actor.control.UiElement;
import com.android.actor.control.input.ActInputManager;
import com.android.actor.script.lua.LuaScript;
import com.android.actor.monitor.Logger;

import java.util.Random;


/**
 * perform click action through InputManager
 */
public class Click {
    private final static String TAG = Click.class.getSimpleName();
    private final static Random mRandom = new Random();
    private final static int CLICK_HOLD_RANGE_MIN = 0;
    private final static int CLICK_HOLD_RANGE = 5;
    private final static int LONG_CLICK_HOLD_RANGE_MIN = 20;
    private final static int LONG_CLICK_HOLD_RANGE = 10;


    public static void click(UiElement ele) throws ApiException.NullElementException {
        Logger.d(TAG, "click " + ele);
        if (ele != null) {
            Point p = ele.getRandomPoint();
            if (p == null) {
                Logger.w(TAG, "attempt to click null rect element");
                return;
            }
            Logger.d(TAG, "click " + p.x + "," + p.y);
            click(p.x, p.y, CLICK_HOLD_RANGE_MIN + mRandom.nextInt(CLICK_HOLD_RANGE));
//        Shell.tap(p.x, p.y);
        } else {
            throw new ApiException.NullElementException("Attempt to invoke click with null.");
        }
    }

    public static void click(int x, int y) {
        Logger.d(TAG, "click " + x + "," + y);
        click(x, y, CLICK_HOLD_RANGE_MIN + mRandom.nextInt(CLICK_HOLD_RANGE));
    }


    public static void longClick(UiElement ele) throws ApiException.NullElementException {
        Logger.d(TAG, "longClick " + ele);
        if (ele != null) {
            Point p = ele.getRandomPoint();
            if (p == null) {
                Logger.w(TAG, "attempt to click null rect element");
                return;
            }
            Logger.d(TAG, "longClick " + p.x + "," + p.y);
            click(p.x, p.y, LONG_CLICK_HOLD_RANGE_MIN + mRandom.nextInt(LONG_CLICK_HOLD_RANGE));
//        EventExecutor.getInstance().longClick(p.x, p.y);
        } else {
            throw new ApiException.NullElementException("Attempt to invoke long click with null.");
        }
    }

    public static void longClick(int x, int y) {
        Logger.d(TAG, "click " + x + "," + y);
        click(x, y, LONG_CLICK_HOLD_RANGE_MIN + mRandom.nextInt(LONG_CLICK_HOLD_RANGE));
    }

    // cannot use InputManager
    public static void click(int x, int y, int holdCount) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        // ignore (x, y) which is out of screen bound or settings bound
        /*if (x < LuaScript.clickBound.left || x > LuaScript.clickBound.right ||
                y < LuaScript.clickBound.top || y > LuaScript.clickBound.bottom) {
            return;
        }*/

        // down
        MotionEvent downEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y);
        // source from screen
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        // 1: INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT
        ActInputManager.getInstance().inFrameEventQueue(downEvent, 1);

        // hold
        int i = 0;
        for (; i < holdCount; i++) {
            eventTime = SystemClock.uptimeMillis() + i * ActInputManager.FRAME_INTERVAL;
            MotionEvent holdEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y);
            holdEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            // 2: INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
            ActInputManager.getInstance().inFrameEventQueue(holdEvent, 2);
        }

        // up
        eventTime = SystemClock.uptimeMillis() + i * ActInputManager.FRAME_INTERVAL;
        MotionEvent upEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_UP, x, y);
        // source from screen
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        // 2: INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
        ActInputManager.getInstance().inFrameEventQueue(upEvent, 2);
    }
}