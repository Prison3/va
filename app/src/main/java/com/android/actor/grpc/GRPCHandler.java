package com.android.actor.grpc;

import android.os.Handler;
import android.os.Looper;
import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;


public class GRPCHandler extends Handler {
    private final static String TAG = GRPCHandler.class.getSimpleName();
    private final static long PING_DELAY = 60 * 1000;
    private final static long STATUS_DELAY = 10 * 60 * 1000;

    public final static int ACTOR_RESET_LOOP = 0;
    private final static int ACTOR_LOOP_PING = 1;
    private final static int ACTOR_LOOP_STATUS = 2;
    public final static int ACTOR_SEND_TO_NODE = 3;
    ActorAdapter actorAdapter;

    GRPCHandler(Looper looper) {
        super(looper);
        actorAdapter = new ActorAdapter(this, getSslContext());
    }

    @Override
    public void handleMessage(android.os.Message msg) {
        Logger.d(TAG, "handleMessage: " + msg.what);
        switch (msg.what) {
            case ACTOR_RESET_LOOP:
                removeMessages(ACTOR_LOOP_PING);
                removeMessages(ACTOR_LOOP_STATUS);
                sendEmptyMessageDelayed(ACTOR_LOOP_PING, 1000);
                sendEmptyMessageDelayed(ACTOR_LOOP_STATUS, 5000);
                break;
            // ping循环
            case ACTOR_LOOP_PING:
                sendEmptyMessageDelayed(ACTOR_LOOP_PING, PING_DELAY);
                if (!actorAdapter.isLastRequestSucceed()) {
                    Logger.w(TAG, "Last request failed, try reset channel.");
                    actorAdapter.resetChannel();
                }
                actorAdapter.onPing();
                actorAdapter.onNext(ActorMessageBuilder.ping());
                break;
            // status循环
            case ACTOR_LOOP_STATUS:
                Logger.d(TAG, "Send ACTOR_LOOP_STATUS");
                sendEmptyMessageDelayed(ACTOR_LOOP_STATUS, STATUS_DELAY);
                Logger.d(TAG, "Send ACTOR_LOOP_STATUS onNext");
                actorAdapter.onNext(ActorMessageBuilder.deviceStatus());
                actorAdapter.onNext(ActorMessageBuilder.appInfos());
                break;
            // 发送msg到node
            case ACTOR_SEND_TO_NODE:
                try {
                    actorAdapter.onNext((com.google.protobuf.Message) msg.obj);
                } catch (Exception e) {
                    Logger.e(TAG, e.toString(), e);
                }
                break;
            default:
                break;
        }
    }

    public void sendMessageInActorQueue(com.google.protobuf.Message _msg) {
        android.os.Message osMsg = new android.os.Message();
        osMsg.obj = _msg;
        osMsg.what = ACTOR_SEND_TO_NODE;
        sendMessage(osMsg);
    }

    private SSLSocketFactory getSslContext() {
        try {
            // Load CAs from an InputStream
            InputStream is = ActApp.getInstance().getAssets().open("node.crt");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            // Create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null);
            X500Principal principal = cert.getSubjectX500Principal();
            keyStore.setCertificateEntry(principal.getName("RFC2253"), cert);
            // Create a TrustManager that trusts the CAs in our KeyStore
            String algorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
            trustManagerFactory.init(keyStore);
            // Create an SSLContext that uses our TrustManager
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
            return sslContext.getSocketFactory();
        } catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException |
                 KeyManagementException e) {
            Logger.e(TAG, "Init ssl context failed: " + e);
            e.printStackTrace();
        }
        return null;
    }
}