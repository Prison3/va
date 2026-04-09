package com.android.va.hook.network;

import com.android.va.runtime.VHost;

import java.lang.reflect.Method;
import java.util.List;

import com.android.va.hook.MethodHook;
import com.android.va.mirror.com.android.internal.net.BRVpnConfig;
import com.android.va.mirror.com.android.internal.net.VpnConfigContext;
import com.android.va.runtime.VActivityThread;
import com.android.va.hook.ProxyMethod;
import com.android.va.proxy.ProxyVpnService;
import com.android.va.utils.MethodParameterUtils;

/**
 * Created by Prison on 2022/2/26.
 */
public class VpnCommonProxy {
    @ProxyMethod("setVpnPackageAuthorization")
    public static class setVpnPackageAuthorization extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("prepareVpn")
    public static class PrepareVpn extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("establishVpn")
    public static class establishVpn extends MethodHook {

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            VpnConfigContext vpnConfigContext = BRVpnConfig.get(args[0]);
            vpnConfigContext._set_user(ProxyVpnService.class.getName());

            handlePackage(vpnConfigContext.allowedApplications());
            handlePackage(vpnConfigContext.disallowedApplications());
            return method.invoke(who, args);
        }

        private void handlePackage(List<String> applications) {
            if (applications == null)
                return;
            if (applications.contains(VActivityThread.getAppPackageName())) {
                applications.add(VHost.getPackageName());
            }
        }
    }

}
