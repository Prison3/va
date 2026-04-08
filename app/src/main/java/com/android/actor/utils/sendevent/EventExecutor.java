package com.android.actor.utils.sendevent;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import com.android.actor.utils.shell.Shell;

import java.util.List;

public class EventExecutor {
    private final static String TAG = EventExecutor.class.getSimpleName();
    private static EventExecutor sInstance;
    private EventHandler mHandler;

    public final static int ACTION_CLICK = 1;
    public final static int ACTION_LONG_CLICK = 2;
    public final static int ACTION_SWIPE_UP = 3;
    public final static int ACTION_SWIPE_DOWN = 4;
    public final static int ACTION_SWIPE_LEFT = 5;
    public final static int ACTION_SWIPE_RIGHT = 6;
    public final static int ACTION_SWIPE_TRACE = 7;

    private EventExecutor() {
        new Thread(() -> {
            Looper.prepare();
            mHandler = new EventHandler();
            Looper.loop();
        }).start();
    }

    public static EventExecutor getInstance() {
        if (sInstance == null) {
            sInstance = new EventExecutor();
        }
        return sInstance;
    }

    public void click(int x, int y) {
        Message msg = new Message();
        msg.what = ACTION_CLICK;
        msg.arg1 = x;
        msg.arg2 = y;
        mHandler.sendMessage(msg);
    }

    public void longClick(int x, int y) {
        Message msg = new Message();
        msg.what = ACTION_LONG_CLICK;
        msg.arg1 = x;
        msg.arg2 = y;
        mHandler.sendMessage(msg);
    }

    public void swipeUp() {
        mHandler.sendEmptyMessage(ACTION_SWIPE_UP);
    }

    public void swipeDown() {
        mHandler.sendEmptyMessage(ACTION_SWIPE_DOWN);
    }

    public void swipeLeft() {
        mHandler.sendEmptyMessage(ACTION_SWIPE_LEFT);
    }

    public void swipeRight() {
        mHandler.sendEmptyMessage(ACTION_SWIPE_RIGHT);
    }

    public void swipeTrace(List<String> trace) {
        Message msg = new Message();
        msg.what = ACTION_SWIPE_TRACE;
        msg.obj = trace;
        mHandler.sendMessage(msg);
    }


    @SuppressLint("HandlerLeak")
    private class EventHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            try {
                switch (msg.what) {
                    case ACTION_CLICK:
                        Shell.execRootCmdSilent(PixelEventFactory.getClickEvent(msg.arg1, msg.arg2));
                        break;
                    case ACTION_LONG_CLICK:
                        Shell.execRootCmdSilent(PixelEventFactory.getLongClickEvent(msg.arg1, msg.arg2));
                        break;
                    case ACTION_SWIPE_UP:
                        Shell.execRootCmdSilent(PixelEventFactory.getSwipeUpEvent());
                        break;
                    case ACTION_SWIPE_DOWN:
                        Shell.execRootCmdSilent(PixelEventFactory.getSwipeDownEvent());
                        break;
                    case ACTION_SWIPE_LEFT:
                        Shell.execRootCmdSilent(PixelEventFactory.getSwipeLeftEvent());
                        break;
                    case ACTION_SWIPE_RIGHT:
                        Shell.execRootCmdSilent(PixelEventFactory.getSwipeRightEvent());
                        break;
                    case ACTION_SWIPE_TRACE:
                        Shell.execRootCmdSilent((List<String>) msg.obj);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
