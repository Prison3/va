package com.android.actor.grpc;

import static com.android.actor.device.FlashConfig.NO_SELF_BUILD_WEBVIEW;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.os.SystemProperties;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActAccessibility;
import com.android.actor.ActApp;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.control.SelfUpdater;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.device.DeviceNumber;
import com.android.actor.device.FlashConfig;
import com.android.actor.fi.FIEntryPoint;
import com.android.actor.fi.FIRequest;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.top.Top;
import com.android.actor.utils.top.TopLineItem;
import com.android.proto.actor.*;
import com.android.proto.common.*;
import com.android.proto.nonblocking.WEditorResp;
import com.google.protobuf.ByteString;

import java.util.List;
import java.util.Map;


public class ActorMessageBuilder {
    private final static String TAG = ActorMessageBuilder.class.getSimpleName();

    // !!! note: all string params must add checkNull().

    public static HelloResp hello() {
        return HelloResp.newBuilder().build();
    }

    public static Ping ping() {
        int timeStampSecond = (int) (System.currentTimeMillis() / 1000);
        String versionRelease = DeviceInfoManager.getInstance().optProp("ro.build.version.release");
        String productModel = DeviceInfoManager.getInstance().optProp("ro.product.device");
        String serial = DeviceNumber.has() ? DeviceNumber.get() : DeviceInfoManager.getInstance().getSerial();
        int actorVer = 0;
        int dexVer = 0;
        int aidlVer = 0;
        int newStageVer = 0;
        int romVer = 0;
        int twebviewVer = 0;
        int webviewVer = 0;
        int chromiumVer = 0;
        if (SelfUpdater.instance().enabled()) {
            try { // don't know package info can be get or not if apk is installing.
                actorVer = ActPackageManager.getInstance().getVersionCode(RocketComponent.PKG_ALPHA);
                newStageVer = ActPackageManager.getInstance().getVersionCode(RocketComponent.PKG_NEW_STAGE);
                romVer = SystemProperties.getInt("ro.build.version.incremental", 0);
                webviewVer = ActPackageManager.getInstance().getVersionCode(RocketComponent.PKG_WEBVIEW);
                chromiumVer = ActPackageManager.getInstance().getVersionCode(RocketComponent.PKG_CHROMIUM);
                twebviewVer = ActPackageManager.getInstance().getVersionCode(RocketComponent.PKG_TWEBVIEW);
                dexVer = ActPackageManager.getInstance().getVersionCode(RocketComponent.PKG_TWEAKS_RELEASE_PKG);
                aidlVer = ActPackageManager.getInstance().getVersionCode(RocketComponent.PKG_TWEAKS_FRAMEWORK);
            } catch (Throwable e) {
                Logger.e(TAG, "ping: ", e);
            }
        }
        if (FlashConfig.has(NO_SELF_BUILD_WEBVIEW)) {
            twebviewVer = Integer.MAX_VALUE;
            webviewVer = Integer.MAX_VALUE;
        }
        if (SelfUpdater.instance().isNoDelay() && romVer > 1600699973) {
            romVer = 0;
        }
        return Ping.newBuilder()
                .setTimestamp(timeStampSecond)
                .setOsVersion(checkNull(versionRelease))
                .setModel(checkNull(productModel))
                .setSerial(checkNull(serial))
                .setActorVersion(actorVer)
                .setDexVersion(dexVer)
                .setAidlVersion(aidlVer)
                .setNewStageVersion(newStageVer)
                .setRomVersion(romVer)
                .setTwebviewVersion(twebviewVer)
                .setWebviewVersion(webviewVer)
                .setChromiumVersion(chromiumVer)
                .build();
    }

    public static AppInfos appInfos() {
        String serial = DeviceNumber.has() ? DeviceNumber.get() : DeviceInfoManager.getInstance().getSerial();
        AppInfos.Builder builder = AppInfos.newBuilder()
                .setSerial(serial);
        FIEntryPoint.getPackages().forEach(packageName -> {
            if (ActPackageManager.getInstance().isAppInstalled(packageName)) {
                builder.addAppInfos(appInfo(packageName));
            }
        });
        return builder.build();
    }

    public static AppInfo appInfo(String packageName) {
        return AppInfo.newBuilder()
                .setPackageName(packageName)
                .setVersionCode(ActPackageManager.getInstance().getVersionCode(packageName))
                .setVersionName(ActPackageManager.getInstance().getVersionName(packageName))
                .setReady(FIRequest.get(packageName).isReady())
                .build();
    }

