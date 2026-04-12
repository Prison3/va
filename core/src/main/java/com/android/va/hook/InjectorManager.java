package com.android.va.hook;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.android.va.hook.account.IAccountManagerProxy;
import com.android.va.hook.app.AppInstrumentation;
import com.android.va.hook.content.IAttributionSourceProxy;
import com.android.va.hook.content.IContentProviderProxy;
import com.android.va.hook.content.ISettingsSystemProxy;
import com.android.va.hook.device.IAutofillManagerProxy;
import com.android.va.hook.device.IContextHubServiceProxy;
import com.android.va.hook.device.IDeviceIdentifiersPolicyProxy;
import com.android.va.hook.device.IFingerprintManagerProxy;
import com.android.va.hook.device.IPowerManagerProxy;
import com.android.va.hook.device.ISensitiveContentProtectionManagerProxy;
import com.android.va.hook.device.ISensorPrivacyManagerProxy;
import com.android.va.hook.device.ISystemSensorManagerProxy;
import com.android.va.hook.device.IVibratorServiceProxy;
import com.android.va.hook.gms.DeviceIdProxy;
import com.android.va.hook.gms.GmsProxy;
import com.android.va.hook.location.ILocationManagerProxy;
import com.android.va.hook.media.AudioPermissionProxy;
import com.android.va.hook.media.IAudioServiceProxy;
import com.android.va.hook.media.IGraphicsStatsProxy;
import com.android.va.hook.media.IMediaRouterServiceProxy;
import com.android.va.hook.media.IMediaSessionManagerProxy;
import com.android.va.hook.miui.IMiuiSecurityManagerProxy;
import com.android.va.hook.miui.IXiaomiAttributionSourceProxy;
import com.android.va.hook.miui.IXiaomiMiuiServicesProxy;
import com.android.va.hook.miui.IXiaomiSettingsProxy;
import com.android.va.hook.network.IConnectivityManagerProxy;
import com.android.va.hook.network.IDnsResolverProxy;
import com.android.va.hook.network.INetworkManagementServiceProxy;
import com.android.va.hook.network.IVpnManagerProxy;
import com.android.va.hook.network.IWifiManagerProxy;
import com.android.va.hook.network.IWifiScannerProxy;
import com.android.va.hook.resources.ResourcesManagerProxy;
import com.android.va.hook.storage.IPersistentDataBlockServiceProxy;
import com.android.va.hook.storage.IStorageManagerProxy;
import com.android.va.hook.storage.IStorageStatsManagerProxy;
import com.android.va.hook.system.ContentServiceStub;
import com.android.va.hook.system.HCallbackProxy;
import com.android.va.hook.system.IAccessibilityManagerProxy;
import com.android.va.hook.system.IActivityClientProxy;
import com.android.va.hook.system.IActivityManagerProxy;
import com.android.va.hook.system.IActivityTaskManagerProxy;
import com.android.va.hook.system.IAlarmManagerProxy;
import com.android.va.hook.system.IAppOpsManagerProxy;
import com.android.va.hook.system.IAppWidgetManagerProxy;
import com.android.va.hook.system.IDevicePolicyManagerProxy;
import com.android.va.hook.system.IDisplayManagerProxy;
import com.android.va.hook.system.IJobServiceProxy;
import com.android.va.hook.system.ILauncherAppsProxy;
import com.android.va.hook.system.INotificationManagerProxy;
import com.android.va.hook.system.IPackageManagerProxy;
import com.android.va.hook.system.IPermissionManagerProxy;
import com.android.va.hook.system.IShortcutManagerProxy;
import com.android.va.hook.system.ISystemUpdateProxy;
import com.android.va.hook.system.IUserManagerProxy;
import com.android.va.hook.system.IWindowManagerProxy;
import com.android.va.hook.system.OsStub;
import com.android.va.hook.system.RestrictionsManagerStub;
import com.android.va.hook.telephony.IPhoneSubInfoProxy;
import com.android.va.hook.telephony.ISubProxy;
import com.android.va.hook.telephony.ITelephonyProxy;
import com.android.va.hook.telephony.ITelephonyRegistryProxy;
import com.android.va.hook.webview.IWebViewUpdateServiceProxy;
import com.android.va.hook.webview.SystemLibraryProxy;
import com.android.va.utils.BuildCompat;
import com.android.va.utils.Logger;

public class InjectorManager {
    public static final String TAG = InjectorManager.class.getSimpleName();

    private static final InjectorManager sInjectorManager = new InjectorManager();

    /** Simple names that must be present after {@link #inject()}. */
    private static final String[] CRITICAL_INJECTOR_NAMES = {
            "IActivityManagerProxy",
            "IPackageManagerProxy",
            "WebViewProxy",
            "IContentProviderProxy"
    };

