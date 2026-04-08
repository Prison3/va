package com.android.va.runtime;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;

import com.android.va.R;
import com.android.va.mirror.android.ddm.BRDdmHandleAppName;
import com.android.va.mirror.android.os.BRProcess;
import com.android.va.utils.Logger;

import java.util.List;

/**
 * Virtual environment process: package / process name ({@link #setupRuntime}) and
 * role ({@link Type}, set in {@link #attach} after {@link VHost} is ready).
 */
public final class VProcess {

    private static final String TAG = VProcess.class.getSimpleName();

    /** Server process, main (host) process, or virtualized app (VA) process. */
    public enum Type {
        Server,
        Va,
        Main,
    }

    private static final VProcess INSTANCE = new VProcess();

    private String mInitialPackageName;
    private String mProcessName;
    private Type mProcessType;

    private VProcess() {
    }

    public static VProcess get() {
        return INSTANCE;
    }

    public String getProcessName() {
        return mProcessName;
    }

    public String getInitialPackageName() {
        return mInitialPackageName;
    }

    public void setupRuntime(String processName, ApplicationInfo appInfo) {
        if (mProcessName != null) {
            return;
        }
        mInitialPackageName = appInfo.packageName;
        mProcessName = processName;
        BRProcess.get().setArgV0(processName);
        BRDdmHandleAppName.get().setAppName(processName, 0);
    }

    /** Called from {@link VRuntime#onAttach} after {@link VHost#attach}. */
    public void attach(Context context) {
        mProcessType = determineProcessType(context);
    }

    private static String resolveCurrentProcessName(Context context) {
        int pid = Process.myPid();
        String processName = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo info : processes) {
                        if (info.pid == pid) {
                            processName = info.processName;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.w(TAG, "Failed to get process name using modern API", e);
            }
        }

        if (processName == null) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo info : processes) {
                        if (info.pid == pid) {
                            processName = info.processName;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.w(TAG, "Failed to get process name using fallback API", e);
            }
        }

        if (processName == null) {
            throw new RuntimeException("processName = null");
        }
        return processName;
    }

    private static Type determineProcessType(Context context) {
        String processName = resolveCurrentProcessName(context);
        if (processName.equals(VHost.getPackageName())) {
            return Type.Main;
        } else if (processName.endsWith(context.getString(R.string.server_process))) {
            return Type.Server;
        } else {
            return Type.Va;
        }
    }

    public boolean isVaProcess() {
        return mProcessType == Type.Va;
    }

    public boolean isMainProcess() {
        return mProcessType == Type.Main;
    }

    public boolean isServerProcess() {
        return mProcessType == Type.Server;
    }

    public boolean isVaApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        return packageName.equals(VHost.getPackageName());
    }
}
