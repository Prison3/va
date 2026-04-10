package com.android.va.hook.system;

import com.android.va.hook.content.ContentProviderDelegate;
import com.android.va.hook.content.ContentProviderStub;
import com.android.va.mirror.android.content.BRContentProviderNative;
import com.android.va.model.AppConfig;
import com.android.va.runtime.VHost;

import android.Manifest;
import android.app.ActivityManager;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.app.BRActivityManagerNative;
import com.android.va.mirror.android.app.BRActivityManagerOreo;
import com.android.va.mirror.android.app.BRLoadedApkReceiverDispatcher;
import com.android.va.mirror.android.app.BRLoadedApkReceiverDispatcherInnerReceiver;
import com.android.va.mirror.android.app.BRLoadedApkServiceDispatcher;
import com.android.va.mirror.android.app.BRLoadedApkServiceDispatcherInnerConnection;
import com.android.va.mirror.android.content.pm.BRUserInfo;
import com.android.va.mirror.android.util.BRSingleton;
import com.android.va.runtime.VProcess;
import com.android.va.runtime.VActivityThread;
import com.android.va.runtime.VAppSystemEnv;
import com.android.va.model.RunningAppProcessInfo;
import com.android.va.model.RunningServiceInfo;
import com.android.va.runtime.VActivityManager;
import com.android.va.runtime.VPackageManager;
import com.android.va.hook.ClassInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.hook.PkgMethodProxy;
import com.android.va.hook.ScanClass;
import com.android.va.proxy.ProxyManifest;
import com.android.va.proxy.ProxyBroadcastRecord;
import com.android.va.proxy.ProxyPendingRecord;
import com.android.va.utils.MethodParameterUtils;
import com.android.va.utils.ActivityManagerCompat;
import com.android.va.utils.BuildCompat;
import com.android.va.utils.ParceledListSliceCompat;
import com.android.va.utils.Reflector;
import com.android.va.utils.TaskDescriptionCompat;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import com.android.va.utils.Logger;

