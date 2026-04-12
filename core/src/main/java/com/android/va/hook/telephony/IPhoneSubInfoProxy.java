package com.android.va.hook.telephony;

import java.lang.reflect.Method;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.telephony.BRTelephonyManager;
import com.android.va.hook.ClassInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.utils.MethodParameterUtils;

/**
 *
 *aidl：https://xrefandroid.com/android-16.0.0_r2/xref/frameworks/base/telephony/java/com/android/internal/telephony/IPhoneSubInfo.aidl
 */
public class IPhoneSubInfoProxy extends ClassInvocationStub {
    public static final String TAG = IPhoneSubInfoProxy.class.getSimpleName();

    public IPhoneSubInfoProxy() {
        if (BRTelephonyManager.get()._check_sServiceHandleCacheEnabled() != null) {
            BRTelephonyManager.get()._set_sServiceHandleCacheEnabled(true);
        }
        if (BRTelephonyManager.get()._check_getSubscriberInfoService() != null) {
            BRTelephonyManager.get().getSubscriberInfoService();
        }
    }

    @Override
    protected Object getWho() {
        return BRTelephonyManager.get().sIPhoneSubInfo();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        BRTelephonyManager.get()._set_sIPhoneSubInfo(proxyInvocation);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }


    @ProxyMethod("getLine1NumberForSubscriber")
    public static class getLine1NumberForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }
}