    @SuppressWarnings("unchecked")
    private static final Class<? extends IInjector>[] INJECTOR_CLASSES = new Class[]{
//            ContentResolverProxy.class,
//            ReLinkerProxy.class,
//            WebViewProxy.class,
//            WebViewFactoryProxy.class,
//            WorkManagerProxy.class,
//            MediaRecorderProxy.class,
//            AudioRecordProxy.class,
//            ISettingsProviderProxy.class,
//            FeatureFlagUtilsProxy.class,
//            MediaRecorderClassProxy.class,
//            SQLiteDatabaseProxy.class,
//            ClassLoaderProxy.class,
//            FileSystemProxy.class,
//            LevelDbProxy.class,
//            GoogleAccountManagerProxy.class,
//            AuthenticationProxy.class,
//            AndroidIdProxy.class,
//            ApkAssetsProxy.class,

            IDisplayManagerProxy.class,
            OsStub.class,
            IActivityManagerProxy.class,
            IPackageManagerProxy.class,
            ITelephonyProxy.class,
            ISubProxy.class,
            HCallbackProxy.class,
            IAppOpsManagerProxy.class,
            INotificationManagerProxy.class,
            IAlarmManagerProxy.class,
            IAppWidgetManagerProxy.class,
            ContentServiceStub.class,
            IWindowManagerProxy.class,
            IUserManagerProxy.class,
            RestrictionsManagerStub.class,
            IMediaSessionManagerProxy.class,
            IAudioServiceProxy.class,
            ISensorPrivacyManagerProxy.class,
            IWebViewUpdateServiceProxy.class,
            SystemLibraryProxy.class,
            GmsProxy.class,
            DeviceIdProxy.class,
            AudioPermissionProxy.class,
            ILocationManagerProxy.class,
            IStorageManagerProxy.class,
            ILauncherAppsProxy.class,
            IJobServiceProxy.class,
            IAccessibilityManagerProxy.class,
            ITelephonyRegistryProxy.class,
            IDevicePolicyManagerProxy.class,
            IAccountManagerProxy.class,
            IConnectivityManagerProxy.class,
            IDnsResolverProxy.class,
            IAttributionSourceProxy.class,
            IContentProviderProxy.class,
            ISettingsSystemProxy.class,
            ISystemSensorManagerProxy.class,

            // Xiaomi-specific proxies (MIUI)
            IMiuiSecurityManagerProxy.class,
            IXiaomiAttributionSourceProxy.class,
            IXiaomiSettingsProxy.class,
            IXiaomiMiuiServicesProxy.class,
            IPhoneSubInfoProxy.class,
            IMediaRouterServiceProxy.class,
            IPowerManagerProxy.class,
            IContextHubServiceProxy.class,
            IVibratorServiceProxy.class,
            IPersistentDataBlockServiceProxy.class,
            // WifiScanner / WifiManager (historically tested on Android 10)
            IWifiManagerProxy.class,
            IWifiScannerProxy.class,

            ResourcesManagerProxy.class,
            IVpnManagerProxy.class,
            IPermissionManagerProxy.class,
            IActivityTaskManagerProxy.class,
            ISystemUpdateProxy.class,
            IAutofillManagerProxy.class,
            IDeviceIdentifiersPolicyProxy.class,
            IStorageStatsManagerProxy.class,
            IShortcutManagerProxy.class,
            INetworkManagementServiceProxy.class,
            IFingerprintManagerProxy.class,
            IGraphicsStatsProxy.class
    };

    /** Registration order preserved for deterministic inject() iteration. */
    private final Map<Class<?>, IInjector> mInjectors = new LinkedHashMap<>();

    public static InjectorManager get() {
        return sInjectorManager;
    }

    public void inject() {
        Logger.d(TAG, "inject: Starting injector initialization");
        int[] registerCounts = registerStandardInjectors();
        int successCount = registerCounts[0];
        int failCount = registerCounts[1];

        int[] specialCounts = registerSpecialInjectors();
        successCount += specialCounts[0];
        failCount += specialCounts[1];

        Logger.d(TAG, "inject: Completed - Success: " + successCount + ", Failed: " + failCount);

        Logger.d(TAG, "inject: Starting injection for " + mInjectors.size() + " injectors");
        int injectSuccessCount = 0;
        int injectFailCount = 0;

        for (IInjector injector : mInjectors.values()) {
            try {
                injector.inject();
                injectSuccessCount++;
            } catch (Exception e) {
                injectFailCount++;
                Logger.e(TAG, "inject: Failed to inject " + injector.getClass().getSimpleName(), e);
                handleInjectionFailure(injector, e);
            }
        }

        Logger.d(TAG, "inject: Injection completed - Success: " + injectSuccessCount + ", Failed: " + injectFailCount);
    }

