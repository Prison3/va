package com.android.actor.fi;

import static com.android.actor.utils.NetUtils.noSSLSocketFactory;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.ResUtils;
import com.android.actor.utils.shell.AsyncShell;
import com.android.actor.utils.shell.Libsu;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FIAppWarmup implements Handler.Callback {

    private static Context sContext;
    private static ReentrantLock sGlobalStartLock = new ReentrantLock();
    private static Runnable sGlobalMoveToBackground = () -> {
        Libsu.exec("am start -n " + RocketComponent.PKG_ALPHA + "/com.android.actor.MainActivity");
        Libsu.exec("settings put system accelerometer_rotation 0");
    };

    public static final OkHttpClient sHttpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .sslSocketFactory(noSSLSocketFactory())
            .hostnameVerifier((hostname, session) -> true)
            .build();

    private String TAG;
    private String mPackageName;
    private HandlerThread mThread;
    private Handler mHandler;
    private boolean mIsReady = false;

    private static final int MSG_START_APP       = 0;
    private static final int MSG_START_FI        = 1;
    private static final int MSG_START_CHECK     = 2;
    private static final int MSG_CHECK           = 3;

    public static boolean init(Context context) {
        try {
            sContext = context;
            File file = new File(context.getDataDir(), "xinjector");
            ResUtils.extractAssetToFileIfNotExist("xinjector", file.getPath());
            Libsu.exec("cp -F " + file.getPath() + " /data/local/tmp/ && chmod 755 /data/local/tmp/xinjector");

            file = new File(context.getDataDir(), "libinjector-glue.so");
            ResUtils.extractAssetToFileIfNotExist("libinjector-glue.so", file.getPath());
            Libsu.exec("cp -F " + file.getPath() + " /data/local/tmp/ && chmod 755 /data/local/tmp/libinjector-glue.so");

            file = new File(context.getDataDir(), "classes.dex");
            ResUtils.extractAssetToFileIfNotExist("classes.dex", file.getPath());
            Libsu.exec("cp -F " + file.getPath() + " /data/local/tmp/ && chmod 755 /data/local/tmp/classes.dex");

            file = new File(context.getDataDir(), "libsandhook-64.so");
            ResUtils.extractAssetToFileIfNotExist("libsandhook-64.so", file.getPath());
            Libsu.exec("cp -F " + file.getPath() + " /data/local/tmp/ && chmod 755 /data/local/tmp/libsandhook-64.so");

            Libsu.exec("cp -F " + context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).sourceDir + " /data/local/tmp/xposed_plugin.apk");
            return true;
        } catch (Throwable e) {
            Logger.e("FIRequest", "Error to init fi files.", e);
            return false;
        }
    }

    public FIAppWarmup(String packageName) {
        TAG = "FIAppWarmup-" + packageName;
        mPackageName = packageName;
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper(), this);
    }

    public void start() {
        if (!ActPackageManager.getInstance().isAppInstalled(mPackageName)) {
            return;
        }
        mHandler.sendEmptyMessage(MSG_START_APP);
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_START_APP:
                startApp();
                break;
            case MSG_START_FI:
                startFI();
                break;
            case MSG_START_CHECK:
                startCheck();
                break;
            case MSG_CHECK:
                check();
                break;
        }
        return true;
    }

    private void startApp() {
        mHandler.removeCallbacksAndMessages(null);
        setReady(false);
        sGlobalStartLock.lock();
        ActApp.removeCallbacks(sGlobalMoveToBackground);
        Logger.i(TAG, "Warmup " + mPackageName);
        Libsu.exec("am force-stop " + mPackageName);
        Libsu.exec("dumpsys deviceidle whitelist +" + mPackageName);

        int uid = ActPackageManager.getInstance().getUid(mPackageName);
        Libsu.exec("iptables -w -D GlobalFilter -p udp -m owner --uid-owner " + uid + " -j DROP");
        Libsu.exec("iptables -w -D GlobalFilter -p tcp -m owner --uid-owner " + uid + " -j DROP");
        Libsu.exec("iptables -w -A GlobalFilter -p udp -m owner --uid-owner " + uid + " -j DROP");
        Libsu.exec("iptables -w -A GlobalFilter -p tcp -m owner --uid-owner " + uid + " -j DROP");

        //Intent intent = ActPackageManager.getInstance().getLaunchIntentForPackage(mPackageName);
        //Libsu.exec("am start -n " + intent.getComponent().flattenToShortString());
        mHandler.sendEmptyMessageDelayed(MSG_START_FI, 2000);
    }

    private void startFI() {
        String cmd = "nohup sh -c '/data/local/tmp/xinjector " + mPackageName + " /data/local/tmp/libinjector-glue.so; setenforce 1' > /dev/null 2>&1 &";
        Libsu.exec(cmd);
        mHandler.sendEmptyMessageDelayed(MSG_START_CHECK, 3000);
    }

    private void startCheck() {
        ActApp.postDelayed(sGlobalMoveToBackground, 10000);
        sGlobalStartLock.unlock();
        mHandler.sendEmptyMessageDelayed(MSG_CHECK, 10000);
    }

    private void check() {
        if (ActPackageManager.getInstance().isAppInstalled(mPackageName)) {
            Logger.v(TAG, "check loop.");
            int port = FIEntryPoint.getPort(mPackageName);
            Request request = new Request.Builder()
                    .url("http://127.0.0.1:" + port)
                    .build();
            try {
                Response response = sHttpClient.newCall(request).execute();
                if (response.code() == 200) {
                    setReady(true);
                } else {
                    Logger.w(TAG, "http check response " + response.code());
                    mHandler.sendEmptyMessage(MSG_START_APP);
                }
            } catch (IOException e) {
                Logger.w(TAG, "http check exception, " + e);
                mHandler.sendEmptyMessage(MSG_START_APP);
            }
        }
        mHandler.sendEmptyMessageDelayed(MSG_CHECK, 10000);
    }

    private void setReady(boolean isReady) {
        if (mIsReady != isReady) {
            Logger.i(TAG, "setReady " + isReady);
            mIsReady = isReady;
            GRPCManager.getInstance().sendAppInfos();
        }
    }

    public boolean isReady() {
        return mIsReady;
    }
}
