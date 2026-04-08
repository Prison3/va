package com.android.actor.utils.proxy;

import static com.android.actor.control.ActPackageManager.APP_UNINSTALLED;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.device.DeviceNumber;
import com.android.actor.device.NewStage;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.google.common.base.Equivalence;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.topjohnwu.superuser.Shell;

import org.apache.commons.io.FileUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ProxyManager {

    private final static String TAG = ProxyManager.class.getSimpleName();
    private static ProxyManager sInstance;
    private ConcurrentHashMap<ProfilePackage, Proxy> mProxyMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String[]> mDirectDomains = new ConcurrentHashMap<>();
    private V2raySocksServer mSocksServer;
    private Executor mStatsExecutor = Executors.newSingleThreadExecutor();
    private Map<ProfilePackage, JSONObject> mBaseStats = new HashMap<>();

    private ProxyManager() {
        load();
        listenAppUninstall();
        mStatsExecutor.execute(this::statsLoop);
    }

    public static ProxyManager getInstance() {
        if (sInstance == null) {
            synchronized (ProxyManager.class) {
                if (sInstance == null) {
                    sInstance = new ProxyManager();
                }
            }
        }
        return sInstance;
    }

    public Set<ProfilePackage> getPackages() {
        return mProxyMap.keySet();
    }

    public Proxy getProxy(String pkgName, int profileId) {
        return mProxyMap.get(ProfilePackage.create(pkgName, profileId));
    }

    public String getAppProxy(String pkgName, int profileId) {
        Proxy proxy = mProxyMap.get(ProfilePackage.create(pkgName, profileId));
        return proxy != null ? proxy.socksProxy : null;
    }

    public String getAppProxyAddr(String pkgName, int profileId) {
        Proxy proxy = mProxyMap.get(ProfilePackage.create(pkgName, profileId));
        return proxy != null ? proxy.socks.getProxyAddr() : null;
    }

    public static class Proxy {
        public ProfilePackage pkg;
        public String socksProxy;
        public String[] directDomains;
        public IptablesChain iptables;
        public ProxyBase socks;
        public DnsBase dns;

        Proxy(ProfilePackage pkg, String socksProxy, String[] directDomains) {
            this.pkg = pkg;
            this.socksProxy = socksProxy;
            this.directDomains = directDomains;
        }

        void checkAndStart() throws Throwable {
            iptables = new IptablesChain(pkg);
            iptables.checkAndSetup();
            if (pkg.profileId == NewStage.instance().getPackageProfileId(pkg.packageName)) {
                iptables.enable(true);
            } else {
                iptables.enable(false);
            }
            socks = new V2ray(pkg, iptables.getRedirectPort(), socksProxy, directDomains);
            socks.checkAndStart();
            dns = new Overture(pkg, socks.getSocksPort());
            dns.checkAndStart();
        }

        void stop() {
            iptables.drop();
            iptables = null;
            socks.stop();
            socks = null;
            dns.stop();
            dns = null;
        }

        void update(String socksProxy) {
            this.socksProxy = socksProxy;
            if (socks == null) {
                return;
            }
            socks.stop();
            int socksPort = socks.getSocksPort();
            socks = new V2ray(pkg, iptables.getRedirectPort(), socksProxy, directDomains);
            socks.setSocksPort(socksPort);
            socks.checkAndStart();
        }

        void updateUid() {
            iptables.updateUid();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof Proxy) {
                return ((Proxy) obj).socksProxy.equals(socksProxy);
            } else if (obj instanceof String) {
                return ((String) obj).equals(socksProxy);
            }
            return false;
        }

        @NonNull
        @Override
        public String toString() {
            return socksProxy;
        }
    }

    private void load() {
        // kill all exist processes to make sure we have a fresh start.
        Shell.cmd("killall v2ray").exec();
        Shell.cmd("killall overture").exec();

        Logger.i(TAG, "Load all exist proxy.");
        SPUtils.getAll(SPUtils.ModuleFile.proxy_direct_domains).forEach((packageName, v) -> {
            String[] domains = ((HashSet<String>) v).toArray(new String[0]);
            Logger.i(TAG, "Load direct domains for " + packageName + ", " + Arrays.toString(domains));
            mDirectDomains.put(packageName, domains);
        });
        SPUtils.getAll(SPUtils.ModuleFile.proxy_socks).forEach((_packageName, v) -> {
            ProfilePackage pkg = ProfilePackage.create(_packageName);
            String proxy = (String) v;
            Logger.i(TAG, "Load " + pkg + ", " + proxy);
            addProxy(pkg, proxy);
        });

        Logger.i(TAG, "Start socks server.");
        mSocksServer = new V2raySocksServer();
        mSocksServer.checkAndStart();
    }

    private void listenAppUninstall() {
        /*ActPackageManager.getInstance().addAppChangeListener((type, packageName) -> {
            if (type == APP_UNINSTALLED) {
                Logger.w(TAG, "App uninstall " + packageName + ", remove proxy.");
                removeProxy(packageName, 0);
            }
        });*/
    }

    public static Boolean testProxy(Proxy proxy){
        return NetUtils.testProxyPort(proxy.socks.mSocksPort);
    }

    public Pair<Boolean, String> addProxy(String packageName, int profileId, String socksProxy) {
        return addProxy(ProfilePackage.create(packageName, profileId), socksProxy);
    }

    public Pair<Boolean, String> addProxy(ProfilePackage pkg, String socksProxy) {
        if (ActStringUtils.isEmpty(socksProxy)) {
            removeProxy(pkg);
            return Pair.create(true, pkg + " remove proxy: " + socksProxy);
        }
        if (!ActStringUtils.checkProxyPattern(socksProxy)) {
            return Pair.create(false, "fail, proxy " + socksProxy + " pattern invalid.");
        }
        if (!ActPackageManager.getInstance().isAppInstalled(pkg.packageName)) {
            return Pair.create(false, "app " + pkg + " not installed.");
        }
        Proxy proxy = mProxyMap.get(pkg);
        if (proxy != null) {
            removeProxy(pkg);
        }
        try {
            proxy = new Proxy(pkg, socksProxy, mDirectDomains.get(pkg.packageName));
            proxy.checkAndStart();
            ActActivityManager.getInstance().forceStopPackage(pkg.packageName);
            mProxyMap.put(pkg, proxy);
            Logger.d(TAG, "Proxy put " + pkg + ", " + proxy);
            SPUtils.putString(SPUtils.ModuleFile.proxy_socks, pkg.toStore(), socksProxy);
            notifyUI(pkg);
            return Pair.create(true, "Proxy added.");
        } catch (Throwable e) {
            Logger.e(TAG, "addProxy exception.", e);
            return Pair.create(false, "Proxy exception, " + e);
        }
    }

    public void updateProxyUid(String packageName, int profileId) {
        ProfilePackage pkg = ProfilePackage.create(packageName, profileId);
        Proxy proxy = mProxyMap.getOrDefault(pkg, null);
        if (proxy != null) {
            proxy.updateUid();
        }
    }

    public Pair<Boolean, String> removeProxy(String packageName, int profileId) {
        return removeProxy(ProfilePackage.create(packageName, profileId));
    }

    public Pair<Boolean, String> removeProxy(ProfilePackage pkg) {
        Proxy proxy = mProxyMap.get(pkg);
        if (proxy == null) {
            return Pair.create(true, "No proxy.");
        }
        proxy.stop();
        ActActivityManager.getInstance().forceStopPackage(pkg.packageName);
        Logger.d(TAG, "Proxy remove " + pkg);
        mProxyMap.remove(pkg);
        SPUtils.removeKey(SPUtils.ModuleFile.proxy_socks, pkg.profileId == 0 ? pkg.packageName : pkg.toString());
        notifyUI(pkg);
        return Pair.create(true, "Proxy removed.");
    }

    public void updateDirectDomains(JSONObject jAllDomains) {
        if (jAllDomains == null) {
            jAllDomains = new JSONObject(0);
        }
        Map<String, String[]> newDomains = new HashMap<>(jAllDomains.size());
        jAllDomains.forEach((packageName, value) -> {
            JSONObject jValue = (JSONObject) value;
            String devices = jValue.getString("devices");
            if (DeviceNumber.isDeviceInArray(devices)) {
                newDomains.put(packageName, jValue.getJSONArray("domains").toArray(new String[0]));
            }
        });
        MapDifference<String, String[]> diff = Maps.difference(newDomains, mDirectDomains, new Equivalence<>() {
            @Override
            protected boolean doEquivalent(String[] s1, String[] s2) {
                Arrays.sort(s1);
                Arrays.sort(s2);
                return Arrays.equals(s1, s2);
            }

            @Override
            protected int doHash(String[] s1) {
                return Arrays.hashCode(s1);
            }
        });
        if (diff.areEqual()) {
            return;
        }

        Logger.i(TAG, "Update direct domains.");
        diff.entriesDiffering().forEach((packageName, valueDiff) -> {
            if (valueDiff.leftValue().length > 0) {
                Logger.i(TAG, "Update direct domains for " + packageName + ", " + Arrays.toString(valueDiff.leftValue()));
                updateDirectDomains(packageName, valueDiff.leftValue());
            } else {
                Logger.i(TAG, "Remove direct domains for " + packageName);
                updateDirectDomains(packageName, null);
            }
        });
        diff.entriesOnlyOnLeft().forEach((packageName, domains) -> {
            if (domains.length > 0) {
                Logger.i(TAG, "Add direct domains for " + packageName + ", " + Arrays.toString(domains));
                updateDirectDomains(packageName, domains);
            }
        });
        diff.entriesOnlyOnRight().forEach((packageName, domains) -> {
            Logger.i(TAG, "Remove direct domains for " + packageName);
            updateDirectDomains(packageName, null);
        });
    }

    private void updateDirectDomains(String packageName, String[] domains) {
        if (domains != null) {
            mDirectDomains.put(packageName, domains);
            SPUtils.putStringSet(SPUtils.ModuleFile.proxy_direct_domains, packageName, Sets.newHashSet(domains));
        } else {
            mDirectDomains.remove(packageName);
            SPUtils.removeKey(SPUtils.ModuleFile.proxy_direct_domains, packageName);
        }
        mProxyMap.forEach((k,v) -> {
            if (k.packageName.equals(packageName)) {
                v.socks.updateDirectDomains(domains);
            }
        });
    }

    public void switchProfile(String packageName, int profileId) {
        for (ProfilePackage pkg : mProxyMap.keySet()) {
            if (pkg.packageName.equals(packageName)) {
                if (pkg.profileId == profileId) {
                    mProxyMap.get(pkg).iptables.enable(true);
                } else {
                    mProxyMap.get(pkg).iptables.enable(false);
                }
            }
        }
    }

    private void statsLoop() {
        long sleepSecond = 60;
        while (true) {
            try {
                Thread.sleep(sleepSecond * 1000);
                mProxyMap.forEach((k,v) -> {
                    JSONObject jStats = v.socks.stats();
                    if (jStats != null) {
                        long direct = jStats.getLong("direct");
                        long socks = jStats.getLong("socks");
                        String directName = k.toStore() + "_direct";
                        String socksName = k.toStore() + "_socks";

                        JSONObject jBaseStats = mBaseStats.get(k);
                        long baseDirect = 0;
                        long baseSocks = 0;
                        if (jBaseStats == null) {
                            baseDirect = SPUtils.getLong(SPUtils.ModuleFile.proxy_stats, directName);
                            baseSocks = SPUtils.getLong(SPUtils.ModuleFile.proxy_stats, socksName);
                            jBaseStats = new JSONObject();
                            jBaseStats.put("direct", baseDirect);
                            jBaseStats.put("socks", baseSocks);
                            mBaseStats.put(k, jBaseStats);
                        } else {
                            baseDirect = jBaseStats.getLongValue("direct");
                            baseSocks = jBaseStats.getLongValue("socks");
                        }

                        direct += baseDirect;
                        socks += baseSocks;
                        SPUtils.putLong(SPUtils.ModuleFile.proxy_stats, directName, direct);
                        SPUtils.putLong(SPUtils.ModuleFile.proxy_stats, socksName, socks);
                        Logger.v(TAG, "ProxyStats: " + k
                                + ", direct " + FileUtils.byteCountToDisplaySize(direct)
                                + ", socks " + FileUtils.byteCountToDisplaySize(socks));
                    }
                });
                sleepSecond = 600;
            } catch (Throwable e) {
                Logger.w(TAG, "Error to stats.", e);
            }
        }
    }

    private void notifyUI(ProfilePackage pkg) {
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_PROXY_CHANGE, pkg);
    }
}
