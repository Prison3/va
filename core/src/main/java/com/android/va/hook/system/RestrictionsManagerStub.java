package com.android.va.hook.system;

import com.android.va.runtime.VHost;

import android.content.Context;

import java.lang.reflect.Method;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.content.BRIRestrictionsManagerStub;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.base.PrisonCore;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;

public class RestrictionsManagerStub extends BinderInvocationStub {

    public RestrictionsManagerStub() {
        super(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIRestrictionsManagerStub.get().asInterface(BRServiceManager.get().getService(Context.RESTRICTIONS_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.RESTRICTIONS_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getApplicationRestrictions")
    public static class GetApplicationRestrictions extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            args[0] = VHost.getPackageName();
            return method.invoke(who, args);
        }
    }
}
