package com.android.actor.control.trace;

import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class EventTrace {
    List<EventPoint> records = new ArrayList<>();
    RectF rect = new RectF();
    int size = 0;
    int duration = 0;
    long trackTime = 0;
    RectF originRect = new RectF();
    float offsetX;
    float offsetY;

    public void add(MotionEvent event) {
        add(event, 0);
    }

    public void add(MotionEvent event, int time) {
        records.add(new EventPoint(event, time));
        size++;
        duration += time;
    }

    private void addP(EventPoint point) {
        records.add(point);
        size++;
    }

    public void setRectAndOffset(RectF rect) {
        offsetX = records.get(0).pointerCoords.x;
        offsetY = records.get(0).pointerCoords.y;
        for (EventPoint p : records) {
            p.pointerCoords.x -= offsetX;
            p.pointerCoords.y -= offsetY;
        }
        originRect = new RectF(rect);
        this.rect.right = rect.right - rect.left;
        this.rect.bottom = rect.bottom - rect.top;
        trackTime = System.currentTimeMillis();
    }

    @NonNull
    @Override
    public EventTrace clone() {
        EventTrace trace = new EventTrace();
        for (EventPoint p : this.records) {
            trace.addP(new EventPoint(p));
        }
        trace.rect = this.rect;
        trace.size = this.size;
        trace.duration = this.duration;
        trace.trackTime = this.trackTime;
        trace.originRect = new RectF(this.originRect);
        trace.offsetX = this.offsetX;
        trace.offsetY = this.offsetY;
        return trace;
    }

    @NonNull
    @Override
    public String toString() {
        return new StringBuilder()
                .append("Trace width ").append((int) (rect.right - rect.left))
                .append(", height").append((int) (rect.bottom - rect.top))
                .append(", points ").append(size)
                .append(", duration ").append(duration)
                .append(" ms, record at ").append(trackTime)
                .toString();
    }

    public String getRecordString() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (; i < records.size() - 1; i++) {
            sb.append(records.get(i).toString()).append("\n");
        }
        sb.append(records.get(i).toString());
        return sb.toString();
    }
}
