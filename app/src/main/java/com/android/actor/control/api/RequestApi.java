package com.android.actor.control.api;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.monitor.Logger;
import com.android.actor.script.lua.LuaLogger;
import com.android.actor.script.lua.LuaUtils;
import com.android.actor.utils.downloader.AmazonS3Manager;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RequestApi {
    private static final String TAG = RequestApi.class.getSimpleName();
    private final OkHttpClient mHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final LuaLogger mLogger;

    public RequestApi(LuaLogger logger) {
        mLogger = logger;
    }

    private final static String LUA_EVENT_URL = "";

    /**
     * 普通 get
     *
     * @param url
     * @return
     */
    public LuaTable get(String url) throws IOException {
        return get(url, null);
    }

    /**
     * 带参数的 get
     *
     * @param url
     * @param params 用 lua table 存放各参数，这样不用自己拼 url，默认加上serial参数，便于各种记录
     * @return
     */
    public LuaTable get(String url, LuaTable params) {
        try {
            HttpUrl httpUrl = HttpUrl.parse(url);
            HttpUrl.Builder builder = httpUrl.newBuilder();
            boolean hasSerial = false;
            if (params != null) {
                for (LuaValue key : params.keys()) {
                    builder.addQueryParameter(key.toString(), params.get(key).toString());
                    if (key.toString().equals("serial")) {
                        hasSerial = true;
                    }
                }
            }
            if (!hasSerial) {
                builder.addQueryParameter("serial", DeviceInfoManager.getInstance().getSerial());
            }
            Request request = new Request.Builder()
                    .url(builder.build())
                    .build();
            mLogger.v(TAG, "get " + request.url());
            Response response = mHttpClient.newCall(request).execute();
            mLogger.v(TAG, "get response code " + response.code());
            return LuaUtils.jsonToTable(response.body().string());
        } catch (IOException | JSONException e) {
            mLogger.e(TAG, "get failed: " + e.toString());
            return LuaValue.tableOf();
        }
    }

    /**
     * 普通 post
     *
     * @param url         默认加上serial参数，便于各种记录
     * @param body        字符串
     * @param contentType 可为空
     * @return
     */
    public LuaTable post(String url, String body, String contentType) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(addSerialQueryString(url))
                    .post(RequestBody.create(contentType != null ? MediaType.parse(contentType) : null, body));
            if (contentType != null) {
                builder.addHeader("Content-Type", contentType);
            }
            Request request = builder.build();
            mLogger.v(TAG, "post " + request.url());
            Response response = mHttpClient.newCall(request).execute();
            mLogger.v(TAG, "post response code " + response.code());

            return LuaUtils.jsonToTable(response.body().string());
        } catch (IOException | JSONException e) {
            mLogger.e(TAG, "post failed: " + e.toString());
            return LuaValue.tableOf();
        }
    }

    /**
     * Post json，如果是 json 格式，请用这个接口
     *
     * @param url
     * @param body json 格式的字符串
     * @return
     */
    public LuaTable postJson(String url, String body) {
        return post(url, body, "application/json");
    }

    /**
     * Post json，如果是 json 格式，请用这个接口
     *
     * @param url
     * @param body lua table 格式
     * @return
     * @throws IOException
     */
    public LuaTable postJson(String url, LuaTable body) {
        JSONObject jBody = new JSONObject();
        for (LuaValue key : body.keys()) {
            jBody.put(key.toString(), body.get(key).toString());
        }
        return postJson(url, jBody.toString());
    }

    /**
     * 上传截图，zip包等，只有数据没有其它参数则用这个接口
     *
     * @param url
     * @param fileName
     * @param bytes
     * @return
     */
    public LuaTable postBytes(String url, String fileName, byte[] bytes) {
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName,
                        RequestBody.create(MediaType.parse("application/octet-stream"), bytes))
                .build();
        return postBody(url, body);
    }

    /**
     * 上传截图，zip包等，除数据外还有其它参数则用这个接口
     *
     * @param url
     * @param params lua table，其中 "file" 里面放数据
     * @return
     */
    public LuaTable postForm(String url, LuaTable params) {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (LuaValue key : params.keys()) {
            if (key.toString().equals("file")) {
                Logger.d(TAG, "postForm file");
                LuaString value = (LuaString) params.get(key);
                builder.addFormDataPart("file", "bytes",
                        RequestBody.create(MediaType.parse("application/octet-stream"), value.m_bytes));
            } else {
                builder.addFormDataPart(key.toString(), params.get(key).toString());
            }
        }
        return postBody(url, builder.build());
    }

    /**
     * postMediaForm，可以自定义指定格式类型，使用例子：
     * local bytes1 = g_driver:takeShot()
     * g_api:postMediaForm(url, 'file1', 'img1.jpg', 'image/jpeg', bytes1)
     *
     * @param url      请求接口的url
     * @param name     form表单名
     * @param filename 文件名，可以为空，默认名为bytes
     * @param type     文件类型
     * @param value    数据，通常是byte[]
     * @return 失败返回空的LuaTable，成功返回接口返回的json转成的LuaTable
     */
    @Deprecated
    public LuaTable postMediaForm(String url, String name, String filename, String type, byte[] value) {
        try {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            if (StringUtils.isEmpty(url) || StringUtils.isEmpty(name) || StringUtils.isEmpty(type) || value == null) {
                mLogger.e("postForm error: name, type or value cannot be empty");
                return LuaValue.tableOf();
            }
            mLogger.d(TAG, "post: {name='" + name + "', filename='" + filename + "', type='" + type + "', content-length=" + value.length + "}");
            builder.addFormDataPart(name, filename == null ? "bytes" : filename,
                    RequestBody.create(MediaType.parse(type), value));
            return postBody(url, builder.build());
        } catch (Exception e) {
            mLogger.e(TAG, "postForm error: " + e.toString(), e);
        }
        return LuaValue.tableOf();
    }

    /**
     * postForm 通用表单发送，发送数据的同时支持文件上传
     *
     * @param url     请求接口的url
     * @param payload 非文件相关的表单数据， 不能为空
     * @param file    文件相关数据，可以传入的key包括name、filename、filetype、file，分别对应表单字段名、文件名、文件类型和文件内容（bytes类型）
     * @return 失败返回空的LuaTable, 成功返回接口返回的json转成LuaTable的结果
     */
    public LuaTable postForm(String url, LuaTable payload, LuaTable file) {
        try {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            if (StringUtils.isEmpty(url)) {
                mLogger.e("postForm error: url cannot be empty");
                return LuaValue.tableOf();
            }
            if (file != null) {
                if (file.get("file").isnil()) {
                    mLogger.e("postForm error: file field must not be empty");
                    return LuaValue.tableOf();
                }
            }
            for (LuaValue key : payload.keys()) {
                builder.addFormDataPart(key.toString(), payload.get(key).toString());
                mLogger.d("postForm add payload: " + key.toString() + ", " + payload.get(key).toString());
            }
            if (file != null) {
                String name = file.get("name").toString();
                if (name.equals("nil")) {
                    name = "file";
                }
                String filename = file.get("filename").toString();
                if (filename.equals("nil")) {
                    filename = "file";
                }
                String filetype = file.get("filetype").toString();
                if (filetype.equals("nil")) {
                    filetype = "application/octet-stream";
                }
                mLogger.d("postForm name: " + name + " filename: " + filename + " filetype: " + filetype);
                LuaString value = (LuaString) file.get("file");
                builder.addFormDataPart(name, filename, RequestBody.create(MediaType.parse(filetype), value.m_bytes));
            }
            return postBody(url, builder.build());
        } catch (Exception e) {
            mLogger.e(TAG, "postForm error: " + e.toString(), e);
        }
        return LuaValue.tableOf();
    }

    private LuaTable postBody(String url, RequestBody body) {
        try {
            Request request = new Request.Builder()
                    .url(addSerialQueryString(url))
                    .method("POST", body)
                    .build();
            mLogger.v(TAG, "post " + request.url());
            Response response = mHttpClient.newCall(request).execute();
            mLogger.v(TAG, "post response code " + response.code());

            return LuaUtils.jsonToTable(response.body().string());
        } catch (IOException | JSONException e) {
            mLogger.e(TAG, "postBody failed: " + e.toString());
            return LuaValue.tableOf();
        }
    }

    /**
     * @param app    lua操作的app名称，不能为空，如果是测试lua可以随意填写
     * @param action 当前事件名称，不能为空
     * @param desc   当前事件描述，不能为空
     * @return
     */
    public boolean uploadEvent(String app, String action, String desc) {
        mLogger.v(TAG, "uploadEvent app " + app + ", action " + action + ", desc " + desc);
        try {
            HttpUrl httpUrl = HttpUrl.parse(LUA_EVENT_URL);
            HttpUrl.Builder builder = httpUrl.newBuilder();
            builder.addQueryParameter("app", app);
            builder.addQueryParameter("serial", DeviceInfoManager.getInstance().getSerial());
            builder.addQueryParameter("action", action);
            builder.addQueryParameter("desc", desc);
            Request request = new Request.Builder()
                    .url(builder.build())
                    .build();
            Response response = mHttpClient.newCall(request).execute();
            JSONObject ret = JSONObject.parseObject(response.body().string());
            return ret.getBoolean("ok");
        } catch (IOException e) {
            mLogger.e(TAG, "uploadEvent failed： " + e.toString());
            return false;
        }
    }

    public static HttpUrl addSerialQueryString(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        HttpUrl.Builder builder = httpUrl.newBuilder();
        if (httpUrl.queryParameter("serial") == null) {
            builder.addQueryParameter("serial", DeviceInfoManager.getInstance().getSerial());
        }
        return builder.build();
    }

    public static LuaTable uploadToS3(String filePath, String key, boolean publicRead) {
        LuaTable table = LuaValue.tableOf();
        String ret = AmazonS3Manager.getInstance().uploadToS3(filePath, key, publicRead);
        if (ret.startsWith("https")) {
            table.set("ok", LuaValue.TRUE);
            table.set("data", ret);
        } else {
            table.set("ok", LuaValue.FALSE);
            table.set("reason", ret);
        }
        return table;
    }

    public static LuaTable uploadToS3(String filePath, String key) {
        return uploadToS3(filePath, key, true);
    }

    public static String downloadFromS3AsString(String key) {
        return AmazonS3Manager.getInstance().downloadFromS3AsString(key);
    }

    public static void downloadFromS3AsFile(String key) {
        AmazonS3Manager.getInstance().downloadFromS3AsFile(key, ActApp.getInstance().getDir("download", 0).getPath() + File.separator + key);
    }

    public static void downloadFromS3AsFile(String key, String filePath) {
        AmazonS3Manager.getInstance().downloadFromS3AsFile(key, filePath);
    }

    public static LuaTable listS3Key(String prefix) {
        return LuaUtils.listToTable(AmazonS3Manager.getInstance().listS3Key(prefix));
    }

    public static LuaTable listS3Object(String prefix) {
        return LuaUtils.listToTable(AmazonS3Manager.getInstance().listS3Object(prefix));
    }
}
