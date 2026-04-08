package com.android.actor.utils.proxy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.shell.Libsu;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.Utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;

public class V2ray extends ProxyBase {

    private static final String TAG = V2ray.class.getSimpleName();
    private static String sParentSocksIP;
    private static int sParentSocksPort;
    private int mApiPort;
    private JSONObject mLastStats;

    public static void setParentSocks(String s) {
        try {
            String[] parts = s.split(":");
            if (parts.length == 2) {
                sParentSocksIP = parts[0];
                sParentSocksPort = Integer.parseInt(parts[1]);
            } else {
                sParentSocksIP = null;
                sParentSocksPort = 0;
            }
        } catch (Throwable e) {
            sParentSocksIP = null;
            sParentSocksPort = 0;
        }
    }

    public V2ray(ProfilePackage pkg, int transparentPort, String addr, String[] directDomains, String confName) {
        super(pkg, transparentPort, addr, directDomains, "v2ray", confName);
        if (mConfTemplate == null) {
            throw new RuntimeException("v2ray is not initialized.");
        }
    }

    public V2ray(ProfilePackage pkg, int transparentPort, String addr, String[] directDomains) {
        this(pkg, transparentPort, addr, directDomains, "v2ray.json");
    }

    @Override
    protected String getConfName() {
        return "v2ray_" + mPkg + ".json";
    }

    @Override
    protected boolean readExist(String cmd) throws Throwable {
        String confPath = mFolderPath + "/" + getConfName();
        String conf = FileUtils.readFileToString(new File(confPath));
        JSONObject jConf = JSON.parseObject(conf);
        JSONArray jInbounds = jConf.getJSONArray("inbounds");
        mSocksPort = jInbounds.getJSONObject(0).getIntValue("port");
        int transparentPort = jInbounds.getJSONObject(1).getIntValue("port");
        Logger.d(TAG, "Read exist mSocksPort " + mSocksPort
                + ", transparentPort " + transparentPort + " for " + mPkg);
        if (transparentPort != mTransparentPort) {
            return false;
        }

        String[] domains;
        JSONArray jRules = jConf.getJSONObject("routing").getJSONArray("rules");
        if (jRules != null && jRules.size() > 0) {
            domains = jRules.getJSONObject(0).getJSONArray("domain")
                    .stream()
                    .map(domain -> ((String) domain).substring(7))
                    .toArray(String[]::new);
        } else {
            domains = new String[0];
        }
        String[] newDomains = mDirectDomains != null ? mDirectDomains : new String[0];
        Arrays.sort(domains);
        Arrays.sort(newDomains);
        if (!Arrays.equals(domains, newDomains)) {
            Logger.d(TAG, "Read exist direct domains, exist: " + Arrays.toString(domains)
                    + ", new: " + Arrays.toString(newDomains));
            return false;
        }
        return true;
    }

