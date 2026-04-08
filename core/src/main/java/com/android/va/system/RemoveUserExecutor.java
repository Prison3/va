package com.android.va.system;

import com.android.va.runtime.VEnvironment;
import com.android.va.model.VPackageSettings;
import com.android.va.model.InstallOption;
import com.android.va.utils.FileUtils;

public class RemoveUserExecutor implements Executor {

    @Override
    public int exec(VPackageSettings ps, InstallOption option, int userId) {
        String packageName = ps.pkg.packageName;
        // delete user dir
        FileUtils.deleteDir(VEnvironment.getDataDir(packageName, userId));
        FileUtils.deleteDir(VEnvironment.getDeDataDir(packageName, userId));
        FileUtils.deleteDir(VEnvironment.getExternalDataDir(packageName, userId));
        return 0;
    }
}
