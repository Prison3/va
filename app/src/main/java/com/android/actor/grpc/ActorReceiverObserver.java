package com.android.actor.grpc;

import com.android.proto.actor.Pong;
import com.android.actor.grpc.process.RequestProcess;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.proto.common.DeviceMockReq;
import com.android.proto.common.GetInstalledAppReq;
import com.android.proto.common.ManageAppReq;
import com.android.proto.common.Message;
import com.android.proto.common.ProxyReq;
import com.android.proto.common.RebootReq;
import com.android.proto.common.RunScriptReq;
import com.android.proto.common.XposedApiReq;
import com.google.protobuf.Any;

import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.grpc.stub.StreamObserver;


public class ActorReceiverObserver implements StreamObserver<Message> {

    private final static String TAG = ActorReceiverObserver.class.getSimpleName();
    private final static String[] SIMPLE_LOG_MESSAGES = {
            "Pong", "XposedApiReq", "RunScriptReq"
    };

    boolean lastRequestSucceed;

    private final HashMap<String, Class> mProcessClassMap = new HashMap<>();
    private final HashMap<String, Class> mReqClassMap = new HashMap<>();
    private final ActorAdapter mAdapter;

    private static final int NONBLOCKING_THREADS = 4;
    private ExecutorService mMainExecutor;
    private ExecutorService mNonblockingExecutor;

    public ActorReceiverObserver(ActorAdapter adapter) {
        this.mAdapter = adapter;
        mMainExecutor = Executors.newFixedThreadPool(1);
        mNonblockingExecutor = Executors.newFixedThreadPool(NONBLOCKING_THREADS);
    }

    @Override
    public void onNext(Message value) {
        if (value.hasVariant()) {
            Any variant = value.getVariant();
            try {
                if (variant.is(Pong.class)) {
                    lastRequestSucceed = true;
                    GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_GRPC_CONNECT, true);
                }

                String typeUrl = variant.getTypeUrl();
                String name = typeUrl.substring(typeUrl.lastIndexOf('.') + 1);
                Class processCls = mProcessClassMap.get(name);
                if (processCls == null) {
                    String processName = name.endsWith("Req") ? name.substring(0, name.length() - 3) : name;
                    processCls = Objects.requireNonNull(getClass().getClassLoader()).loadClass("com.android.actor.grpc.process.Process" + processName);
                    mProcessClassMap.put(name, processCls);
                }

                Class reqCls = mReqClassMap.get(name);
                if (reqCls == null) {
                    try {
                        reqCls = Objects.requireNonNull(getClass().getClassLoader()).loadClass("com.android.proto.common." + name);
                    } catch (ClassNotFoundException ignored) {
                    }
                    if (reqCls == null) {
                        try {
                            reqCls = getClass().getClassLoader().loadClass("com.android.proto.nonblocking." + name);
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                    if (reqCls == null) {
                        reqCls = getClass().getClassLoader().loadClass("com.android.proto.actor." + name);
                    }
                    mReqClassMap.put(name, reqCls);
                }
                com.google.protobuf.Message req = variant.unpack(reqCls);
                processRequest(req, name, processCls, reqCls);
            } catch (Throwable e) {
                Logger.e(TAG, "onNext exception.", e);
            }
        }
    }

    private void processRequest(com.google.protobuf.Message req, String name, Class processCls, Class reqCls) {
        ExecutorService executor;
        if (reqCls.getName().startsWith("com.android.proto.nonblocking")) {
            executor = mNonblockingExecutor;
        } else {
            executor = mMainExecutor;
        }
        executor.execute(() -> {
            try {
                Logger.v(TAG, "Unpacked <" + name + ">" +
                        (ArrayUtils.contains(SIMPLE_LOG_MESSAGES, name) ? "" : ("\n" + req)));
                mAdapter.record("Unpacked <" + name + ">");
                ((RequestProcess) processCls.newInstance()).process(req);
            } catch (Throwable e) {
                Logger.e(TAG, "processRequest exception.", e);
            }
        });
    }

    @Override
    public void onError(Throwable t) {
        lastRequestSucceed = false;
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_GRPC_CONNECT, false);
        Logger.w(TAG, t);
//        t.printStackTrace();
    }

    @Override
    public void onCompleted() {

    }

    public boolean isLastRequestSucceed() {
        return lastRequestSucceed;
    }

    @Deprecated
    private void process(Any variant) {
        if (variant.is(Pong.class)) {
            lastRequestSucceed = false;
            Logger.d(TAG, "Pong - " + "lastRequestSucceed = " + lastRequestSucceed);

        } else if (variant.is(GetInstalledAppReq.class)) {

        } else if (variant.is(DeviceMockReq.class)) {

        } else if (variant.is(ManageAppReq.class)) {

        } else if (variant.is(RebootReq.class)) {

        } else if (variant.is(RunScriptReq.class)) {

        } else if (variant.is(XposedApiReq.class)) {

        } else if (variant.is(ProxyReq.class)) {

        }
    }
}