    /**
     * @return int[] { successCount, failCount }
     */
    private int[] registerStandardInjectors() {
        int success = 0;
        int fail = 0;
        for (Class<? extends IInjector> clazz : INJECTOR_CLASSES) {
            try {
                IInjector injector = clazz.getDeclaredConstructor().newInstance();
                registerInjector(injector);
                success++;
            } catch (Exception e) {
                fail++;
                Logger.e(TAG, "inject: Failed to instantiate injector " + clazz.getSimpleName(), e);
            }
        }
        return new int[]{success, fail};
    }

    /**
     * @return int[] { successCount, failCount }
     */
    private int[] registerSpecialInjectors() {
        int success = 0;
        int fail = 0;

        try {
            registerInjector(AppInstrumentation.get());
            success++;
        } catch (Exception e) {
            fail++;
            Logger.e(TAG, "inject: Failed to register AppInstrumentation", e);
        }

        try {
            registerInjector(new IActivityClientProxy(null));
            success++;
        } catch (Exception e) {
            fail++;
            Logger.e(TAG, "inject: Failed to register IActivityClientProxy", e);
        }

        if (BuildCompat.isS()) {
            try {
                registerInjector(new ISensitiveContentProtectionManagerProxy());
                success++;
            } catch (Exception e) {
                fail++;
                Logger.e(TAG, "inject: Failed to register ISensitiveContentProtectionManagerProxy", e);
            }
        }

        return new int[]{success, fail};
    }

    public void checkEnvironment(Class<?> clazz) {
        IInjector injector = mInjectors.get(clazz);
        if (injector != null && injector.isBadEnv()) {
            Logger.d(TAG, "checkEnvironment: " + clazz.getSimpleName() + " detected bad environment, injecting");
            injector.inject();
        }
    }

    public void checkAllEnvironments() {
        Logger.d(TAG, "checkAllEnvironments: Checking all injector environments");
        int injectedCount = 0;
        for (Map.Entry<Class<?>, IInjector> entry : mInjectors.entrySet()) {
            IInjector injector = entry.getValue();
            if (injector != null && injector.isBadEnv()) {
                Logger.d(TAG, "checkAllEnvironments: " + entry.getKey().getSimpleName()
                        + " detected bad environment, injecting");
                injector.inject();
                injectedCount++;
            }
        }
        Logger.d(TAG, "checkAllEnvironments: Completed - Injected: " + injectedCount);
    }

    void registerInjector(IInjector injector) {
        if (injector == null) {
            Logger.w(TAG, "registerInjector: Attempted to register null injector");
            return;
        }
        Class<?> clazz = injector.getClass();
        IInjector existing = mInjectors.put(clazz, injector);
        if (existing != null) {
            Logger.w(TAG, "registerInjector: Replaced existing injector for " + clazz.getSimpleName());
        }
    }

    private void handleInjectionFailure(IInjector injector, Exception e) {
        String injectorName = injector.getClass().getSimpleName();
        Logger.e(TAG, "handleInjectionFailure: Injection failed for " + injectorName + " - " + e.getMessage(), e);

        if (isCriticalInjector(injectorName)) {
            Logger.w(TAG, "handleInjectionFailure: Critical injector " + injectorName + " failed, attempting recovery");
            try {
                if (injector.isBadEnv()) {
                    Logger.d(TAG, "handleInjectionFailure: Attempting to recover injector " + injectorName);
                    injector.inject();
                    Logger.d(TAG, "handleInjectionFailure: Successfully recovered injector " + injectorName);
                }
            } catch (Exception recoveryException) {
                Logger.e(TAG, "handleInjectionFailure: Injector recovery failed for " + injectorName, recoveryException);
            }
        }
    }

    private boolean isCriticalInjector(String injectorName) {
        return injectorName.contains("ActivityManager") ||
                injectorName.contains("PackageManager") ||
                injectorName.contains("WebView") ||
                injectorName.contains("ContentProvider");
    }

    public boolean areCriticalInjectorsInstalled() {
        Set<String> registered = new HashSet<>();
        for (Class<?> injectorClass : mInjectors.keySet()) {
            registered.add(injectorClass.getSimpleName());
        }

        Logger.d(TAG, "areCriticalInjectorsInstalled: Checking " + CRITICAL_INJECTOR_NAMES.length + " critical injectors");
        for (String name : CRITICAL_INJECTOR_NAMES) {
            if (!registered.contains(name)) {
                Logger.w(TAG, "areCriticalInjectorsInstalled: Critical injector missing - " + name);
                return false;
            }
        }

        Logger.d(TAG, "areCriticalInjectorsInstalled: All critical injectors are installed");
        return true;
    }

    public void reinitializeInjectors() {
        Logger.d(TAG, "reinitializeInjectors: Starting injector reinitialization");
        int previousCount = mInjectors.size();

        mInjectors.clear();
        Logger.d(TAG, "reinitializeInjectors: Cleared " + previousCount + " existing injectors");

        inject();

        Logger.d(TAG, "reinitializeInjectors: Injector reinitialization completed - New count: " + mInjectors.size());
    }
}
