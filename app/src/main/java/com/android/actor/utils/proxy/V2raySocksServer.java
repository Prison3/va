package com.android.actor.utils.proxy;

import static com.android.actor.control.RocketComponent.PKG_ALPHA;

import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class V2raySocksServer extends BinaryBase {

    private static final String TAG = V2raySocksServer.class.getSimpleName();

    public V2raySocksServer() {
        super(ProfilePackage.create(PKG_ALPHA, 0), "v2ray", "v2ray_socks_server.json");
    }

    @Override
    protected String getConfName() {
        return "v2ray_socks_server.json";
    }

    @Override
    protected boolean readExist(String cmd) throws Throwable {
        return true;
    }

    @Override
    protected String newCmdline() throws Throwable {
        Logger.d(TAG, "New cmdline socks server.");
        File logFile = new File(mBinaryPath + "_" + mPkg + ".log");
        logFile.delete();

        String conf = String.format(mConfTemplate, logFile.getPath(), logFile.getPath());
        String confPath = mFolderPath + "/" + getConfName();
        FileUtils.writeStringToFile(new File(confPath), conf);
        return mBinaryPath + " run -c " + confPath;
    }

    @Override
    public String getShowMsg() {
        return "v2ray-socks-server " + getPid();
    }
}
