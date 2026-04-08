package com.android.va.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.va.base.AppCallback;
import com.android.va.system.ServiceManager;
import com.android.va.system.IActivityManagerService;
import com.android.va.model.AppConfig;
import com.android.va.model.UnbindRecord;
import com.android.va.model.PendingResultData;
import com.android.va.model.RunningAppProcessInfo;
import com.android.va.model.RunningServiceInfo;
import com.android.va.utils.Logger;
import com.android.va.utils.StoragePermissionHelper;

public class VActivityManager extends VManager<IActivityManagerService> {
    private static final String TAG = VActivityManager.class.getSimpleName();
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;
    private static final long PROGRESSIVE_DELAY_MS = 200;
    
    private final Context mContext;
    private static final VActivityManager sVActivityManager = new VActivityManager();

    private VActivityManager() {
        mContext = VHost.getContext();
    }

    public static VActivityManager get() {
        return sVActivityManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.ACTIVITY_MANAGER;
    }

    /**
     * Execute a service call with retry logic
     */
    private <T> T executeWithRetry(ServiceCallable<T> callable, String operationName, T defaultValue) {
        int retryCount = 0;
        
        while (retryCount < MAX_RETRIES) {
            try {
                IActivityManagerService service = getService();
                if (service != null) {
                    T result = callable.call(service);
                    if (result != null || !callable.isNullable()) {
                        return result;
                    }
                    Logger.w(TAG, operationName + " returned null, retry " + (retryCount + 1) + "/" + MAX_RETRIES);
                } else {
                    Logger.w(TAG, "ActivityManager service is null for " + operationName + ", retry " + (retryCount + 1) + "/" + MAX_RETRIES);
                }
            } catch (DeadObjectException e) {
                Logger.w(TAG, "ActivityManager service died during " + operationName + ", clearing cache and retrying " + (retryCount + 1) + "/" + MAX_RETRIES, e);
                clearServiceCache();
                if (!sleep(RETRY_DELAY_MS)) {
                    break;
                }
            } catch (RemoteException e) {
                Logger.e(TAG, "RemoteException in " + operationName, e);
                break;
            } catch (Exception e) {
                Logger.e(TAG, "Unexpected error in " + operationName, e);
                break;
            }
            
            // Progressive delay for null service case
            if (retryCount < MAX_RETRIES - 1) {
                sleep(PROGRESSIVE_DELAY_MS * (retryCount + 1));
            }
            retryCount++;
        }
        
        Logger.e(TAG, "Failed to execute " + operationName + " after " + MAX_RETRIES + " retries");
        return defaultValue;
    }

    /**
     * Execute a void service call with retry logic
     */
    private void executeWithRetryVoid(ServiceRunnable runnable, String operationName) {
        executeWithRetry(service -> {
            runnable.run(service);
            return Boolean.TRUE;
        }, operationName, Boolean.FALSE);
    }

