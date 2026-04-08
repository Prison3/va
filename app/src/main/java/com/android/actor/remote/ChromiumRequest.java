package com.android.actor.remote;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.device.NewStage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.shell.Libsu;
import com.topjohnwu.superuser.nio.ExtendedFile;

import java.io.File;
import java.io.IOException;

public class ChromiumRequest implements Handler.Callback {

    private static final String TAG = ChromiumRequest.class.getSimpleName();
    public static ChromiumRequest instance = new ChromiumRequest();
    private HandlerThread mThread;
    private Handler mHandler;
    private JSONObject mBody;
    private Callback.C3<Boolean, String, String> mCallback;
    private ExtendedFile mResponseFile;
    private boolean mIsRunning = false;

    private static final int MSG_START          = 0;
    private static final int MSG_TIMEOUT        = 1;
    private static final int MSG_CHECK_RESPONSE = 2;
    private static final int MSG_SUCCESS        = 3;
    private static final int MSG_ERROR          = 4;

    private ChromiumRequest() {
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper(), this);
    }

    public synchronized void startRequest(JSONObject jBody, Callback.C3<Boolean, String, String> callback) throws Throwable {
        if (mIsRunning) {
            callback.onResult(false, "Another request is running.", null);
            return;
        }
        mIsRunning = true;
        mBody = jBody;
        mCallback = callback;
        mHandler.sendEmptyMessage(MSG_START);
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_START:
                try {
                    start();
                } catch (Throwable e) {
                    Logger.e(TAG, "Error to start.", e);
                    error("Error to start, " + e);
                }
                break;
            case MSG_TIMEOUT:
                error("Timeout.");
                break;
            case MSG_CHECK_RESPONSE:
                if (mResponseFile.exists()) {
                    mHandler.sendEmptyMessage(MSG_SUCCESS);
                } else {
                    mHandler.sendEmptyMessageDelayed(MSG_CHECK_RESPONSE, 1000);
                }
                break;
            case MSG_SUCCESS:
                Logger.i(TAG, "MSG_SUCCESS.");
                try {
                    String content = Libsu.readFileToString(mResponseFile);
                    mCallback.onResult(true, null, content);
                    clean();
                } catch (IOException e) {
                    error("Error to read " + mResponseFile + ", " + e);
                }
                break;
            case MSG_ERROR:
                String message = (String) msg.obj;
                Logger.e(TAG, "MSG_ERROR, " + message);
                mCallback.onResult(false, message, null);
                clean();
                break;
        }
        return false;
    }

    private void start() throws Throwable {
        String proxy = mBody.getString("proxy");
        Pair<Boolean, String> pair = ProxyManager.getInstance().addProxy(RocketComponent.PKG_CHROMIUM, 0, proxy);
        if (!pair.first) {
            error("Set proxy error, " + pair.second);
            return;
        }
        String msg = ActPackageManager.getInstance().clearApp(RocketComponent.PKG_CHROMIUM, 0);
        if (msg != null) {
            error("Error to clear chromium, " + msg);
            return;
        }
        File file = new File(NewStage.PARAMETERS_PATH + "/" + ActPackageManager.getInstance().getUid(RocketComponent.PKG_CHROMIUM) + "/web_disable_js");
        if (!file.exists()) {
            if (!file.createNewFile()) {
                error("Can't create " + file);
                return;
            }
        }
        if (!file.setReadable(true, false)) {
            error("Can't set readable " + file);
            return;
        }

        String method = mBody.getString("method");
        if (method == null) {
            method = "GET";
        } else if (!method.equals("POST")) {
            error("Uppercase GET or POST method.");
            return;
        }

        int timeout = mBody.getIntValue("timeout");
        if (timeout < 1) {
            timeout = 30;
        }
        mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT, timeout * 1000);

        String url = mBody.getString("url");
        Uri uri = Uri.parse(url);
        String urlPath = uri.getPath();
        if (urlPath.equals("/") || urlPath.isEmpty()) {
            urlPath = "/-";
        }
        mResponseFile = Libsu.fs().getFile("/sdcard/Android/data/org.chromium.chrome/html/" + uri.getHost() + urlPath);
        Logger.d(TAG, "mResponseFile " + mResponseFile);
        if (mResponseFile.exists()) {
            if (!mResponseFile.delete()) {
                error("Can't remove old " + mResponseFile);
                return;
            }
        }

        JSONObject jHeaders = mBody.getJSONObject("headers");
        ExtendedFile dir = Libsu.fs().getFile("/sdcard/Android/data/org.chromium.chrome/html");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                error("Error to mkdir " + dir);
                return;
            }
        }
        file = new File(dir.getPath() + "/_request_headers");
        ExtendedFile tmpFile = Libsu.fs().getFile(file.getPath() + ".tmp");
        StringBuilder builder = new StringBuilder();
        builder.append(url);
        builder.append('\n');
        builder.append(method);
        builder.append('\n');
        for (String name : jHeaders.keySet()) {
            builder.append(name);
            builder.append('\n');
            builder.append(jHeaders.getString(name));
            builder.append('\n');
        }
        Libsu.writeStringToFile(tmpFile, builder.toString());
        if (!tmpFile.renameTo(file)) {
            throw new Exception("Can't rename " + tmpFile + " to " + file);
        }

        file = new File(dir.getPath() + "/_request_body");
        tmpFile = Libsu.fs().getFile(file.getPath() + ".tmp");
        String body = mBody.getString("body");
        Libsu.writeStringToFile(tmpFile, body);
        if (!tmpFile.renameTo(file)) {
            throw new Exception("Can't rename " + tmpFile + " to " + file);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.setPackage(RocketComponent.PKG_CHROMIUM);
        msg = ActActivityManager.getInstance().startActivityAndWait(intent);
        if (msg != null) {
            error("Error to start activity, " + msg);
            return;
        }
        mHandler.sendEmptyMessageDelayed(MSG_CHECK_RESPONSE, 1000);
    }

    private void clean() {
        mHandler.removeCallbacksAndMessages(null);
        mBody = null;
        mCallback = null;
        mResponseFile = null;
        mIsRunning = false;
    }

    private void error(String msg) {
        mHandler.obtainMessage(MSG_ERROR, msg).sendToTarget();
    }
}
