package com.android.actor.control.trace;

import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.android.actor.device.DeviceInfoManager;

public class EventPoint {
    // MotionEvent.obtain(long downTime, long eventTime, int action, float x, float y, float pressure, float size, int metaState, float xPrecision, float yPrecision, int deviceId, int edgeFlags);
    // MotionEvent.obtain(long downTime, long eventTime, int action, int pointerCount, MotionEvent.PointerProperties[] pointerProperties, MotionEvent.PointerCoords[] pointerCoords, int metaState, int buttonState, float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source, int flags);
    int t;
    int action;
    int pointerCount;
    MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
    MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
    int metaState;
    int buttonState;
    float xPrecision;
    float yPrecision;
    int deviceId;
    int edgeFlags;
    int source;
    int flags;

    public EventPoint(MotionEvent event) {
        this(event, 0);
    }

    public EventPoint(String msg) {
        String[] list = msg.split(",");
        t = Integer.parseInt(list[0]);
        action = Integer.parseInt(list[1]);
        pointerCount = Integer.parseInt(list[2]);
        String pointP = list[3];
        pointerProperties.id = Integer.parseInt(pointP.split("_")[0]);
        pointerProperties.toolType = Integer.parseInt(pointP.split("_")[1]);

        String[] pointC = list[4].split("_");
        pointerCoords.x = Float.parseFloat(pointC[0]);
        pointerCoords.y = Float.parseFloat(pointC[1]);
        pointerCoords.pressure = Float.parseFloat(pointC[2]);
        pointerCoords.size = Float.parseFloat(pointC[3]);
        pointerCoords.touchMajor = Float.parseFloat(pointC[4]);
        pointerCoords.touchMinor = Float.parseFloat(pointC[5]);
        pointerCoords.toolMajor = Float.parseFloat(pointC[6]);
        pointerCoords.toolMinor = Float.parseFloat(pointC[7]);
        pointerCoords.orientation = Float.parseFloat(pointC[8]);

        metaState = Integer.parseInt(list[5]);
        buttonState = Integer.parseInt(list[6]);
        xPrecision = Float.parseFloat(list[7]);
        yPrecision = Float.parseFloat(list[8]);
        deviceId = Integer.parseInt(list[9]);
        edgeFlags = Integer.parseInt(list[10]);
        source = Integer.parseInt(list[11]);
        flags = Integer.parseInt(list[12]);
    }

    // single finger
    public EventPoint(MotionEvent event, int time) {
        t = time;
        action = event.getAction();
        pointerCount = 1;
        event.getPointerProperties(0, pointerProperties);
        event.getPointerCoords(0, pointerCoords);
        metaState = event.getMetaState();
        buttonState = event.getButtonState();
        xPrecision = event.getXPrecision();
        yPrecision = event.getYPrecision();
        deviceId = event.getDeviceId();
        edgeFlags = event.getFlags();
        source = event.getSource();
        flags = event.getFlags();
    }

    public EventPoint(EventPoint point) {
        this.t = point.t;
        this.action = point.action;
        this.pointerCount = point.pointerCount;
        this.pointerProperties.copyFrom(point.pointerProperties);
        this.pointerCoords.copyFrom(point.pointerCoords);
        this.metaState = point.metaState;
        this.buttonState = point.buttonState;
        this.xPrecision = point.xPrecision;
        this.yPrecision = point.yPrecision;
        this.deviceId = point.deviceId;
        this.edgeFlags = point.edgeFlags;
        this.source = point.source;
        this.flags = point.flags;
    }

    public MotionEvent obtain(long downTime, float startX, float startY) {
        pointerCoords.x += startX;
        pointerCoords.y += startY;
        if (pointerCoords.x >= 0 && pointerCoords.x <= DeviceInfoManager.getInstance().width &&
                pointerCoords.y >= 0 && pointerCoords.y <= DeviceInfoManager.getInstance().height) {
            return MotionEvent.obtain(downTime,
                    downTime + t,
                    action,
                    pointerCount,
                    new MotionEvent.PointerProperties[]{pointerProperties},
                    new MotionEvent.PointerCoords[]{pointerCoords},
                    metaState,
                    buttonState,
                    xPrecision,
                    yPrecision,
                    deviceId,
                    edgeFlags,
                    source,
                    flags);
        }
        return null;
    }

    @NonNull
    @Override
    public String toString() {
        String pointP = pointerProperties.id + "_" + pointerProperties.toolType;

        String pointC = pointerCoords.x + "_" +
                pointerCoords.y + "_" +
                pointerCoords.pressure + "_" +
                pointerCoords.size + "_" +
                pointerCoords.touchMajor + "_" +
                pointerCoords.touchMinor + "_" +
                pointerCoords.toolMajor + "_" +
                pointerCoords.toolMinor + "_" +
                pointerCoords.orientation;

        return new StringBuilder()
                .append(t).append(",")
                .append(action).append(",")
                .append(pointerCount).append(",")
                .append(pointP).append(",")
                .append(pointC).append(",")
                .append(metaState).append(",")
                .append(buttonState).append(",")
                .append(xPrecision).append(",")
                .append(yPrecision).append(",")
                .append(deviceId).append(",")
                .append(edgeFlags).append(",")
                .append(source).append(",")
                .append(flags)
                .toString();
    }
}
