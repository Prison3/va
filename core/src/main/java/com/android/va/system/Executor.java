package com.android.va.system;

import com.android.va.model.VPackageSettings;
import com.android.va.model.InstallOption;

public interface Executor {
    public static final String TAG = Executor.class.getSimpleName();

    int exec(VPackageSettings ps, InstallOption option, int userId);
}
