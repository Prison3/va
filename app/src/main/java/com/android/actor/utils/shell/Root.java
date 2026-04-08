package com.android.actor.utils.shell;

import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;

import java.io.IOException;
import java.util.List;

public class Root {
    private final static String TAG = Root.class.getSimpleName();
    private static boolean mHaveRoot = false;


    public static boolean checkRoot() {
        if (mHaveRoot) {
            return true;
        }
        return acquireRoot();
    }

    public static boolean acquireRoot() {
        try {
            List<String> ret = Shell.execRootCmd("ls /data/data");
            if (ret.size() == 0) {
                Logger.d(TAG, "acquire MagiskRoot fail.");
                GRPCManager.getInstance().sendNotification("error", "Not get root");
                return false;
            } else {
                Logger.i(TAG, "acquire MagiskRoot success.");
                mHaveRoot = true;
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}