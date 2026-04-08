package com.android.actor.grpc;

import android.os.HandlerThread;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.SPUtils;
import com.android.proto.common.SmsMessage;

import java.util.ArrayList;
import java.util.List;

public class GRPCManager {
    private final static String TAG = GRPCManager.class.getSimpleName();
    private static GRPCManager sInstance;

    private GRPCHandler mHandler;
    private GRPCHandlerThread mHandlerThread;
    private List<Callback.C0> mPreparedCallbacks; // Only used to store callbacks before handler prepared.

    private GRPCManager() {
        Logger.d(TAG, "GRPCManager init");
        mHandlerThread = new GRPCHandlerThread();
        mHandlerThread.start();
        mHandler = new GRPCHandler(mHandlerThread.getLooper());
    }

    class GRPCHandlerThread extends HandlerThread {

        public GRPCHandlerThread() {
            super("ActorGRPCHandler");
        }

        @Override
        protected void onLooperPrepared() {
            Logger.i(TAG, "Actor GRPC looper prepared.");
            synchronized (mHandlerThread) {
                if (mPreparedCallbacks != null) {
                    mPreparedCallbacks.forEach(Callback.C0::onResult);
                    mPreparedCallbacks = null;
                }
            }
        }
    }

    public synchronized static GRPCManager getInstance() {
        if (sInstance == null) {
            sInstance = new GRPCManager();
        }
        return sInstance;
    }

    public void prepared(Callback.C0 callback) {
        synchronized (mHandlerThread) {
            if (mHandler != null) {
                callback.onResult();
            } else {
                if (mPreparedCallbacks == null) {
                    mPreparedCallbacks = new ArrayList<>();
                }
                mPreparedCallbacks.add(callback);
            }
        }
    }

    public ActorAdapter getActorChannel() {
        return mHandler.actorAdapter;
    }

    public void sendNotification(String level, String msg) {
        sendMessageInActorQueue(ActorMessageBuilder.notification(level, msg));
    }

    public void sendAppInfos() {
        sendMessageInActorQueue(ActorMessageBuilder.appInfos());
    }

    public void sendMessageInActorQueue(com.google.protobuf.Message protoMsg) {
        if (mHandler != null) {
            mHandler.sendMessageInActorQueue(protoMsg);
        }
    }

    public void uploadSms(String uuid, SmsMessage msg) {
        sendMessageInActorQueue(ActorMessageBuilder.smsUpload(uuid, msg));
    }

    public void setUpActorChannel(String address) {
        ActorAdapter.DEFAULT_RETRY_ADDRESS = address;
        mHandler.actorAdapter.resetChannel();
    }

}