    public static DeviceStatus deviceStatus() {
        DeviceStatus.Builder builder = DeviceStatus.newBuilder();
        builder.setSerial(DeviceNumber.has() ? DeviceNumber.get() : DeviceInfoManager.getInstance().getSerial());
        builder.setWifi(checkNull(DeviceInfoManager.getInstance().getConnManager().getConnectionInfo()));
        builder.setRomVersion(checkNull(DeviceInfoManager.getInstance().optProp("ro.build.new_stage_image")));
        builder.setBootVersion(checkNull(DeviceInfoManager.getInstance().optBootVersion()));
        builder.setTime(System.currentTimeMillis());
        builder.setElapsedTime(SystemClock.elapsedRealtime());
        if (ActAccessibility.isStart()) {
            assert ActAccessibility.getInstance() != null;
            if (ActAccessibility.getInstance().mBatteryReceiver != null) {
                builder.setBatteryCurrent(ActAccessibility.getInstance().mBatteryReceiver.getBatteryPercentage());
                builder.setBatteryTemperature(ActAccessibility.getInstance().mBatteryReceiver.getBatteryTemperature());
            }
        }
        try {
            Top top = new Top();
            builder.setCpu(top.getCpuUsage());
            builder.setMem(top.getMemoryFree());
            for (TopLineItem item : top.getTopArray()) {
                builder.addTopApps(getTopMessage(item));
            }
        } catch (Exception e) {
            Logger.e(TAG, e.toString());
            e.printStackTrace();
        }
        return builder.build();
    }

    public static com.android.proto.actor.Notification notification(String level, String msg) {
        String serial = DeviceNumber.has() ? DeviceNumber.get() : DeviceInfoManager.getInstance().getSerial();
        return com.android.proto.actor.Notification
                .newBuilder()
                .setSerial(checkNull(serial))
                .setLevel(checkNull(level))
                .setMsg(checkNull(msg))
                .build();
    }

    private static TopMessage getTopMessage(TopLineItem item) {
        TopMessage.Builder builder = TopMessage.newBuilder();
        builder.setPid(item.getPid());
        builder.setUser(checkNull(item.getUser()));
        builder.setVirtualMemory(item.getVirtualMemory());
        builder.setResidentMemory(item.getResidentMemory());
        builder.setShareMemory(item.getShareMemory());
        builder.setStatus(checkNull(item.getStatus()));
        builder.setCpu(item.getCpuPercentage());
        builder.setMem(item.getMemoryPercentage());
        builder.setTime(checkNull(item.getTimeUsage()));
        builder.setArgs(checkNull(item.getArgs()));
        return builder.build();
    }

    public static GetInstalledAppResp installedAppList(String clientId) {
        List<PackageInfo> pkgInfoList = ActPackageManager.getInstance().getInstalledPackage();
        GetInstalledAppResp.Builder builder = GetInstalledAppResp.newBuilder();
        builder.setClientId(checkNull(clientId));
        for (PackageInfo pkgInfo : pkgInfoList) {
            builder.putApps(checkNull(pkgInfo.packageName), checkNull(pkgInfo.versionName));
        }
        return builder.build();
    }

