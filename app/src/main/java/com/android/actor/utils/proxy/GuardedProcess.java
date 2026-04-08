package com.android.actor.utils.proxy;

import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.ReflectUtils;
import com.topjohnwu.superuser.Shell;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.util.Arrays;

public class GuardedProcess implements Runnable {

    private static final String TAG = GuardedProcess.class.getSimpleName();
    private static final File NULL_FILE = new File("/dev/null");
    private Thread mThread;
    private String[] mCommand;
    private Process mProcess;
    private BlockingReference mBlockingRef = new BlockingReference();

    public GuardedProcess(String... command) {
        // We don't have permission to run normal command, have to use root.
        // TODO: Change permission in system.
        //mCommand = ArrayUtils.insert(0, command, "su");
        mCommand = command;
    }

    public String getStringCommand() {
        return String.join(" ", mCommand);
    }

    public void start() {
        mThread = new Thread(this);
        mThread.start();
    }

    public void stop() {
        mThread.interrupt();
        // make sure process died first, then return.
        try {
            mBlockingRef.take();
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void run() {
        try {
            Logger.d(TAG, "Process start " + Arrays.toString(mCommand));
            do {
                long startTime = System.currentTimeMillis();
                mProcess = new ProcessBuilder(mCommand)
                        .redirectErrorStream(true)
                        .redirectOutput(NULL_FILE)
                        .start();
                mProcess.waitFor();

                // if process exit immediately, break, or this loop may try to start process again and again.
                if (System.currentTimeMillis() - startTime < 1000) {
                    Logger.e(TAG, "Process exit too fast, " + Arrays.toString(mCommand));
                    break;
                }
                Logger.d(TAG, "Process " + mProcess + " died, restart " + Arrays.toString(mCommand));
            } while (true);
        } catch (InterruptedException e) {
            Logger.d(TAG, "Process " + mProcess + " interrupted.");
        } catch (Throwable e) {
            Logger.e(TAG, "Process " + mProcess + " unexpected exception.", e);
            GRPCManager.getInstance().sendNotification("error", "GuardedProcess unexpected exception, " + e);
        } finally {
            Logger.d(TAG, "Destroy " + mProcess);
            if (mProcess != null) {
                int pid = getPid();
                if (pid > 0) {
                    Shell.cmd("kill -9 " + pid).exec();
                }
            }
        }
        mProcess = null;
        mBlockingRef.put(null);
    }

    public int getPid() {
        try {
            if (mProcess != null) {
                return ReflectUtils.getIntField(mProcess, "pid");
            }
        } catch (Throwable e) {
        }
        return 0;
    }
}
