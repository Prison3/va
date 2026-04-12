package com.android.va.hook.telephony;

import java.lang.reflect.Method;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.mirror.com.android.internal.telephony.BRITelephonyRegistryStub;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.utils.MethodParameterUtils;

//https://xrefandroid.com/android-16.0.0_r2/xref/frameworks/base/core/java/com/android/internal/telephony/ITelephonyRegistry.aidl
public class ITelephonyRegistryProxy extends BinderInvocationStub {
    public ITelephonyRegistryProxy() {
        super(BRServiceManager.get().getService("telephony.registry"));
    }

    @Override
    protected Object getWho() {
        return BRITelephonyRegistryStub.get().asInterface(BRServiceManager.get().getService("telephony.registry"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("telephony.registry");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("listenForSubscriber")
    public static class ListenForSubscriber extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("listen")
    public static class Listen extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
