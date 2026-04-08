package com.android.actor.control.api;

import static com.android.actor.control.api.Swipe.DEFAULT_SWIPE_TYPE;
import static com.android.actor.control.api.Swipe.RECT;
import static com.android.actor.control.api.Swipe.interpolate;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.android.actor.control.input.ActInputManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NDK;

import java.util.Random;

public class MotionEventOnce implements Parcelable {

    private static final String TAG = MotionEventOnce.class.getSimpleName();
    private int fromX;
    private int fromY;
    private int toX;
    private int toY;
    private boolean vertical = false;
    private long downTime;
    private long eventTime;

    private MotionEventOnce() {
    }

    protected MotionEventOnce(Parcel in) {
        fromX = in.readInt();
        fromY = in.readInt();
        toX = in.readInt();
        toY = in.readInt();
        vertical = in.readByte() != 0;
        downTime = in.readLong();
        eventTime = in.readLong();
    }

    public static final Creator<MotionEventOnce> CREATOR = new Creator<MotionEventOnce>() {
        @Override
        public MotionEventOnce createFromParcel(Parcel in) {
            return new MotionEventOnce(in);
        }

        @Override
        public MotionEventOnce[] newArray(int size) {
            return new MotionEventOnce[size];
        }
    };

    public static MotionEventOnce create() {
        return new MotionEventOnce();
    }

    public void down(int fromX, int fromY) {
        Logger.d(TAG, "down " + fromX + "," + fromY);
        this.fromX = fromX;
        this.fromY = fromY;

        downTime = SystemClock.uptimeMillis();
        eventTime = SystemClock.uptimeMillis();
        // down
        MotionEvent downEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_DOWN, fromX, fromY);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(downEvent, 1);
    }

    public void move(int toX, int toY , int count, int type){
        Logger.d(TAG, "move " + toX + "," + toY);
        this.toX = toX;
        this.toY = toY;
        Random random = new Random();

        float cx = (float) (toX + fromX) / 2 + (toX - fromX) * (vertical ? 2 : 0.7f) * (random.nextFloat() - 0.5f); // allow over rect in direction move
        float cy = (float) (toY + fromY) / 2 + (toY - fromY) * (vertical ? 0.7f : 2) * (random.nextFloat() - 0.5f);
        cx = Math.max(0, Math.min(cx, RECT.right));
        cy = Math.max(0, Math.min(cy, RECT.bottom));
        float[] xl = vertical ? NDK.interpolateBezierCurveTwo((float) fromX, cx, (float) toX, count)
                : interpolate((float) fromX, (float) toX, count, type);
        float[] yl = vertical ? interpolate((float) fromY, (float) toY, count, DEFAULT_SWIPE_TYPE)
                : NDK.interpolateBezierCurveTwo((float) fromY, cy, (float) toY, count);

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
    }

    public void up() {
        MotionEvent upEvent = MotionEventGenerator.generate(downTime, eventTime, MotionEvent.ACTION_UP, toX, toY);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ActInputManager.getInstance().inFrameEventQueue(upEvent, 2);
        Logger.d(TAG, "eventTime - downTime " + (eventTime - downTime));
        Wait.waits((int) (eventTime - downTime));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(fromX);
        dest.writeInt(fromY);
        dest.writeInt(toX);
        dest.writeInt(toY);
        dest.writeByte((byte) (vertical ? 1 : 0));
        dest.writeLong(downTime);
        dest.writeLong(eventTime);
    }
}
