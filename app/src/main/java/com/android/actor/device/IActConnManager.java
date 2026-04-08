package com.android.actor.device;

import android.content.Context;

import com.android.actor.monitor.Logger;

public abstract class IActConnManager {

    public interface IUserInfo {
    }

    private static final String TAG = IActConnManager.class.getSimpleName();

    protected Context mContext;
    protected String mType;
    protected boolean mEnabled = false;
    protected boolean mConnected = false;

    public IActConnManager(Context context, String type) {
        mContext = context;
        mType = type;
    }

    public String getType() {
        return mType;
    }

    public abstract String getConnectionInfo();

    public abstract String getConnectionDetail();

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            Logger.i(TAG, "Set " + mType + " enabled " + enabled);
            mEnabled = enabled;
            onEnabled(enabled);
        }
    }

    protected abstract void onEnabled(boolean enabled);

    public boolean isConnected() {
        return mConnected;
    }

    protected void setConnected(boolean connected) {
        if (mConnected != connected) {
            Logger.i(TAG, "Set " + mType + " connected " + connected);
            mConnected = connected;
            onConnected(connected);
        }
    }

    protected abstract void onConnected(boolean connected);

    public abstract boolean checkAndReconnect();

    public abstract void connect(IUserInfo userInfo);
}
