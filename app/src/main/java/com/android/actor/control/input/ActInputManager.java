package com.android.actor.control.input;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Queue;

public class ActInputManager {
    private final static String TAG = ActInputManager.class.getSimpleName();
    private final static int LOOP_INPUT_START = 123;
    private final static int LOOP_INPUT_STOP = 321;
    // loop per frame
    private final static int LOOP = 1;
    private final static int IN_EVENT = 2;
    // loop continue
    private final static int LOOP_EXACT = 3;
    private final static int IN_EVENT_EXACT = 4;
    public final static int FRAME_INTERVAL = 20;

    private static ActInputManager sInstance;
    private final InputManager mInputManager;
    private InputHandler mInputHandler;
    private Method mInjectEventMethod;
    private Queue<FrameEvent> mFrameEventQueue;

    public synchronized static ActInputManager getInstance() {
        if (sInstance == null) {
            sInstance = new ActInputManager(ActApp.getInstance());
        }
        return sInstance;
    }

    private ActInputManager(Context context) {
        mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        try {
            // can not post to another app.
            mInjectEventMethod = InputManager.class.getMethod("injectInputEvent", InputEvent.class, int.class);
            mInjectEventMethod.setAccessible(true);
            mFrameEventQueue = new LinkedList<>();
            mInputLoopThread.start();
        } catch (Exception e) {
            Logger.e(TAG, "Not get inject method. " + e.toString());
            e.printStackTrace();
        }
    }

    private Thread mInputLoopThread = new Thread(() -> {
        Looper.prepare();
        Logger.d(TAG, "InputManager looper prepare.");
        mInputHandler = new InputHandler();
        Looper.loop();
    });

    private void invokeInjectEvent(FrameEvent frame) {
        try {
            if (mInjectEventMethod != null) {
                mInjectEventMethod.invoke(mInputManager, frame.event, frame.code);
                if (frame.event instanceof MotionEvent) {
                    ((MotionEvent) frame.event).recycle();
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    // todo 处理锁问题，设置队列大小
    public synchronized void inFrameEventQueue(InputEvent event, int mode) {
        Message message = new Message();
        message.what = IN_EVENT;
        message.obj = new FrameEvent(event, mode);
        mInputHandler.sendMessage(message);
    }

    public synchronized void inFrameEventQueueExact(MotionEvent event, int mode, int nextTime) {
        Message message = new Message();
        message.what = IN_EVENT_EXACT;
        message.obj = new FrameEvent(event, mode, nextTime);
        mInputHandler.sendMessage(message);
    }

    public synchronized FrameEvent optFrameEventQueue() {
        return mFrameEventQueue.poll();
    }

    class InputHandler extends Handler {
        public InputHandler() {
            sendEmptyMessage(LOOP_INPUT_START);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case LOOP_INPUT_START:
                        sendEmptyMessageDelayed(LOOP, FRAME_INTERVAL);
                        break;

                    case LOOP_INPUT_STOP:
                        removeMessages(LOOP);
                        break;

                    case IN_EVENT:
                        mFrameEventQueue.offer((FrameEvent) msg.obj);
                        break;

                    case LOOP:
                        sendEmptyMessageDelayed(LOOP, FRAME_INTERVAL);
                        FrameEvent frame = optFrameEventQueue();
                        if (frame != null) {
                            invokeInjectEvent(frame);
                        }
                        break;

                    case IN_EVENT_EXACT:
                        mFrameEventQueue.offer((FrameEvent) msg.obj);
                        sendEmptyMessage(LOOP_EXACT);
                        break;

                    case LOOP_EXACT:
                        FrameEvent frame1 = optFrameEventQueue();
                        if (frame1 != null) {
                            invokeInjectEvent(frame1);
                            sendEmptyMessageDelayed(LOOP_EXACT, frame1.nextTime);
                        }
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}