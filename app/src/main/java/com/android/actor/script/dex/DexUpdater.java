package com.android.actor.script.dex;

import android.content.pm.PackageInfo;
import android.util.Pair;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.device.DeviceNumber;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.Callback;
import com.android.actor.utils.downloader.DownloadManager;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.utils.DateUtils;

import java.io.File;
import java.util.Date;

public class DexUpdater {

    private static final String TAG = DexUpdater.class.getSimpleName();
    public static final DexUpdater instance = new DexUpdater();
    private String mUrl;
    private String mTestUrl;
    private boolean mUseTestUrl = false;
    private BlockingReference<File> mDownloadingBlock;
    private long mLastCheckTime;
    private long mLastModified;
    private String mPath;

    private DexUpdater() {
    }

    public void setUrl(String url, String testUrl, String testDevices, JSONArray jBranches) {
        if (url != null && url.isEmpty()) {
            url = null;
        }
        mUrl = url;
        mTestUrl = testUrl;
        mUseTestUrl = DeviceNumber.isDeviceInArray(testDevices);
        if (jBranches != null) {
            for (int i = 0; i < jBranches.size(); ++i) {
                JSONObject jBranch = jBranches.getJSONObject(i);
                if (DeviceNumber.isDeviceInArray(jBranch.getString("devices"))) {
                    mTestUrl = jBranch.getString("url");
                    mUseTestUrl = true;
                }
            }
        }
        Logger.d(TAG, "Set url " + mUrl + ", " + mTestUrl + ", " + mUseTestUrl);
    }

    public synchronized boolean isJustUpdated() {
        return System.currentTimeMillis() - mLastCheckTime < 5000L;
    }

    public synchronized void updateAsync(Callback.C1<Boolean> callback) {
        new Thread(() -> {
            try {
                update();
                callback.onResult(true);
            } catch (Throwable e) {
                Logger.w(TAG, "Failed update ds, " + e);
                callback.onResult(false);
            }
        }).start();
    }

    public synchronized Pair<String, Long> update() throws Throwable {
        try {
            if (isJustUpdated()) {
                if (mPath == null) {
                    throw new Exception("mPath is null since it's just updated.");
                }
                return Pair.create(mPath, mLastModified);
            }

            String url = mUseTestUrl ? mTestUrl : mUrl;
            if (url != null) {
                mDownloadingBlock = new BlockingReference<>();
                DownloadManager.instance().addDownload(url, "/data/new_stage", "ds.apk", (url1, file, reason) -> {
                    if (file == null) {
                        Logger.e(TAG, "Failed to get newest dex, " + reason);
                        mDownloadingBlock.put(null);
                    } else {
                        mDownloadingBlock.put(file);
                        file.setReadable(true, false);
                    }
                });
            } else {
                throw new Exception("No online ds.apk is configured.");
            }

            PackageInfo localInfo = ActPackageManager.getInstance().getPackageInfo(RocketComponent.PKG_DS);
            String localPath = null;
            long localLastModified = 0;
            if (localInfo != null) {
                localPath = "/data/new_stage/ds.local.apk";
                File localFile = new File(localPath);
                File localFileTmp = new File(localPath + ".tmp");
                File sourceFile = new File(localInfo.applicationInfo.sourceDir);
                if (sourceFile.exists()) {
                    FileUtils.copyFile(sourceFile, localFileTmp, true);
                    if (!localFileTmp.renameTo(localFile)) {
                        throw new Exception("Can't rename " + localFileTmp + " to " + localFile);
                    }
                    localFile.setReadable(true, false);
                    localLastModified = new File(localPath).lastModified();
                    Logger.d(TAG, "Local last modified " + DateUtils.formatDate(new Date(localLastModified)));
                } else {
                    localPath = null;
                }
            }
            String path = localPath;
            if (mDownloadingBlock != null) {
                File onlineFile = mDownloadingBlock.take();
                if (onlineFile != null) {
                    if (localPath == null || localLastModified < onlineFile.lastModified()) {
                        path = onlineFile.getPath();
                    }
                } else {
                    throw new Exception("Online dex download failed.");
                }
            }
            if (path == null) {
                throw new Exception("No dex installed or downloaded.");
            }
            mLastModified = new File(path).lastModified();
            mLastCheckTime = System.currentTimeMillis();
            mPath = path;
            return Pair.create(mPath, mLastModified);
        } catch (Throwable e) {
            mPath = null;
            mLastModified = 0;
            mLastCheckTime = System.currentTimeMillis();
            throw e;
        }
    }
}
