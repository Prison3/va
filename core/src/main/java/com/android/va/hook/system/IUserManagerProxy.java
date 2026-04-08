package com.android.va.hook.system;

import com.android.va.runtime.VHost;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.content.pm.BRUserInfo;
import com.android.va.mirror.android.os.BRIUserManagerStub;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.base.PrisonCore;
import com.android.va.runtime.VActivityThread;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;

public class IUserManagerProxy extends BinderInvocationStub {
    public IUserManagerProxy() {
        super(BRServiceManager.get().getService(Context.USER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIUserManagerStub.get().asInterface(BRServiceManager.get().getService(Context.USER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.USER_SERVICE);
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

    @ProxyMethod("getProfileParent")
    public static class GetProfileParent extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object prison = BRUserInfo.get()._new(VActivityThread.getUserId(), "Prison", BRUserInfo.get().FLAG_PRIMARY());
            return prison;
        }
    }

    @ProxyMethod("getUsers")
    public static class getUsers extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return new ArrayList<>();
        }
    }
}
