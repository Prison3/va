package com.android.actor.utils.proxy;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.device.ProfilePackage;
import com.android.actor.utils.ActStringUtils;

import java.util.regex.Matcher;

public abstract class ProxyBase extends BinaryBase {

    protected int mTransparentPort;
    protected int mSocksPort;
    protected String mProtocol;
    protected String mProxyUser;
    protected String mProxyPass;
    protected String mProxyIP;
    protected String mProxyPort;
    protected String[] mDirectDomains;

    public ProxyBase(ProfilePackage pkg, int transparentPort, String proxy, String[] directDomains,
                     String binaryName, String confName) {
        super(pkg, binaryName, confName);
        mTransparentPort = transparentPort;
        mDirectDomains = directDomains;
        Matcher m = ActStringUtils.matchProxyPattern(proxy);
        assert m != null;
        mProtocol = m.group("protocol");
        if (ActStringUtils.isEmpty(mProtocol) || "socks5".equals(mProtocol)) {
            mProtocol = "socks";
        }
        mProxyUser = m.group("user");
        mProxyPass = m.group("pwd");
        mProxyIP = m.group("ip");
        mProxyPort = m.group("port");
    }

    public void setSocksPort(int port) {
        mSocksPort = port;
    }

    public int getSocksPort() {
        return mSocksPort;
    }

    public String getUser() {
        return mProxyUser;
    }

    public String getPass() {
        return mProxyPass;
    }

    public String getProxy() {
        String s = "";
        if (!ActStringUtils.isEmpty(mProxyUser)) {
            s = mProxyUser + ':' + mProxyPass + '@';
        }
        return s + getProxyAddr();
    }

    public String getProxyAddr() {
        return mProxyIP + ':' + mProxyPort;
    }

    public void updateDirectDomains(String[] domains) {
        mDirectDomains = domains;
        checkAndStart(); // exist config will be checked, so we don't need to call stop.
    }

    public JSONObject stats() {
        return null;
    }
}
