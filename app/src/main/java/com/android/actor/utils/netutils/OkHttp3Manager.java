package com.android.actor.utils.netutils;

import okhttp3.*;

public class OkHttp3Manager {
    private static OkHttp3Manager instance;
    private OkHttpClient client;

    private OkHttp3Manager() {
        client = new OkHttpClient();
    }

    public static OkHttp3Manager getInstance() {
        if (instance == null) {
            instance = new OkHttp3Manager();
        }
        return instance;
    }

    public void get(String url, Callback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Call call = client.newCall(request);
        call.enqueue(callback);
    }

    public void post(String url, String body, Callback callback) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(mediaType, body);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        Call call = client.newCall(request);
        call.enqueue(callback);
    }
}
