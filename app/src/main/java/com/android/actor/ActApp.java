package com.android.actor;

import com.android.va.runtime.VHost;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.actor.control.ActPackageManager;
import com.android.actor.device.NewStage;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;
import com.android.actor.utils.shell.Libsu;
import com.android.va.base.PrisonCore;

public class ActApp extends Application {
    private final static String TAG = ActApp.class.getSimpleName();
    private static ActApp app;
    private static Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static boolean sSettingUp;

    private ActBroadcastReceiver mActBroadcastReceiver;

    public static ActApp getInstance() {
        return app;
    }

    public static Application currentApplication() {
        try {
            Class cls = ReflectUtils.findClass("android.app.ActivityThread", ClassLoader.getSystemClassLoader());
            return (Application) ReflectUtils.callStaticMethod(cls, "currentApplication");
        } catch (Throwable e) {
            Logger.e("currentApplication exception.", e);
            return null;
        }
    }

    public static ClassLoader classLoader() {
        return currentApplication().getClassLoader();
    }

    public static Handler getMainHandler() {
        return sMainHandler;
    }

    public static void post(Runnable runnable) {
        sMainHandler.post(runnable);
    }

    public static void postDelayed(Runnable runnable, long ms) {
        sMainHandler.postDelayed(runnable, ms);
    }

    public static void removeCallbacks(Runnable runnable) {
        sMainHandler.removeCallbacks(runnable);
    }

    public static boolean isSettingUp() {
        return sSettingUp;
    }

    /**
     * 与 prison {@code FoxRiver#attachBaseContext} 一致：尽早 {@link PrisonCore#startUp}，保证 VA / Hook 在 Application 初始化前就绪。
     */
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (base == null) {
            return;
        }
        app = this;
        if (VHost.getContext() == null) {
            PrisonCore.get().startUp(this, new ActorPrisonSettings(this), new ActorPrisonAppCallback());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PrisonCore.get().initializeServices();
        } catch (Throwable e) {
            Logger.e(TAG, "PrisonCore.initializeServices failed", e);
        }

        if (!getProcessName().equals(getPackageName())) {
            return;
        }

        /*sSettingUp = SettingUp.instance.isSettingUp();
        if (sSettingUp) {
            Logger.w(TAG, "-----------------");
            Logger.w(TAG, "   setting up");
            Logger.w(TAG, "-----------------");
            SettingUp.instance.enterCheckLoop();
        }*/

        /*ExceptionHandler.setup();
        DeviceNumber.setAsSerial();*/
        GRPCManager.getInstance();
        ActPackageManager.getInstance();
        /*if (Root.acquireRoot()) {

            ActActivityManager.getInstance();
            RemoteServiceManager.getInstance();
            // must call init handler in main looper here.
            ScriptExecutor.getInstance();
            ActInputManager.getInstance();
            Libsu.waitReady(() -> {
                ProxyManager.getInstance();
            });
        }
        DeviceInfoManager.getInstance();*/
        //WebUtils.changeWebViewProvider();
        //Libsu.connectService();

        /*ExceptionHandler.checkLastMainThreadCrash();
        prepareDeviceStatusCheck();*/
        registerActorReceiver();
        //resetProfileData();
        //DddWatcher.instance.start();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (mActBroadcastReceiver != null) {
            unregisterReceiver(mActBroadcastReceiver);
        }
    }

    private void prepareDeviceStatusCheck() {
        NewStage.instance().selfCheck();
    }

    private void registerActorReceiver() {
        mActBroadcastReceiver = new ActBroadcastReceiver();
        mActBroadcastReceiver.register(this);
    }

    private void resetProfileData() {
        Libsu.waitReady(() -> {
            NewStage.instance().resetProfileData();
        });
    }
}
