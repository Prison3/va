package com.android.va.system;

import com.android.va.runtime.VEnvironment;
import com.android.va.model.VPackageSettings;
import com.android.va.model.InstallOption;
import com.android.va.utils.FileUtils;

public class RemoveAppExecutor implements Executor {
    @Override
    public int exec(VPackageSettings ps, InstallOption option, int userId) {
        FileUtils.deleteDir(VEnvironment.getAppDir(ps.pkg.packageName));
        return 0;
    }
}
