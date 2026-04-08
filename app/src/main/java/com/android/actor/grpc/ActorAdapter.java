package com.android.actor.grpc;

import android.annotation.SuppressLint;
import android.os.Handler;
import com.alibaba.fastjson.JSON;
import com.android.actor.utils.shell.Libsu;
import com.android.proto.actor.ActorSrvGrpc;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.proto.common.Message;
import com.google.protobuf.Any;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.ArrayUtils;

import javax.net.ssl.SSLSocketFactory;
import java.util.*;

public class ActorAdapter {
    public static String SP_GRPC_NODE_ADDRESS = "grpc_node_address";


    public static String DEFAULT_RETRY_ADDRESS;

    public static String DEFAULT_NO_CONNECT_ADDRESS = "0.0.0.0:0";
    public static String DEFAULT_NODE_ADDRESS = "192.168.100.136:8011";

    private final static String TAG = ActorAdapter.class.getSimpleName();

    private final static String[] SIMPLE_LOG_MESSAGES = {
            "Ping", "DeviceStatus", "XposedApiResp", "WEditorResp"
    };

    private final Handler mHandler;
    private final SSLSocketFactory mSSLSocketFactory;

    private int mPingCount = 0;
    private int mPongCount = 0;

    ManagedChannel channel;
    ActorSrvGrpc.ActorSrvStub asyncStub;
    StreamObserver<Message> toNodeObserver;
    ActorReceiverObserver fromNodeObserver;

    public ActorAdapter(Handler handler, SSLSocketFactory sslSocketFactory) {
        //  初始化时 DEFAULT_RETRY_ADDRESS 为空，resetChannel 取到的是 getNodeAddressFromSp() 的值
        String localSpNodeAddrs = SPUtils.getString(SPUtils.ModuleFile.config, SP_GRPC_NODE_ADDRESS, null);
        Logger.d(TAG, "localSpNodeAddrs: " + localSpNodeAddrs);
        if (localSpNodeAddrs == null) {
            SPUtils.putString(SPUtils.ModuleFile.config, SP_GRPC_NODE_ADDRESS, JSON.toJSONString(Arrays.asList(DEFAULT_NO_CONNECT_ADDRESS, DEFAULT_NODE_ADDRESS)));
            DEFAULT_RETRY_ADDRESS = DEFAULT_NODE_ADDRESS;
        } else {
            List<String> addressList = JSON.parseArray(localSpNodeAddrs, String.class);
            DEFAULT_RETRY_ADDRESS = addressList.get(addressList.size() - 1);
        }
        Logger.d(TAG, "DEFAULT_RETRY_ADDRESS: " + DEFAULT_RETRY_ADDRESS);
        mHandler = handler;
        mSSLSocketFactory = sslSocketFactory;
        Logger.i(TAG, "Init ssl context: " + mSSLSocketFactory);
        fromNodeObserver = new ActorReceiverObserver(this);
        resetChannel();
    }

    public void resetChannel() {
        resetChannelInternal();
    }

    private void resetChannelInternal() {
        setupChannel(fromNodeObserver, GRPCHandler.ACTOR_RESET_LOOP, ActorAdapter.DEFAULT_RETRY_ADDRESS);
        if (!fromNodeObserver.lastRequestSucceed) {
            fromNodeObserver.lastRequestSucceed = true;
        }
    }

    public void onNext(com.google.protobuf.Message _msg) {
        if (channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            String name = _msg.getClass().getSimpleName();
            Logger.v(TAG, "Pack <" + name + ">" + (ArrayUtils.contains(SIMPLE_LOG_MESSAGES, name) ? "" : ("\n" + _msg)));
            record("Pack <" + name + ">");
            Message msg = Message.newBuilder().setVariant(Any.pack(_msg)).build();
            toNodeObserver.onNext(msg);
        }
    }

    public boolean isLastRequestSucceed() {
        return fromNodeObserver.isLastRequestSucceed();
    }

    protected final void setupChannel(StreamObserver<Message> fromNode, int stopSignal, String addr) {
        String address = addr == null ? DEFAULT_RETRY_ADDRESS : addr;
        Logger.d(TAG, "DEFAULT_RETRY_ADDRESS from *" + DEFAULT_RETRY_ADDRESS + "* to *" + address + "*");
        ActorAdapter.DEFAULT_RETRY_ADDRESS = address;
        String host = address.split(":")[0];
        int port = Integer.parseInt(address.split(":")[1]);
        resetCounter(); // restart the counter of ping and pong
        Logger.i(TAG, "Reset channel " + host + ":" + port);
        record("ResetChannel <" + host + ":" + port + ">");
        if (channel != null) {
            if (!channel.isShutdown() || !channel.isTerminated()) {
                mHandler.sendEmptyMessage(stopSignal);
                Logger.i(TAG, "Shutdown channel now and remove message.");
                channel.shutdownNow();
            }
        }
        if (mSSLSocketFactory != null) {
            // https://github.com/grpc/grpc-java/issues/6374
            channel = OkHttpChannelBuilder.forAddress(host, port)
                    .useTransportSecurity() // the same as negotiationType(NegotiationType.TLS)
                    .hostnameVerifier((s, sslSession) -> true) // avoid this: javax.net.ssl.SSLPeerUnverifiedException: Cannot verify hostname
                    .sslSocketFactory(mSSLSocketFactory)
                    .build();
        } else {
            Logger.e(TAG, "Actor is using plain text grpc, try fix it!");
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        }
        asyncStub = ActorSrvGrpc.newStub(channel);
        toNodeObserver = asyncStub.chat(fromNode);
    }


