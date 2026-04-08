package com.android.va.base;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import java.lang.reflect.Field;

import me.weishu.reflection.Reflection;

import com.android.va.hook.content.ContentProviderDelegate;
import com.android.va.runtime.VHost;
import com.android.va.runtime.VEnvironment;
import com.android.va.runtime.VActivityThread;
import com.android.va.runtime.VNativeCore;
import com.android.va.system.SystemServer;
import com.android.va.runtime.VActivityManager;
import com.android.va.runtime.VPackageManager;
import com.android.va.hook.InjectorManager;
import com.android.va.utils.SimpleCrashFix;
import com.android.va.utils.StoragePermissionHelper;
import com.android.va.utils.BuildCompat;
import com.android.va.utils.StackTraceFilter;
import com.android.va.utils.SocialMediaAppCrashPrevention;
import com.android.va.utils.DexCrashPrevention;
import com.android.va.utils.NativeCrashPrevention;
import com.android.va.utils.CrashMonitor;
import com.android.va.utils.Logger;

public final class PrisonCore {
    public static final String TAG = PrisonCore.class.getSimpleName();
    private static final PrisonCore S_PRISON_CORE = new PrisonCore();
    private ProcessType.Type mProcessType;
    private Thread.UncaughtExceptionHandler mExceptionHandler;
    private Settings mSettings;
    private AppCallback mAppCallback;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private PrisonCore() {
    }

    public static PrisonCore get() {
        return S_PRISON_CORE;
    }
    public Handler getHandler() {
        return mHandler;
    }
    public Thread.UncaughtExceptionHandler getExceptionHandler() {
        return mExceptionHandler;
    }
    public void setExceptionHandler(Thread.UncaughtExceptionHandler exceptionHandler) {
        mExceptionHandler = exceptionHandler;
    }
    public void startUp(Context context, Settings settings, AppCallback appCallback) {
        VHost.get().attach(context);
        mSettings = settings;
        mAppCallback = appCallback;
        mProcessType = ProcessType.determineProcessType(VHost.getContext());

        try {
            SimpleCrashFix.installSimpleFix();
            StackTraceFilter.install();
            SocialMediaAppCrashPrevention.initialize();
            DexCrashPrevention.initialize();
            NativeCrashPrevention.initialize();
            CrashMonitor.initialize();
        } catch (Exception e) {
            Logger.w(TAG, "Failed to install simple crash fix or stack trace filter at class loading: " + e.getMessage());
        }

        setEssentialProperties(context);
        initNotificationManager();
        // Initialize VPN service for internet access
        initVpnService();

        if (isMainProcess()) {
            LogcatManager.get().init(VHost.getContext()).start();
        }
        if (isServerProcess() && settings.isEnableDaemonService()) {
            SystemServer.get().startDaemon();
        }
        if (isPrisonProcess()) {
            VEnvironment.load();
            InjectorManager.get().inject();
        }
    }

    /**
     * Initialize Prison core services
     * Includes: server process initialization, service manager initialization, ContentProvider proxy initialization, etc.
     */
    public void initializeServices() {
        try {
            SystemServer.get().ensureServerInitialized();
            SystemServer.get().warmupServices();
            VActivityThread.hookActivityThread();
            if (isPrisonProcess()) {
                ContentProviderDelegate.init();
            }
            if (!isServerProcess()) {
                // Initialize the ServiceManager to ensure services are available
                try {
                    SystemServer.get().warmupServices();
                } catch (Exception e) {
                    Logger.w(TAG, "Failed to initialize ServiceManager, continuing with fallback: " + e.getMessage());
                }
                
                // Reset transaction throttler on startup
                VPackageManager.get().resetTransactionThrottler();
            }

        } catch (Exception e) {     
            // Try to continue with fallback services
            try {
                if (!isServerProcess()) {
                    SystemServer.get().warmupServices();
                }
            } catch (Exception fallbackEx) {
                Logger.e(TAG, "Fallback initialization also failed", fallbackEx);
            }
        }
    }

    public void startActivity(Intent intent, int userId) {
        if (mSettings.isEnableLauncherActivity()) {
            VActivityManager.get().launch(intent, userId);
        } else {
            VActivityManager.get().startActivity(intent, userId);
        }
    }


