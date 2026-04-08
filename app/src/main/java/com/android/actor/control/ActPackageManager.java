package com.android.actor.control;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.device.ManagedProfiles;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ReflectUtils;
import com.android.actor.utils.WebUtils;
import com.android.actor.utils.shell.Libsu;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static com.android.actor.control.RocketComponent.PKG_WEBVIEW;

import hidden.android.os.UserHandleHidden;

public class ActPackageManager {
    private final static String TAG = ActPackageManager.class.getSimpleName();
    private static ActPackageManager sInstance;
    private final Context mContext;

    private final PackageManager mPackageManager;
    private final ActivityManager mActivityManager;
    private final InstallHandler mInstallHandler;
    private final InstallReceiver mInstallReceiver;

    private static final String ACTION_INSTALL_APK = "com.android.actor.ACTION_INSTALL_APK";
    private static final String ACTION_UNINSTALL_APP = "com.android.actor.ACTION_UNINSTALL_APP";
    private static final int MSG_CHECK_TO_RUN_NEXT = 0;
    private static final int MSG_INSTALL_APK = 1;
    private static final int MSG_INSTALL_END = 2;
    private static final int MSG_UNINSTALL_APP = 3;
    private static final int MSG_UNINSTALL_END = 4;
    private static final int MSG_CLEAR_APP = 5;
    private static final int MSG_CLEAR_END = 6;
    private static final int MSG_CLEAR_CACHE = 7;
    private static final int MSG_CLEAR_CACHE_END = 8;

    // No matter install, uninstall, upgrade, we let them run one job at once.
    private ConcurrentLinkedQueue<InstallInfo> mInstallQueue;

    public static final int APP_INSTALLED = 0;
    public static final int APP_UNINSTALLED = 1;
    private List<Callback.C2<Integer, String>> mAppChangeListeners = new ArrayList<>();

    public static ActPackageManager getInstance() {
        if (sInstance == null) {
            synchronized (ActPackageManager.class) {
                if (sInstance == null) {
                    sInstance = new ActPackageManager(ActApp.getInstance());
                }
            }
        }
        return sInstance;
    }

    private ActPackageManager(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        HandlerThread thread = new HandlerThread("ActPackageManager");
        thread.start();
        mInstallHandler = new InstallHandler(thread.getLooper());
        mInstallReceiver = new InstallReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_INSTALL_APK);
        filter.addAction(ACTION_UNINSTALL_APP);
        context.registerReceiver(mInstallReceiver, filter);
        mInstallQueue = new ConcurrentLinkedQueue<>();

