package com.android.va.hook.device;

import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.MethodHook;
import com.android.va.hook.ProxyMethod;
import com.android.va.mirror.android.hardware.biometrics.BRIAuthServiceStub;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.utils.Logger;
import com.android.va.utils.MethodParameterUtils;

/**
 * Hook {@link android.hardware.biometrics.IAuthService}。
 * 服务名与 {@code android.content.Context.AUTH_SERVICE} 相同（{@code "auth"}，API 29+）；当前模块所用 android.jar 无该常量，故用字面量。
 * AIDL：https://xrefandroid.com/android-16.0.0_r2/xref/frameworks/base/core/java/android/hardware/biometrics/IAuthService.aidl
 */
public class IAuthServiceProxy extends BinderInvocationStub {
    public static final String TAG = IAuthServiceProxy.class.getSimpleName();

    private static final String SERVICE_NAME = "auth";

    public IAuthServiceProxy() {
        super(BRServiceManager.get().getService(SERVICE_NAME));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(SERVICE_NAME);
        if (binder == null) {
            Logger.w(TAG, "getWho: AUTH_SERVICE binder is null");
            return null;
        }
        return BRIAuthServiceStub.get().asInterface(binder);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE_NAME);
        //Logger.d(TAG, "inject: replaced system service " + SERVICE_NAME);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    /**
     * int canAuthenticate(String opPackageName, int userId, int authenticators)
     */
    @ProxyMethod("canAuthenticate")
    public static class CanAuthenticate extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object original = method.invoke(who, args);
            Logger.d(TAG, "canAuthenticate: result=" + original + " -> 0");
            return 0;
        }
    }
}