    public boolean launchApk(String packageName, int userId) {
        mAppCallback.beforeMainLaunchApk(packageName,userId);
        // Check storage permissions on Android 11+ (SDK 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (! StoragePermissionHelper.hasAllFilesAccess()) {
                Logger.w(TAG, "All files access not granted for launching: " + packageName);
                if (mAppCallback.onStoragePermissionNeeded(packageName, userId)) {
                    // Host app will handle the permission request, cancel launch
                    Logger.d(TAG, "Launch cancelled - host app handling permission request");
                    return false;
                }
                // Otherwise, continue but warn
                Logger.w(TAG, "Launching without all files access - some file operations may fail");
            }
        }
        Intent launchIntentForPackage = VPackageManager.get().getLaunchIntentForPackage(packageName, userId);
        if (launchIntentForPackage == null) {
            return false;
        }
        startActivity(launchIntentForPackage, userId);
        return true;
    }


    public AppCallback getAppCallback() {
        return mAppCallback == null ? AppCallback.EMPTY : mAppCallback;
    }

    public boolean isPrisonProcess() {
        return mProcessType == ProcessType.Type.Prison;
    }

    public boolean isMainProcess() {
        return mProcessType == ProcessType.Type.Main;
    }

    public boolean isServerProcess() {
        return mProcessType == ProcessType.Type.Server;
    }

    public Settings  getSettings(){
        return mSettings;
    }

    private void initNotificationManager() {
        NotificationManager nm = (NotificationManager) VHost.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String CHANNEL_ONE_ID = VHost.getPackageName() + ".prison_core";
        String CHANNEL_ONE_NAME = "prison_core";
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ONE_ID, CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableLights(true);
        notificationChannel.setLightColor(Color.RED);
        notificationChannel.setShowBadge(true);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(notificationChannel);
    }

    /**
     * Set essential properties for the context
     */
    private void setEssentialProperties(Context context) {
        if(!VNativeCore.disableHiddenApi()){
            try {
                Reflection.unseal(context);
            } catch (Throwable t) {
                // Log detailed error information for debugging
                String androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
                String deviceInfo = Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.BRAND + ")";
                Logger.w(TAG, "Reflection.unseal failed on Android " + androidVersion + ", Device: " + deviceInfo + ": " + t.getMessage());
                if (t.getCause() != null) {
                    Logger.w(TAG, "Caused by: " + t.getCause().getClass().getSimpleName() + ": " + t.getCause().getMessage());
                }
                // This is not critical - the app can continue without unsealing
                // VNativeCore.disableHiddenApi() may have already handled it
            }
        }

        try {
            VNativeCore.disableResourceLoading();
        } catch (Exception e) {
            Logger.w(TAG, "Failed to call native resource disabling: " + e.getMessage());
        }
        
        // Set only essential system properties that don't require system permissions
        try {
            // Set properties to handle window management issues (these are usually allowed)
            System.setProperty("android.view.WindowManager.IGNORE_WINDOW_LEAKS", "true");
            System.setProperty("android.app.Activity.IGNORE_WINDOW_LEAKS", "true");
            System.setProperty("android.view.WindowManager.SUPPRESS_WINDOW_LEAK_WARNINGS", "true");
            
            // Try to disable overlay loading via reflection (safer than system properties)
            try {
                Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
                Field disableOverlayField = resourcesManagerClass.getDeclaredField("mDisableOverlayLoading");
                disableOverlayField.setAccessible(true);
                // This might not work, but worth trying
            } catch (Exception e) {
                Logger.w(TAG, "Could not access ResourcesManager overlay field: " + e.getMessage());
            }
            
            // Try to disable window leak warnings via reflection
            try {
                Field ignoreLeaksField = WindowManager.class.getDeclaredField("mIgnoreWindowLeaks");
                ignoreLeaksField.setAccessible(true);
                // This might not work, but worth trying
            } catch (Exception e) {
                Logger.w(TAG, "Could not access WindowManager leak field: " + e.getMessage());
            }
        } catch (Exception e) {
            Logger.w(TAG, "Failed to set essential properties: " + e.getMessage());
        }
    }
    
    /**
     * Initialize VPN service for internet access
     */
    private void initVpnService() {
        try {
            // Start the VPN service asynchronously to prevent blocking main thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Start the VPN service to ensure internet access works
                        Intent vpnIntent = new Intent(VHost.getContext(), com.android.va.proxy.ProxyVpnService.class);
                        vpnIntent.setAction("android.net.VpnService");
                        
                        if (BuildCompat.isOreo()) {
                            VHost.getContext().startForegroundService(vpnIntent);
                        } else {
                            VHost.getContext().startService(vpnIntent);
                        }
                        
                        Logger.d(TAG, "VPN service started successfully for internet access");
                    } catch (Exception e) {
                        Logger.w(TAG, "Failed to start VPN service: " + e.getMessage());
                        // Don't fail initialization if VPN service fails
                        // The app can still work without VPN, just with limited network access
                    }
                }
            }, "VPNServiceInit").start();
            
        } catch (Exception e) {
            Logger.w(TAG, "Failed to initialize VPN service: " + e.getMessage());
            // Don't fail initialization if VPN service fails
        }
    }
    


    public void addServiceAvailableCallback(Runnable callback) {
        SystemServer.get().addServiceAvailableCallback(callback);
    }

    public boolean isPrisonApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        return packageName.equals(VHost.getPackageName());
    }
}
