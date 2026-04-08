package com.android.actor.utils.downloader;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.annotation.JSONField;
import com.android.actor.device.NewStage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.Callback.C2;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

public class DeviceFilesUpdater extends Handler {

    public interface IVersionListener {
        void onVersionChanged(float version);
    }

    static class FileCrc {

        @JSONField(name = "crc")
        long crc;
        @JSONField(name = "local_crc")
        long localCrc;

        public FileCrc() {
        }

        public FileCrc(long crc) {
            this.crc = crc;
        }

        public long getCrc() {
            return crc;
        }

        public void setCrc(long crc) {
            this.crc = crc;
        }

        public long getLocalCrc() {
            return localCrc;
        }

        public void setLocalCrc(long localCrc) {
            this.localCrc = localCrc;
        }
    }

    private static final String TAG = DeviceFilesUpdater.class.getSimpleName();
    private static DeviceFilesUpdater sInstance;
    private static final String FILES_ROOT_FOLDER = NewStage.PARAMETERS_PATH + "/device-files";
    private static final String FILES_LIST = FILES_ROOT_FOLDER + ".json";
    private static final int MSG_NEW_FILES = 1;
    private static final int MSG_ENUMERATE_MODELS = 2;
    private static final int MSG_CHECK_MODEL_FILES = 3;

    public static final int STATUS_DOWNLOADING = 100;
    public static final int STATUS_SUCCESS = 101;
    public static final int STATUS_FAIL = 102;

    private final List<IVersionListener> mVersionListeners = new ArrayList<>();
    private float mVersion;
    private Map<String, Map<String, FileCrc>> mModelFiles;
    private boolean mIsReady = false;

    public static DeviceFilesUpdater instance() {
        if (sInstance == null) {
            synchronized (DeviceFilesUpdater.class) {
                if (sInstance == null) {
                    HandlerThread thread = new HandlerThread(TAG);
                    thread.start();
                    sInstance = new DeviceFilesUpdater(thread.getLooper());
                }
            }
        }
        return sInstance;
    }

    private DeviceFilesUpdater(Looper looper) {
        super(looper);
        init();
    }

