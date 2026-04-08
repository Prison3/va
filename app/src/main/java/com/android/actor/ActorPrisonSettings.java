package com.android.actor;

import android.content.Context;

import com.android.va.base.Settings;

import java.io.File;

/**
 * 与 prison {@code FoxRiver#createSettings} 对齐的宿主配置。
 * 须在 {@link android.app.Application#attachBaseContext} 阶段可用，因此持有 {@link Context}，不依赖 {@link ActApp#getInstance()}。
 */
public final class ActorPrisonSettings extends Settings {

    private final Context mHostContext;

    public ActorPrisonSettings(Context host) {
        mHostContext = host.getApplicationContext();
    }

    @Override
    public String getHostPackageName() {
        return mHostContext.getPackageName();
    }

    @Override
    public boolean requestInstallPackage(File file, int userId) {
        if (file == null) {
            return false;
        }
        try {
            mHostContext.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
        } catch (Throwable ignored) {
        }
        return false;
    }
}
