package com.android.actor.utils.proxy;

import android.content.pm.PackageManager;

import com.android.actor.control.ActPackageManager;
import com.android.actor.device.ManagedProfiles;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;
import com.topjohnwu.superuser.Shell;

import org.apache.commons.lang3.ArrayUtils;

import java.util.List;

public class IptablesChain {
    private final static String TAG = IptablesChain.class.getSimpleName();
    private final ProfilePackage mPkg;
    private String mChainName;
    private int mUid;
    private int mPort = -1;

    public IptablesChain(ProfilePackage pkg) throws PackageManager.NameNotFoundException {
        mPkg = pkg;
        mUid = ManagedProfiles.instance.getPackageProfileUid(pkg.packageName, pkg.profileId);
        mChainName = "app_" + mUid + "_" + pkg.profileId;
    }

    public int getTargetUid() {
        return mUid;
    }

    public int getRedirectPort() {
        return mPort;
    }

    public void checkAndSetup() {
        readExist();
        if (mPort <= 0) {
            setUp();
        }
    }

    private void readExist() {
        List<String> out = showChain();
        if (out != null && out.size() > 0) {
            if (out.size() > 1) {
                Logger.e(TAG, "Exist multi rules in " + mChainName);
            }
            String[] parts = out.get(0).split(" ");
            mPort = Integer.parseInt(parts[parts.length - 1]);
            Logger.d(TAG, "Read exist mPort " + mPort + " for " + mPkg + '/' + mChainName);
        }
    }

    private void setUp() {
        mPort = NetUtils.findAvailablePort();
        setUpOnExistPort();
    }

    private void setUpOnExistPort() {
        String[] cmd = new String[]{
                String.format("iptables -w -t nat -F %s", mChainName),
                String.format("iptables -w -t nat -N %s", mChainName),
                String.format("iptables -w -t nat -A %s -p tcp -m owner --uid-owner %d -j REDIRECT --to-ports %d", mChainName, mUid, mPort),
        };
        Logger.d(TAG, ArrayUtils.toString(cmd, "\n"));
        Shell.cmd(cmd).exec();
    }

    public void enable(boolean enable) {
        if (enable) {
            String cmd = String.format("iptables -w -t nat -D OUTPUT -j %s", mChainName) + '\n'
                    + String.format("iptables -w -t nat -A OUTPUT -j %s", mChainName);
            Logger.d(TAG, cmd);
            Shell.su(cmd).exec();
        } else {
            String cmd = String.format("iptables -w -t nat -D OUTPUT -j %s", mChainName);
            Logger.d(TAG, cmd);
            Shell.su(cmd).exec();
        }
    }

    public void updateUid() {
        Logger.d(TAG, "Update uid " + mPkg);
        drop();
        mUid = ManagedProfiles.instance.getPackageProfileUid(mPkg.packageName, mPkg.profileId);
        mChainName = "app_" + mUid + "_" + mPkg.profileId;
        setUpOnExistPort();
    }

    public void drop() {
        String[] cmd = new String[] {
                String.format("iptables -w -t nat -D OUTPUT -j %s", mChainName),
                String.format("iptables -w -t nat -F %s", mChainName)
        };
        Logger.d(TAG, ArrayUtils.toString(cmd, "\n"));
        Shell.su(cmd).exec();
    }

    public List<String> showChain() {
        String cmd = String.format("iptables -w -t nat -L %s --line-numbers -nvx", mChainName);
        Logger.d(TAG, cmd);
        Shell.Result result = Shell.su(cmd).exec();
        if (result.getCode() == 0) {
            List<String> out = result.getOut();
            if (out.size() >= 3) {
                out.remove(0);
                out.remove(0);
                return out;
            }
        }
        return null;
    }
}