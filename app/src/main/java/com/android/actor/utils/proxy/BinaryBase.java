package com.android.actor.utils.proxy;

import android.content.pm.PackageManager;

import com.android.actor.ActApp;
import com.android.actor.control.ActPackageManager;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ResUtils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public abstract class BinaryBase {

    private static final String TAG = BinaryBase.class.getSimpleName();
    private static Map<String, String> sBinaryPaths = new HashMap<>();
    private static Map<String, String> sConfTemplates = new HashMap<>();
    private static Map<String, Boolean> sNeedRestart = new HashMap<>();
    protected String mName;
    protected String mBinaryPath;
    protected String mFolderPath;
    protected String mConfTemplate;
    protected ProfilePackage mPkg;
    protected int mUid;
    private GuardedProcess mGuardedProcess;

    public BinaryBase(ProfilePackage pkg, String binaryName, String confName) {
        mPkg = pkg;
        mName = binaryName;
        try {
            initBinary(binaryName);
            initConfTemplate(confName);
            initPost();
        } catch (Throwable e) {
            throw new RuntimeException("Init exception.", e);
        }
        try {
            mUid = ActPackageManager.getInstance().getUidOfPackage(mPkg.packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.e(TAG, "App " + mPkg + " not installed but initializing proxy?");
        }
    }

    private void initBinary(String name) throws Throwable {
        mFolderPath = ActApp.getInstance().getFilesDir().getPath() + "/" + name;
        mBinaryPath = sBinaryPaths.get(name);
        if (mBinaryPath != null) {
            return;
        }
        mBinaryPath = ActApp.getInstance().getFilesDir().getPath() + "/" + name + "/" + name;
        Logger.d(TAG, "Init binary " + name + ", sBinaryPath " + mBinaryPath);
        if (ResUtils.extractAsset("bin/" + name, mBinaryPath)) {
            sNeedRestart.put(name, true);
        }
        sBinaryPaths.put(name, mBinaryPath);
    }

    private void initConfTemplate(String name) throws Throwable {
        mConfTemplate = sConfTemplates.get(name);
        if (mConfTemplate != null) {
            return;
        }
        InputStream input = ActApp.getInstance().getAssets().open("bin/" + name);
        mConfTemplate = IOUtils.toString(input);
        input.close();
        sConfTemplates.put(name, mConfTemplate);
    }

    protected void initPost() {
    }

    protected abstract String getConfName();

    public void checkAndStart() {
        try {
            if (mGuardedProcess == null) {
                start();
            } else if (!readExist(mGuardedProcess.getStringCommand())) { // exist config not match.
                Logger.w(TAG, "Exist conf not match, restart a new one.");
                stop();
                start();
            }
            checkAndStartPost();
        } catch (Throwable e) {
            Logger.e(TAG, "Check exist exception.", e);
        }
    }

    protected abstract boolean readExist(String cmd) throws Throwable;

    protected void checkAndStartPost() throws Throwable {
    }

    protected abstract String newCmdline() throws Throwable;

    private void start() throws Throwable {
        String cmd = newCmdline();
        mGuardedProcess = new GuardedProcess(StringUtils.split(cmd, ' '));
        mGuardedProcess.start();
    }

    public void stop() {
        try {
            if (mGuardedProcess != null) {
                mGuardedProcess.stop();
                mGuardedProcess = null;
            }
            stopPost();
        } catch (Throwable e) {
            Logger.e(TAG, "Failed to stop " + mBinaryPath, e);
        }
    }

    protected void stopPost() throws Throwable {
    }

    public int getPid() {
        if (mGuardedProcess != null) {
            return mGuardedProcess.getPid();
        }
        return 0;
    }

    public abstract String getShowMsg();
}
