package com.android.actor.utils.proxy;

import com.android.actor.device.NewStage;
import com.android.actor.device.ProfilePackage;

public abstract class DnsBase extends BinaryBase {

    protected int mListenPort;
    protected int mProxyPort;

    public DnsBase(ProfilePackage pkg, int proxyPort, String binaryName, String confName) {
        super(pkg, binaryName, confName);
        mProxyPort = proxyPort;
    }

    public int getListenPort() {
        return mListenPort;
    }

    public int getProxyPort() {
        return mProxyPort;
    }

    @Override
    protected void checkAndStartPost() throws Throwable {
        //NewStage.instance().clearUidDns(mUid);
        /*NewStage.instance().setUidDns(mUid, new String[] {
                "127.0.0.1:" + mListenPort
        });*/
    }

    @Override
    protected void stopPost() throws Throwable {
        //NewStage.instance().clearUidDns(mUid);
    }
}
