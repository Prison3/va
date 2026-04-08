package com.android.actor.control;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.device.DeviceNumber;
import com.android.actor.remote.LppiManagerConnection;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.Callback;
import com.android.actor.utils.downloader.DownloadManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.shell.Libsu;
import com.topjohnwu.superuser.Shell;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActPackageInstaller {

    private static final String TAG = ActPackageInstaller.class.getSimpleName();
    private static final Pattern PATTERN_URL = Pattern.compile("https?://.+/([^_]+)_(.+)\\.\\w+");
    private static ActPackageInstaller sInstance;
    private ExecutorService mConfigInstallExecutor = Executors.newSingleThreadExecutor();
    private JSONObject mConfig;

    public static ActPackageInstaller instance() {
        if (sInstance == null) {
            synchronized (ActPackageInstaller.class) {
                if (sInstance == null) {
                    sInstance = new ActPackageInstaller();
                }
            }
        }
        return sInstance;
    }

    public void installFromConfig(JSONObject jContent) {
        if (jContent == null) {
            jContent = new JSONObject();
        }
        if (jContent.equals(mConfig)) {
            return;
        }
        mConfig = jContent;
        mConfigInstallExecutor.submit(new Runnable() {
            @Override
            public void run() {
                Logger.d(TAG, "Install apks from config start.");
                JSONObject jConfig = mConfig;
                for (Map.Entry<String, Object> entry : jConfig.entrySet()) {
                    String url = entry.getKey();
                    String numberStr = (String) entry.getValue();
                    if (!DeviceNumber.isDeviceInArray(numberStr)) {
                        continue;
                    }
                    Logger.d(TAG, "Device match " + numberStr + ", " + url);
                    Matcher matcher = PATTERN_URL.matcher(url);
                    if (matcher.find()) {
                        String packageName = matcher.group(1);
                        String version = matcher.group(2);
                        PackageInfo info = ActPackageManager.getInstance().getPackageInfo(packageName);
                        if (info == null || !info.versionName.equals(version)) {
                            Logger.i(TAG, "Install apk from config " + url);
                            String reason = installAppUrl(url);
                            if (reason != null) {
                                Logger.e(TAG, "Error to install apk from config, " + reason);
                                try {
                                    Thread.sleep(60 * 1000);
                                } catch (InterruptedException e) {
                                }
                                mConfigInstallExecutor.submit(this);
                                return;
                            }
                        }
                    } else {
                        Logger.e(TAG, "Url not match, " + url);
                    }
                }
                Logger.d(TAG, "Install apks from config end.");
            }
        });
    }

    public static abstract class Listener {
        public void onPreInstall(String packageName, int versionCode) {
        }

        private void _onPreInstall(String packageName, int versionCode) {
            Logger.v(TAG, "onPreInstall " + packageName + ", " + versionCode);
            onPreInstall(packageName, versionCode);
        }

        public abstract void postInstall(boolean success, String reason);

        private void _postInstall(boolean success, String reason) {
            Logger.v(TAG, "postInstall " + success + ", " + reason);
            postInstall(success, reason);
        }
    }

    public String installAppUrl(String url) {
        try {
            BlockingReference<String> blockingReference = new BlockingReference<>();
            installAppUrl(url, new Listener() {
                @Override
                public void postInstall(boolean success, String reason) {
                    blockingReference.put(success ? null : reason);
                }
            });
            return blockingReference.take();
        } catch (Throwable e) {
            Logger.e(TAG, "Error to install app from url " + url, e);
            return e.toString();
        }
    }

    public void installAppUrl(String url, Listener listener) {
        Logger.d(TAG, "installAppUrl " + url);
        if (ActStringUtils.isEmpty(url)) {
            listener._postInstall(false, "app_cdn_url is null or empty.");
            return;
        }
        File file = DownloadManager.instance().getFile(url);
        if (file != null) {
            installApp(file, listener);
            return;
        }

        DownloadManager.instance().addDownload(url, (_url, _file, reason) -> {
            if (_file == null) {
                listener._postInstall(false, reason);
                return;
            }
            installApp(_file, listener);
        });
    }

    public String installAppFile(String[] paths) throws InterruptedException {
        BlockingReference<String> blockingReference = new BlockingReference();
        installAppFile(paths, new Listener() {
            @Override
            public void postInstall(boolean success, String reason) {
                blockingReference.put(success ? null : reason);
            }
        });
        return blockingReference.take();
    }

    public void installAppFile(String path, Listener listener) {
        installAppFile(new String[] { path }, listener);
    }

    public void installAppFile(String[] paths, Listener listener) {
        Logger.d(TAG, "installAppFile " + ArrayUtils.toString(paths));
        File[] files = Arrays.stream(paths).map(path -> new File(path)).toArray(File[]::new);
        if (Arrays.stream(files).anyMatch(file -> !file.exists())) {
            listener._postInstall(false, "File not exist.");
            return;
        }
        installApp(files, listener);
    }

    private void installApp(File file, Listener listener) {
        File[] files;
        String name = file.getName();
        if (name.endsWith(".zip") || name.endsWith(".tar")) {
            File dir = new File(ActApp.getInstance().getDir("tmp", 0), FilenameUtils.getBaseName(name));
            Libsu.exec("rm -rf " + dir.getPath());
            dir.mkdir();
            Shell.Result result;
            if (name.endsWith(".zip")) {
                result = Libsu.exec("unzip -d " + dir.getPath() + " " + file.getPath() + " && chmod -R 777 " + dir.getPath());
            } else {
                result = Libsu.exec("tar xvf " + file.getPath() + " -C " + dir.getPath() + " && chmod -R 777 " + dir.getPath());
            }
            if (result.getCode() == 0) {
                files = FileUtils.listFiles(dir, new String[] { "apk" }, true).stream().filter(f -> !f.getPath().contains("MACOSX")).toArray(File[]::new);
            } else {
                listener._postInstall(false, "unzip/tar failure.");
                return;
            }
        } else {
            files = new File[] { file };
        }
        installApp(files, listener);
    }

    private void installApp(File[] files, Listener listener) {
        Logger.d(TAG, "Install app " + ArrayUtils.toString(files));
        File baseFile;
        if (files.length == 1) {
            baseFile = files[0];
        } else {
            Optional<File> opt = Arrays.stream(files).filter(f -> f.getPath().endsWith("base.apk")).findFirst();
            if (!opt.isPresent()) {
                listener._postInstall(false, "Expect base.apk in " + ArrayUtils.toString(files));
                return;
            }
            baseFile = opt.get();
        }

        PackageInfo info = ActPackageManager.getInstance().getPackageManager().getPackageArchiveInfo(baseFile.getPath(), PackageManager.GET_META_DATA);
        if (info == null) {
            listener._postInstall(false, "Not a valid apk, " + baseFile);
            return;
        }

        PackageInfo existInfo = ActPackageManager.getInstance().getPackageInfo(info.packageName);
        if (existInfo != null && existInfo.getLongVersionCode() > info.getLongVersionCode() ) {
            ActPackageManager.getInstance().uninstallApp(info.packageName, (packageName, success, reason) -> {
                if (success) {
                    listener._onPreInstall(info.packageName, (int) info.getLongVersionCode());
                    installAppReal(files, listener);
                } else {
                    listener._postInstall(false, "Failed to uninstall exist, " + reason);
                }
            });
            return;
        }
        listener._onPreInstall(info.packageName, (int) info.getLongVersionCode());
        installAppReal(files, listener);
    }

    private void installAppReal(File[] files, Listener listener) {
        ActPackageManager.getInstance().installApk(files, (packageName, success, reason) -> {
            // unpack dex package if package name match after install
            if (RocketComponent.PKG_TWEAKS_FRAMEWORK.equals(packageName)) {
                if (success) {
                    LppiManagerConnection connection = new LppiManagerConnection();
                    connection.switchModule(packageName, true, (success1, reason1) -> {
                        listener._postInstall(success1, reason1);
                        Logger.i(TAG, "LppiFramework installed, reboot");
                        Libsu.reboot();
                    });
                } else {
                    listener._postInstall(false, reason);
                }
            } else {
                listener._postInstall(success, reason);
            }
        });
    }
}
