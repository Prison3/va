package com.android.va.system;

import com.android.va.runtime.VHost;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.va.runtime.VRuntime;
import com.android.va.runtime.VAppSystemEnv;
import com.android.va.runtime.VEnvironment;
import com.android.va.model.InstallOption;

public class ServiceManager {
    private static ServiceManager sServiceManager = null;
    public static final String ACTIVITY_MANAGER = "activity_manager";
    public static final String JOB_MANAGER = "job_manager";
    public static final String PACKAGE_MANAGER = "package_manager";
    public static final String STORAGE_MANAGER = "storage_manager";
    public static final String USER_MANAGER = "user_manager";
    public static final String ACCOUNT_MANAGER = "account_manager";
    public static final String LOCATION_MANAGER = "location_manager";
    public static final String NOTIFICATION_MANAGER = "notification_manager";

    private final Map<String, IBinder> mCaches = new HashMap<>();
    private final List<ISystemService> mServices = new ArrayList<>();
    private final static AtomicBoolean isStartup = new AtomicBoolean(false);

    public static ServiceManager get() {
        if (sServiceManager == null) {
            synchronized (ServiceManager.class) {
                if (sServiceManager == null) {
                    sServiceManager = new ServiceManager();
                }
            }
        }
        return sServiceManager;
    }

    public static IBinder getService(String name) {
        return get().getServiceInternal(name);
    }

    private ServiceManager() {
        mCaches.put(ACTIVITY_MANAGER, ActivityManagerService.get());
        mCaches.put(JOB_MANAGER, JobManagerService.get());
        mCaches.put(PACKAGE_MANAGER, PackageManagerService.get());
        mCaches.put(STORAGE_MANAGER, StorageManagerService.get());
        mCaches.put(USER_MANAGER, ProfileManagerService.get());
        mCaches.put(ACCOUNT_MANAGER, AccountManagerService.get());
        mCaches.put(LOCATION_MANAGER, LocationManagerService.get());
        mCaches.put(NOTIFICATION_MANAGER, NotificationManagerService.get());
    }

    public IBinder getServiceInternal(String name) {
        return mCaches.get(name);
    }

    /**
     * Start up all system services and initialize the VA system.
     * This method should be called once during system initialization.
     */
    public void startup() {
        if (isStartup.getAndSet(true)) {
            return;
        }
        VEnvironment.load();

        mServices.add(PackageManagerService.get());
        mServices.add(ProfileManagerService.get());
        mServices.add(ActivityManagerService.get());
        mServices.add(JobManagerService.get());
        mServices.add(StorageManagerService.get());
        mServices.add(PackageInstallerService.get());
        mServices.add(ProcessManagerService.get());
        mServices.add(AccountManagerService.get());
        mServices.add(LocationManagerService.get());
        mServices.add(NotificationManagerService.get());

        for (ISystemService service : mServices) {
            service.systemReady();
        }

        List<String> preInstallPackages = VAppSystemEnv.getPreInstallPackages();
        for (String preInstallPackage : preInstallPackages) {
            try {
                if (!PackageManagerService.get().isInstalled(preInstallPackage, VUserHandle.USER_ALL)) {
                    PackageInfo packageInfo = VHost.getContext().getPackageManager().getPackageInfo(preInstallPackage, 0);
                    PackageManagerService.get().installPackageAsUser(packageInfo.applicationInfo.sourceDir, InstallOption.installBySystem(), VUserHandle.USER_ALL);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        // Initialize JAR environment using improved JarManager
        JarManager.getInstance().initializeAsync();
    }
}