    /**
     * Execute a simple service call without retry
     */
    private <T> T executeSimple(ServiceCallable<T> callable, String operationName, T defaultValue) {
        try {
            IActivityManagerService service = getService();
            if (service != null) {
                return callable.call(service);
            }
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in " + operationName, e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error in " + operationName, e);
        }
        return defaultValue;
    }

    /**
     * Sleep helper with interrupt handling
     */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    private interface ServiceCallable<T> {
        T call(IActivityManagerService service) throws RemoteException;
        default boolean isNullable() {
            return true;
        }
    }

    @FunctionalInterface
    private interface ServiceRunnable {
        void run(IActivityManagerService service) throws RemoteException;
    }

    public AppConfig initProcess(String packageName, String processName, int userId) {
        return executeWithRetry(
            service -> service.initProcess(packageName, processName, userId),
            "initProcess(" + packageName + ", " + processName + ")",
            null
        );
    }

    public void restartProcess(String packageName, String processName, int userId) {
        executeWithRetryVoid(
            service -> service.restartProcess(packageName, processName, userId),
            "restartProcess(" + packageName + ", " + processName + ")"
        );
    }

    public void launch(Intent intent, int userId) {
        try {
            Intent splash = new Intent();
            splash.setClass(mContext, VLauncherActivity.class);
            splash.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            splash.putExtra(VLauncherActivity.KEY_INTENT, intent);
            splash.putExtra(VLauncherActivity.KEY_PKG, intent.getPackage());
            splash.putExtra(VLauncherActivity.KEY_USER_ID, userId);
            mContext.startActivity(splash);
            Logger.d(TAG, "VLauncherActivity.launch() called for package: " + intent.getPackage());
        } catch (Exception e) {
            Logger.e(TAG, "Error in launch()", e);
        }
    }

    /**
     * Starts an activity for the given user, using {@link VLauncherActivity} when enabled in {@link VHost},
     * otherwise starting through the virtual activity manager service directly.
     */
    public void startActivity(Intent intent, int userId) {
        if (VHost.get().isEnableLauncherActivity()) {
            launch(intent, userId);
        } else {
            startActivityThroughService(intent, userId);
        }
    }

    /** Direct path to AMS (no launcher shell); use when already inside {@link VLauncherActivity} or similar. */
    public void startActivityThroughService(Intent intent, int userId) {
        executeWithRetryVoid(
            service -> service.startActivity(intent, userId),
            "startActivity"
        );
    }

    /**
     * Resolves the launcher intent for {@code packageName} and starts it for {@code userId},
     * after storage checks and {@link AppCallback} hooks.
     */
    public boolean launchApk(String packageName, int userId) {
        AppCallback cb = VHost.get();
        cb.beforeMainLaunchApk(packageName, userId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!StoragePermissionHelper.hasAllFilesAccess()) {
                Logger.w(TAG, "All files access not granted for launching: " + packageName);
                if (cb.onStoragePermissionNeeded(packageName, userId)) {
                    Logger.d(TAG, "Launch cancelled - host app handling permission request");
                    return false;
                }
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

    public int startActivityAms(int userId, Intent intent, String resolvedType, IBinder resultTo, 
                                String resultWho, int requestCode, int flags, Bundle options) {
        return executeWithRetry(
            service -> service.startActivityAms(userId, intent, resolvedType, resultTo, resultWho, requestCode, flags, options),
            "startActivityAms",
            -1
        );
    }

    public int startActivities(int userId, Intent[] intent, String[] resolvedType, IBinder resultTo, Bundle options) {
        return executeSimple(
            service -> service.startActivities(userId, intent, resolvedType, resultTo, options),
            "startActivities",
            -1
        );
    }

    public ComponentName startService(Intent intent, String resolvedType, boolean requireForeground, int userId) {
        return executeWithRetry(
            service -> service.startService(intent, resolvedType, requireForeground, userId),
            "startService",
            null
        );
    }

    public int stopService(Intent intent, String resolvedType, int userId) {
        return executeSimple(
            service -> service.stopService(intent, resolvedType, userId),
            "stopService",
            -1
        );
    }

    public Intent bindService(Intent service, IBinder binder, String resolvedType, int userId) {
        return executeSimple(
            serviceManager -> serviceManager.bindService(service, binder, resolvedType, userId),
            "bindService",
            null
        );
    }

    public void unbindService(IBinder binder, int userId) {
        executeSimple(
            service -> {
                service.unbindService(binder, userId);
                return null;
            },
            "unbindService",
            null
        );
    }

    public void stopServiceToken(ComponentName componentName, IBinder token, int userId) {
        executeSimple(
            service -> {
                service.stopServiceToken(componentName, token, userId);
                return null;
            },
            "stopServiceToken",
            null
        );
    }

    public void onStartCommand(Intent proxyIntent, int userId) {
        executeSimple(
            service -> {
                service.onStartCommand(proxyIntent, userId);
                return null;
            },
            "onStartCommand",
            null
        );
    }

    public UnbindRecord onServiceUnbind(Intent proxyIntent, int userId) {
        return executeSimple(
            service -> service.onServiceUnbind(proxyIntent, userId),
            "onServiceUnbind",
            null
        );
    }

    public void onServiceDestroy(Intent proxyIntent, int userId) {
        executeSimple(
            service -> {
                service.onServiceDestroy(proxyIntent, userId);
                return null;
            },
            "onServiceDestroy",
            null
        );
    }

    public IBinder acquireContentProviderClient(ProviderInfo providerInfo) {
        try {
            IActivityManagerService service = getService();
            if (service != null) {
                return service.acquireContentProviderClient(providerInfo);
            } else {
                Logger.w(TAG, "ActivityManager service is null for acquireContentProviderClient");
            }
        } catch (DeadObjectException e) {
            Logger.w(TAG, "ActivityManager service died during acquireContentProviderClient, clearing cache", e);
            clearServiceCache();
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in acquireContentProviderClient", e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error in acquireContentProviderClient", e);
        }
        return null;
    }

    public Intent sendBroadcast(Intent intent, String resolvedType, int userId) {
        return executeSimple(
            service -> service.sendBroadcast(intent, resolvedType, userId),
            "sendBroadcast",
            null
        );
    }

    public IBinder peekService(Intent intent, String resolvedType, int userId) {
        return executeSimple(
            service -> service.peekService(intent, resolvedType, userId),
            "peekService",
            null
        );
    }

    public void onActivityCreated(int taskId, IBinder token, IBinder activityRecord) {
        executeSimple(
            service -> {
                service.onActivityCreated(taskId, token, activityRecord);
                return null;
            },
            "onActivityCreated",
            null
        );
    }

    public void onActivityResumed(IBinder token) {
        executeSimple(
            service -> {
                service.onActivityResumed(token);
                return null;
            },
            "onActivityResumed",
            null
        );
    }

    public void onActivityDestroyed(IBinder token) {
        executeSimple(
            service -> {
                service.onActivityDestroyed(token);
                return null;
            },
            "onActivityDestroyed",
            null
        );
    }

    public void onFinishActivity(IBinder token) {
        executeSimple(
            service -> {
                service.onFinishActivity(token);
                return null;
            },
            "onFinishActivity",
            null
        );
    }

    public RunningAppProcessInfo getRunningAppProcesses(String callerPackage, int userId) {
        try {
            return getService().getRunningAppProcesses(callerPackage, userId);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in getRunningAppProcesses", e);
        }
        return null;
    }

    public RunningServiceInfo getRunningServices(String callerPackage, int userId) {
        try {
            return getService().getRunningServices(callerPackage, userId);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in getRunningServices", e);
        }
        return null;
    }

    public void scheduleBroadcastReceiver(Intent intent, PendingResultData pendingResultData, int userId) {
        try {
            getService().scheduleBroadcastReceiver(intent, pendingResultData, userId);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in scheduleBroadcastReceiver", e);
        }
    }

    public void finishBroadcast(PendingResultData data) {
        try {
            getService().finishBroadcast(data);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in finishBroadcast", e);
        }
    }

    public String getCallingPackage(IBinder token, int userId) {
        return executeSimple(
            service -> service.getCallingPackage(token, userId),
            "getCallingPackage",
            null
        );
    }

    public ComponentName getCallingActivity(IBinder token, int userId) {
        return executeSimple(
            service -> service.getCallingActivity(token, userId),
            "getCallingActivity",
            null
        );
    }

    public void getIntentSender(IBinder target, String packageName, int uid) {
        try {
            getService().getIntentSender(target, packageName, uid, VActivityThread.getUserId());
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException in getIntentSender", e);
        }
    }

    public String getPackageForIntentSender(IBinder target) {
        return executeSimple(
            service -> service.getPackageForIntentSender(target, VActivityThread.getUserId()),
            "getPackageForIntentSender",
            null
        );
    }

    public int getUidForIntentSender(IBinder target) {
        return executeSimple(
            service -> service.getUidForIntentSender(target, VActivityThread.getUserId()),
            "getUidForIntentSender",
            -1
        );
    }
}
