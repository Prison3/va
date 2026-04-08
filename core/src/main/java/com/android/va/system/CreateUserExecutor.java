package com.android.va.system;

import com.android.va.runtime.VEnvironment;
import com.android.va.model.VPackageSettings;
import com.android.va.model.InstallOption;
import com.android.va.utils.FileUtils;

public class CreateUserExecutor implements Executor {

    @Override
    public int exec(VPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        FileUtils.deleteDir(VEnvironment.getDataLibDir(packageName, userId));

        // create user dir
        FileUtils.mkdirs(VEnvironment.getDataDir(packageName, userId));
        FileUtils.mkdirs(VEnvironment.getDataCacheDir(packageName, userId));
        FileUtils.mkdirs(VEnvironment.getDataFilesDir(packageName, userId));
        FileUtils.mkdirs(VEnvironment.getDataDatabasesDir(packageName, userId));
        FileUtils.mkdirs(VEnvironment.getDeDataDir(packageName, userId));

//        try {
//            // /data/data/xx/lib -> /data/app/xx/lib
//            FileUtils.createSymlink(VEnvironment.getAppLibDir(ps.pkg.packageName).getAbsolutePath(), VEnvironment.getDataLibDir(packageName, userId).getAbsolutePath());
//        } catch (Exception e) {
//            e.printStackTrace();
//            return -1;
//        }
        return 0;
    }
}
