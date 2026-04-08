package com.android.actor.utils.shell;

import android.os.AsyncTask;
import android.os.Handler;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AsyncShell extends AsyncTask<Void, Void, Boolean> {
    private final static String TAG = AsyncShell.class.getSimpleName();
    private final String mInterpreter;
    private final String[] mCmds;
    private final ShellCallBack mCallback;
    private Process mProcess;
    private StreamGobbler mStderrGobbler;
    private StreamGobbler mStdoutGobbler;
    private boolean mLogPrint = false;

    public AsyncShell(String cmd, ShellCallBack callback) {
        this("su", new String[]{cmd}, 0, callback);
    }

    public AsyncShell(String[] cmds, ShellCallBack callback) {
        this("su", cmds, 0, callback);
    }

    public AsyncShell(String interpreter, String[] cmds, ShellCallBack callback) {
        this(interpreter, cmds, 0, callback);
    }

    /**
     * @param interpreter
     * @param cmds
     * @param timeout     子线程会报Handler没有Looper.prepare(),请不要在子线程使用
     * @param callback
     */
    public AsyncShell(String interpreter, String[] cmds, long timeout, ShellCallBack callback) {
        mInterpreter = interpreter;
        mCmds = cmds;
        mCallback = callback;

        if (timeout > 0) {
            new Handler().postDelayed(this::forceClean, timeout);
        }
    }

    public AsyncShell withLogPrint() {
        mLogPrint = true;
        return this;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            Logger.d(TAG, "Run cmd in thread " + Thread.currentThread() + " with " + mInterpreter + ":\n  " + ActStringUtils.arrayToString(mCmds));
            mProcess = Runtime.getRuntime().exec(mInterpreter);
            DataOutputStream outputStream = new DataOutputStream(mProcess.getOutputStream());
            mStderrGobbler = new StreamGobbler("STDERR", mProcess.getErrorStream());
            mStderrGobbler.start();
            mStdoutGobbler = new StreamGobbler("STDOUT", mProcess.getInputStream());
            mStdoutGobbler.start();
            for (String cmd : mCmds) {
                outputStream.writeBytes(cmd + "\n");
            }
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            mProcess.waitFor();

            synchronized (this) {
                if (mProcess == null) {
                    return false;
                } else {
                    mProcess = null;
                    while (!mStderrGobbler.isEnded() || !mStdoutGobbler.isEnded()) {
                        wait();
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Logger.e(TAG, "Run cmd failed, " + e);
            e.printStackTrace();
            return false;
        }
    }

    public void forceClean() {
        synchronized (this) {
            if (mProcess != null) {
                Logger.e(TAG, "Force clean cmd.");
                mProcess.destroy();
                mProcess = null;
            }
        }
    }

    @Override
    protected void onPostExecute(Boolean isOk) {
        mCallback.onResult(isOk, mStderrGobbler.getOutputs(), mStdoutGobbler.getOutputs());
    }

    private class StreamGobbler extends Thread {
        private String mType;
        private InputStream mInputStream;
        private List<String> mOutputs = new ArrayList<>(); // Since all outputs are put into memory, this should not be used for large outputs.
        private boolean mIsEnded = false;

        public StreamGobbler(String type, InputStream inputStream) {
            mType = type;
            mInputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                Logger.d(TAG, "Read " + mType + " result in thread " + Thread.currentThread());
                BufferedReader reader = new BufferedReader(new InputStreamReader(mInputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (mLogPrint) {
                        Logger.v(TAG, " " + line);
                    }
                    mOutputs.add(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            synchronized (AsyncShell.this) {
                mIsEnded = true;
                AsyncShell.this.notify();
            }
        }

        public boolean isEnded() {
            return mIsEnded;
        }

        public List<String> getOutputs() {
            return mOutputs;
        }
    }

    public interface ShellCallBack {
        void onResult(boolean success, List<String> stderr, List<String> output);
    }
}