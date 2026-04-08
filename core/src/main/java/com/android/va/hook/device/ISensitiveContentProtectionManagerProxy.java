package com.android.va.hook.device;

import com.android.va.runtime.VHost;

import android.os.IBinder;

import com.android.va.hook.MethodHook;
import com.android.va.base.PrisonCore;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.utils.Logger;

import java.lang.reflect.Method;

/**
 * Hook for ISensitiveContentProtectionManager (Android 14+)
 * Fixes SecurityException: Specified calling package [pkg] does not match the calling uid
 */
public class ISensitiveContentProtectionManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ISensitiveContentProtection";

    public ISensitiveContentProtectionManagerProxy() {
        super(BRServiceManager.get().getService("sensitive_content_protection_service"));
    }

    @Override
    protected Object getWho() {
        try {
            IBinder binder = BRServiceManager.get().getService("sensitive_content_protection_service");
            if (binder == null) return null;
            Class<?> stubClass = Class.forName("android.view.ISensitiveContentProtectionManager$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Exception e) {
            Logger.d(TAG, "getWho error: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        if (BRServiceManager.get().getService("sensitive_content_protection_service") != null) {
            replaceSystemService("sensitive_content_protection_service");
            Logger.d(TAG, "Hooked SensitiveContentProtectionManagerService");
        } else {
            Logger.d(TAG, "Skipping SensitiveContentProtectionManagerService hook (service not found)");
        }
    }

    @Override
    public boolean isBadEnv() {
        IBinder binder = BRServiceManager.get().getService("sensitive_content_protection_service");
        return binder != null && binder != this;
    }

    @ProxyMethod("setSensitiveContentProtection")
    public static class SetSensitiveContentProtection extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0) {
                // setSensitiveContentProtection(IBinder windowToken, String packageName, boolean isProtected)
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof String) {
                        String pkg = (String) args[i];
                        if (pkg != null && !pkg.equals(VHost.getPackageName())) {
                            Logger.d(TAG, "Fixing package name in setSensitiveContentProtection: " + pkg + " -> " + VHost.getPackageName());
                            args[i] = VHost.getPackageName();
                        }
                    }
                }
            }
            return method.invoke(who, args);
        }
    }
}