    @Override
    protected String newCmdline() throws Throwable {
        if (mSocksPort <= 0) {
            mSocksPort = NetUtils.findAvailablePort();
        }
        Logger.d(TAG, "New cmdline transparent listen on " + mTransparentPort
                + ", socks listen on " + mSocksPort + " for " + mPkg + ", to " + getProxy());
        File logFile = new File(mBinaryPath + "_" + mPkg + ".log");
        logFile.delete();

        String user = "";
        JSONObject jUser = new JSONObject();
        if (!StringUtils.isEmpty(mProxyUser) && !" ".equals(mProxyPass)) {
            jUser.put("user", mProxyUser);
            jUser.put("pass", mProxyPass);
            user = JSON.toJSONString(jUser, true);
        }

        String proxySettings = "";
        String parentSocks = "";
        if (!StringUtils.isEmpty(sParentSocksIP) && sParentSocksPort > 0) {
            proxySettings = "\"proxySettings\": {\"tag\": \"clash\"},";
            parentSocks = "" +
                    ", {\n" +
                    "    \"protocol\": \"socks\",\n" +
                    "    \"settings\": {\n" +
                    "      \"servers\": [{\n" +
                    "        \"address\": \"" + sParentSocksIP + "\",\n" +
                    "        \"port\": " + sParentSocksPort + "\n" +
                    "      }]\n" +
                    "    },\n" +
                    "    \"tag\": \"clash\"\n" +
                    "  }";
        }

        String routing = "{}";
        if (mDirectDomains != null && mDirectDomains.length > 0) {
            JSONObject jRouting = new JSONObject(2);
            jRouting.put("domainStrategy", "AsIs");
            JSONArray jRules = new JSONArray(1);
            JSONObject jRule = new JSONObject(3);
            jRule.put("type", "field");
            JSONArray jDomains = new JSONArray();
            Arrays.stream(mDirectDomains).forEach(domain -> {
                if (!domain.startsWith("domain:")) {
                    jDomains.add("domain:" + domain);
                } else {
                    jDomains.add(domain);
                }
            });
            jRule.put("domain", jDomains);
            jRule.put("outboundTag", "direct");
            jRules.add(jRule);
            // https://guide.v2fly.org/advanced/traffic.html
            jRules.add(JSON.parseObject("{\n" +
                    "        \"inboundTag\": [\n" +
                    "          \"api\"\n" +
                    "        ],\n" +
                    "        \"outboundTag\": \"api\",\n" +
                    "        \"type\": \"field\"\n" +
                    "      }"));
            jRouting.put("rules", jRules);
            routing = JSON.toJSONString(jRouting, true);
            mApiPort = NetUtils.findAvailablePort();
        } else {
            mApiPort = 0;
        }

        String conf = String.format(mConfTemplate, logFile.getPath(), logFile.getPath(),
                mSocksPort, mTransparentPort, mApiPort, mProtocol, mProxyIP, mProxyPort, user, proxySettings, parentSocks, routing);
        String confPath = mFolderPath + "/" + getConfName();
        FileUtils.writeStringToFile(new File(confPath), conf);
        return mBinaryPath + " run -c " + confPath;
    }

    @Override
    public JSONObject stats() {
        if (mApiPort > 0) {
            Shell.Result result = Libsu.exec(mBinaryPath + " api stats --server=127.0.0.1:" + mApiPort);
            if (result.getCode() == 0) {
                /**
                 Value       Name
                 1   0           outbound>>>direct>>>traffic>>>downlink
                 2   0           outbound>>>direct>>>traffic>>>uplink
                 3   528.83KB    outbound>>>socks-outbound>>>traffic>>>downlink
                 4   101.29KB    outbound>>>socks-outbound>>>traffic>>>uplink
                 */
                long direct = 0;
                long socks = 0;
                for (String line : result.getOut()) {
                    String[] parts = StringUtils.splitByWholeSeparator(line, null);
                    if (parts.length == 3) {
                        String readableSize = parts[1];
                        String[] sizeParts = StringUtils.splitByCharacterType(readableSize);
                        long size = Long.parseLong(sizeParts[0]);
                        long multiply = 0;
                        if (sizeParts.length > 1) {
                            switch (sizeParts[sizeParts.length - 1]) {
                                case "EB":
                                    multiply = FileUtils.ONE_EB;
                                    break;
                                case "PB":
                                    multiply = FileUtils.ONE_PB;
                                    break;
                                case "TB":
                                    multiply = FileUtils.ONE_TB;
                                    break;
                                case "GB":
                                    multiply = FileUtils.ONE_GB;
                                    break;
                                case "MB":
                                    multiply = FileUtils.ONE_MB;
                                    break;
                                case "KB":
                                    multiply = FileUtils.ONE_KB;
                                    break;
                            }
                        }
                        size *= multiply;

                        switch (parts[2]) {
                            case "outbound>>>direct>>>traffic>>>downlink":
                            case "outbound>>>direct>>>traffic>>>uplink":
                                direct += size;
                                break;
                            case "outbound>>>socks-outbound>>>traffic>>>downlink":
                            case "outbound>>>socks-outbound>>>traffic>>>uplink":
                                socks += size;
                                break;
                        }
                    }
                }
                mLastStats = new JSONObject();
                mLastStats.put("direct", direct);
                mLastStats.put("socks", socks);
            }
        } else {
            mLastStats = null;
        }
        return mLastStats;
    }

    @Override
    public String getShowMsg() {
        return "v2ray pid " + getPid() + ", " + getProxyAddr();
    }
}
