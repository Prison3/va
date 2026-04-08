package com.android.actor.utils.notification;

public class ActObservable {
    private final int mCode;
    private final ActObserver mObserver;

    public ActObservable(int code, ActObserver obs) {
        mCode = code;
        mObserver = obs;
    }

    public int getCode() {
        return mCode;
    }

    public void notifyObservers(Object arg) {
        mObserver.update(this, arg);
    }
}
