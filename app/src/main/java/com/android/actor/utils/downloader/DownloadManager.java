package com.android.actor.utils.downloader;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.android.actor.ActApp;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO: 文件名相同的文件会被当作同一个文件，上传文件的时候需自行加上文件版本号
public class DownloadManager extends Handler {

    private static final String TAG = DownloadManager.class.getSimpleName();

    private static final int MSG_INIT = 0;
    private static final int MSG_ADD_DOWNLOAD = 1;
    private static final int MSG_ONE_DOWNLOADED = 2;
    private static final int MSG_COUNT_FATAL = 3;

    private static final int MAX_THREADS = 4;
    private ExecutorService mExecutor;

    private int FATAL_TO_REPORT_COUNT = 10;
    private int FATAL_IN_MILLISECONDS = 5 * 60 * 1000;
    private LinkedList<Long> mFatalCounter = new LinkedList<>();

    static class Info {
        String url;
        String folder;
        String filename;
        DownloadCallback callback;
        String reason;

        Info(String url, String folder, String filename, DownloadCallback callback) {
            this.url = url;
            this.folder = StringUtils.isEmpty(folder) ? ActApp.getInstance().getDir("download", 0).getPath() : folder;
            this.filename = filename;
            this.callback = callback;
        }

        String getFileName() {
            if (!StringUtils.isEmpty(filename)) {
                return filename;
            }
            return Uri.parse(url).getLastPathSegment(); // For now, same file name will be treated same file.
        }

        File getFile() {
            String fileName = getFileName();
            if (fileName == null) {
                return null;
            }
            return new File(folder, fileName);
        }

        String getReason() {
            return reason == null ? "" : reason;
        }
    }
    private Map<String, List<DownloadCallback>> mCallbacks;

    private static DownloadManager sInstance;
    public static DownloadManager instance() {
        if (sInstance == null) {
            synchronized (DownloadManager.class) {
                if (sInstance == null) {
                    HandlerThread thread = new HandlerThread("DownloadManager");
                    thread.start();
                    sInstance = new DownloadManager(thread.getLooper());
                }
            }
        }
        return sInstance;
    }

    private DownloadManager(Looper looper) {
        super(looper);
        sendEmptyMessage(MSG_INIT);
    }

    public File getFile(String url) {
        return getFile(url, null, null);
    }

    public File getFile(String url, String folder, String filename) {
        File file = new Info(url, folder, filename, null).getFile();
        return file != null && file.exists() && file.length() != 0 ? file : null;
    }


    public String getFileName(String str) {
        return new Info(str, null, null, null).getFileName();
    }

    public File getParent(String str) {
        return new Info(str, null, null, null).getFile().getParentFile();
    }


    public void addDownload(String url, DownloadCallback callback) {
        addDownload(url, null, null, callback);
    }

    public void addDownload(String url, String folder, String filename, DownloadCallback callback) {
        Info info = new Info(url, folder, filename, callback);
        if (info.getFileName() == null) {
            callback.onEnd(url, null, "No path in url, can't name file.");
        } else {
            obtainMessage(MSG_ADD_DOWNLOAD, info).sendToTarget();
        }
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_INIT: {
                mExecutor = Executors.newFixedThreadPool(MAX_THREADS);
                mCallbacks = new HashMap<>();
                mExecutor.execute(() -> {
                    for (File file : ActApp.getInstance().getDir("download", 0).listFiles()) {
                        if (!file.getName().endsWith(".zip") && !file.getName().endsWith(".apk")) {
                            continue;
                        }
                        if (System.currentTimeMillis() - file.lastModified() > 3 * 24 * 3600 * 1000) {
                            Logger.d(TAG, "Remove old downloaded file " + file + ", " + file.lastModified());
                            try {
                                FileUtils.forceDelete(file);
                            } catch (Throwable e) {
                                Logger.e(TAG, "Failed to delete " + file, e);
                            }
                        }
                    }
                });
                break;
            }
            case MSG_ADD_DOWNLOAD: {
                Info info = (Info) msg.obj;
                Logger.v(TAG, "Add download " + info.url + ", " + info.getFile() + ", callback " + info.callback);
                List<DownloadCallback> callbacks = mCallbacks.get(info.url);
                if (callbacks == null) {
                    callbacks = new ArrayList<>(1);
                    callbacks.add(info.callback);
                    mCallbacks.put(info.url, callbacks);
                    queueDownload(info);
                } else {
                    callbacks.add(info.callback);
                    Logger.v(TAG, "Download " + info.url + " is already queued, callback " + info.callback);
                }
                break;
            }
            case MSG_ONE_DOWNLOADED: {
                boolean downloaded = msg.arg1 == 1;
                Info info = (Info) msg.obj;
                File file = info.getFile();
                if (!downloaded || !file.exists()) {
                    file = null;
                } else {
                    Logger.v(TAG, "One downloaded " + info.url + ", " + info.getFile());
                }
                for (DownloadCallback callback : mCallbacks.get(info.url)) {
                    try {
                        callback.onEnd(info.url, file, info.getReason());
                    } catch (Throwable e) {
                        Logger.e(TAG, "Download callback " + callback + " exception.", e);
                    }
                }
                mCallbacks.remove(info.url);
                break;
            }
            case MSG_COUNT_FATAL: {
                long time = System.currentTimeMillis();
                while (mFatalCounter.size() > 0) {
                    long first = mFatalCounter.peek();
                    if (first + FATAL_IN_MILLISECONDS < time) {
                        mFatalCounter.pop();
                    } else {
                        break;
                    }
                }
                mFatalCounter.add(time);

                if (mFatalCounter.size() >= FATAL_TO_REPORT_COUNT) {
                    String _msg = "HTTP download failure " + mFatalCounter.size() + " in " + (FATAL_IN_MILLISECONDS / 1000) + "s.";
                    Logger.w(TAG, _msg);
                    GRPCManager.getInstance().sendNotification("warning", _msg);
                    mFatalCounter.clear();
                }
                break;
            }
        }
    }

    private void queueDownload(Info info) {
        mExecutor.execute(() -> {
            boolean downloaded = false;
            WiredHttpDownloader wiredDownloader = null;
            try {
                wiredDownloader = new WiredHttpDownloader(info.getFile(), info.url);
                if (wiredDownloader.prepare()) {
                    wiredDownloader.download();
                }
                downloaded = true;
            } catch (Throwable e) {
                Logger.w(TAG, "Download failed through wired " + info.url + ", " + e);
                if (wiredDownloader != null && wiredDownloader.isReachable()) {
                    Logger.w(TAG, "Wired gateway is reachable, skip wireless download.");
                    info.reason = e.toString();
                    sendEmptyMessage(MSG_COUNT_FATAL);
                } else {
                    try {
                        HttpDownloader downloader = new HttpDownloader(info.getFile(), info.url);
                        if (downloader.prepare()) {
                            downloader.download();
                        }
                        downloaded = true;
                    } catch (Throwable e2) {
                        Logger.e(TAG, "Download failed " + info.url + ", " + e2);
                        info.reason = e2.toString();
                        sendEmptyMessage(MSG_COUNT_FATAL);
                    }
                }
            }
            obtainMessage(MSG_ONE_DOWNLOADED, downloaded ? 1 : 0, 0, info).sendToTarget();
        });
    }
}
