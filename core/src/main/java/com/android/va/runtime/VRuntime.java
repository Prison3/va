package com.android.va.runtime;

import android.content.pm.ApplicationInfo;

import com.android.va.mirror.android.ddm.BRDdmHandleAppName;
import com.android.va.mirror.android.os.BRProcess;

/**
 * Process / package identity used by the virtual runtime (single init per process).
 */
public final class VRuntime {

    private static String sInitialPackageName;
    private static String sProcessName;

    private VRuntime() {
    }

    public static String getProcessName() {
        return sProcessName;
    }

    public static String getInitialPackageName() {
        return sInitialPackageName;
    }

    public static void setupRuntime(String processName, ApplicationInfo appInfo) {
        if (sProcessName != null) {
            return;
        }
        sInitialPackageName = appInfo.packageName;
        sProcessName = processName;
        BRProcess.get().setArgV0(processName);
        BRDdmHandleAppName.get().setAppName(processName, 0);
    }
}
