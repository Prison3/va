package com.android.va.runtime;

import android.content.Context;
import android.os.Process;

import com.android.va.mirror.android.app.BRActivityThread;
import com.android.va.mirror.android.os.BRUserHandle;

/**
 * Host (installer) application identity: package name, UID, user id, and base {@link Context}.
 * Filled once during {@link com.android.va.base.PrisonCore#startUp}.
 */
public final class VHost {

    private static final VHost INSTANCE = new VHost();

    private Context mContext;
    private int mUid;
    private int mUserId;
    private String mPackageName;

    private VHost() {
    }

    public static VHost get() {
        return INSTANCE;
    }

    /**
     * Called from {@link com.android.va.base.PrisonCore#startUp} with the host {@code Application} context.
     */
    public void attach(Context context) {
        mContext = context;
        mUid = Process.myUid();
        mPackageName = context.getPackageName();
        mUserId = BRUserHandle.get().myUserId();
    }

    public static String getPackageName() {
        return INSTANCE.mPackageName;
    }

    public static int getUid() {
        return INSTANCE.mUid;
    }

    public static int getUserId() {
        return INSTANCE.mUserId;
    }

    public static Context getContext() {
        return INSTANCE.mContext;
    }

    /** Current process {@link android.app.ActivityThread} instance (host process). */
    public static Object mainThread() {
        return BRActivityThread.get().currentActivityThread();
    }
}
