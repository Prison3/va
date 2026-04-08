package com.android.actor.control;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.actor.ActApp;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.downloader.DownloadManager;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.shell.Shell;
import com.topjohnwu.superuser.Shell.Result;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SelfUpdater {

    private static SelfUpdater _instance;
    private static final String TAG = SelfUpdater.class.getSimpleName();
    private static final File DISABLE_FILE = new File("/data/new_stage/disable_self_update");
    private static final File NO_DELAY_FILE = new File("/data/new_stage/ota_no_delay");
    private static int MAX_APK_DELAY = 5 * 60;
    private static int MAX_ROM_DELAY = 30 * 60;
    private static final int MAX_ROM_RETRY = 60;
    private static final int TIMEOUT = 30 * 60;
    private String mApkUrl;
    private String mRomUrl;
    private PayloadSpec mPayloadSpec;
    private int mRomRetry;
    private HandlerThread mThread;
    private StatusHandler mHandler;
    private UpdateEngine mUpdateEngine;

    private static final int MSG_NO_DELAY = -1;

    // apk msg
    private static final int MSG_NEW_APK = 0;
    private static final int MSG_APK_UPDATING = 1;
    private static final int MSG_APK_TIMEOUT = 2;

    // rom msg
    private static final int MSG_NEW_ROM = 10;
    private static final int MSG_ROM_DOWNLOADING = 11;
    private static final int MSG_ROM_DOWNLOADED = 12;
    private static final int MSG_ROM_PREPARE_UPDATE = 13;
    private static final int MSG_ROM_APPLY_PAYLOAD = 14;
    private static final int MSG_ROM_INSTALL_APK = 15;
    private static final int MSG_ROM_SWITCH_SLOT = 16;
    private static final int MSG_ROM_END_ERROR = 20;
    private static final int MSG_ROM_END_WAIT = 21;

    public synchronized static SelfUpdater instance() {
        if (_instance == null) {
            _instance = new SelfUpdater();
        }
        return _instance;
    }

    public SelfUpdater() {
        mThread = new HandlerThread("SelfUpdater");
        mThread.setPriority(Thread.MIN_PRIORITY);
        mThread.start();
        mHandler = new StatusHandler(mThread.getLooper());
        if (isNoDelay()) {
            MAX_APK_DELAY = 0;
            MAX_ROM_DELAY = 0;
        }
        Logger.i(TAG, "Constructed, current slot " + SystemProperties.get("ro.boot.slot_suffix")
                + ", no delay " + (MAX_APK_DELAY == 0));
    }

    public boolean enabled() {
        return !DISABLE_FILE.exists();
    }

    public void apkUpdate(String apkUrl) {
        if (!StringUtils.isEmpty(apkUrl)) {
            mHandler.obtainMessage(MSG_NEW_APK, apkUrl).sendToTarget();
        }
    }

    // NewStage.apk and ROM may need to be updated at the same time.
    public void otaUpdate(String newStageUrl, String romUrl) {
        if (!StringUtils.isEmpty(romUrl)) {
            mHandler.obtainMessage(MSG_NEW_ROM, Pair.create(newStageUrl, romUrl)).sendToTarget();
        }
    }

    public void setNoDelay(boolean noDelay) {
        try {
            Logger.i(TAG, "Set no delay " + noDelay);
            if (noDelay) {
                NO_DELAY_FILE.createNewFile();
            } else {
                FileUtils.forceDelete(NO_DELAY_FILE);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        MAX_APK_DELAY = 0;
        MAX_ROM_DELAY = 0;
        mHandler.obtainMessage(MSG_NO_DELAY, noDelay).sendToTarget();
    }

    public boolean isNoDelay() {
        return NO_DELAY_FILE.exists() || ActApp.isSettingUp();
    }

    class StatusHandler extends Handler {

        public StatusHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_NO_DELAY: {
                    if (hasMessages(MSG_APK_UPDATING) || hasMessages(MSG_ROM_DOWNLOADING)) {
                        Logger.w(TAG, "Remove delay messages to restart.");
                        removeMessages(MSG_APK_UPDATING);
                        removeMessages(MSG_APK_TIMEOUT);
                        removeMessages(MSG_ROM_DOWNLOADING);
                        mApkUrl = null;
                        mRomUrl = null;
                    }
                    break;
                }
                /** apk **/
                case MSG_NEW_APK:
                    if (mApkUrl == null && mRomUrl == null) {
                        mApkUrl = (String) msg.obj;
                        // random seconds to delay update, to reduce network peek.
                        int delay = RandomUtils.nextInt(0, MAX_APK_DELAY);
                        Logger.i(TAG, "Got new apk url: " + mApkUrl + ", delay " + delay + "s to update.");
                        sendEmptyMessageDelayed(MSG_APK_UPDATING, delay * 1000);
                        sendEmptyMessageDelayed(MSG_APK_TIMEOUT, TIMEOUT * 1000);
                    }
                    break;
                case MSG_APK_UPDATING:
                    Logger.i(TAG, "start to update " + mApkUrl);
                    ActPackageInstaller.instance().installAppUrl(mApkUrl, new ActPackageInstaller.Listener() {
                        String _packageName;
                        @Override
                        public void onPreInstall(String packageName, int versionCode) {
                            Logger.v(TAG, "onPreInstall " + packageName + ", " + versionCode);
                            _packageName = packageName;
                        }

                        @Override
                        public void postInstall(boolean success, String reason) {
                            Logger.v(TAG, "postInstall " + success + ", " + reason);
                            if (RocketComponent.PKG_NEW_STAGE.equals(_packageName)) {
                                Logger.i(TAG, "NewStage.apk installed, reboot.");
                                Libsu.reboot();
                            }
                            mApkUrl = null;
                            mHandler.removeMessages(MSG_APK_TIMEOUT);
                        }
                    });
                    break;
                case MSG_APK_TIMEOUT:
                    Logger.w(TAG, "timeout, something must be wrong, reboot.");
                    Libsu.reboot();
                    break;

                /** rom **/
                case MSG_NEW_ROM:
                    if (mApkUrl == null && mRomUrl == null) {
                        Pair<String, String> pair = (Pair<String, String>) msg.obj;
                        mApkUrl = pair.first;
                        mRomUrl = pair.second;
                        // random seconds to delay update, to reduce network peek.
                        int delay = RandomUtils.nextInt(0, MAX_ROM_DELAY);
                        Logger.i(TAG, "Got new NewStage url: " + mApkUrl
                                + ", ROM url: " + mRomUrl + ", delay " + delay + "s to update.");
                        mRomRetry = 0;
                        sendEmptyMessageDelayed(MSG_ROM_DOWNLOADING, delay * 1000);
                    }
                    break;
                case MSG_ROM_DOWNLOADING: {
                    Logger.i(TAG, "Start download rom.");
                    File file = DownloadManager.instance().getFile(mRomUrl);
                    if (file == null) {
                        DownloadManager.instance().addDownload(mRomUrl, (url, file1, reason) -> {
                            if (file1 != null) {
                                obtainMessage(MSG_ROM_DOWNLOADED, file1).sendToTarget();
                            } else {
                                Logger.e(TAG, "Failed to download ROM, " + reason);
                                ++mRomRetry;
                                if (mRomRetry >= MAX_ROM_RETRY) {
                                    Logger.w(TAG, "Reach max rom retry.");
                                    sendEmptyMessage(MSG_ROM_END_ERROR);
                                } else {
                                    Logger.i(TAG, "Wait 60s to retry download.");
                                    sendEmptyMessageDelayed(MSG_ROM_DOWNLOADING, 60000);
                                }
                            }
                        });
                    } else {
                        obtainMessage(MSG_ROM_DOWNLOADED, file).sendToTarget();
                    }
                    break;
                }
                case MSG_ROM_DOWNLOADED: {
                    File file = (File) msg.obj;
                    Logger.i(TAG, "ROM downloaded " + file);
                    File newFile = new File("/data/ota_package/update.zip");
                    Logger.i(TAG, "Copy file to " + newFile);
                    Result result = Libsu.exec("rm -f /data/ota_package/update.zip " +
                            "&& cp " + file.getPath() + " " + newFile.getPath() +
                            "&& chmod 644 " + newFile.getPath() +
                            "&& chown system:cache " + newFile.getPath());
                    if (result.getCode() == 0) {
                        sendMessageDelayed(obtainMessage(MSG_ROM_PREPARE_UPDATE, newFile), 2000);
                    } else {
                        obtainMessage(MSG_ROM_END_WAIT, "copy ota file failed.").sendToTarget();
                    }
                    break;
                }
                case MSG_ROM_PREPARE_UPDATE: {
                    Logger.i(TAG, "Prepare update engine.");
                    File file = (File) msg.obj;
                    mPayloadSpec = prepareUpdateEngine(file);
                    if (mPayloadSpec == null) {
                        obtainMessage(MSG_ROM_END_WAIT, "mPayloadSpec is null.").sendToTarget();
                    } else {
                        mRomRetry = 0;
                        sendEmptyMessage(MSG_ROM_APPLY_PAYLOAD);
                    }
                    break;
                }
                case MSG_ROM_APPLY_PAYLOAD: {
                    Logger.i(TAG, "Apply payload.");
                    applyPayload();
                    break;
                }
                case MSG_ROM_INSTALL_APK: {
                    Logger.i(TAG, "Install NewStage.apk");
                    if (!StringUtils.isEmpty(mApkUrl)) {
                        ActPackageInstaller.instance().installAppUrl(mApkUrl, new ActPackageInstaller.Listener() {
                            @Override
                            public void onPreInstall(String packageName, int versionCode) {
                                Logger.v(TAG, "onPreInstall " + packageName + ", " + versionCode);
                            }

                            @Override
                            public void postInstall(boolean success, String reason) {
                                Logger.v(TAG, "postInstall " + success + ", " + reason);
                                if (success) {
                                    mRomRetry = 0;
                                    sendEmptyMessage(MSG_ROM_SWITCH_SLOT);
                                } else {
                                    ++mRomRetry;
                                    if (mRomRetry >= MAX_ROM_RETRY) {
                                        Logger.w(TAG, "Reach max rom retry.");
                                        sendEmptyMessage(MSG_ROM_END_ERROR);
                                    } else {
                                        Logger.i(TAG, "Wait 30s to retry install.");
                                        sendEmptyMessageDelayed(MSG_ROM_INSTALL_APK, 30000);
                                    }
                                }
                            }
                        });
                    } else {
                        mRomRetry = 0;
                        sendEmptyMessage(MSG_ROM_SWITCH_SLOT);
                    }
                    break;
                }
                case MSG_ROM_SWITCH_SLOT: {
                    Logger.i(TAG, "Switch slot, current is " + SystemProperties.get("ro.boot.slot_suffix"));
                    switchSlot();

                    // if something is wrong after slot switch, manually check by someone.
                    // and if rebooted before dd boot block, boot image is not patched.
                    break;
                }
                case MSG_ROM_END_ERROR: {
                    Logger.e(TAG, "ROM end with error, reboot.");
                    GRPCManager.getInstance().sendNotification("error", "OTA error to reboot.");
                    Libsu.reboot();
                    break;
                }
                case MSG_ROM_END_WAIT: {
                    Logger.e(TAG, "ROM end with error, wait for someone to check this.");
                    String errMsg = (String) msg.obj;
                    Logger.e(TAG, "Error msg: " + errMsg);
                    GRPCManager.getInstance().sendNotification("error", "OTA failure, " + errMsg);
                    break;
                }
            }
        }
    }

    private PayloadSpec prepareUpdateEngine(File file) {
        try {
            if (mUpdateEngine == null) {
                mUpdateEngine = new UpdateEngine();
            }
            resetUpdateEngine();
            PayloadSpec payloadSpec = forNonStreaming(file);
            Logger.d(TAG, " " + payloadSpec.getUrl());
            Logger.d(TAG, " " + payloadSpec.getOffset());
            Logger.d(TAG, " " + payloadSpec.getSize());
            Logger.d(TAG, " " + payloadSpec.getProperties());
            return payloadSpec;
        } catch (Throwable e) {
            Logger.e(TAG, "Prepare update engine exception.", e);
            return null;
        }
    }

    private void applyPayload() {
        try {
            mUpdateEngine.bind(new UpdateEngineCallback() {
                @Override
                public void onStatusUpdate(int status, float percent) {
                    Logger.v(TAG, "onStatusUpdate " + status + ", " + percent);
                }

                @Override
                public void onPayloadApplicationComplete(int errorCode) {
                    Logger.i(TAG, "onPayloadApplicationComplete " + errorCode);
                    mUpdateEngine.unbind();
                    if (errorCode == UpdateEngine.ErrorCodeConstants.UPDATED_BUT_NOT_ACTIVE) {
                        Logger.i(TAG, "Payload apply ok, but not active.");
                        mRomRetry = 0;
                        mHandler.sendEmptyMessage(MSG_ROM_INSTALL_APK);
                    } else {
                        // we may get "kNewRootfsVerificationError = 15" error, but retry will work, I don't know why.
                        Logger.e(TAG, "Payload apply error.");
                        ++mRomRetry;
                        if (mRomRetry >= MAX_ROM_RETRY) {
                            Logger.w(TAG, "Reach max rom retry.");
                            mHandler.sendEmptyMessage(MSG_ROM_END_ERROR);
                        } else {
                            Logger.i(TAG, "Wait 5s to retry apply payload.");
                            mHandler.sendEmptyMessageDelayed(MSG_ROM_APPLY_PAYLOAD, 5000);
                        }
                    }
                }
            }, mHandler);

            List<String> properties = new ArrayList<>(mPayloadSpec.getProperties());
            properties.add("SWITCH_SLOT_ON_REBOOT=0");
            Logger.d(TAG, " " + ArrayUtils.toString(properties.toArray(new String[0])));
            mUpdateEngine.applyPayload(mPayloadSpec.getUrl(), mPayloadSpec.getOffset(), mPayloadSpec.getSize(), properties.toArray(new String[0]));
        } catch (Throwable e) {
            Logger.e(TAG, "Apply payload exception.", e);
            mHandler.obtainMessage(MSG_ROM_END_WAIT, "apply payload exception.").sendToTarget();
        }
    }

    private void switchSlot() {
        try {
            mUpdateEngine.bind(new UpdateEngineCallback() {
                @Override
                public void onStatusUpdate(int status, float percent) {
                    Logger.v(TAG, "onStatusUpdate " + status + ", " + percent);
                }

                @Override
                public void onPayloadApplicationComplete(int errorCode) {
                    Logger.i(TAG, "onPayloadApplicationComplete " + errorCode);
                    mUpdateEngine.unbind();
                    if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) {
                        Logger.i(TAG, "Switch slot ok, reboot.");
                        Libsu.reboot();
                    } else {
                        Logger.e(TAG, "Switch slot error.");
                        ++mRomRetry;
                        if (mRomRetry >= MAX_ROM_RETRY) {
                            Logger.w(TAG, "Reach max rom retry.");
                            mHandler.sendEmptyMessage(MSG_ROM_END_ERROR);
                        } else {
                            Logger.i(TAG, "Wait 5s to retry switch slot.");
                            mHandler.sendEmptyMessageDelayed(MSG_ROM_SWITCH_SLOT, 5000);
                        }
                    }
                }
            }, mHandler);

            // SWITCH_SLOT_ON_REBOOT=1 is default
            List<String> properties = new ArrayList<>(mPayloadSpec.getProperties());
            properties.add("RUN_POST_INSTALL=0");
            Logger.d(TAG, " " + ArrayUtils.toString(properties.toArray(new String[0])));
            mUpdateEngine.applyPayload(mPayloadSpec.getUrl(), mPayloadSpec.getOffset(), mPayloadSpec.getSize(), properties.toArray(new String[0]));
        } catch (Throwable e) {
            Logger.e(TAG, "Switch slot exception.", e);
            mHandler.obtainMessage(MSG_ROM_END_WAIT, "switch slot exception.").sendToTarget();
        }
    }

    private void resetUpdateEngine() {
        if (mUpdateEngine != null) {
            try {
                if (!mUpdateEngine.unbind()) {
                    Logger.e(TAG, "Unbind failed.");
                }
            } catch (Throwable e) {
                Logger.e(TAG, "Unbind exception.", e);
            }
            try {
                mUpdateEngine.cancel();
            } catch (Throwable e) {}
        }
    }

    public PayloadSpec forNonStreaming(File packageFile) throws IOException {
        boolean payloadFound = false;
        long payloadOffset = 0;
        long payloadSize = 0;

        List<String> properties = new ArrayList<>();
        try (ZipFile zip = new ZipFile(packageFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            long offset = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // Zip local file header has 30 bytes + filename + sizeof extra field.
                // https://en.wikipedia.org/wiki/Zip_(file_format)
                long extraSize = entry.getExtra() == null ? 0 : entry.getExtra().length;
                offset += 30 + name.length() + extraSize;

                if (entry.isDirectory()) {
                    continue;
                }

                long length = entry.getCompressedSize();
                if ("payload.bin".equals(name)) {
                    if (entry.getMethod() != ZipEntry.STORED) {
                        throw new IOException("Invalid compression method.");
                    }
                    payloadFound = true;
                    payloadOffset = offset;
                    payloadSize = length;
                } else if ("payload_properties.txt".equals(name)) {
                    InputStream inputStream = zip.getInputStream(entry);
                    if (inputStream != null) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                        String line;
                        while ((line = br.readLine()) != null) {
                            properties.add(line);
                        }
                    }
                }
                offset += length;
            }
        }

        if (!payloadFound) {
            throw new IOException("Failed to find payload entry in the given package.");
        }
        return PayloadSpec.newBuilder()
                .url("file://" + packageFile.getAbsolutePath())
                .offset(payloadOffset)
                .size(payloadSize)
                .properties(properties)
                .build();
    }

    static class PayloadSpec {

        public static Builder newBuilder() {
            return new Builder();
        }

        private String mUrl;
        private long mOffset;
        private long mSize;
        private List<String> mProperties;

        public PayloadSpec(Builder b) {
            this.mUrl = b.mUrl;
            this.mOffset = b.mOffset;
            this.mSize = b.mSize;
            this.mProperties = b.mProperties;
        }

        public String getUrl() {
            return mUrl;
        }

        public long getOffset() {
            return mOffset;
        }

        public long getSize() {
            return mSize;
        }

        public List<String> getProperties() {
            return mProperties;
        }

        public static class Builder {
            private String mUrl;
            private long mOffset;
            private long mSize;
            private List<String> mProperties;

            public Builder() {
            }

            public Builder url(String url) {
                this.mUrl = url;
                return this;
            }

            public Builder offset(long offset) {
                this.mOffset = offset;
                return this;
            }

            public Builder size(long size) {
                this.mSize = size;
                return this;
            }

            public Builder properties(List<String> properties) {
                this.mProperties = properties;
                return this;
            }

            public PayloadSpec build() {
                return new PayloadSpec(this);
            }
        }
    }
}
