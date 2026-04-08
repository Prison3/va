package com.android.actor.device;

import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;
import com.topjohnwu.superuser.Shell;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class ManagedProfiles {

    private static final String TAG = ManagedProfiles.class.getSimpleName();
    public static final ManagedProfiles instance = new ManagedProfiles();

    private ManagedProfiles() {
        NewStage.instance().resetProfileData();
    }

    public int[] getAppProfileIds(String packageName) {
        int currentProfileId = NewStage.instance().getPackageProfileId(packageName);
        List<Integer> idList = new ArrayList<>();
        idList.add(currentProfileId);
        try {
            for (String name : Libsu.listNames("/data/data")) {
                if (name.startsWith(packageName + "_")) {
                    int id = Integer.parseInt(name.substring(packageName.length() + 1));
                    if (idList.contains(id)) {
                        continue;
                    }
                    idList.add(id);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return idList.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    public int createAppDataDir(String packageName) {
        int[] ids = getAppProfileIds(packageName);
        int profileId = ids[ids.length - 1] + 1;
        if (createAppDataDir(packageName, profileId)) {
            return profileId;
        }
        return -1;
    }

    public boolean createAppDataDir(String packageName, int profileId) {
        try {
            Logger.i(TAG, "Create app data dir " + packageName + '-' + profileId);
            if (profileId == 0) {
                throw new Exception("createAppData not accept profile 0");
            }
            if (ArrayUtils.contains(getAppProfileIds(packageName), profileId)) {
                return true;
            }

            int uid = ActPackageManager.getInstance().getUidOfPackage(packageName);
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

            String dataPath = "/data/data/" + packageName + "_" + profileId;
            String dePath = "/data/user_de/0/" + packageName + "_" + profileId;
            String sdPath = "/sdcard/Android/data/" + packageName + "_" + profileId;
            mkdir.apply(dePath);
            mkdir.apply(sdPath);
            mkdir.apply(dataPath);
            return true;
        } catch (Throwable e) {
            Logger.e(TAG, "Create app data exception.", e);
            return false;
        }
    }

    public void emptyAppData(String packageName, int profileId) throws Throwable {
        Logger.i(TAG, "Empty app data " + packageName + '-' + profileId);
        String dataPath = "/data/data/" + packageName;
        String dePath = "/data/user_de/0/" + packageName;
        String sdPath = "/sdcard/Android/data/" + packageName;
        if (profileId != NewStage.instance().getPackageProfileId(packageName)) {
            dataPath += "_" + profileId;
            dePath += "_" + profileId;
            sdPath += "_" + profileId;
        }
        ActActivityManager.getInstance().forceStopPackage(packageName);
        Shell.su("rm -rf " + dataPath + "/*").exec();
        Shell.su("rm -rf " + dataPath + "/.*").exec();
        Shell.su("rm -rf " + dePath + "/*").exec();
        Shell.su("rm -rf " + dePath + "/.*").exec();
        Shell.su("rm -rf " + sdPath + "/*").exec();
        Shell.su("rm -rf " + sdPath + "/.*").exec();
    }

    public void emptyAppCache(String packageName, int profileId) throws Throwable {
        Logger.i(TAG, "Empty app cache " + packageName + '-' + profileId);
        String dataPath = "/data/data/" + packageName;
        String dePath = "/data/user_de/0/" + packageName;
        String sdPath = "/sdcard/Android/data/" + packageName;
        if (profileId != NewStage.instance().getPackageProfileId(packageName)) {
            dataPath += "_" + profileId;
            dePath += "_" + profileId;
            sdPath += "_" + profileId;
        }
        Function<String, Void> empty = path -> {
            Shell.su("rm -rf " + path + "/cache/*").exec();
            Shell.su("rm -rf " + path + "/cache/.*").exec();
            Shell.su("rm -rf " + path + "/code_cache/*").exec();
            Shell.su("rm -rf " + path + "/code_cache/.*").exec();
            return null;
        };
        empty.apply(dataPath);
        empty.apply(dePath);
        empty.apply(sdPath);
    }

    public int getPackageProfileUid(String packageName, int profileId) {
        String path = "/data/data/" + packageName;
        if (profileId != NewStage.instance().getPackageProfileId(packageName)) {
            path += "_" + profileId;
        }
        try {
            return Libsu.getPathUid(path);
        } catch (Throwable e) {
            Logger.e(TAG, "getPackageProfileUid " + path, e);
            return -1;
        }
    }
}
