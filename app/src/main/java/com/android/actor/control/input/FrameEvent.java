package com.android.actor.control.input;

import android.view.InputEvent;

public class FrameEvent {
    public InputEvent event;
    public int code;
    public int nextTime;

    public FrameEvent(InputEvent event, int code) {
        this.event = event;
        this.code = code;
    }

    public FrameEvent(InputEvent event, int code, int nextTime){
        this.event = event;
        this.code = code;
        this.nextTime = nextTime;
    }

    @Override
    public String toString() {
        return "Code " + code + ", " + event.toString();
    }
}