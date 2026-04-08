package com.android.actor.control.trace;

import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.android.actor.device.DeviceInfoManager;

@Deprecated
public class EventPointSimple {
    // MotionEvent.obtain(long downTime, long eventTime, int action, float x, float y, int metaState)
    int t;
    int action;
    float x;
    float y;
    int metaState;

    public EventPointSimple(MotionEvent event) {
        this(event, 0);
    }

    public EventPointSimple(String msg) {
        String[] list = msg.split(",");
        t = Integer.parseInt(list[0]);
        action = Integer.parseInt(list[1]);
        x = Float.parseFloat(list[2]);
        y = Float.parseFloat(list[3]);
        metaState = Integer.parseInt(list[4]);
    }

    // single finger
    public EventPointSimple(MotionEvent event, int time) {
        t = time;
        action = event.getAction();
        x = event.getX();
        y = event.getY();
        metaState = event.getMetaState();
    }

    public EventPointSimple(EventPointSimple point) {
        this.t = point.t;
        this.action = point.action;
        this.x = point.x;
        this.y = point.y;
        this.metaState = point.metaState;
    }

    public MotionEvent obtain(long downTime, float startX, float startY) {
        float realX = x + startX;
        float realY = y + startY;
        if (realX >= 0 && realX <= DeviceInfoManager.getInstance().width &&
                realY >= 0 && realY <= DeviceInfoManager.getInstance().height) {
            return MotionEvent.obtain(downTime,
                    downTime + t,
                    action,
                    x + startX,
                    y + startY,
                    metaState);
        }
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        return new StringBuilder()
                .append(t).append(",")
                .append(action).append(",")
                .append(x).append(",")
                .append(y).append(",")
                .append(metaState)
                .toString();
    }


}