        IntentFilter globalFilter = new IntentFilter();
        globalFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        globalFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        globalFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        globalFilter.addDataScheme("package");
        context.registerReceiver(mPackageReceiver, globalFilter);
        Logger.d(TAG, "register PackageReceiver");
    }

    public PackageManager getPackageManager() {
        return mPackageManager;
    }

    public void addAppChangeListener(Callback.C2<Integer, String> listener) {
        mAppChangeListeners.add(listener);
    }

    public void removeAppChangeListener(Callback.C2<Integer, String> listener) {
        mAppChangeListeners.remove(listener);
    }

    private void notifyAppChange(int type, String packageName) {
        mAppChangeListeners.forEach(listener -> listener.onResult(type, packageName));
    }

    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getDataString();
            String pkgName = msg.replace("package:", "");
            switch (intent.getAction()) {
                case Intent.ACTION_PACKAGE_ADDED:
                    Logger.i(TAG, "Install package: " + pkgName);
                    if (PKG_WEBVIEW.equals(pkgName)) {
                        WebUtils.changeWebViewProvider();
                    }
                    notifyAppChange(APP_INSTALLED, pkgName);
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        return;
                    }
                    Logger.i(TAG, "Remove package: " + pkgName);
                    notifyAppChange(APP_UNINSTALLED, pkgName);
                    break;
                case Intent.ACTION_PACKAGE_REPLACED:
                    Logger.i(TAG, "Replace package: " + pkgName);
                    if (PKG_WEBVIEW.equals(pkgName)) {
                        WebUtils.changeWebViewProvider();
                    }
                    break;
            }
        }
    };

    public Intent getLaunchIntentForPackage(String packageName) {
        return mPackageManager.getLaunchIntentForPackage(packageName);
    }

    public String getPackageTitle(String packageName) {
        return getPackageInfo(packageName, 0).applicationInfo.loadLabel(getPackageManager()).toString();
    }

    public List<PackageInfo> getAllPackage() {
        return mPackageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES);
    }

    public List<PackageInfo> getInstalledPackage() {
        List<PackageInfo> infoList = getAllPackage();
        List<PackageInfo> list = new ArrayList<>();
        for (PackageInfo packageInfo : infoList) {
            if (!isSystemApp(packageInfo.applicationInfo)
                    && packageInfo.applicationInfo.uid >= 10000
                    && !RocketComponent.isRocketComponent(packageInfo.packageName)) {
                list.add(packageInfo);
            }
        }
        return list;
    }

    public List<String> getPackageNameList(boolean sort, boolean withRocket) {
        List<PackageInfo> infos = getInstalledPackage();
        if (sort) {
            infos.sort((o1, o2) -> o1.packageName.compareTo(o2.packageName));
        }
        if (withRocket) {
            return infos.stream().map(info -> info.packageName).collect(Collectors.toList());
        }
        return infos.stream().map(info -> info.packageName).filter(pkgName -> !RocketComponent.isRocketComponent(pkgName)).collect(Collectors.toList());
    }

    public boolean isAppInstalled(String packageName) {
        try {
            mPackageManager.getPackageUid(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public PackagesBuilder getPackagesBuilder() {
        return new PackagesBuilder(getInstalledPackage());
    }

    public static class PackagesBuilder {

        private Stream<PackageInfo> stream;
        private boolean withRocket = false;
        private boolean withInputMethod = false;
        private List<PackageInfo> additionalInfos;
        private boolean sortByUid = false;

        public PackagesBuilder(List<PackageInfo> infos) {
            stream = infos.stream().sorted(Comparator.comparing(info -> info.packageName));
        }

        public PackagesBuilder withRocket() {
            withRocket = true;
            return this;
        }

        public PackagesBuilder withInputMethod() {
            withInputMethod = true;
            return this;
        }

        public PackagesBuilder withGMS() {
            if (additionalInfos == null) {
                additionalInfos = new ArrayList<>(2);
            }
            additionalInfos.add(ActPackageManager.getInstance().getPackageInfo(RocketComponent.GMS));
            return this;
        }

        public PackagesBuilder withGooglePlay() {
            if (additionalInfos == null) {
                additionalInfos = new ArrayList<>(2);
            }
            additionalInfos.add(ActPackageManager.getInstance().getPackageInfo(RocketComponent.GOOGLE_PLAY));
            return this;
        }

        public PackagesBuilder sortByUid() {
            sortByUid = true;
            return this;
        }

        public Stream<PackageInfo> build() {
            if (!withRocket) {
                stream = stream.filter(info -> !RocketComponent.isRocketComponent(info.packageName));
            }
            if (!withInputMethod) {
                InputMethodManager inputMethodManager = (InputMethodManager) ActApp.getInstance().getSystemService(Context.INPUT_METHOD_SERVICE);
                List<String> inputMethods = inputMethodManager.getInputMethodList().stream().map(info -> info.getPackageName()).collect(Collectors.toList());
                stream = stream.filter(info -> !inputMethods.contains(info.packageName));
            }
            if (additionalInfos != null) {
                stream = Stream.concat(stream, additionalInfos.stream());
            }
            if (sortByUid) {
                stream = stream.sorted(Comparator.comparingInt(info -> info.applicationInfo.uid));
            }
            return stream;
        }

        public PackageInfo[] buildInfos() {
            return build().toArray(PackageInfo[]::new);
        }

        public String[] buildNames() {
            return build().map(info -> info.packageName).toArray(String[]::new);
        }

        public ProfilePackage[] buildProfilePackages() {
            return build().map(info -> ProfilePackage.create(info.packageName, 0)).toArray(ProfilePackage[]::new);
        }
    }

    // return processName
    public Set<String> getAllProcessOfPackage(String pkgName) {
        try {
            Set<String> processes = new HashSet<>();
            PackageInfo info = mPackageManager.getPackageInfo(pkgName,
                    PackageManager.GET_SERVICES | PackageManager.GET_ACTIVITIES
                            | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS);
            if (info != null) {
                if (info.activities != null) {
                    for (ActivityInfo ai : info.activities) {
                        processes.add(ai.processName);
                    }
                }
                if (info.services != null) {
                    for (ServiceInfo si : info.services) {
                        processes.add(si.processName);
                    }
                }
                if (info.providers != null) {
                    for (ProviderInfo pi : info.providers) {
                        processes.add(pi.processName);
                    }
                }
                if (info.receivers != null) {
                    for (ActivityInfo ai : info.receivers) {
                        processes.add(ai.processName);
                    }
                }
            }
            return processes;
        } catch (PackageManager.NameNotFoundException ignored) {

        }
        return null;
    }

    private boolean isSystemApp(ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 // 原本是系统应用又升级成第三方应用的
                || (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    public int getUidOfPackage(String pkgName) throws PackageManager.NameNotFoundException {
        ApplicationInfo appInfo = mPackageManager.getApplicationInfo(pkgName, 0);
        return appInfo.uid;
    }

    public int getUid(String packageName) {
        try {
            return getUidOfPackage(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    public PackageInfo getPackageInfo(String packageName) {
        return getPackageInfo(packageName, PackageManager.GET_META_DATA);
    }

    public PackageInfo getPackageInfo(String packageName, int flags) {
        try {
            return mPackageManager.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public int getVersionCode(String packageName){
        PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo != null) {
            return packageInfo.versionCode;
        }
        return -1;
    }

    public String getVersionName(String packageName){
        PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo != null) {
            return packageInfo.versionName;
        }
        return "";
    }

    public Drawable loadIcon(String packageName) {
        try {
            return mPackageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return mContext.getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }

    public Drawable loadBadgedIcon(String packageName, int profileId) {
        Drawable icon;
        try {
            icon = mPackageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            icon = mContext.getDrawable(android.R.drawable.sym_def_app_icon);
        }
        if (profileId == 0) {
            return icon;
        }

        Paint paint = new Paint();
        paint.setColor(0xFFFF6000);
        paint.setTextSize(48);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setFakeBoldText(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        int width = icon.getIntrinsicWidth();
        Bitmap bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawText(String.valueOf(profileId), width / 30 * 23, width / 8 * 7, paint);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(bitmap);

        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{
                icon,
                mContext.getDrawable(R.drawable.ic_corp_icon_badge_shadow),
                mContext.getDrawable(R.drawable.ic_corp_icon_badge_color),
                bitmapDrawable
        });
        return layerDrawable;
    }

    static class InstallInfo {
        int what;
        File[] files;
        String packageName;
        int profileId;
        Callback.C3<String, Boolean, String> callback;

        InstallInfo(int what, File[] files, String packageName, int profileId, Callback.C3<String, Boolean, String> callback) {
            this.what = what;
            this.files = files;
            this.packageName = packageName;
            this.profileId = profileId;
            this.callback = callback;
        }
    }

    class InstallHandler extends Handler {

        private boolean mIsRunning = false;

        public InstallHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_CHECK_TO_RUN_NEXT: {
                    if (!mIsRunning) {
                        InstallInfo info = mInstallQueue.peek();
                        if (info != null) {
                            mIsRunning = true;
                            obtainMessage(info.what, info).sendToTarget();
                        }
                    }
                    break;
                }
                case MSG_INSTALL_APK: {
                    InstallInfo info = (InstallInfo) msg.obj;
                    installApkInternal(info.files);
                    break;
                }
                case MSG_INSTALL_END: {
                    InstallInfo info = mInstallQueue.poll();
                    boolean success = msg.arg1 == PackageInstaller.STATUS_SUCCESS;
                    String reason = (String) msg.obj;
                    reason = reason == null ? "" : reason;
                    info.callback.onResult(info.packageName, success, reason);
                    endRun();
                    break;
                }
                case MSG_UNINSTALL_APP: {
                    InstallInfo info = (InstallInfo) msg.obj;
                    try {
                        getUidOfPackage(info.packageName);
                    } catch (PackageManager.NameNotFoundException e) {
                        Logger.w(TAG, "Uninstall non-exist app " + info.packageName);
                        Message message = obtainMessage(MSG_UNINSTALL_END);
                        message.arg1 = PackageInstaller.STATUS_SUCCESS;
                        message.obj = "App not exist.";
                        message.sendToTarget();
                        return;
                    }
                    uninstallAppInternal(info.packageName);
                    break;
                }
                case MSG_UNINSTALL_END: {
                    InstallInfo info = mInstallQueue.poll();
                    boolean success = msg.arg1 == PackageInstaller.STATUS_SUCCESS;
                    String reason = (String) msg.obj;
                    reason = reason == null ? "" : reason;
                    info.callback.onResult(info.packageName, success, reason);
                    endRun();
                    break;
                }
                case MSG_CLEAR_APP: {
                    InstallInfo info = (InstallInfo) msg.obj;
                    clearAppInternal(info.packageName, info.profileId);
                    break;
                }
                case MSG_CLEAR_END: {
                    InstallInfo info = mInstallQueue.poll();
                    boolean success = msg.arg1 == 0;
                    String reason = (String) msg.obj;
                    reason = reason == null ? "" : reason;
                    info.callback.onResult(info.packageName, success, reason);
                    endRun();
                    break;
                }
                case MSG_CLEAR_CACHE: {
                    InstallInfo info = (InstallInfo) msg.obj;
                    clearCacheInternal(info.packageName, info.profileId);
                    break;
                }
                case MSG_CLEAR_CACHE_END: {
                    InstallInfo info = mInstallQueue.poll();
                    boolean success = msg.arg1 == 0;
                    String reason = (String) msg.obj;
                    reason = reason == null ? "" : reason;
                    info.callback.onResult(info.packageName, success, reason);
                    endRun();
                    break;
                }
            }
        }

        private void endRun() {
            mIsRunning = false;
            sendEmptyMessage(MSG_CHECK_TO_RUN_NEXT);
        }
    }

    class InstallReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_INSTALL_APK: {
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100);
                    String reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        Logger.i(TAG, "Install apk success, " + packageName);
                    } else {
                        Logger.e(TAG, "Install apk failure, status " + status + ", " + reason);
                    }

                    InstallInfo info = mInstallQueue.peek();
                    info.packageName = packageName;
                    Message message = mInstallHandler.obtainMessage(MSG_INSTALL_END);
                    message.arg1 = status;
                    message.obj = reason;
                    message.sendToTarget();
                    break;
                }
                case ACTION_UNINSTALL_APP: {
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100);
                    String reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        Logger.i(TAG, "Uninstall app success, " + packageName);
                    } else {
                        Logger.e(TAG, "Uninstall app failure, status " + status + ", " + reason);
                    }
                    Message message = mInstallHandler.obtainMessage(MSG_UNINSTALL_END);
                    message.arg1 = status;
                    message.obj = reason;
                    message.sendToTarget();
                    break;
                }
            }
        }
    }

    public String installApkFile(String path) {
        BlockingReference<String> result = new BlockingReference<>();
        ActPackageInstaller.instance().installAppFile(path, new ActPackageInstaller.Listener() {
            @Override
            public void postInstall(boolean success, String reason) {
                result.put(success ? null : (StringUtils.isEmpty(reason) ? "Unknown reason." : reason));
            }
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public String installApkUrl(String url) {
        BlockingReference<String> result = new BlockingReference<>();
        ActPackageInstaller.instance().installAppUrl(url, new ActPackageInstaller.Listener() {
            @Override
            public void postInstall(boolean success, String reason) {
                result.put(success ? null : (StringUtils.isEmpty(reason) ? "Unknown reason." : reason));
            }
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public void installApk(File[] files, Callback.C3<String,  // package name
            Boolean, // success
            String   // reason
            > callback) {
        InstallInfo info = new InstallInfo(MSG_INSTALL_APK, files, null, 0, callback);
        mInstallQueue.add(info);
        mInstallHandler.sendEmptyMessage(MSG_CHECK_TO_RUN_NEXT);
    }

    // https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/content/InstallApkSessionApi.java
    private void installApkInternal(File... files) {
        Logger.i(TAG, "Install apk " + ArrayUtils.toString(files));
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(MODE_FULL_INSTALL);
            int sessionId = getPackageManager().getPackageInstaller().createSession(params);
            PackageInstaller.Session session = getPackageManager().getPackageInstaller().openSession(sessionId);
            for (File file : files) {
                OutputStream output = session.openWrite(file.getName(), 0, file.length());
                InputStream input = FileUtils.openInputStream(file);
                IOUtils.copy(input, output);
                output.close();
                input.close();
            }
            Intent intent = new Intent(ACTION_INSTALL_APK);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pendingIntent.getIntentSender());
            session.close();
            Logger.d(TAG, "Install apk committed.");
        } catch (IOException e) {
            Logger.e(TAG, "Install apk exception", e);
            Message message = mInstallHandler.obtainMessage(MSG_INSTALL_END);
            message.arg1 = -100;
            message.obj = e.toString();
            message.sendToTarget();
        }
    }

    public String[] backupApp(String packageName) throws Throwable {
        PackageInfo info = getPackageInfo(packageName);
        File dir = new File(mContext.getDir("apk_backup", 0), packageName);
        Libsu.exec("rm -rf " + dir.getPath());
        FileUtils.forceMkdir(dir);
        List<String> paths = new ArrayList<>() {{
            add(info.applicationInfo.sourceDir);
            addAll(Arrays.asList(info.applicationInfo.splitSourceDirs != null ? info.applicationInfo.splitSourceDirs : new String[0]));
        }};
        List<String> toPaths = new ArrayList<>(paths.size());
        for (String path : paths) {
            String to = dir.getPath() + "/" + FilenameUtils.getName(path);
            Logger.v(TAG, "Backup apk " + path + " -> " + to);
            FileUtils.copyFile(new File(path), new File(to));
            toPaths.add(to);
        }
        return toPaths.toArray(String[]::new);
    }

    public String uninstallApp(String pkgName) {
        BlockingReference<String> result = new BlockingReference<>();
        uninstallApp(pkgName, (_pkgName, success, reason) -> {
            result.put(success ? null : (StringUtils.isEmpty(reason) ? "Unknown reason." : reason));
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public void uninstallApp(String pkgName, Callback.C3<String,
            Boolean, // True if success
            String   // reason
            > callback) {
        InstallInfo info = new InstallInfo(MSG_UNINSTALL_APP, null, pkgName, 0, callback);
        mInstallQueue.add(info);
        mInstallHandler.sendEmptyMessage(MSG_CHECK_TO_RUN_NEXT);
    }

    // services/core/java/com/android/server/pm/PackageManagerShellCommand.java
    private void uninstallAppInternal(String pkgName) {
        Logger.i(TAG, "Uninstall " + pkgName);
        Intent intent = new Intent(ACTION_UNINSTALL_APP);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        getPackageManager().getPackageInstaller().uninstall(pkgName, pendingIntent.getIntentSender());
        Logger.i(TAG, "Uninstall " + pkgName + " committed.");
    }

    public String clearApp(String pkgName, int profileId) {
        BlockingReference<String> result = new BlockingReference<>();
        clearApp(pkgName, profileId, (_pkgName, success, reason) -> {
            result.put(success ? null : (StringUtils.isEmpty(reason) ? "Unknown reason." : reason));
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public void clearApp(String pkgName, int profileId, Callback.C3<String,
            Boolean, // success
            String   // reason
            > callback) {
        InstallInfo info = new InstallInfo(MSG_CLEAR_APP, null, pkgName, profileId, callback);
        mInstallQueue.add(info);
        mInstallHandler.sendEmptyMessage(MSG_CHECK_TO_RUN_NEXT);
    }

    private void clearAppInternal(String pkgName, int profileId) {
        Logger.i(TAG, "Clear app " + pkgName + "-" + profileId);
        if (!isAppInstalled(pkgName)) {
            Logger.w(TAG, "Clear non-exist app data.");
            Message message = mInstallHandler.obtainMessage(MSG_CLEAR_END);
            message.arg1 = -3;
            message.obj = "App " + pkgName + " not exists.";
            message.sendToTarget();
            return;
        }

        ClipboardManager clipboardManager = (ClipboardManager) ActApp.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.setText(null);

        try {
            /*if (profileId == NewStage.instance().getPackageProfileId(pkgName)) {
                ReflectUtils.callMethod(mActivityManager, "clearApplicationUserData", new Object[]{
                        pkgName, new IPackageDataObserver.Stub() {
                    @Override
                    public void onRemoveCompleted(String packageName, boolean succeeded) {
                        Logger.i(TAG, "Clear app onRemoveCompleted " + packageName + ", " + succeeded);
                        Message message = mInstallHandler.obtainMessage(MSG_CLEAR_END);
                        message.arg1 = succeeded ? 0 : -1;
                        message.obj = succeeded ? "" : "Unknown reason, check logcat for more detail";
                        message.sendToTarget();
                    }
                }
                }, new Class[]{
                        String.class, IPackageDataObserver.class
                });
            } else {*/
                ManagedProfiles.instance.emptyAppData(pkgName, profileId);
                Message message = mInstallHandler.obtainMessage(MSG_CLEAR_END);
                message.arg1 = 0;
                message.obj = "";
                message.sendToTarget();
            //}
        } catch (Throwable e) {
            Logger.e(TAG, "clearApplicationUserData exception.", e);
            Message message = mInstallHandler.obtainMessage(MSG_CLEAR_END);
            message.arg1 = -2;
            message.obj = e.toString();
            message.sendToTarget();
        }
    }

    public String clearCache(String pkgName, int profileId) {
        BlockingReference<String> result = new BlockingReference<>();
        clearCache(pkgName, profileId, (_pkgName, success, reason) -> {
            result.put(success ? null : (StringUtils.isEmpty(reason) ? "Unknown reason." : reason));
        });
        try {
            return result.take();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }

    public void clearCache(String pkgName, int profileId, Callback.C3<String,
            Boolean, // success
            String   // reason
            > callback) {
        InstallInfo info = new InstallInfo(MSG_CLEAR_CACHE, null, pkgName, profileId, callback);
        mInstallQueue.add(info);
        mInstallHandler.sendEmptyMessage(MSG_CHECK_TO_RUN_NEXT);
    }

    private void clearCacheInternal(String pkgName, int profileId) {
        Logger.i(TAG, "Clear cache " + pkgName);
        if (!isAppInstalled(pkgName)) {
            Logger.w(TAG, "Clear non-exist app cache.");
            Message message = mInstallHandler.obtainMessage(MSG_CLEAR_CACHE_END);
            message.arg1 = -3;
            message.obj = "App " + pkgName + " not exists.";
            message.sendToTarget();
            return;
        }

        try {
            /*if (profileId == NewStage.instance().getPackageProfileId(pkgName)) {
                ReflectUtils.callMethod(mPackageManager, "deleteApplicationCacheFiles", new Object[]{
                        pkgName, new IPackageDataObserver.Stub() {
                    @Override
                    public void onRemoveCompleted(String packageName, boolean succeeded) {
                        Logger.i(TAG, "Clear cache onRemoveCompleted " + packageName + ", " + succeeded);
                        Message message = mInstallHandler.obtainMessage(MSG_CLEAR_CACHE_END);
                        message.arg1 = succeeded ? 0 : -1;
                        message.obj = succeeded ? "" : "Unknown reason, check logcat for more detail";
                        message.sendToTarget();
                    }
                }
                }, new Class[]{
                        String.class, IPackageDataObserver.class
                });
            } else {*/
                ManagedProfiles.instance.emptyAppCache(pkgName, profileId);
                Message message = mInstallHandler.obtainMessage(MSG_CLEAR_CACHE_END);
                message.arg1 = 0;
                message.obj = "";
                message.sendToTarget();
            //}
        } catch (Throwable e) {
            Logger.e(TAG, "deleteApplicationCacheFiles exception.", e);
            Message message = mInstallHandler.obtainMessage(MSG_CLEAR_CACHE_END);
            message.arg1 = -2;
            message.obj = e.toString();
            message.sendToTarget();
        }
    }

    public ActivityInfo getActivityInfo(ComponentName cn) throws PackageManager.NameNotFoundException {
        return mPackageManager.getActivityInfo(cn, 0);
    }

    public void revokeRuntimePermission(String packageName, String permName, UserHandle user) {
        try {
            Logger.v(TAG, "revokeRuntimePermission " + packageName + ", " + permName);
            ReflectUtils.callMethod(mPackageManager, "revokeRuntimePermission", packageName, permName, user);
            ReflectUtils.callMethod(mPackageManager, "updatePermissionFlags", new Object[] {
                    permName, packageName, 1 << 1 /* FLAG_PERMISSION_USER_FIXED */, 0, UserHandleHidden.of(0)
            }, new Class[] {
                    String.class, String.class, int.class, int.class, UserHandle.class
            });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void revokeRuntimePermissions(String packageName) {
        PackageInfo info = getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        for (int i = 0; i < info.requestedPermissions.length; ++i) {
            if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                try {
                    revokeRuntimePermission(packageName, info.requestedPermissions[i], UserHandleHidden.of(0));
                } catch (Throwable e) {
                }
            }
        }
    }

    public void grantRuntimePermission(String packageName, String permName, UserHandle user) {
        try {
            Logger.v(TAG, "grantRuntimePermission " + packageName + ", " + permName);
            ReflectUtils.callMethod(mPackageManager, "grantRuntimePermission", packageName, permName, user);
            ReflectUtils.callMethod(mPackageManager, "updatePermissionFlags", new Object[] {
                    permName, packageName, 1 << 1 /* FLAG_PERMISSION_USER_FIXED */, 0, UserHandleHidden.of(0)
            }, new Class[] {
                    String.class, String.class, int.class, int.class, UserHandle.class
            });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void resetRuntimePermissions(String packageName, List<String> allowPermissions) {
        PackageInfo info = getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        if(info == null || info.requestedPermissions  == null){
            Logger.e(TAG,"target "+packageName +"  packageInfo retrieved is null ,ignored resetRuntimePermissions");
            return;
        }
        for (int i = 0; i < info.requestedPermissions.length; ++i) {
            String permission = info.requestedPermissions[i];
            try {
                PermissionInfo permissionInfo = mPackageManager.getPermissionInfo(permission, 0);
                if ((permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS) {
                    if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        if (!allowPermissions.contains(permission)) {
                            revokeRuntimePermission(packageName, permission, UserHandleHidden.of(0));
                        }
                    } else {
                        if (allowPermissions.contains(permission)) {
                            grantRuntimePermission(packageName, permission, UserHandleHidden.of(0));
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            } catch (Throwable e) {
                Logger.e(TAG, "resetRuntimePermissions exception.", e);
            }
        }
    }
}