    private void init() {
        try {
            File file = new File(FILES_LIST);
            if (file.exists()) {
                fixPermissions();
                String content = FileUtils.readFileToString(file);
                JSONObject jContent = JSON.parseObject(content);
                mVersion = jContent.getFloatValue("version");
                mModelFiles = JSON.parseObject(jContent.getString("files"), new TypeReference<Map<String, Map<String, FileCrc>>>(){});
                Logger.v(TAG, "Local list loaded, v" + mVersion + ", models " + mModelFiles.keySet());
                mIsReady = true;
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Error to load local list.", e);
        }
    }

    public float getVersion() {
        return mVersion;
    }

    public void addVersionListener(IVersionListener listener) {
        synchronized (mVersionListeners) {
            mVersionListeners.add(listener);
        }
    }

    public void removeVersionListener(IVersionListener listener) {
        synchronized (mVersionListeners) {
            mVersionListeners.remove(listener);
        }
    }

    private void notifyVersionUpdate() {
        synchronized (mVersionListeners) {
            for (IVersionListener listener : mVersionListeners) {
                try {
                    listener.onVersionChanged(mVersion);
                } catch (Throwable e) {
                    Logger.e(TAG, "Exception to notify version listener " + listener, e);
                }
            }
        }
    }

    public void update(String url) {
        if (!StringUtils.isEmpty(url)) {
            obtainMessage(MSG_NEW_FILES, url).sendToTarget();
        }
    }

    public void checkAndDownloadFiles(String model, C2<Integer, String> callback) {
        obtainMessage(MSG_CHECK_MODEL_FILES, Pair.create(model, callback)).sendToTarget();
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_NEW_FILES: {
                String url = (String) msg.obj;
                Matcher matcher = Pattern.compile("https?://.+/([^_]+)_(.+)\\.\\w+").matcher(url);
                if (matcher.matches()) {
                    float version = Float.parseFloat(matcher.group(2));
                    if (version != mVersion) {
                        Logger.i(TAG, "Got new device files url: " + url);
                        mIsReady = false;
                        removeMessages(MSG_ENUMERATE_MODELS);
                        obtainMessage(MSG_ENUMERATE_MODELS, Pair.create(url, version)).sendToTarget();
                    }
                } else {
                    Logger.e(TAG, "Unexpected format url " + url);
                }
                break;
            }
            case MSG_ENUMERATE_MODELS: {
                Pair<String, Float> pair = (Pair<String, Float>) msg.obj;
                String url = pair.first;
                float version = pair.second;
                ZipInputStream input = null;
                try {
                    Map<String, Map<String, FileCrc>> localFiles = mModelFiles;
                    if (localFiles == null) {
                        localFiles = new HashMap<>(0);
                    }
                    Map<String, Map<String, FileCrc>> files = new HashMap<>();

                    URL aURL = new URL(url);
                    URLConnection connection = aURL.openConnection();
                    connection.setConnectTimeout(5000);
                    if (connection instanceof HttpsURLConnection) {
                        HttpsURLConnection https = (HttpsURLConnection) connection;
                        https.setSSLSocketFactory(NetUtils.noSSLSocketFactory());
                        https.setHostnameVerifier((hostname, session) -> true);
                    }
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(connection.getInputStream());
                    input = new ZipInputStream(bufferedInputStream);

                    ZipEntry entry;
                    Pattern reModel = Pattern.compile("^device-files/([^/]+)/$");
                    Pattern reFile = Pattern.compile("^device-files/([^/]+)(/.+)");

                    while ((entry = input.getNextEntry()) != null) {
                        String name = entry.getName();
                        Matcher matcher = reModel.matcher(name);
                        if (matcher.matches()) {
                            String model = matcher.group(1);
                            files.put(model, new HashMap<>());
                        } else if (name.endsWith("/") || name.endsWith(".DS_Store") || name.contains("__MACOSX")) {
                            continue;
                        } else {
                            matcher = reFile.matcher(name);
                            if (matcher.matches()) {
                                String model = matcher.group(1);
                                String path = matcher.group(2);
                                if (entry.getCrc() == -1) {
                                    throw new Exception("Zip file " + model + path + " must have crc.");
                                }
                                FileCrc fileCrc = new FileCrc(entry.getCrc());
                                Map<String, FileCrc> map = localFiles.get(model);

                                boolean shouldDownload = true;
                                if (map != null) {
                                    FileCrc c = map.get(path);
                                    if (c != null && c.localCrc == fileCrc.crc) {
                                        shouldDownload = false;
                                    }
                                }

                                if (shouldDownload) {
                                    Logger.d(TAG, "Download " + model + path + ", crc " + fileCrc.crc);
                                    File file = new File(FILES_ROOT_FOLDER + '/' + model + path);
                                    File tmpFile = new File(file.getAbsolutePath() + ".dl");
                                    File dir = file.getParentFile();
                                    if (!dir.exists()) {
                                        dir.mkdirs();
                                    }
                                    FileOutputStream output = new FileOutputStream(tmpFile);
                                    IOUtils.copy(input, output);
                                    output.close();
                                    file.delete();
                                    tmpFile.renameTo(file);
                                    file.setReadable(true, false);
                                } else {
                                    Logger.d(TAG, "File " + model + path + " exist and crc match.");
                                }
                                fileCrc.localCrc = fileCrc.crc;
                                files.get(model).put(path, fileCrc);
                            } else {
                                throw new Exception("Unexpected file " + name);
                            }
                        }
                    }
                    mModelFiles = files;
                    mVersion = version;
                    fixPermissions();
                    writeLocalList();
                    Logger.i(TAG, "Models " + files.keySet() + ", at version " + version);
                    mIsReady = true;
                    notifyVersionUpdate();
                } catch (Throwable e) {
                    Logger.e(TAG, "Failed to enumerate models.", e);
                }
                IOUtils.closeQuietly(input);
                break;
            }
            case MSG_CHECK_MODEL_FILES: {
                Pair<String, C2<Integer, String>> pair = (Pair<String, C2<Integer, String>>) msg.obj;
                String model = pair.first;
                C2<Integer, String> callback = pair.second;
                if (!mIsReady) {
                    onStatusChanged(callback, STATUS_FAIL, "Model file list not ready.");
                    return;
                }
                Map<String, FileCrc> modelFiles = mModelFiles.get(model);
                if (modelFiles == null) {
                    onStatusChanged(callback, STATUS_FAIL, "No model files for " + model);
                    return;
                }
                onStatusChanged(callback, STATUS_SUCCESS, null);
                break;
            }
        }
    }

    private void onStatusChanged(C2<Integer, String> callback, int status, String msg) {
        if (status != STATUS_FAIL) {
            //Logger.v(TAG, msg);
        } else {
            Logger.e(TAG, msg);
        }
        callback.onResult(status, msg);
    }

    private void writeLocalList() throws Throwable {
        File file = new File(FILES_LIST + ".new");
        JSONObject jContent = new JSONObject();
        jContent.put("version", mVersion);
        jContent.put("files", mModelFiles);
        FileUtils.writeStringToFile(file, JSON.toJSONString(jContent, true));
        file.renameTo(new File(FILES_LIST));
    }

    private void fixPermissions() {
        FileUtils.listFilesAndDirs(new File(FILES_ROOT_FOLDER), FalseFileFilter.INSTANCE, TrueFileFilter.INSTANCE).forEach(dir -> {
            dir.setReadable(true, false);
            dir.setExecutable(true, false);
        });
    }
}