    public void setNodeAddress(String address) {
        if (mHandler != null) {
            ActorAdapter.DEFAULT_RETRY_ADDRESS = address;
            resetChannel();
        }
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_GRPC_SET_ADDRESS);
    }

    public void record(String msg) {
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_GRPC_MSG, msg);
    }

    public void onPong() {
        mPongCount++;
        if (mPingCount > 100) {
            resetCounter();
        }
    }

    @SuppressLint("DefaultLocale")
    public void onPing() {
        mPingCount++;
        if (Math.abs(mPingCount - mPongCount) > 3) {
            Logger.w(TAG, String.format("PingCount(%d) - PongCount(%d) > 3, reset channel", mPingCount, mPongCount));
            resetChannel();
        }
    }

    public void resetCounter() {
        mPingCount = 0;
        mPongCount = 0;
    }

    // 从 sp 中获取 node address 默认为第 最后一 个元素
    public List<String> getNodeAddressFromSp() {
        Logger.d(TAG, "SP_GRPC_NODE_ADDRESS:" + SP_GRPC_NODE_ADDRESS + "DEFAULT_RETRY_ADDRESS:" + DEFAULT_RETRY_ADDRESS);
        String nodeAddresses = SPUtils.getString(SPUtils.ModuleFile.config, SP_GRPC_NODE_ADDRESS, null);
        List<String> addrs = JSON.parseArray(nodeAddresses, String.class);
        Logger.d(TAG, "getspAddresses: " + nodeAddresses);
        String nodeAddress = addrs.get(addrs.size() - 1);
        Logger.d(TAG, "getspAddress: " + nodeAddress);
        return addrs;
    }

    public void addNodeAddressToSp(String nodeAddress) {
        if (ActStringUtils.checkProxyPattern(nodeAddress)) {
            String sp_grpc_node_address = SPUtils.getString(SPUtils.ModuleFile.config, ActorAdapter.SP_GRPC_NODE_ADDRESS, null);
            List<String> addrs = JSON.parseArray(sp_grpc_node_address, String.class);
            // check if the address is already in the list
            if (addrs.contains(nodeAddress)) {
                // find the node address in the list, back to the last one
                addrs.remove(nodeAddress);
                Logger.d(TAG, "addNodeAddressToSp remove the nodeAddress: " + nodeAddress);
                addrs.add(nodeAddress);
                Logger.d(TAG, "addNodeAddressToSp add the nodeAddress back to the last one: " + nodeAddress);
            } else {
                addrs.add(nodeAddress);
                SPUtils.putString(SPUtils.ModuleFile.config, ActorAdapter.SP_GRPC_NODE_ADDRESS, JSON.toJSONString(addrs));
                Logger.d(TAG, "addNodeAddressToSp: " + nodeAddress + "  sp_grpc_node_address: " + addrs);
            }
            SPUtils.putString(SPUtils.ModuleFile.config, ActorAdapter.SP_GRPC_NODE_ADDRESS, JSON.toJSONString(addrs));
        }
    }

    public void removeNodeAddressFromSp(String nodeAddress) {
        if (ActStringUtils.checkProxyPattern(nodeAddress)) {
            String sp_grpc_node_address = SPUtils.getString(SPUtils.ModuleFile.config, ActorAdapter.SP_GRPC_NODE_ADDRESS, null);
            List<String> addrs = JSON.parseArray(sp_grpc_node_address, String.class);
            // check if the address is already in the list
            if (addrs.contains(nodeAddress)) {
                // find the node address in the list, back to the last one
                addrs.remove(nodeAddress);
                Logger.d(TAG, "removeNodeAddressFromSp remove the nodeAddress: " + nodeAddress);
                SPUtils.putString(SPUtils.ModuleFile.config, ActorAdapter.SP_GRPC_NODE_ADDRESS, JSON.toJSONString(addrs));
                Logger.d(TAG, "removeNodeAddressFromSp: " + nodeAddress + "  sp_grpc_node_address: " + addrs);
            }
        }
    }
}
