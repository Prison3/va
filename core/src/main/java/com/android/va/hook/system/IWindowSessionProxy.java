package com.android.va.hook.system;

import com.android.va.runtime.VHost;

import android.os.IInterface;
import android.view.WindowManager;

import java.lang.reflect.Method;

import com.android.va.hook.MethodHook;
import com.android.va.base.PrisonCore;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;


@Deprecated
public class IWindowSessionProxy extends BinderInvocationStub {
    public static final String TAG = IWindowSessionProxy.class.getSimpleName();

    private IInterface mSession;

    public IWindowSessionProxy(IInterface session) {
        super(session.asBinder());
        mSession = session;
    }

    @Override
    protected Object getWho() {
        return mSession;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @ProxyMethod("addToDisplay")
    public static class AddToDisplay extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof WindowManager.LayoutParams) {
                    ((WindowManager.LayoutParams) arg).packageName = VHost.getPackageName();
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("addToDisplayAsUser")
    public static class AddToDisplayAsUser extends AddToDisplay {
    }
}