    public static DeviceMockResp deviceMock(String clientId, boolean success, String reason) {
        return DeviceMockResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static DeviceUnmockResp deviceUnmock(String clientId, boolean success, String reason) {
        return DeviceUnmockResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static GetDeviceMockParamsResp getDeviceMockParams(String clientId, boolean success, String data, String reason) {
        return GetDeviceMockParamsResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setData(checkNull(data))
                .setReason(checkNull(reason))
                .build();
    }

    public static ManageAppResp manageApp(String clientId, boolean success, String reason) {
        return ManageAppResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static RebootResp reboot(String clientId, boolean success, String reason) {
        return RebootResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static ProxyResp proxy(String clientId, boolean success, String reason) {
        return ProxyResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static RunScriptResp runScript(String clientId, String taskId, boolean success, String reason) {
        return RunScriptResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static RunScriptCheckingResp runScriptChecking(String clientId, String taskId, String status, String log, String result,String record) {
        return RunScriptCheckingResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setStatus(checkNull(status))
                .setLog(checkNull(log))
                .setScriptResult(checkNull(result))
                .setScriptRecord(checkNull(record))
                .build();
    }

    public static StopScriptResp stopScript(String clientId, String taskId, boolean success, String reason) {
        return StopScriptResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static XposedApiResp xposedApi(String clientId, boolean success, String reason, String body, String proxy) {
        return XposedApiResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .setBody(checkNull(body))
                .setProxy(checkNull(proxy))
                .build();
    }

    public static WEditorResp weditor(String clientId, boolean success, String reason, String body, ByteString binary) {
        return WEditorResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .setBody(checkNull(body))
                .setBinary(checkBinary(binary))
                .build();
    }

    public static String checkNull(String s) {
        return s == null ? "" : s;
    }

    public static ByteString checkBinary(ByteString binary) {
        return binary == null ? ByteString.EMPTY : binary;
    }

    public static SmsMessage smsMessage(boolean send, String from, String to, String content, long timestamp) {
        return SmsMessage.newBuilder()
                .setSend(send)
                .setFrom(checkNull(from))
                .setTo(checkNull(to))
                .setContent(checkNull(content))
                .setTimestamp(timestamp)
                .build();
    }

    public static String smsMessageCloud(String from, String to, String content, long timestamp) {
        String serial = DeviceNumber.has() ? DeviceNumber.get() : DeviceInfoManager.getInstance().getSerial();
        String msg = "sms_message=" + content + "&from=" + from + "&to=" + to + "&serial=" + serial + "&timestamp=" + timestamp;
        Logger.d(TAG, "sms_content:" + msg);
        return msg;
    }

    public static SmsUploadReq smsUpload(String uuid, SmsMessage msg) {
        String serial = DeviceNumber.has() ? DeviceNumber.get() : DeviceInfoManager.getInstance().getSerial();
//        Logger.d(TAG, "smsUpload serial:" + serial + ", uuid:" + uuid);
        SmsUploadReq.Builder builder = SmsUploadReq.newBuilder()
                .setSerial(checkNull(serial))
                .setUuid(checkNull(uuid));
        if (msg != null) {
            builder.setMsg(msg);
        }
        return builder.build();
    }

    public static SmsHistoryResp smsHistory(String clientId, int total, boolean success, String reason, List<SmsMessage> records) {
        SmsHistoryResp.Builder builder = SmsHistoryResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTotal(total)
                .setSuccess(success)
                .setReason(checkNull(reason));
        if (records != null) {
            for (SmsMessage record : records) {
                builder.addHistory(record);
            }
        }
        return builder.build();
    }

    public static SmsSendResp smsSend(String clientId, boolean success, String reason, SmsMessage msg) {
        String serial = DeviceInfoManager.getInstance().getSerial();
        SmsSendResp.Builder builder = SmsSendResp.newBuilder()
                .setSerial(checkNull(serial))
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setReason(checkNull(reason));
        if (msg != null) {
            builder.setMsg(msg);
        }
        return builder.build();
    }

    public static TRegisterNewResp t_registerNew(String clientId, String taskId, boolean success, String reason) {
        return TRegisterNewResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static TCheckRegisterResp t_checkRegister(String clientId, String taskId, String status, String log, String result, String registerInformation) {
        return TCheckRegisterResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setStatus(checkNull(status))
                .setLog(checkNull(log))
                .setScriptResult(checkNull(result))
                .setRegisterInformation(checkNull(registerInformation))
                .build();
    }

    public static TSwipeSyncResp t_swipeSync(String clientId, String taskId, boolean success, String reason) {
        return TSwipeSyncResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static TSwipeEndResp t_swipeEnd(String clientId, String taskId, boolean success, String reason) {
        return TSwipeEndResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static AirplaneModeStartAndEndResp airplaneModeStartAndEnd(String clientId, boolean success, String serial, String exportIp) {
        return AirplaneModeStartAndEndResp.newBuilder()
                .setSerial(checkNull(serial))
                .setClientId(checkNull(clientId))
                .setSuccess(success)
                .setExportIp(exportIp)
                .build();
    }

    public static CheckAirplaneModeStartAndEndResp checkAirplaneModeStartAndEnd(String clientId, boolean success, String serial, String exportIpBeforeSwitching, String exportIpAfterSwitching) {
        return CheckAirplaneModeStartAndEndResp.newBuilder()
                .setSerial(checkNull(serial))
                .setClientId(checkNull(clientId))
                .setExportIpBeforeSwitching(exportIpBeforeSwitching)
                .setExportIpAfterSwitching(exportIpAfterSwitching)
                .setSuccess(success)
                .build();
    }

    public static TGetDetailResp tGetDetail(String clientId, String serial, String taskId, Map<String, String> result, boolean success) {
        return TGetDetailResp.newBuilder()
                .setSuccess(success)
                .setClientId(checkNull(clientId))
                .setSerial(checkNull(serial))
                .setTaskId(checkNull(taskId))
                .putAllResult(result)
                .build();
    }

    public static TCheckDetailResp tCheckDetail(String clientId, String serial, String taskId, String status, String log, String scriptResult, Map<String, String> result) {
        return TCheckDetailResp.newBuilder()
                .setSerial(checkNull(serial))
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setStatus(checkNull(status))
                .setLog(checkNull(checkNull(log)))
                .setScriptResult(checkNull(scriptResult))
                .putAllResult(result)
                .build();
    }

    public static AppRegisterResp appRegister(String clientId, String taskId, boolean success, String reason) {
        return AppRegisterResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setTaskId(checkNull(taskId))
                .setSuccess(success)
                .setReason(checkNull(reason))
                .build();
    }

    public static GetSpInformationResp getSpInformation(String clientId, String serial, String moduleName, String key, String value) {
        return GetSpInformationResp.newBuilder()
                .setClientId(checkNull(clientId))
                .setSerial(checkNull(serial))
                .setModuleName(checkNull(moduleName))
                .setKey(checkNull(key))
                .setValue(checkNull(value))
                .build();
    }

}