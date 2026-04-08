package com.android.actor.device;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ResUtils;
import com.android.actor.utils.downloader.DeviceFilesUpdater;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NewDeviceGenerator {

    private static final String TAG = NewDeviceGenerator.class.getSimpleName();
    private Context mContext;
    private String mPackageName;
    private int mProfileId;
    /** 资产目录 device_list 失败时为非 null 空列表，避免界面 NPE */
    private List<String> mModelList = new ArrayList<>();
    private String mDeviceModel;
    private boolean mRandomGPS;
    private boolean mUseSim;
    private boolean mIsModify;
    JSONObject mParameters ;
    private Callback.C2<Integer, String> mCallback;

    public static final int MSG_FORCE_STOP_APP      = 0;
    public static final int MSG_SWITCH_PROFILE      = 1;
    public static final int MSG_CLEAR_APP           = 3;
    public static final int MSG_UPDATE_DEVICE_FILES = 4;
    public static final int MSG_MODIFY_DEVICE       = 5;
    public static final int MSG_CLEAR_APP2          = 6;
    public static final int MSG_DONE  = 100;
    public static final int MSG_ERROR = 101;
    private NewDeviceHandler mHandler = new NewDeviceHandler();

    public NewDeviceGenerator(Context context) {
        mContext = context;
        try {
            mModelList = ResUtils.listLAssets("device_list").stream()
                    .map(name -> StringUtils.removeEnd(name, ".json"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Throwable e) {
            Logger.e(TAG, "List models exception.", e);
            mModelList = new ArrayList<>();
        }
    }

    public List<String> getModelList() {
        return mModelList;
    }


    public void generate(String packageName, int profileId, String model, String country, boolean randomGPS, boolean useSim, boolean isModify, Callback.C2<Integer, String> callback) {
        generate(packageName, profileId, model, country, randomGPS, useSim, isModify, new JSONObject(), callback);
    }

    public void generate(String packageName, int profileId, String model, String country, boolean randomGPS, boolean useSim, boolean isModify, JSONObject jParameters, Callback.C2<Integer, String> callback) {
        mPackageName = packageName;
        mProfileId = profileId;
        mDeviceModel = model;
        mRandomGPS = randomGPS;
        mIsModify = isModify;
        mUseSim = useSim;
        mCallback = callback;
        mParameters = jParameters;
        mParameters.put("country", country);
        mHandler.sendEmptyMessage(MSG_FORCE_STOP_APP);
    }

    class NewDeviceHandler extends Handler {

        long mStartTime;

        NewDeviceHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_FORCE_STOP_APP:
                    onStatusChanged(MSG_FORCE_STOP_APP, "ForceStopApp " + mPackageName + ", old uid "
                            + ActPackageManager.getInstance().getUid(mPackageName));
                    mStartTime = System.currentTimeMillis();
                    ActActivityManager.getInstance().forceStopPackage(mPackageName, (success, reason) -> {
                        if (success) {
                            sendEmptyMessage(MSG_SWITCH_PROFILE);
                        } else {
                            sendEmptyMessage(MSG_ERROR);
                        }
                    });
                    break;
                case MSG_SWITCH_PROFILE: {
                    onStatusChanged(MSG_SWITCH_PROFILE, "Switch profile " + mPackageName + " to " + mProfileId);
                    if (mIsModify) {
                        NewStage.instance().switchProfileForMockAsync(mPackageName, mProfileId, reason -> {
                            if (reason == null) {
                                sendEmptyMessage(MSG_CLEAR_APP);
                            } else {
                                sendEmptyMessage(MSG_ERROR);
                            }
                        });
                    } else {
                        sendEmptyMessage(MSG_CLEAR_APP);
                    }
                    break;
                }
                case MSG_CLEAR_APP:
                    onStatusChanged(MSG_CLEAR_APP, "ClearApp " + mPackageName + ", new uid "
                            + ActPackageManager.getInstance().getUid(mPackageName));
                    ActPackageManager.getInstance().clearApp(mPackageName, mProfileId, (packageName, success, reason) -> {
                        if (success) {
                            sendEmptyMessage(MSG_UPDATE_DEVICE_FILES);
                        } else {
                            sendEmptyMessage(MSG_ERROR);
                        }
                    });
                    break;
                case MSG_UPDATE_DEVICE_FILES: {
                    String _model = mDeviceModel;
                    if (_model == null) {
                        List<String> modelList = mModelList;
                        if ("com.einnovation.temu".equals(mPackageName)) {
                            modelList = mModelList.stream().filter(name -> !name.equals("Google | Pixel 6 Pro")).collect(Collectors.toList());
                        }
                        _model = modelList.get(RandomUtils.nextInt(0, modelList.size()));
                    }
                    String model = _model;
                    Logger.d(TAG, "MSG_MODIFY_DEVICE " + model);
                    onStatusChanged(MSG_UPDATE_DEVICE_FILES, "Update device files " + model);

                    if (mPackageName.equals("com.xingin.xhs")) {
                        DeviceFilesUpdater.instance().checkAndDownloadFiles(StringUtils.split(model, '|')[1].trim(), (status, msg1) -> {
                            if (status == DeviceFilesUpdater.STATUS_SUCCESS) {
                                obtainMessage(MSG_MODIFY_DEVICE, model).sendToTarget();
                            } else {
                                sendEmptyMessage(MSG_ERROR);
                            }
                        });
                    } else {
                        obtainMessage(MSG_MODIFY_DEVICE, model).sendToTarget();
                    }
                    break;
                }
                case MSG_MODIFY_DEVICE:
                    String model = (String) msg.obj;
                    onStatusChanged(MSG_MODIFY_DEVICE, "ModifyDevice " + mPackageName + ", model " + model + ", isModify " + mIsModify);
                    if (mIsModify) {
                        String newStagMsg;
                        if (mPackageName.equals(RocketComponent.PKG_CHROMIUM)) {
                            newStagMsg = NewStage.instance().modifyWebview(mPackageName, mProfileId, mParameters.toString());
                        } else {
                            mParameters.put("_model", model);
                            newStagMsg = NewStage.instance().modify(mPackageName, mProfileId, mParameters.toString());
                        }
                        if (newStagMsg != null) {
                            onStatusChanged(MSG_ERROR, "ModifyDevice failed, " + newStagMsg);
                            sendEmptyMessage(MSG_ERROR);
                        } else {
                            sendEmptyMessage(MSG_CLEAR_APP2);
                        }

                    } else {
                        String resetMsg = NewStage.instance().reset(mPackageName, 0);
                        if (resetMsg == null) {
                            sendEmptyMessage(MSG_CLEAR_APP2);
                        } else {
                            sendEmptyMessage(MSG_ERROR);
                        }
                    }
                    break;
                case MSG_CLEAR_APP2: // Clear one more time, just in case.
                    onStatusChanged(MSG_CLEAR_APP2, "ClearApp2 " + mPackageName);
                    ActPackageManager.getInstance().clearApp(mPackageName, mProfileId, (packageName, success, reason) -> {
                        if (success) {
                            sendEmptyMessage(MSG_DONE);
                        } else {
                            sendEmptyMessage(MSG_ERROR);
                        }
                    });
                    break;
                case MSG_DONE:
                    long cost = System.currentTimeMillis() - mStartTime;
                    onStatusChanged(MSG_DONE, "Done, cost " + (cost / 1000) + "s.");
                    break;
                case MSG_ERROR:
                    onStatusChanged(MSG_ERROR, "ERROR!");
                    break;
            }
        }
    }

    private void onStatusChanged(int status, String text) {
        Logger.v(TAG, text);
        mCallback.onResult(status, text);
    }
}
