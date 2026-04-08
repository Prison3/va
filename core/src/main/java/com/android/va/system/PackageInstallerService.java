package com.android.va.system;

import java.util.ArrayList;
import com.android.va.utils.Logger;
import java.util.List;

import com.android.va.system.ISystemService;
import com.android.va.system.IPackageInstallerService;
import com.android.va.model.VPackageSettings;
import com.android.va.system.CopyExecutor;
import com.android.va.system.CreatePackageExecutor;
import com.android.va.system.CreateUserExecutor;
import com.android.va.system.Executor;
import com.android.va.system.RemoveAppExecutor;
import com.android.va.system.RemoveUserExecutor;
import com.android.va.model.InstallOption;

public class PackageInstallerService extends IPackageInstallerService.Stub implements ISystemService {
    private static final PackageInstallerService sService = new PackageInstallerService();

    public static PackageInstallerService get() {
        return sService;
    }

    public static final String TAG = PackageInstallerService.class.getSimpleName();

    @Override
    public int installPackageAsUser(VPackageSettings ps, int userId) {
        List<Executor> executors = new ArrayList<>();
        // 创建用户环境相关操作
        executors.add(new CreateUserExecutor());
        // 创建应用环境相关操作
        executors.add(new CreatePackageExecutor());
        // 拷贝应用相关文件
        executors.add(new CopyExecutor());
        InstallOption option = ps.installOption;
        for (Executor executor : executors) {
            int exec = executor.exec(ps, option, userId);
            Logger.d(TAG, "installPackageAsUser: " + executor.getClass().getSimpleName() + " exec: " + exec);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public int uninstallPackageAsUser(VPackageSettings ps, boolean removeApp, int userId) {
        List<Executor> executors = new ArrayList<>();
        if (removeApp) {
            // 移除App
            executors.add(new RemoveAppExecutor());
        }
        // 移除用户相关目录
        executors.add(new RemoveUserExecutor());
        InstallOption option = ps.installOption;
        for (Executor executor : executors) {
            int exec = executor.exec(ps, option, userId);
            Logger.d(TAG, "uninstallPackageAsUser: " + executor.getClass().getSimpleName() + " exec: " + exec);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public int clearPackage(VPackageSettings ps, int userId) {
        List<Executor> executors = new ArrayList<>();
        // 移除用户相关目录
        executors.add(new RemoveUserExecutor());
        // 创建用户环境相关操作
        executors.add(new CreateUserExecutor());
        InstallOption option = ps.installOption;
        for (Executor executor : executors) {
            int exec = executor.exec(ps, option, userId);
            Logger.d(TAG, "uninstallPackageAsUser: " + executor.getClass().getSimpleName() + " exec: " + exec);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public int updatePackage(VPackageSettings ps) {
        List<Executor> executors = new ArrayList<>();
        executors.add(new CreatePackageExecutor());
        executors.add(new CopyExecutor());
        InstallOption option = ps.installOption;
        for (Executor executor : executors) {
            int exec = executor.exec(ps, option, -1);
            if (exec != 0) {
                return exec;
            }
        }
        return 0;
    }

    @Override
    public void systemReady() {

    }
}
