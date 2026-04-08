package com.android.actor.utils.sms;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.provider.Telephony;
import com.android.actor.ActApp;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.grpc.ActorMessageBuilder;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.netutils.OkHttp3Manager;
import com.android.proto.common.SmsMessage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SmsUtil {
    private static final String TAG = SmsUtil.class.getSimpleName();

    public static void uploadNode(String sender, String content, long time) {
        String uuid = time + "_" + ActStringUtils.md5(content).toLowerCase();
        String data = sender + "@" + content;
        Logger.i(TAG, "receive from " + sender + ", uuid: " + uuid);
        // String phone = DeviceInfoManager.getInstance().getPhoneNumber();
        // 由于这个上报的phonenum的       TelephonyManager.getLine1Number的方法会上报错误的号码信息，因此去掉这里的上报
        SmsMessage msg = ActorMessageBuilder.smsMessage(false, sender, "", content, time);
        GRPCManager.getInstance().uploadSms(uuid, msg);
        // use to check if the sms uploadNode success
        SPUtils.putString(SPUtils.ModuleFile.sms_upload, uuid, data);
    }

    public static void uploadCloud(String sender, String content, long time) throws IOException {
        String uuid = time + "_" + ActStringUtils.md5(content).toLowerCase();
        Logger.i(TAG, "receive from " + sender + ", uuid: " + uuid);
        String phone = DeviceInfoManager.getInstance().getPhoneNumber();
        String msg = ActorMessageBuilder.smsMessageCloud(URLEncoder.encode(sender,"UTF-8"), URLEncoder.encode(phone,"UTF-8"),URLEncoder.encode(content, "UTF-8"), time);
        String url = "http://spider-proxy.sheincorp.cn/phoneRecord/?" + msg;

        Logger.d(TAG, "encodeUrl:" + url);
        new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttp3Manager.getInstance().get(url, new Callback() {
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        // 处理成功响应
                        String responseData = response.body().string();
                        Logger.d(TAG, "responseData :" + responseData);
                        // ...
                    }

                    @Override
                    public void onFailure(Call call, IOException e) {
                        // 处理请求失败
                        e.printStackTrace();
                    }
                });
            }
        }).start();
    }

    public static void onUploaded(String uuid) {
        SPUtils.removeKey(SPUtils.ModuleFile.sms_upload, uuid);
    }

    public static void checkAndReUpload() {
        try {
            Map<String, ?> map = SPUtils.getAll(SPUtils.ModuleFile.sms_upload);
            for (String uuid : map.keySet()) {
                String data = map.get(uuid).toString();
                Logger.i(TAG, "checkNotUpload found: " + uuid + ", redo uploadNode to node");
                try {
                    String[] ws = data.split("@");
                    String[] us = uuid.split("_");
                    String sender = ws[0];
                    String content = ws[1];
                    long time = Long.parseLong(us[0]);
                    String phone = DeviceInfoManager.getInstance().getPhoneNumber();
                    SmsMessage msg = ActorMessageBuilder.smsMessage(false, sender, phone, content, time);
                    GRPCManager.getInstance().uploadSms(uuid, msg);
                } catch (Exception e) {
                    Logger.e(TAG, "checkNotUpload err: " + e.toString(), e);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, e.toString(), e);
        }
    }

    @SuppressLint("Range")
    public static int getSmsTotalCount(String filter) throws Exception {
        int total = 0;
        String selection = null;
        String[] selectionArgs = null;
        if (!ActStringUtils.isEmpty(filter)) {
            selection = Telephony.Sms.BODY + " like ?";
            selectionArgs = new String[]{"%" + filter + "%"};
        }
        Cursor cursor = ActApp.getInstance().getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[]{"count(*)"}, selection, selectionArgs, null);
        if (cursor.moveToNext()) {
            total = cursor.getInt(cursor.getColumnIndex("count(*)"));
        }
        cursor.close();
        return total;
    }

    public static List<SmsMessage> readHistory(String filter, int page, int size) throws Exception {
        List<SmsMessage> records = new ArrayList<>();
        // /data/data/com.android.providers.telephony/databases/mmssms.db, table sms
        if (size == 0) {
            size = 10;
        }
        String selection = null;
        String[] selectionArgs = null;
        if (!ActStringUtils.isEmpty(filter)) {
            selection = Telephony.Sms.BODY + " like ?";
            selectionArgs = new String[]{"%" + filter + "%"};
        }
        Logger.d(TAG, "readHistory filter " + filter + ", size " + size + ", page " + page);
        Cursor cursor = ActApp.getInstance().getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.BODY, Telephony.Sms.TYPE},
                selection, selectionArgs, Telephony.Sms._ID + " desc");
        int i = 0;
        cursor.moveToPosition(page * size - 1);
        while (cursor.moveToNext() && i < (page + 1) * size) {
            @SuppressLint("Range") int id = cursor.getInt(cursor.getColumnIndex(Telephony.Sms._ID));
            @SuppressLint("Range") String address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS));
            @SuppressLint("Range") long date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));
            @SuppressLint("Range") long dateSend = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE_SENT));
            @SuppressLint("Range") String body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
            @SuppressLint("Range") int type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE));
            if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                records.add(ActorMessageBuilder.smsMessage(true, DeviceInfoManager.getInstance().getPhoneNumber(), address, body, date));
            } else if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                records.add(ActorMessageBuilder.smsMessage(false, address, DeviceInfoManager.getInstance().getPhoneNumber(), body, date));
            } else {
                Logger.w(TAG, "Unknown sms type: " + type + ", address: " + address + ", content: " + body);
            }
            i++;
        }
        cursor.close();
        return records;
    }
}
