package com.android.actor.fi;

import static com.android.actor.fi.FIAppWarmup.sHttpClient;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActPackageManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.NetUtils;
import com.android.actor.utils.shell.Libsu;

import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FIRequest implements Handler.Callback {

    private static Map<String, FIRequest> sFIRequestMap = new HashMap<>();

    private String TAG;
    private String mPackageName;
    private FIAppWarmup mAppWarmup;
    private boolean mIsRunning = false;
    private HandlerThread mThread;
    private Handler mHandler;
    private JSONObject mBody;
    private Callback.C2<Boolean, String> mCallback;

    private static final int MSG_SEND_REQUEST    = 2;
    private static final int MSG_SUCCESS         = 3;
    private static final int MSG_ERROR           = 4;

    public static synchronized FIRequest get(String packageName) {
        FIRequest request = sFIRequestMap.get(packageName);
        if (request == null) {
            request = new FIRequest(packageName);
            sFIRequestMap.put(packageName, request);
        }
        return request;
    }

    public FIRequest(String packageName) {
        TAG = "FIRequest-" + packageName;
        mPackageName = packageName;
        mAppWarmup = new FIAppWarmup(packageName);
        mAppWarmup.start();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper(), this);
    }

    public boolean isReady() {
        return mAppWarmup.isReady();
    }

    public synchronized void startRequest(JSONObject jBody, Callback.C2<Boolean, String> _callback) throws Throwable {
        if (FIEntryPoint.getPort(mPackageName) <= 0) {
            callback(_callback, false, "Port not configured for " + mPackageName);
            return;
        }
        if (!ActPackageManager.getInstance().isAppInstalled(mPackageName)) {
            callback(_callback, false, "App not installed.");
            return;
        }
        int installedVersionCode = ActPackageManager.getInstance().getVersionCode(mPackageName);
        if (installedVersionCode != jBody.getIntValue("version_code")) {
            callback(_callback, false, "Version code not match " + installedVersionCode);
            return;
        }
        if (mIsRunning) {
            callback(_callback, false, "Another request is running.");
            return;
        }
        mIsRunning = true;
        mBody = jBody;
        mCallback = _callback;

        mHandler.sendEmptyMessage(MSG_SEND_REQUEST);
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_SEND_REQUEST:
                sendRequest();
                break;
            case MSG_SUCCESS:
                String body = (String) msg.obj;
                Logger.i(TAG, "MSG_SUCCESS, " + body);
                callback(true, body);
                clean();
                break;
            case MSG_ERROR:
                String message = (String) msg.obj;
                Logger.e(TAG, "MSG_ERROR, " + message);
                callback(false, message);
                clean();
                break;
        }
        return true;
    }

    private void sendRequest() {
        String action = mBody.getString("action");
        Request request = new Request.Builder()
                .url("http://127.0.0.1:" + FIEntryPoint.getPort(mPackageName) + "/" + action)
                .method("POST", RequestBody.create(MediaType.parse("application/json; charset=utf-8"), mBody.toString()))
                .build();
        try {
            Response response = sHttpClient.newCall(request).execute();
            Logger.i(TAG, "http response code " + response.code());
            if (response.code() == 200) {
                try {
                    String body = response.body().string();
                    JSONObject jBody = JSON.parseObject(body);
                    if (jBody.getBooleanValue("success")) {
                        mHandler.obtainMessage(MSG_SUCCESS, body).sendToTarget();
                    } else {
                        error(body);
                    }
                } catch (Throwable e) {
                    error(e.toString());
                }
            } else {
                error("onResponse " + response.code() + ", " + response.body().string());
            }
        } catch (IOException e) {
            Logger.w(TAG, "http exception, " + e);
            error("http exception, " + e);
        }
    }

    private void callback(boolean success, String str) {
        callback(mCallback, success, str);
    }

    private void callback(Callback.C2<Boolean, String> _callback, boolean success, String str) {
        Logger.v(TAG, "callback " + success + ", " + str);
        _callback.onResult(success, str);
    }

    private void clean() {
        mHandler.removeCallbacksAndMessages(null);

        if ("com.instagram.android".equals(mPackageName) && mBody != null && "pi".equals(mBody.getString("action"))) {
            Logger.w(TAG, "Kill instagram, since PI can only be called once.");
            Libsu.exec("am force-stop com.instagram.android");
        }

        mBody = null;
        mCallback = null;
        mIsRunning = false;
    }

    private void error(String msg) {
        mHandler.obtainMessage(MSG_ERROR, msg).sendToTarget();
    }
}
