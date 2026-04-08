package com.android.va.hook.media;

import android.content.Context;

import java.lang.reflect.Method;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.media.BRIMediaRouterServiceStub;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.utils.MethodParameterUtils;

/**
 * Created by Prison on 2022/3/1.
 */
public class IMediaRouterServiceProxy extends BinderInvocationStub {

    public IMediaRouterServiceProxy() {
        super(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIMediaRouterServiceStub.get().asInterface(BRServiceManager.get().getService(Context.MEDIA_ROUTER_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.MEDIA_ROUTER_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("registerClientAsUser")
    public static class registerClientAsUser extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("registerRouter2")
    public static class registerRouter2 extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    /**
     * Hook getSystemRoutes to fix SecurityException: callerPackageName does not match calling uid.
     * When running inside VA, the Binder calling uid is the host app's uid, but the request
     * may carry the virtual app's package name. Replacing package name in args with host package
     * so that MediaRouterService's validation passes.
     */
    @ProxyMethod("getSystemRoutes")
    public static class getSystemRoutes extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceAllAppPkg(args);
            return method.invoke(who, args);
        }
    }
}
