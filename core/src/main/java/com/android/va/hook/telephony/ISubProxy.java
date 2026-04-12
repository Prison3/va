package com.android.va.hook.telephony;

import android.os.IBinder;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;

import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.MethodHook;
import com.android.va.hook.ProxyMethod;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.mirror.com.android.internal.telephony.BRISubStub;
import com.android.va.utils.Logger;
import com.android.va.utils.MethodParameterUtils;

/**
 * Hook {@code ISub}（ServiceManager 注册名 {@value #SERVICE_ISUB}）。
 * 与 {@link ITelephonyProxy}（{@link android.content.Context#TELEPHONY_SERVICE} Binder）互补。
 * AIDL：{@code com.android.internal.telephony.ISub}
 * https://xrefandroid.com/android-13.0.0_r83/xref/frameworks/base/telephony/java/com/android/internal/telephony/ISub.aidl
 */
public class ISubProxy extends BinderInvocationStub {
    public static final String TAG = ISubProxy.class.getSimpleName();

    /** {@link android.os.ServiceManager#getService(String)} 名称，与 SubscriptionManager 一致 */
    public static final String SERVICE_ISUB = "isub";

    public ISubProxy() {
        super(BRServiceManager.get().getService(SERVICE_ISUB));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(SERVICE_ISUB);
        return BRISubStub.get().asInterface(binder);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE_ISUB);
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

    @ProxyMethod("getSimStateForSlotIndex")
    public static class getSimStateForSlotIndex extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int slotIndex = -1;
            if (args != null && args.length > 0 && args[0] != null) {
                slotIndex = ((Number) args[0]).intValue();
            }
            Logger.d(TAG, "hook getSimStateForSlotIndex(" + slotIndex + ") -> SIM_STATE_READY");
            return TelephonyManager.SIM_STATE_READY;
        }
    }
}
