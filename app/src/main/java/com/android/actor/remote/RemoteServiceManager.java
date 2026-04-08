package com.android.actor.remote;

import android.content.pm.PackageInfo;
import android.os.FileObserver;
import android.os.RemoteException;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActPackageManager;
import com.android.internal.aidl.service.ParcelCreator;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;
import com.android.internal.os.IMyActionListener;
import com.android.actor.ActApp;
import com.android.actor.control.RocketComponent;
import com.android.actor.device.NewStage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;

import java.io.File;
import java.util.*;

public class RemoteServiceManager {

    private final static String TAG = RemoteServiceManager.class.getSimpleName();
    private final static String SERVICE_FILE_DIR = "/data/new_stage/WaghalAidlServices";
    private static RemoteServiceManager sInstance;
    private Map<String, JSONObject> mDexInfoCache;
    private Map<String, RemoteServiceConnection> mConnectionMap = new TreeMap<>();
    private Callback.C2<String, RemoteServiceConnection> mUiListener;
    private FileObserver mDexFileObserver;

    private RemoteServiceManager() {
        checkDirExists();
        updateConnectionsAtInit();
    }

    public synchronized static RemoteServiceManager getInstance() {
        if (sInstance == null) {
            sInstance = new RemoteServiceManager();
        }
        return sInstance;
    }

    // 目录需要777权限
    public boolean checkDirExists() {
        File file = new File(SERVICE_FILE_DIR);
        if (!file.exists()) {
            if (file.mkdir()) {
                Logger.d(TAG, "mkdir " + SERVICE_FILE_DIR + " success.");
                return file.setWritable(true, false)
                        && file.setReadable(true, false)
                        && file.setExecutable(true, false);
            }
            return false;
        } else {
            return true;
        }
    }

    private void updateConnectionsAtInit() {
        Logger.d("testTag", "start to updateConnectionsAtInit");
        RequestV0 request = ParcelCreator.buildRequestV0(RocketComponent.PKG_ALPHA, RequestV0.TYPE_INFO, null, null);
        NewStage.instance().registerAppStarted(request, new IMyActionListener.Stub() {
            @Override
            public ResponseV0 onActionRequest(RequestV0 request) throws RemoteException {
                synchronized (RemoteServiceManager.this) {
                    JSONArray jInfoList = JSON.parseArray(request.getBody());
                    boolean first = mDexInfoCache == null;
                    if (first) {
                        mDexInfoCache = new HashMap<>();
                    }
                    Logger.d("testTag", "onActionRequest");

                    for (int i = 0; i < jInfoList.size(); ++i) {
                        JSONObject jInfo = (JSONObject) jInfoList.getJSONObject(i);
                        String app = jInfo.getString("app");
                        mDexInfoCache.put(app, jInfo);
                        Logger.d("testTag", "mDexInfoCache is :" + mDexInfoCache);
                        if (!first) {
                            updateConnection(app, jInfo, null);
                        }
                    }
                }
                return null;
            }
        });

        synchronized (this) {
            ActPackageManager.getInstance().addAppChangeListener((type, packageName) -> {
                updateConnection(packageName, null, null);
            });
            List<PackageInfo> infoList = ActPackageManager.getInstance().getInstalledPackage();
            for (PackageInfo packageInfo : infoList) {
                updateConnection(packageInfo.packageName, null, packageInfo);
            }
        }
    }

    private void updateConnection(String app, JSONObject jInfo, PackageInfo packageInfo) {
        /*if (ActApp.isSettingUp()) {
            return;
        }
        if (jInfo == null) {
            Logger.d("testTag", "app is " + app + " jInfo is null");
            Logger.d("testTag", "mDexInfoCache is :" + mDexInfoCache);

            jInfo = mDexInfoCache.get(app);
        }
        if (packageInfo == null) {
            packageInfo = ActPackageManager.getInstance().getPackageInfo(app);
        }
        RemoteServiceConnection conn = null;
        if (packageInfo == null) {
            mConnectionMap.remove(app);
            Logger.i(TAG, "Delete " + app);
        } else {
            conn = new RemoteServiceConnection(app, jInfo);
            mConnectionMap.put(app, conn);
            if (conn.canUse()) {
                Logger.i(TAG, "New " + conn);
            } else {
                Logger.i(TAG, "Close " + conn);
            }
        }
        notifyConnChanged(app, conn);*/
    }

    public synchronized RemoteServiceConnection getConnection(String packageName) {
        return mConnectionMap.get(packageName);
    }

    public synchronized Collection<RemoteServiceConnection> getConnectionList() {
        return mConnectionMap.values();
    }

    public void setUiListener(Callback.C2<String, RemoteServiceConnection> listener) {
        mUiListener = listener;
    }

    public void removeUiListener() {
        mUiListener = null;
    }

    public void notifyConnChanged(String app, RemoteServiceConnection conn) {
        if (mUiListener != null) {
            ActApp.getInstance().getMainHandler().post(() -> {
                mUiListener.onResult(app, conn);
            });
        }
    }
}
