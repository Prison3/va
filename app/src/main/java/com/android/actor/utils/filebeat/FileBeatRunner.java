package com.android.actor.utils.filebeat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import androidx.annotation.NonNull;

import com.android.actor.ActApp;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.device.DeviceNumber;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.ResUtils;
import com.android.actor.utils.downloader.DownloadManager;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.shell.Shell;

import java.io.File;
import java.net.InetAddress;

public class FileBeatRunner implements Handler.Callback {

    private static final String TAG = FileBeatRunner.class.getSimpleName();
    private HandlerThread mThread;
    private Handler mHandler;
    private File mBinaryFile;
    private String mConfigPath;

    private static final int MSG_INIT = 0;
    private static final int MSG_RUN  = 1;

    private static final int DELAY_INIT = 30 * 1000;
    private static final int RETRY_INIT = 600 * 1000;
    private static final int RETRY_RUN = 600 * 1000;

    public FileBeatRunner() {
    }

    public void start() {
        mThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_LOWEST) {
            @Override
            protected void onLooperPrepared() {
                mHandler = new Handler(mThread.getLooper(), FileBeatRunner.this);
                mHandler.sendEmptyMessageDelayed(MSG_INIT, DELAY_INIT);
            }
        };
        mThread.start();
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_INIT:
                try {
                    init();
                    mHandler.sendEmptyMessage(MSG_RUN);
                } catch (Throwable e) {
                    Logger.w(TAG, "Failed to init, " + e);
                    mHandler.sendEmptyMessageDelayed(MSG_INIT, RETRY_INIT);
                }
                break;
            case MSG_RUN:
                try {
                    run();
                } catch (Throwable e) {
                    Logger.w(TAG, "Error to run, " + e);
                }
                mHandler.sendEmptyMessageDelayed(MSG_RUN, RETRY_RUN);
                break;
        }
        return true;
    }

    private void init() throws Throwable {
        if (!DeviceNumber.has()) {
            throw new Exception("No device number.");
        }
        String gateway = DeviceInfoManager.getInstance().getConnManager().getWired().getGateway();
        if (ActStringUtils.isEmpty(gateway)) {
            throw new Exception("no wired gateway.");
        }
        InetAddress addr = InetAddress.getByName(gateway);
        if (!addr.isReachable(2000)) {
            throw new Exception("gateway " + gateway + " unreachable.");
        }

        Logger.d(TAG, "Check ElasticSearch 9200 port.");
        if (NetUtils.httpGet("http://" + gateway + ":9200") == null) {
            throw new Exception("ElasticSearch 9200 port is not accessible.");
        }

        mBinaryFile = new File(ActApp.getInstance().getFilesDir().getPath() + "/filebeat/filebeat");
        if (!mBinaryFile.exists()) {
            if (!mBinaryFile.getParentFile().exists()) {
                mBinaryFile.getParentFile().mkdir();
            }
            String url = "http://" + gateway + "/out/filebeat";
            File file = DownloadManager.instance().getFile(url);
            if (file == null) {
                BlockingReference<File> blockingReference = new BlockingReference<>();
                DownloadManager.instance().addDownload(url, (url1, file1, reason) -> blockingReference.put(file1));
                file = blockingReference.take();
                if (file == null) {
                    throw new Exception("Can't download " + url);
                }
            }
            file.setExecutable(true, false);
            if (!file.renameTo(mBinaryFile)) {
                throw new Exception("Can't rename " + file + " to " + mBinaryFile);
            }
        }

        File dir = new File(mBinaryFile.getParent() + "/etc");
        if (!dir.exists()) {
            dir.mkdir();
        }
        String path = dir.getPath() + "/os-release";
        ResUtils.extractAssetToFileIfNotExist("bin/os-release", path);

        mConfigPath = mBinaryFile.getParent() + "/filebeat.yml";
        Logger.d(TAG, "mConfigPath " + mConfigPath);
        Libsu.exec("rm -f " + mConfigPath);
        ResUtils.extractAsset("bin/filebeat.yml", mConfigPath, DeviceNumber.get(), gateway);
        Libsu.exec("chmod 600 " + mConfigPath);
        Libsu.exec("chown root:root " + mConfigPath);
    }

    private void run() throws Throwable {
        Logger.d(TAG, "Run filebeat.");
        Libsu.exec("killall filebeat");
        Shell.execRootCmd(mBinaryFile.getPath() + " -c " + mConfigPath);
        Logger.d(TAG, "Run filebeat end.");
    }
}
