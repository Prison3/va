package com.android.va.hook.telephony;

import android.content.Context;
import android.os.IBinder;
import java.lang.reflect.Method;
import java.util.List;

import com.android.va.hook.MethodHook;
import com.android.va.base.PrisonCore;
import com.android.va.mirror.android.os.BRServiceManager;
import com.android.va.mirror.com.android.internal.telephony.BRITelephonyStub;
import com.android.va.runtime.VActivityThread;
import com.android.va.model.PCell;
import com.android.va.runtime.VLocationManager;
import com.android.va.hook.BinderInvocationStub;
import com.android.va.hook.ProxyMethod;
import com.android.va.utils.Logger;
import com.android.va.utils.Md5Utils;

public class ITelephonyManagerProxy extends BinderInvocationStub {
    public static final String TAG = ITelephonyManagerProxy.class.getSimpleName();

    public ITelephonyManagerProxy() {
        super(BRServiceManager.get().getService(Context.TELEPHONY_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder telephony = BRServiceManager.get().getService(Context.TELEPHONY_SERVICE);
        return BRITelephonyStub.get().asInterface(telephony);
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getDeviceId")
    public static class GetDeviceId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
//                MethodParameterUtils.replaceFirstAppPkg(args);
//                return method.invoke(who, args);
            Object original = method.invoke(who, args);
            if (original == null) return null;
            return Md5Utils.md5(PrisonCore.getPackageName());
        }
    }

    @ProxyMethod("getImeiForSlot")
    public static class getImeiForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
//                MethodParameterUtils.replaceFirstAppPkg(args);
//                return method.invoke(who, args);
            Object original = method.invoke(who, args);
            if (original == null) return null;
            return Md5Utils.md5(PrisonCore.getPackageName());
        }
    }

    @ProxyMethod("getMeidForSlot")
    public static class GetMeidForSlot extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
//                MethodParameterUtils.replaceFirstAppPkg(args);
//                return method.invoke(who, args);
            Object original = method.invoke(who, args);
            if (original == null) return null;
            return Md5Utils.md5(PrisonCore.getPackageName());
        }
    }

    @ProxyMethod("isUserDataEnabled")
    public static class IsUserDataEnabled extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return true;
        }
    }


    @ProxyMethod("getLine1NumberForDisplay")
    public static class getLine1NumberForDisplay extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return null;
        }
    }

    @ProxyMethod("getSubscriberId")
    public static class GetSubscriberId extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object original = method.invoke(who, args);
            if (original == null) return null;
            return Md5Utils.md5(PrisonCore.getPackageName());
        }
    }

    @ProxyMethod("getDeviceIdWithFeature")
    public static class GetDeviceIdWithFeature extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Object original = method.invoke(who, args);
            if (original == null) return null;
            return Md5Utils.md5(PrisonCore.getPackageName());
        }
    }

    @ProxyMethod("getCellLocation")
    public static class GetCellLocation extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "getCellLocation");
            if (VLocationManager.isFakeLocationEnable()) {
                PCell cell = VLocationManager.get().getCell(VActivityThread.getUserId(), VActivityThread.getAppPackageName());
                if (cell != null) {
                    // TODO Transfer PCell to CdmaCellLocation/GsmCellLocation
                    return null;
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAllCellInfo")
    public static class GetAllCellInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (VLocationManager.isFakeLocationEnable()) {
                List<PCell> cell = VLocationManager.get().getAllCell(VActivityThread.getUserId(), VActivityThread.getAppPackageName());
                // TODO Transfer PCell to CdmaCellLocation/GsmCellLocation
                return cell;
            }
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                return null;
            }
        }
    }

    @ProxyMethod("getNetworkOperator")
    public static class GetNetworkOperator extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "getNetworkOperator");
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getNetworkTypeForSubscriber")
    public static class GetNetworkTypeForSubscriber extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Throwable e) {
                return 0;
            }
        }
    }

    @ProxyMethod("getNeighboringCellInfo")
    public static class GetNeighboringCellInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Logger.d(TAG, "getNeighboringCellInfo");
            if (VLocationManager.isFakeLocationEnable()) {
                List<PCell> cell = VLocationManager.get().getNeighboringCell(VActivityThread.getUserId(), VActivityThread.getAppPackageName());
                // TODO Transfer PCell to CdmaCellLocation/GsmCellLocation
                return null;
            }
            return method.invoke(who, args);
        }
    }
}
