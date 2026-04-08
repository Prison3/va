package com.android.actor.device;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.system.Os;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActBroadcastReceiver;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageInstaller;
import com.android.actor.control.AppPermissions;
import com.android.actor.utils.*;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.shell.Libsu;
import com.android.actor.utils.shell.Shell;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;
import com.android.internal.ds.IDexCallback;
import com.android.internal.os.IMyActionListener;
import com.android.actor.ActApp;
import com.android.actor.control.RocketComponent;
import com.android.actor.control.ActPackageManager;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.android.internal.os.INewStage;
import com.android.internal.os.Parameters;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class NewStage {

    private static final String TAG = NewStage.class.getSimpleName();
    public static final String PARAMETERS_PATH = "/data/new_stage";
    private static final String[] REINSTALL_APPS = new String[]{
            //"a.test",
            //"com.zzkko",
            "com.xingin.xhs"
    };
    private INewStage mService;
    private static NewStage sInstance;
    private boolean mProfileDataReset = false;

    public NewStage(Context context, INewStage service) {
        mService = service;
    }

    public static NewStage instance() {
        if (sInstance == null) {
            IBinder binder = ServiceManager.getService("new_stage");
            INewStage newStage;
            if (binder == null) {
                Logger.e(TAG, "Can't get new_stage service.");
                newStage = new INewStage.Default();
            } else {
                newStage = INewStage.Stub.asInterface(binder);
            }
            sInstance = new NewStage(ActApp.getInstance(), newStage);
        }
        return sInstance;
    }

    public void selfCheck() {
        new Handler().postDelayed(() -> {
            Logger.d(TAG, "Device mock run self check.");
            if (!isReady()) {
                GRPCManager.getInstance().sendNotification("error", "Device mock is still no ready after 30s.");
            }
        }, 30000);
    }

    public boolean isReady() {
        try {
            return mService.isReady();
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
            return false;
        }
    }

    public void registerAppStarted(RequestV0 info, IMyActionListener listener) {
        try {
            mService.registerAppStarted(info, listener);
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
        }
    }

    public void registerScriptCallback(IDexCallback callback) {
        try {
            mService.registerScriptCallback(callback);
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
        }
    }

    public ResponseV0 requestAppAction(RequestV0 request) {
        try {
            return mService.requestAppAction(request);
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
            return null;
        }
    }

    public String modify(String packageName, int profileId, String parameters) {
        try {
            JSONObject jConfig = FlashConfig.readAsJson(FlashConfig.LIMITED_EDITION);
            if (jConfig != null) {
                int maxMockCount = jConfig.getIntValue("max_mock_count");
                int mockCount = SPUtils.getInt(SPUtils.ModuleFile.mocked_device, "mock_count");
                if (maxMockCount > 0 && mockCount > maxMockCount) {
                    return "Reach limit.";
                }
                SPUtils.putInt(SPUtils.ModuleFile.mocked_device, "mock_count", mockCount + 1);
            }
            rmPropertiesNew(packageName, profileId);
            File dir = new File("/sdcard/Android/data/" + packageName);
            if (!dir.exists()) {
                Shell.execRootCmd("mkdir " + dir.getPath());
            }

            JSONObject jParameters = JSON.parseObject(parameters);
            String country = jParameters.getString("country");
            String model = jParameters.getString("_model");
            NetUtils.metricCounter("new_stage_mock", packageName, country, model);
            String msg = mService.modify(packageName, profileId, new Parameters(parameters));
            if (msg != null) {
                return msg;
            }

            AppPermissions.instance.apply(packageName);
            cleanSdcard();
            String currentDate = TimeFormatUtils.getCurrentDateStr();

            Logger.d(TAG, "Collect daily info for " + packageName + " " + currentDate);
            String collectedInfo = SPUtils.getString(SPUtils.ModuleFile.daily_info, packageName);
            // 解析已收集的数据，如果为空则创建一个新的 JSON 对象
            JSONObject collectedInfoJson = collectedInfo != null && !collectedInfo.isEmpty() ?
                    JSONObject.parseObject(collectedInfo) : new JSONObject();

            // 获取当前日期的数据对象，如果不存在则创建一个新的 JSON 对象
            JSONObject dataObject = collectedInfoJson.getJSONObject(currentDate);
            if (dataObject == null) {
                dataObject = new JSONObject();
            }
            // 更新数据对象中特定动作的计数
            Integer count = dataObject.getInteger("daily_device_mock_count");
            dataObject.put("daily_device_mock_count", count != null ? count + 1 : 1);
            // 将更新后的数据对象放回已收集的数据中
            collectedInfoJson.put(currentDate, dataObject);

            // 将更新后的数据存储回 SharedPreferences
            SPUtils.putString(SPUtils.ModuleFile.daily_info, packageName, collectedInfoJson.toJSONString());

            return null;
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
            return e.toString();
        }
    }


    public String modifyWebview(String packageName, int profileId, String parameters) {
        try {
            rmPropertiesNew(packageName, profileId);

            JSONObject jParameters = JSON.parseObject(parameters);
            String country = jParameters.getString("country");
            String model = jParameters.getString("_model");
            NetUtils.metricCounter("new_stage_mock_webview", packageName, country, model);
            return mService.modifyWebview(packageName, profileId, new Parameters(parameters));
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
            return e.toString();
        }
    }

    public String reset(String packageName, int profileId) {
        try {
            rmPropertiesNew(packageName, profileId);
            return mService.reset(packageName, profileId);
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
            return null;
        }
    }

    public String resetByUid(int uid, int profileId) {
        try {
            rmPropertiesNew(uid, profileId);
            return mService.resetByUid(uid, profileId);
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
            return null;
        }
    }

    private void rmPropertiesNew(String packageName, int profileId) throws Throwable {
        rmPropertiesNew(ActPackageManager.getInstance().getUidOfPackage(packageName), profileId);
    }

    private void rmPropertiesNew(int uid, int profileId) throws Throwable {
        Shell.execRootCmd("rm -rf /dev/__properties__new/" + uid + "/" + profileId);
    }

    public List<String> getModifiedPackages() {
        try {
            return mService.getModifiedPackages();
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
            return null;
        }
    }

    public String getPackageParameters(String packageName) {
        try {
            return mService.getPackageParameters(packageName);
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
            return null;
        }
    }

    public String startActivityAndWait(Intent intent) {
        try {
            return mService.startActivityAndWait(intent);
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
            return e.toString();
        }
    }

    public String getCurrentActivity() {
        try {
            return mService.getCurrentActivity();
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
            return null;
        }
    }

    public void setBrightness(int brightness) {
        try {
            mService.setBrightness(brightness);
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
        }
    }

    public String getPackageParameterValue(String packageName, String key) {
        try {
            return mService.getPackageParameterValue(packageName, key);
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
        }
        return null;
    }

    public String getPackageSystemProperty(String packageName, String name) {
        try {
            return mService.getPackageSystemProperty(packageName, name);
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
        }
        return null;
    }

    public void reinstallApp(String packageName, Callback.C1<String> callback) {
        new Thread(() -> {
            String reason;
            try {
                String[] paths = ActPackageManager.getInstance().backupApp(packageName);
                ActPackageManager.getInstance().uninstallApp(packageName);
                reason = ActPackageInstaller.instance().installAppFile(paths);
            } catch (Throwable e) {
                Logger.e(TAG, "Reinstall app exception.", e);
                reason = e.toString();
            }
            callback.onResult(reason);
        }).start();
    }

    public void reinstallAppForMock(String packageName, Callback.C1<String> callback) {
        if (ArrayUtils.contains(REINSTALL_APPS, packageName)) {
            reinstallApp(packageName, callback);
        } else {
            int uid = ActPackageManager.getInstance().getUid(packageName);
            Function<String, Void> mkdir = path -> {
                try {
                    String tmpPath = path + ".tmp";
                    if (Libsu.exists(tmpPath)) {
                        Libsu.exec("rm -rf " + tmpPath);
                    }
                    Libsu.mkdir(tmpPath);
                    if (!path.startsWith("/sdcard")) {
                        Libsu.exec("chmod 700 " + tmpPath);
                        Libsu.exec("chcon u:object_r:app_data_file:s0 " + tmpPath);
                        Libsu.exec("chown " + uid + ":" + uid + " " + tmpPath);
                    }
                    Libsu.rename(tmpPath, path);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
                return null;
            };

            String dataPath = "/data/data/" + packageName;
            String dePath = "/data/user_de/0/" + packageName;
            String sdPath = "/sdcard/Android/data/" + packageName;
            mkdir.apply(dePath);
            mkdir.apply(sdPath);
            mkdir.apply(dataPath);
            callback.onResult(null);
        }
    }

    public int getPackageProfileId(String packageName) {
        try {
            int uid = ActPackageManager.getInstance().getUid(packageName);
            if (uid >= 10000) {
                return getPackageProfileId(uid);
            }
            return 0;
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
            return 0;
        }
    }

    public int getPackageProfileId(int uid) {
        try {
            return mService.getPackageProfileId(uid);
        } catch (RemoteException e) {
            Logger.stackTrace(TAG, e);
        }
        return 0;
    }

    public void switchProfileAsync(String packageName, int profileId, Callback.C1<String> callback) {
        new Thread(() -> {
            String reason = switchProfile(packageName, profileId, null);
            callback.onResult(reason);
        }).start();
    }

    public String switchProfile(String packageName, int profileId, Callback.C1<Integer> progressCallback) {
        try {
            Function<Integer, Void> progress = n -> {
                if (progressCallback != null) {
                    progressCallback.onResult(n);
                }
                return null;
            };

            progress.apply(1);
            int currentUid = ActPackageManager.getInstance().getUidOfPackage(packageName);
            int currentProfileId = getPackageProfileId(currentUid);
            Logger.d(TAG, "Switch profile " + packageName + " " + currentProfileId + " -> " + profileId);
            if (currentProfileId == profileId) {
                return null;
            }

            progress.apply(10);
            ActActivityManager.getInstance().forceStopPackage(packageName);
            moveDataBeforeUninstall(packageName, currentProfileId);
            int uid = ManagedProfiles.instance.getPackageProfileUid(packageName, profileId);
            if (uid < 10000) {
                return "getPackageProfileUid " + packageName + "-" + profileId + " < 10000.";
            }
            if (uid != currentUid) {
                mService.assignPackageUid(packageName, uid);
                progress.apply(20);

                PackageInfo info = ActPackageManager.getInstance().getPackageInfo(packageName);
                List<String> paths = new ArrayList<>() {{
                    add(info.applicationInfo.sourceDir);
                    addAll(Arrays.asList(info.applicationInfo.splitSourceDirs != null ? info.applicationInfo.splitSourceDirs : new String[0]));
                }};
                int mb = (int) (paths.stream().mapToLong(path -> new File(path).length()).sum() / 1024 / 1024);
                int seconds = mb / (200 / 20);
                Logger.d(TAG, "Expect " + seconds + "s to reinstall.");
                float fProgress = 20f;
                float step = 60f / seconds;

                BlockingReference<String> blockingReference = new BlockingReference();
                reinstallApp(packageName, reason -> {
                    blockingReference.put(reason);
                });
                while (!blockingReference.hasPut()) {
                    fProgress += step;
                    if (fProgress < 80f) {
                        progress.apply((int) fProgress);
                    }
                    Thread.sleep(1000);
                }

                String reason = blockingReference.take();
                if (reason != null) {
                    return reason;
                }
                int newUid = ActPackageManager.getInstance().getUidOfPackage(packageName);
                if (uid != newUid) {
                    return "Expect uid " + uid + ", but installed package uid " + newUid;
                }
            }

            progress.apply(80);
            moveDataAfterReinstall(packageName, profileId);
            mService.switchProfile(uid, profileId);

            progress.apply(90);
            AppPermissions.instance.apply(packageName);
            cleanSdcard();
            progress.apply(100);
            return null;
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
            return e.toString();
        }
    }

    public void switchProfileForMockAsync(String packageName, int profileId, Callback.C1<String> callback) {
        new Thread(() -> {
            String reason = switchProfileForMock(packageName, profileId);
            callback.onResult(reason);
        }).start();
    }

    public String switchProfileForMock(String packageName, int profileId) {
        if (!ArrayUtils.contains(REINSTALL_APPS, packageName)) {
            return null;
        }
        try {
            Logger.d(TAG, "Switch profile for mock, " + packageName + " " + profileId);
            ActActivityManager.getInstance().forceStopPackage(packageName);
            int uid = ManagedProfiles.instance.getPackageProfileUid(packageName, profileId);
            resetByUid(uid, profileId);

            uid = ActPackageManager.getInstance().getUidOfPackage(packageName);
            int currentProfileId = getPackageProfileId(uid);
            Logger.d(TAG, " current " + uid + ", currentProfileId " + currentProfileId);

            moveDataBeforeUninstall(packageName, currentProfileId);
            BlockingReference<String> blockingReference = new BlockingReference();
            reinstallAppForMock(packageName, reason -> {
                blockingReference.put(reason);
            });
            String reason = blockingReference.take();
            if (reason != null) {
                return reason;
            }
            uid = ActPackageManager.getInstance().getUidOfPackage(packageName);

            deleteDataAfterReinstall(packageName, profileId);
            mService.switchProfile(uid, profileId);
            ProxyManager.getInstance().updateProxyUid(packageName, profileId);

            cleanSdcard();
            return null;
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
            return e.toString();
        }
    }

    private void cleanSdcard() {
        Libsu.exec("rm -rf /sdcard/Download/*");
        Libsu.exec("rm -rf /sdcard/Download/.*");
        Libsu.exec("rm -rf /sdcard/Pictures/*");
        Libsu.exec("rm -rf /sdcard/Pictures/.*");
        Libsu.exec("rm -rf /sdcard/Movies/*");
        Libsu.exec("rm -rf /sdcard/Movies/.*");
        Libsu.exec("rm -rf /sdcard/Music/*");
        Libsu.exec("rm -rf /sdcard/Music/.*");
        Libsu.exec("rm -rf /sdcard/DCIM/*");
        Libsu.exec("rm -rf /sdcard/DCIM/.*");
        Libsu.exec("mkdir /sdcard/DCIM/Camera");
        ResUtils.updateMedia("/sdcard/Download");
        ResUtils.updateMedia("/sdcard/Pictures");
        ResUtils.updateMedia("/sdcard/Movies");
        ResUtils.updateMedia("/sdcard/Music");
        ResUtils.updateMedia("/sdcard/DCIM");
    }

    public void resetProfileData() {
        try {
            if (mProfileDataReset) {
                return;
            }
            Logger.d(TAG, "Check and reset profile data.");
            mProfileDataReset = true;
            for (PackageInfo info : ActPackageManager.getInstance().getInstalledPackage()) {
                String[] names = Libsu.listNames("/data/data");
                List<Integer> idList = new ArrayList<>();
                for (String name : names) {
                    if (name.startsWith(info.packageName + "_")) {
                        idList.add(Integer.parseInt(name.substring(info.packageName.length() + 1)));
                    }
                }
                int size;
                if (!idList.contains(0)) {
                    idList.add(0);
                    size = idList.size();
                } else {
                    size = idList.size() + 1;
                }
                int missingId = 0;
                //Logger.d(TAG, "idList " + info.packageName + ", " + ArrayUtils.toString(idList) + ", size " + size);
                for (int i = 0; i < size; ++i) {
                    if (!idList.contains(i)) {
                        missingId = i;
                        break;
                    }
                }
                int profileId = getPackageProfileId(info.packageName);
                if (missingId != profileId) {
                    Logger.d(TAG, "Reset profile data, " + info.packageName + ", current " + missingId + ", real " + profileId);
                    ActActivityManager.getInstance().forceStopPackage(info.packageName);
                    //moveData(info.packageName, missingId, profileId);
                    mService.switchProfile(info.applicationInfo.uid, missingId);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void moveDataBeforeUninstall(String packageName, int currentProfileId) {
        Logger.d(TAG, "moveDataBeforeUninstall " + packageName + ", currentProfileId " + currentProfileId);
        listDataData(packageName);
        String from = "/sdcard/Android/data/" + packageName;
        String to = from + "_" + currentProfileId;
        Libsu.exec("mv " + from + " " + to);
        from = "/data/user_de/0/" + packageName;
        to = from + "_" + currentProfileId;
        Libsu.exec("mv " + from + " " + to);
        from = "/data/data/" + packageName;
        to = from + "_" + currentProfileId;
        Libsu.exec("mv " + from + " " + to);
        listDataData(packageName);
    }

    private void moveDataAfterReinstall(String packageName, int profileId) {
        Logger.d(TAG, "moveDataAfterReinstall " + packageName + ", profileId " + profileId);
        listDataData(packageName);
        String path = "/sdcard/Android/data/" + packageName;
        Libsu.exec("rm -rf " + path);
        path = "/data/user_de/0/" + packageName;
        Libsu.exec("rm -rf " + path);
        path = "/data/data/" + packageName;
        Libsu.exec("rm -rf " + path);

        String from = "/sdcard/Android/data/" + packageName + "_" + profileId;
        String to = "/sdcard/Android/data/" + packageName;
        Libsu.exec("mv " + from + " " + to);
        to = "/data/user_de/0/" + packageName;
        from = to + "_" + profileId;
        Libsu.exec("mv " + from + " " + to);
        to = "/data/data/" + packageName;
        from = to + "_" + profileId;
        Libsu.exec("mv " + from + " " + to);
        listDataData(packageName);
    }

    private void deleteDataAfterReinstall(String packageName, int profileId) {
        Logger.d(TAG, "deleteDataAfterReinstall " + packageName + ", profileId " + profileId);
        listDataData(packageName);
        String path = "/sdcard/Android/data/" + packageName + "_" + profileId;
        Libsu.exec("rm -rf " + path);
        path = "/data/user_de/0/" + packageName + "_" + profileId;
        Libsu.exec("rm -rf " + path);
        path = "/data/data/" + packageName + "_" + profileId;
        Libsu.exec("rm -rf " + path);
        listDataData(packageName);
    }

    private void moveData(String packageName, int currentProfileId, int profileId) {
        Logger.d(TAG, "moveData " + packageName + ", " + currentProfileId + " -> " + profileId);
        listDataData(packageName);
        String from = "/sdcard/Android/data/" + packageName;
        String to = from + "_" + currentProfileId;
        Libsu.exec("mv " + from + " " + to);
        from = "/data/user_de/0/" + packageName;
        to = from + "_" + currentProfileId;
        Libsu.exec("mv " + from + " " + to);
        from = "/data/data/" + packageName;
        to = from + "_" + currentProfileId;
        Libsu.exec("mv " + from + " " + to);

        from = "/sdcard/Android/data/" + packageName + "_" + profileId;
        to = "/sdcard/Android/data/" + packageName;
        Libsu.exec("mv " + from + " " + to);
        to = "/data/user_de/0/" + packageName;
        from = to + "_" + profileId;
        Libsu.exec("mv " + from + " " + to);
        to = "/data/data/" + packageName;
        from = to + "_" + profileId;
        Libsu.exec("mv " + from + " " + to);
        listDataData(packageName);
    }

    private void listDataData(String packageName) {
        com.topjohnwu.superuser.Shell.Result result = Libsu.exec("ls -l /data/data | grep " + packageName);
        Logger.d(TAG, "\n -- listDataData --\n " + StringUtils.join(result.getOut(), "\n ") + "\n ");
    }


    public static int getRomVersion() {
        return SystemProperties.getInt("ro.build.version.incremental", 0);
    }

    public static int getNewStageVersion() {
        return ActPackageManager.getInstance().getPackageInfo(RocketComponent.PKG_NEW_STAGE).versionCode;
    }

    public void inputText(String text) {
        try {
            mService.inputText(text);
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
        }
    }

    public void inputAction(int action) {
        try {
            mService.inputAction(action);
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
        }
    }

    public void deleteSurroundingText(int beforeLength, int afterLength) {
        try {
            mService.deleteSurroundingText(beforeLength, afterLength);
        } catch (Throwable e) {
            Logger.stackTrace(TAG, e);
        }
    }
}
