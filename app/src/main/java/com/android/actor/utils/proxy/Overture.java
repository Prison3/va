package com.android.actor.utils.proxy;

import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;

public class Overture extends DnsBase {

    private static final String TAG = Overture.class.getSimpleName();

    public Overture(ProfilePackage pkg, int proxyPort) {
        super(pkg, proxyPort, "overture", "overture.yml");
    }

    @Override
    protected String getConfName() {
        return "overture_" + mPkg + ".yml";
    }

    @Override
    protected boolean readExist(String cmd) throws Throwable {
        String confPath = mBinaryPath + "_" + mPkg + ".yml";
        List<String> lines = FileUtils.readLines(new File(confPath));
        String line = lines.get(0);
        mListenPort = Integer.parseInt(line.substring(line.lastIndexOf(':') + 1));
        line = lines.stream().filter(l -> l.contains("socks5Address: 127.0.0.1")).findFirst().get();
        int proxyPort = Integer.parseInt(line.substring(line.lastIndexOf(':') + 1));
        Logger.d(TAG, "Read exist mListenPort " + mListenPort + ", proxyPort " + proxyPort + " for " + mPkg);
        return proxyPort == mProxyPort;
    }

    @Override
    protected String newCmdline() throws Throwable {
        mListenPort = NetUtils.findAvailablePort();
        Logger.d(TAG, "New cmdline listen on " + mListenPort + " for " + mPkg + ", proxy through " + mProxyPort);
        String conf = String.format(mConfTemplate, mListenPort, mProxyPort);
        String confPath = mBinaryPath + "_" + mPkg + ".yml";
        FileUtils.writeStringToFile(new File(confPath), conf);
        File logFile = new File(mBinaryPath + "_" + mPkg + ".log");
        logFile.delete();
        return mBinaryPath + " -c " + confPath + " -l " + logFile.getPath() + " -v";
    }

    @Override
    public String getShowMsg() {
        return "overture pid " + getPid() + ", :" + mListenPort;
    }
}
