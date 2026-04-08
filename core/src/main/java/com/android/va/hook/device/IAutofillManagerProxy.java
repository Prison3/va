package com.android.va.hook.device;

import com.android.va.runtime.VHost;

import android.content.ComponentName;

import java.lang.reflect.Method;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.mirror.android.view.BRIAutoFillManagerStub;
import com.android.va.runtime.VActivityThread;
import com.android.va.runtime.VRuntime;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.proxy.ProxyManifest;

public class IAutofillManagerProxy extends BinderInvocationStub {
    public static final String TAG = IAutofillManagerProxy.class.getSimpleName();

    public IAutofillManagerProxy() {
        super(BRServiceManager.get().getService("autofill"));
    }

    @Override
    protected Object getWho() {
        return BRIAutoFillManagerStub.get().asInterface(BRServiceManager.get().getService("autofill"));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService("autofill");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("startSession")
    public static class StartSession extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null)
                        continue;
                    if (args[i] instanceof ComponentName) {
                        args[i] = new ComponentName(VHost.getPackageName(), ProxyManifest.getProxyActivity(VActivityThread.getAppPid()));
                    }
                }
            }
            return method.invoke(who, args);
        }
    }
}
