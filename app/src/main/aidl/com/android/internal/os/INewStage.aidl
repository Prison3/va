package com.android.internal.os;

import com.android.internal.os.Parameters;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;
import com.android.internal.os.IMyActionListener;
import com.android.internal.os.IInputMethodCallback;
import com.android.internal.ds.IDexCallback;
import com.android.internal.ds.IDexBinaryCallback;

interface INewStage {

    boolean isReady();

    void registerAppStarted(in RequestV0 info, in IMyActionListener listener);

    ResponseV0 requestAppAction(in RequestV0 request);

    String callbackToScript(in String body);

    void registerScriptCallback(in IDexCallback callback);

    String modify(String packageName, int profileId, in Parameters parameters);

    String reset(String packageName, int profileId);

    List<String> getModifiedPackages();

    String getPackageParameters(String packageName);

    String startActivityAndWait(in Intent intent);

    String getCurrentActivity();

    /**
     * 设置亮度，0 - 255
     */
    void setBrightness(int brightness);

    String getPackageParameterValue(String packageName, String key);

    String getPackageSystemProperty(String packageName, String name);

    void setDnsLogSeverity(int logSeverity);

    void setUidDns(int uid, in String[] servers);

    void clearUidDns(int uid);

    String modifyWebview(String packageName, int profileId, in Parameters parameters);

    void registerInputMethod(in IInputMethodCallback callback);

    void inputText(String text);

    void inputAction(int action);

    String getParameter(int uid, String key);

    int getPackageProfileId(int uid);

    void switchProfile(int uid, int profileId);

    void assignPackageUid(String packageName, int uid);

    String resetByUid(int uid, int profileId);

    void httpGet(String url, in IDexCallback callback);

    void httpGetBinary(String url, in IDexBinaryCallback callback);

    int getGmsUid();

    void deleteSurroundingText(int beforeLength, int afterLength);
}