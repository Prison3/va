package com.android.va.hook.device;

import com.android.va.runtime.VHost;

import java.lang.reflect.Method;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.os.BRIDeviceIdentifiersPolicyServiceStub;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.runtime.VRuntime;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.utils.Md5Utils;

public class IDeviceIdentifiersPolicyProxy extends BinderInvocationStub {

    public IDeviceIdentifiersPolicyProxy() {
        super(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected Object getWho() {
        return BRIDeviceIdentifiersPolicyServiceStub.get().asInterface(BRServiceManager.get().getService("device_identifiers"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("device_identifiers");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getSerialForPackage")
    public static class x extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
//                args[0] = Prison.getHostPkg();
//                return method.invoke(who, args);
            return Md5Utils.md5(VHost.getPackageName());
        }
    }
}
