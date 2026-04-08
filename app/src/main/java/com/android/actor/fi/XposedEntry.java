package com.android.actor.fi;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import com.android.actor.monitor.Logger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {

    private static String TAG = "XposedEntry";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        TAG = "XposedEntry-" + lpparam.packageName;
        FIEntryPoint.main();

        XposedHelpers.findAndHookMethod(Binder.class, "execTransactInternal", int.class, long.class, long.class, int.class, int.class, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Binder binder = (Binder) param.thisObject;
                String desc = binder.getInterfaceDescriptor();
                //Logger.d(TAG, "getInterfaceDescriptor " + desc);
                if (desc != null) {
                    if (desc.equals("com.google.android.gms.auth.api.identity.internal.IBeginSignInCallback")
                            || desc.equals("com.google.android.gms.auth.api.credentials.internal.ICredentialsCallbacks")
                            || desc.equals("com.google.android.play.core.splitinstall.protocol.ISplitInstallServiceCallback")) {
                        Logger.w(TAG, "Block InterfaceDescriptor " + desc);
                        param.setResult(false);
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.os.BinderProxy", lpparam.classLoader, "transact", int.class, Parcel.class, Parcel.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                IBinder iBinder = (IBinder) param.thisObject;
                String desc = iBinder.getInterfaceDescriptor();
                if (desc != null) {
                    if (desc.contains("gms") || desc.contains(".play")) {
                        //Logger.d(TAG, "transact desc " + desc);
                        if (desc.equals("com.google.android.play.core.splitinstall.protocol.ISplitInstallService")) {
                            Logger.w(TAG, "Block transact desc " + desc);
                            param.setResult(false);
                        }
                    }
                }
            }
        });
    }
}