@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityManagerProxy extends ClassInvocationStub {
    public static final String TAG = IActivityManagerProxy.class.getSimpleName();

    @Override
    protected Object getWho() {
        Object iActivityManager = null;
        if (BuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (BuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        return BRSingleton.get(iActivityManager).get();
    }

    @Override
    protected void inject(Object base, Object proxy) {
        Object iActivityManager = null;
        if (BuildCompat.isOreo()) {
            iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
        } else if (BuildCompat.isL()) {
            iActivityManager = BRActivityManagerNative.get().gDefault();
        }
        BRSingleton.get(iActivityManager)._set_mInstance(proxy);
    }

    @Override
    public boolean isBadEnv() {
        return getProxyInvocation() != getWho();
    }

    @Override
    protected void onBindMethod() {
        super.onBindMethod();
        addMethodHook(new PkgMethodProxy("getAppStartMode"));
        addMethodHook(new PkgMethodProxy("setAppLockedVerifying"));
        addMethodHook(new PkgMethodProxy("reportJunkFromApp"));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return super.invoke(proxy, method, args);
        } catch (SecurityException e) {
            // Handle SecurityExceptions gracefully for all ActivityManager calls
            String methodName = method.getName();
            Logger.w(TAG, "ActivityManager invoke: SecurityException in " + methodName + ", returning safe default", e);
            
            // Return appropriate default values based on method
            if (methodName.startsWith("set") || methodName.startsWith("update")) {
                return null; // For setter methods, return null (success)
            } else if (methodName.startsWith("get") || methodName.startsWith("query")) {
                return null; // For getter methods, return null (empty result)
            } else if (methodName.startsWith("start") || methodName.startsWith("bind")) {
                return false; // For start/bind methods, return false (not started)
            } else if (methodName.startsWith("stop") || methodName.startsWith("unbind")) {
                return true; // For stop/unbind methods, return true (stopped)
            } else {
                return null; // Default fallback
            }
        } catch (Exception e) {
            Logger.e(TAG, "ActivityManager invoke: Unexpected error in " + method.getName(), e);
            return super.invoke(proxy, method, args);
        }
    }

    /**
     * {@link android.app.IActivityManager#getContentProviderExternal} / getContentProvider：
     * 系统级 Provider（settings/media/telephony、GMS 等）走 {@link ContentProviderDelegate#update}；
     * 其余解析虚拟包并套 {@link ContentProviderStub}。
     */
    @ProxyMethod("getContentProvider")
    public static class GetContentProvider extends MethodHook {

        /** 获取后仅需 {@link ContentProviderDelegate#update}、不替换进程的 authority（精确匹配） */
        private static boolean isDelegateOnlyAuthority(String auth) {
            if (auth == null) {
                return false;
            }
            switch (auth) {
                case "settings":
                case "media":
                case "telephony":
                case "com.google.android.gms.chimera":
                case "com.huawei.android.launcher.settings":
                case "com.hihonor.android.launcher.settings":
                    return true;
                default:
                    return auth.contains("com.google.android.gms")
                            || auth.contains("com.android.vending")
                            || auth.contains("com.google.android.gsf");
            }
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int authIndex = getAuthIndex();
            int userIndex = getUserIndex();
            if (args == null || authIndex >= args.length) {
                return method.invoke(who, args);
            }
            Object authObj = args[authIndex];
            if (!(authObj instanceof String auth)) {
                return method.invoke(who, args);
            }

            if (ProxyManifest.isProxy(auth)) {
                return method.invoke(who, args);
            }

            if (BuildCompat.isQ()) {
                args[1] = VHost.getPackageName();
            }

            if (isDelegateOnlyAuthority(auth)) {
                Object holder = method.invoke(who, args);
                ContentProviderDelegate.update(holder, auth);
                return holder;
            }

            ProviderInfo providerInfo = VPackageManager.get().resolveContentProvider(auth, GET_META_DATA, VActivityThread.getUserId());
            if (providerInfo == null) {
                return null;
            }

            IBinder providerBinder = null;
            if (VActivityThread.getAppPid() != -1) {
                AppConfig appConfig = VActivityManager.get().initProcess(providerInfo.packageName,providerInfo.processName,VActivityThread.getUserId());
                if (appConfig.bpid != VActivityThread.getAppPid()) {
                    providerBinder = VActivityManager.get().acquireContentProviderClient(providerInfo);
                }
                args[authIndex] = ProxyManifest.getProxyAuthorities(appConfig.bpid);
                if (userIndex < args.length) {
                    args[userIndex] = VHost.getUserId();
                }
            }
            if (providerBinder == null) {
                return null;
            }

            Object holder = method.invoke(who, args);
            assert holder != null;
            Reflector.with(holder).field("info").set(providerInfo);
            Reflector.with(holder).field("provider").set(new ContentProviderStub().wrapper(BRContentProviderNative.get().asInterface(providerBinder), providerInfo.packageName, providerInfo.authority));
            return holder;
        }

        protected int getAuthIndex() {
            return BuildCompat.isQ() ? 2 : 1;
        }

        protected int getUserIndex() {
            return getAuthIndex() + 1;
        }
    }

    @ProxyMethod("startService")
    public static class StartService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Intent intent = (Intent) args[1];
            String resolvedType = (String) args[2];
            ResolveInfo resolveInfo = VPackageManager.get().resolveService(intent, 0, resolvedType, VActivityThread.getUserId());
            if (resolveInfo == null) {
                return method.invoke(who, args);
            }

            int requireForegroundIndex = getRequireForeground();
            boolean requireForeground = false;
            if (requireForegroundIndex != -1) {
                requireForeground = (boolean) args[requireForegroundIndex];
            }
            return VActivityManager.get().startService(intent, resolvedType, requireForeground, VActivityThread.getUserId());
        }

        public int getRequireForeground() {
            if (BuildCompat.isOreo()) {
                return 3;
            }
            return -1;
        }
    }

    @ProxyMethod("stopService")
    public static class StopService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                Intent intent = (Intent) args[1];
                String resolvedType = (String) args[2];
                
                                        // Check if the service belongs to the current app
                        if (intent != null && intent.getComponent() != null) {
                            String servicePackage = intent.getComponent().getPackageName();
                            String currentPackage = VActivityThread.getAppPackageName();

                            // If trying to stop a service from a different package, return false instead of crashing
                            if (!servicePackage.equals(currentPackage)) {
                                Logger.w(TAG, "StopService: Attempting to stop service from different package: " +
                                        servicePackage + " (current: " + currentPackage + "), returning false");
                                return false;
                            }
                        }
                
                return VActivityManager.get().stopService(intent, resolvedType, VActivityThread.getUserId());
            } catch (SecurityException e) {
                Logger.w(TAG, "StopService: SecurityException caught, returning false", e);
                return false;
            } catch (Exception e) {
                Logger.e(TAG, "StopService: Error stopping service", e);
                return false;
            }
        }
    }

    @ProxyMethod("stopServiceToken")
    public static class StopServiceToken extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                ComponentName componentName = (ComponentName) args[0];
                IBinder token = (IBinder) args[1];
                
                                        // Check if the service belongs to the current app
                        if (componentName != null) {
                            String servicePackage = componentName.getPackageName();
                            String currentPackage = VActivityThread.getAppPackageName();

                            // If trying to stop a service from a different package, return true instead of crashing
                            if (!servicePackage.equals(currentPackage)) {
                                Logger.w(TAG, "StopServiceToken: Attempting to stop service from different package: " +
                                        servicePackage + " (current: " + currentPackage + "), returning true");
                                return true;
                            }
                        }
                
                VActivityManager.get().stopServiceToken(componentName, token, VActivityThread.getUserId());
                return true;
            } catch (SecurityException e) {
                Logger.w(TAG, "StopServiceToken: SecurityException caught, returning true", e);
                return true;
            } catch (Exception e) {
                Logger.e(TAG, "StopServiceToken: Error stopping service token", e);
                return true;
            }
        }
    }

    @ProxyMethod("setActivityLocusContext")
    public static class SetActivityLocusContext extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                                        // Check if the target package matches the current app
                        if (args != null && args.length >= 2) {
                            String targetPackage = (String) args[1];
                            String currentPackage = VActivityThread.getAppPackageName();

                            // If trying to set locus context for a different package, return success instead of crashing
                            if (targetPackage != null && !targetPackage.equals(currentPackage)) {
                                Logger.w(TAG, "SetActivityLocusContext: Attempting to set locus context for different package: " +
                                        targetPackage + " (current: " + currentPackage + "), returning success");
                                return null; // Return null for success
                            }
                        }
                
                // Proceed with original call if package matches
                return method.invoke(who, args);
            } catch (SecurityException e) {
                Logger.w(TAG, "SetActivityLocusContext: SecurityException caught, returning success", e);
                return null; // Return null for success
            } catch (Exception e) {
                Logger.e(TAG, "SetActivityLocusContext: Error setting locus context", e);
                return null; // Return null for success
            }
        }
    }

    public static Object BindServiceCommon(Object who, Method method, Object[] args,int callingPackageIndex) throws Throwable {
        try {
            Intent intent = (Intent) args[2];
            String resolvedType = (String) args[3];
            IServiceConnection connection = (IServiceConnection) args[4];

            // Check for null intent
            if (intent == null) {
                Logger.w(TAG, "BindServiceCommon: Intent is null, proceeding with original call");
                return method.invoke(who, args);
            }

            //int flags = MethodParameterUtils.toInt(args[5]);

            int userId = intent.getIntExtra("_B_|_UserId", -1);
            userId = userId == -1 ? VActivityThread.getUserId() : userId;
            ResolveInfo resolveInfo = VPackageManager.get().resolveService(intent, 0, resolvedType, userId);
            if (resolveInfo != null || VAppSystemEnv.isOpenPackage(intent.getComponent())) {
                Intent proxyIntent = VActivityManager.get().bindService(intent,
                        connection == null ? null : connection.asBinder(),
                        resolvedType,
                        userId);
                if (connection != null) {
                    if (intent.getComponent() == null && resolveInfo != null) {
                        intent.setComponent(new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name));
                    }
                    IServiceConnection proxy = ServiceConnectionDelegate.createProxy(connection, intent);
                    args[4] = proxy;

                    WeakReference<?> weakReference = BRLoadedApkServiceDispatcherInnerConnection.get(connection).mDispatcher();
                    if (weakReference != null) {
                        BRLoadedApkServiceDispatcher.get(weakReference.get())._set_mConnection(proxy);
                    }
                }

                //Logger.d(TAG,"Intent:" + intent + "-->" + "proxyIntent:" + proxyIntent + ",flag:" + intent.getFlags() + "proxyFlag:" + proxyIntent.getFlags());
                if (proxyIntent != null && proxyIntent.getComponent() != null && 
                    proxyIntent.getComponent().getPackageName().equals(VHost.getPackageName())){
                    int flagsIndex = getFlagsIndex(args);
                    if (flagsIndex >= 0) {
                        int flags = MethodParameterUtils.toInt(args[flagsIndex]);
                        flags &= ~Context.BIND_EXTERNAL_SERVICE;
                        args[flagsIndex] = flags;
                    }
                }
                args[callingPackageIndex] = VHost.getPackageName();

                if (proxyIntent != null) {
                    args[2] = proxyIntent;
                    return method.invoke(who, args);
                }
            }
            return method.invoke(who, args);
        } catch (Exception e) {
            Logger.e(TAG, "BindServiceCommon: Unexpected error", e);
            return method.invoke(who, args);
        }
    }

    private static int getFlagsIndex(Object[] args) {
        // Heuristic: flags is the first Number after IServiceConnection position (index 4)
        for (int i = 5; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Integer || arg instanceof Long) {
                return i;
            }
        }
        return -1;
    }
    @ProxyMethod("bindService")
    public static class BindService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BindServiceCommon(who,method,args,6);
        }

        @Override
        protected boolean isEnable() {
            return VProcess.get().isVaProcess() || VProcess.get().isServerProcess();
        }
    }

    // android 14 add
    @ProxyMethod("bindServiceInstance")
    public static class bindServiceInstance extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BindServiceCommon(who,method,args,7);
        }

        @Override
        protected boolean isEnable() {
            return VProcess.get().isVaProcess() || VProcess.get().isServerProcess();
        }
    }

    // 10.0
    @ProxyMethod("bindIsolatedService")
    public static class BindIsolatedService extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // instanceName
            args[6] = null;
            return BindServiceCommon(who,method,args,7);
        }

        @Override
        protected boolean isEnable() {
            return VProcess.get().isVaProcess() || VProcess.get().isServerProcess();
        }
    }

    @ProxyMethod("unbindService")
    public static class UnbindService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IServiceConnection iServiceConnection = (IServiceConnection) args[0];
            if (iServiceConnection == null) {
                return method.invoke(who, args);
            }
            VActivityManager.get().unbindService(iServiceConnection.asBinder(), VActivityThread.getUserId());
            ServiceConnectionDelegate delegate = ServiceConnectionDelegate.getDelegate(iServiceConnection.asBinder());
            if (delegate != null) {
                args[0] = delegate;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getRunningAppProcesses")
    public static class GetRunningAppProcesses extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            RunningAppProcessInfo runningAppProcesses = VActivityManager.get().getRunningAppProcesses(VActivityThread.getAppPackageName(), VActivityThread.getUserId());
            if (runningAppProcesses == null) {
                return new ArrayList<>();
            }
            return runningAppProcesses.mAppProcessInfoList;
        }
    }

    @ProxyMethod("getServices")
    public static class GetServices extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            RunningServiceInfo runningServices = VActivityManager.get().getRunningServices(VActivityThread.getAppPackageName(), VActivityThread.getUserId());
            if (runningServices == null) {
                return new ArrayList<>();
            }
            return runningServices.mRunningServiceInfoList;
        }
    }

    @ProxyMethod("getIntentSender")
    public static class GetIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int type = (int) args[0];
            Intent[] intents = (Intent[]) args[getIntentsIndex(args)];
            MethodParameterUtils.replaceFirstAppPkg(args);

            for (int i = 0; i < intents.length; i++) {
                Intent intent = intents[i];
                switch (type) {
                    case ActivityManagerCompat.INTENT_SENDER_ACTIVITY:
                        Intent shadow = new Intent();
                        shadow.setComponent(new ComponentName(VHost.getPackageName(), ProxyManifest.getProxyPendingActivity(VActivityThread.getAppPid())));
                        ProxyPendingRecord.saveStub(shadow, intent, VActivityThread.getUserId());
                        intents[i] = shadow;
                        break;
                }
            }
            IInterface invoke = (IInterface) method.invoke(who, args);
            if (invoke != null) {
                String[] packagesForUid = VPackageManager.get().getPackagesForUid(VActivityThread.getCallingBoundUid());
                if (packagesForUid.length < 1) {
                    packagesForUid = new String[]{VHost.getPackageName()};
                }
                VActivityManager.get().getIntentSender(invoke.asBinder(), packagesForUid[0], VActivityThread.getCallingBoundUid());
            }
            return invoke;
        }

        private int getIntentsIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent[]) {
                    return i;
                }
            }
            if (BuildCompat.isR()) {
                return 6;
            } else {
                return 5;
            }
        }
    }

    @ProxyMethod("getPackageForIntentSender")
    public static class getPackageForIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return VActivityManager.get().getPackageForIntentSender(invoke.asBinder());
        }
    }

    @ProxyMethod("getUidForIntentSender")
    public static class getUidForIntentSender extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            IInterface invoke = (IInterface) args[0];
            return VActivityManager.get().getUidForIntentSender(invoke.asBinder());
        }
    }

    @ProxyMethod("getIntentSenderWithSourceToken")
    public static class GetIntentSenderWithSourceToken extends GetIntentSender {
    }

    @ProxyMethod("getIntentSenderWithFeature")
    public static class GetIntentSenderWithFeature extends GetIntentSender {
    }

    @ProxyMethod("broadcastIntentWithFeature")
    public static class BroadcastIntentWithFeature extends BroadcastIntent {
    }

    @ProxyMethod("broadcastIntent")
    public static class BroadcastIntent extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int intentIndex = getIntentIndex(args);
            Intent intent = (Intent) args[intentIndex];
            String resolvedType = (String) args[intentIndex + 1];
            Intent proxyIntent = VActivityManager.get().sendBroadcast(intent, resolvedType, VActivityThread.getUserId());
            if (proxyIntent != null) {
                proxyIntent.setExtrasClassLoader(VActivityThread.getApplication().getClassLoader());
                ProxyBroadcastRecord.saveStub(proxyIntent, intent, VActivityThread.getUserId());
                args[intentIndex] = proxyIntent;
            }
            // ignore permission
            for (int i = 0; i < args.length; i++) {
                Object o = args[i];
                if (o instanceof String[]) {
                    args[i] = null;
                }
            }
            return method.invoke(who, args);
        }

        int getIntentIndex(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Intent) {
                    return i;
                }
            }
            return 1;
        }
    }

    @ProxyMethod("unregisterReceiver")
    public static class unregisterReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishReceiver")
    public static class finishReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("publishService")
    public static class PublishService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("peekService")
    public static class PeekService extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastAppPkg(args);
            Intent intent = (Intent) args[0];
            String resolvedType = (String) args[1];
            IBinder peek = VActivityManager.get().peekService(intent, resolvedType, VActivityThread.getUserId());
            return peek;
        }
    }

    // todo
    @ProxyMethod("sendIntentSender")
    public static class SendIntentSender extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    // android 11 add
    @ProxyMethod("registerReceiverWithFeature")
    public static class RegisterReceiverWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            int receiverIndex = getReceiverIndex();
            if (args[receiverIndex] != null) {
                IIntentReceiver intentReceiver = (IIntentReceiver) args[receiverIndex];
                IIntentReceiver proxy = InnerReceiverDelegate.createProxy(intentReceiver);

                WeakReference<?> weakReference = BRLoadedApkReceiverDispatcherInnerReceiver.get(intentReceiver).mDispatcher();
                if (weakReference != null) {
                    BRLoadedApkReceiverDispatcher.get(weakReference.get())._set_mIIntentReceiver(proxy);
                }

                args[receiverIndex] = proxy;
            }
            // ignore permission
            if (args[getPermissionIndex()] != null) {
                args[getPermissionIndex()] = null;
            }

            // API 33+：Context.RECEIVER_EXPORTED / NOT_EXPORTED 要求显式导出性（Android 13）
            if (BuildCompat.isTiramisu()) {
                int flagsIndex = args.length - 1;
                int flags = (int)args[flagsIndex];
                if((flags & RECEIVER_NOT_EXPORTED) == 0 && (flags & RECEIVER_EXPORTED) == 0){
                    flags |= RECEIVER_NOT_EXPORTED;
                }
                args[flagsIndex] = flags;
            }

            return method.invoke(who, args);
        }

        public int getReceiverIndex() {
            if (BuildCompat.isS()) {
                return 4;
            }
            return 3;
        }

        public int getPermissionIndex() {
            if (BuildCompat.isS()) {
                return 6;
            }
            return 5;
        }
    }

    //maxTargetSdk=29
    @ProxyMethod("registerReceiver")
    public static class RegisterReceiver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            int receiverIndex = 2;
            if (args[receiverIndex] != null) {
                IIntentReceiver intentReceiver = (IIntentReceiver) args[receiverIndex];
                IIntentReceiver proxy = InnerReceiverDelegate.createProxy(intentReceiver);

                WeakReference<?> weakReference = BRLoadedApkReceiverDispatcherInnerReceiver.get(intentReceiver).mDispatcher();
                if (weakReference != null) {
                    BRLoadedApkReceiverDispatcher.get(weakReference.get())._set_mIIntentReceiver(proxy);
                }

                args[receiverIndex] = proxy;
            }
            int permissionIndex = 4;
            // ignore permission
            if (args[permissionIndex] != null) {
                args[permissionIndex] = null;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("grantUriPermission")
    public static class GrantUriPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastUid(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setServiceForeground")
    public static class setServiceForeground extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // Allow foreground service promotion to proceed, and neutralize service type enforcement on Android 14+
            // Find an int argument that represents foregroundServiceType and zero it out
            for (int i = args.length - 1; i >= 0; i--) {
                if (args[i] instanceof Integer) {
                    args[i] = 0; // remove service type requirements (e.g., MICROPHONE)
                    break;
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getHistoricalProcessExitReasons")
    public static class getHistoricalProcessExitReasons extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return ParceledListSliceCompat.create(new ArrayList<>());
        }
    }

    @ProxyMethod("getCurrentUser")
    public static class getCurrentUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object vaUser = BRUserInfo.get()._new(VActivityThread.getUserId(), "Va", BRUserInfo.get().FLAG_PRIMARY());
            return vaUser;
        }
    }

    @ProxyMethod("checkPermission")
    public static class checkPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceLastUid(args);
            String permission = (String) args[0];
            if (permission.equals(Manifest.permission.ACCOUNT_MANAGER)
                    || permission.equals(Manifest.permission.SEND_SMS)) {
                return PackageManager.PERMISSION_GRANTED;
            }
            
            // Handle all audio-related permissions comprehensively
            if (isAudioPermission(permission)) {
                Logger.d(TAG, "ActivityManager checkPermission: Granting audio permission: " + permission);
                return PackageManager.PERMISSION_GRANTED;
            }

            // Handle storage/media read permissions so apps can query MediaStore
            if (isStorageOrMediaPermission(permission)) {
                Logger.d(TAG, "ActivityManager checkPermission: Granting storage/media permission: " + permission);
                return PackageManager.PERMISSION_GRANTED;
            }
            
            return method.invoke(who, args);
        }
    }

    // Keep in sync with IPackageManagerProxy helper
    private static boolean isAudioPermission(String permission) {
        if (permission == null) return false;
        return permission.equals(Manifest.permission.RECORD_AUDIO)
                || permission.equals(Manifest.permission.CAPTURE_AUDIO_OUTPUT)
                || permission.equals(Manifest.permission.MODIFY_AUDIO_SETTINGS)
                || permission.equals("android.permission.FOREGROUND_SERVICE_MICROPHONE")
                || permission.equals("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
                || permission.equals("android.permission.FOREGROUND_SERVICE_CAMERA")
                || permission.equals("android.permission.FOREGROUND_SERVICE_LOCATION")
                || permission.equals("android.permission.FOREGROUND_SERVICE_HEALTH")
                || permission.equals("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
                || permission.equals("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
                || permission.equals("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED")
                || permission.equals("android.permission.FOREGROUND_SERVICE_PHONE_CALL")
                || permission.equals("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE");
    }

    @ProxyMethod("checkUriPermission")
    public static class checkUriPermission extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return PERMISSION_GRANTED;
        }
    }

    // Keep in sync with IPackageManagerProxy helper
    private static boolean isStorageOrMediaPermission(String permission) {
        if (permission == null) return false;
        if (permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE)
                || permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return true;
        }
        if (permission.equals(Manifest.permission.READ_MEDIA_AUDIO)
                || permission.equals(Manifest.permission.READ_MEDIA_VIDEO)
                || permission.equals(Manifest.permission.READ_MEDIA_IMAGES)
                || permission.equals("android.permission.READ_MEDIA_VISUAL")
                || permission.equals("android.permission.READ_MEDIA_AURAL")
                || permission.equals(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            return true;
        }
        if (permission.equals("android.permission.READ_MEDIA_AUDIO_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_VIDEO_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_IMAGES_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
                || permission.equals("android.permission.READ_MEDIA_AURAL_USER_SELECTED")) {
                return true;
        }
        return false;
    }

    // for < Android 10
    @ProxyMethod("setTaskDescription")
    public static class SetTaskDescription extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[1];
            args[1] = TaskDescriptionCompat.fix(td);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("setRequestedOrientation")
    public static class setRequestedOrientation extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    @ProxyMethod("registerUidObserver")
    public static class registerUidObserver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("unregisterUidObserver")
    public static class unregisterUidObserver extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }

    @ProxyMethod("updateConfiguration")
    public static class updateConfiguration extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return 0;
        }
    }
}
