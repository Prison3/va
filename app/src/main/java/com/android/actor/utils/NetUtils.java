package com.android.actor.utils;

import android.annotation.SuppressLint;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.device.DeviceNumber;
import com.android.actor.monitor.Logger;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetUtils {

    private static final String TAG = NetUtils.class.getSimpleName();

    public static String GET_IP_URL = "http://lumtest.com/myip.json";
    private static final OkHttpClient sHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .sslSocketFactory(noSSLSocketFactory())
            .hostnameVerifier((hostname, session) -> true)
            .build();
    private static final ExecutorService sExecutor = Executors.newCachedThreadPool();

    public static String macBytesToString(byte[] addr) {
        if (addr == null) {
            return "";
        }
        StringBuffer buf = new StringBuffer();
        for (byte b : addr) {
            buf.append(String.format("%02X:", b));
        }
        if (buf.length() > 0) {
            buf.deleteCharAt(buf.length() - 1);
        }
        return buf.toString();
    }

    public static byte[] macStringToBytes(String mac) {
        byte[] bArr = new byte[6];
        String[] split = mac.split(":");
        for (int i = 0; i < split.length; i++) {
            bArr[i] = (byte) Integer.parseInt(split[i], 16);
        }
        return bArr;
    }

    // http://www.java2s.com/example/java-src/pkg/org/springframework/util/socketutils-bd0bb.html
    public static int findAvailablePort() {
        int port;
        int i = 0;
        int maxCount = 100;
        while (i < maxCount) {
            try {
                ServerSocket socket = new ServerSocket(0);
                port = socket.getLocalPort();
                socket.close();
                return port;
            } catch (Exception e) {
                Logger.w(TAG, "Try to find available local port exception.", e);
            }
            ++i;
        }
        throw new RuntimeException("Can't find available port after " + maxCount + " times.");
    }

    public static SSLSocketFactory noSSLSocketFactory() {
        try {
            TrustManager[] trustAllManager = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllManager, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static OkHttpClient getHttpClient() {
        return sHttpClient;
    }

    public static String httpHead(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .build();
            Response response = sHttpClient.newCall(request).execute();
            if (response.code() == 200) {
                return response.headers().toString();
            }
            Logger.w(TAG, "httpHead " + url + " response code " + response.code());
            return null;
        } catch (Throwable e) {
            Logger.w(TAG, "httpHead " + url + " exception, " + e);
            return null;
        }
    }

    public static String httpGet(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = sHttpClient.newCall(request).execute();
            if (response.code() == 200) {
                return response.body().string();
            }
            Logger.w(TAG, "httpGet " + url + " response code " + response.code());
            return null;
        } catch (Throwable e) {
            Logger.w(TAG, "httpGet " + url + " exception.", e);
            return null;
        }
    }

    public static JSONObject httpGetJson(String url) {
        try {
            String content = httpGet(url);
            return JSON.parseObject(content);
        } catch (Throwable e) {
            Logger.e(TAG, "httpGetJson " + url + " exception.", e);
            return null;
        }
    }

    public static boolean testProxyPort(int port) {
        Logger.d(TAG, "testProxyPort on 127.0.0.1:" + port);
        BlockingReference<Boolean> blockingReference = new BlockingReference<>();
        for (String url : new String[] {
                "http://spider-proxy.sheincorp.cn/test",
                "http://g.cn"
        }) {
            sExecutor.execute(new TestProxyRunnable(port, url, blockingReference));
        }
        try {
            return blockingReference.take();
        } catch (InterruptedException e) {
            Logger.e(TAG, "testProxyPort blockingReference.take() exception.", e);
            return false;
        }
    }

    static class TestProxyRunnable implements Runnable {

        int port;
        String url;
        BlockingReference<Boolean> blockingReference;

        TestProxyRunnable(int port, String url, BlockingReference<Boolean> blockingReference) {
            this.port = port;
            this.url = url;
            this.blockingReference = blockingReference;
        }

        @Override
        public void run() {
            boolean result = false;
            try {
                OkHttpClient httpClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", port)))
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .cache(null)
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Cache-Control", "no-cache")
                        .build();

                Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful() || response.code() == 301 || response.code() == 302) {
                    Logger.d(TAG, "testProxyPort " + port + " Network is ok. response.code: " + response.code() + ", url " + url);
                    result = true;
                } else {
                    Logger.d(TAG, "testProxyPort " + port + " Unexpected network response, code: " + response.code() + ", url " + url);
                }
            } catch (Throwable e) {
                Logger.d(TAG, "testProxyPort " + port + " occur exception" + e + ", url " + url);
            }
            blockingReference.put(result);
        }
    }

    public static void metricCounter(String name, String label1, String label2, String label3) {
        sExecutor.submit(() -> {
            httpGet(String.format("http://47.251.53.181:8882/metric_counter?name=%s&label1=%s&label2=%s&label3=%s&serial=%s",
                    name, label1 != null ? label1 : "", label2 != null ? label2 : "", label3 != null ? label3 : "", DeviceNumber.get()));
        });
    }

    @SuppressLint("DefaultLocale")
    public static void metricGauge(String name, String label1, String label2, String label3, int value, Optional<Integer> expire) {
        sExecutor.submit(() -> {
            httpGet(String.format("http://47.251.53.181:8882/metric_gauge?name=%s&label1=%s&label2=%s&label3=%s&serial=%s&value=%s&expire=%d",
                    name, label1 != null ? label1 : "", label2 != null ? label2 : "", label3 != null ? label3 : "", DeviceNumber.get(), value, expire.orElse(600)));
        });
        // 单位是秒

    }

    public enum SpiderProxyStatus {
        proxy_not_engaged(0),
        proxy_in_use(1),
        proxy_blocked(2);

        private final int value;

        SpiderProxyStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public static final String SPIDER_PROXY_URL = "http://spider-proxy.sheincorp.cn/changed_proxy_status/?";

    public static void reportSpiderProxy(String proxy, SpiderProxyStatus spiderProxyStatus) {
        int status = spiderProxyStatus.value;
        String url = spiderProxyStatus + "ip=" + proxy + "&status=" + status;

        Logger.d(TAG, "reportSpiderProxy url: " + url);
//        httpGet(url);

    }

}
