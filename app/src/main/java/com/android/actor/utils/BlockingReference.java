package com.android.actor.utils;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingReference<T> {

    private ReentrantLock mTakeLock = new ReentrantLock();
    private Condition mTakeCondition = mTakeLock.newCondition();
    private T mObj;
    private boolean mPut = false;

    public void put(T obj) {
        mTakeLock.lock();
        try {
            mObj = obj;
            mPut = true;
            mTakeCondition.signal();
        } finally {
            mTakeLock.unlock();
        }
    }

    public T take() throws InterruptedException {
        mTakeLock.lockInterruptibly();
        try {
            if (!mPut) {
                mTakeCondition.await();
            }
        } finally {
            mTakeLock.unlock();
        }
        return mObj;
    }

    public boolean hasPut() {
        return mPut;
    }
}
