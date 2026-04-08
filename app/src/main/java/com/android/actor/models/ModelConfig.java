package com.android.actor.models;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.alibaba.fastjson.JSONObject;
import com.amazonaws.services.s3.internal.Constants;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.FileHelper;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.downloader.DownloadCallback;
import com.android.actor.utils.downloader.DownloadManager;
import org.apache.commons.lang3.RandomUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ModelConfig {
    private static final int MAX_MODEL_DELAY = 300;
    private static final int MSG_MODEL_UNZIP = 2;
    private static final int MSG_MODEL_UPDATING = 1;
    private static final int MSG_NEW_MODEL = 0;
    private static final String TAG = "ModelConfig";
    public static final ModelConfig instance = new ModelConfig();
    private StatusHandler mHandler;
    private HandlerThread mThread;

    public enum ModelType {
        Slider,
        Rotation
    }

    public ModelConfig() {
        HandlerThread handlerThread = new HandlerThread(TAG);
        this.mThread = handlerThread;
        handlerThread.setPriority(1);
        this.mThread.start();
        this.mHandler = new StatusHandler(this.mThread.getLooper());
    }

    public void updateConfig(JSONObject jSONObject) {
        if (jSONObject == null) {
            return;
        }
        String str = null;
        String str2 = null;
        for (String str3 : jSONObject.keySet()) {
            if (str3.equals("slider")) {
                str = jSONObject.getString("slider");
                str2 = ModelType.Slider.name();
            } else if (str3.equals("rotation")) {
                str = jSONObject.getString("rotation");
                str2 = ModelType.Rotation.name();
            }
            if (!ActStringUtils.isEmpty(str)) {
                JSONObject jSONObject2 = new JSONObject();
                jSONObject2.put("type", (Object) str2);
                jSONObject2.put(Constants.URL_ENCODING, (Object) str);
                this.mHandler.obtainMessage(0, jSONObject2).sendToTarget();
            }
        }
    }

    public boolean isReady(ModelType modelType) {
        String string = SPUtils.getString(SPUtils.ModuleFile.ai_models, modelType.name(), "");
        // String string = "/sdcard/picodet_m_416_coco_lcnet_slider_v4";
        Logger.d(TAG, "path is :" + string);
        if (ActStringUtils.isEmpty(string)) {
            Logger.w(TAG, modelType + " model dir is empty");
            return false;
        }
        File file = new File(string);
        if (!file.isDirectory()) {
            Logger.w(TAG, modelType + " model dir " + file.getAbsolutePath() + " doesn't exist");
            return false;
        }
        File file2 = new File(string + File.separator + "model.pdmodel");
        if (!file2.exists()) {
            Logger.w(TAG, modelType + " model file " + file2.getAbsolutePath() + " doesn't exist");
            return false;
        }
        File file3 = new File(string + File.separator + "model.pdiparams");
        if (!file3.exists()) {
            Logger.w(TAG, modelType + " model params " + file3.getAbsolutePath() + " doesn't exit");
            return false;
        }
        File file4 = new File(string + File.separator + "infer_cfg.yml");
        if (file4.exists()) {
            return true;
        }
        Logger.w(TAG, modelType + " infer cfg " + file4.getAbsolutePath() + " doesn't exist");
        return false;
    }

    public String getModelDir(ModelType modelType) {
        return SPUtils.getString(SPUtils.ModuleFile.ai_models, modelType.name(), "");
    }

    public class StatusHandler extends Handler {
        public StatusHandler(Looper looper) {
            super(looper);
        }

        @Override // android.os.Handler
        public void handleMessage(Message message) {
            int i = message.what;
            if (i == 0) {
                Logger.d(TAG, "handleMessage: MSG_NEW_MODEL");
                JSONObject jSONObject = (JSONObject) message.obj;
                String string = jSONObject.getString(Constants.URL_ENCODING);
                String fileName = DownloadManager.instance().getFileName(string);
                File file = new File(DownloadManager.instance().getParent(string).getAbsolutePath() + File.separator + fileName.substring(0, fileName.lastIndexOf(".zip")));
                if (file.exists()) {
                    Logger.i(ModelConfig.TAG, "model dir " + file.getAbsolutePath() + " exists, skip to get new model url: " + string);
                    return;
                }
                int nextInt = RandomUtils.nextInt(0, 300);
                Logger.i(ModelConfig.TAG, "Got new model url: " + string + ", delay " + nextInt + "s to update.");
                sendMessageDelayed(obtainMessage(1, jSONObject), nextInt * 1000);
            } else if (i == 1) {
                final JSONObject jSONObject2 = (JSONObject) message.obj;
                String string2 = jSONObject2.getString(Constants.URL_ENCODING);
                Logger.i(ModelConfig.TAG, "start to update " + string2);
                File file2 = DownloadManager.instance().getFile(string2);
                if (file2 != null) {
                    jSONObject2.put("file", (Object) file2);
                    sendMessage(obtainMessage(2, jSONObject2));
                    return;
                }
                DownloadManager.instance().addDownload(string2, new DownloadCallback() { // from class: com.dragonfly.agent.models.-$$Lambda$ModelConfig$StatusHandler$-Chcj_7Uzab2m569JBaqdRrmG_c
                    @Override // com.dragonfly.agent.utils.downloader.DownloadCallback
                    public final void onEnd(String str, File file3, String str2) {
                        ModelConfig.StatusHandler.this.lambda$handleMessage$0$ModelConfig$StatusHandler(jSONObject2, str, file3, str2);
                    }
                });
            } else if (i == 2) {
                JSONObject jSONObject3 = (JSONObject) message.obj;
                String string3 = jSONObject3.getString("type");
                File file3 = (File) jSONObject3.getObject("file", File.class);
                try {
                    List<String> unzipFile = FileHelper.unzipFile(file3);
                    for (int i2 = 0; i2 < unzipFile.size(); i2++) {
                        if (unzipFile.get(i2).endsWith("model.pdmodel")) {
                            SPUtils.putString(SPUtils.ModuleFile.ai_models, string3, new File(unzipFile.get(i2)).getParent());
                            Logger.i(ModelConfig.TAG, "successfully to unzip model file " + file3.getAbsolutePath());
                            if (string3.equals(ModelType.Slider.name())) {
                                SliderAI.setDirty();
                            } else if (string3.equals(ModelType.Rotation.name())) {
//                                RotationAI.setDirty();
                            }
                        }
                    }
                } catch (IOException unused) {
                    Logger.w(ModelConfig.TAG, "failed to unzip file " + file3.getAbsolutePath());
                }
            }
        }

        public /* synthetic */ void lambda$handleMessage$0$ModelConfig$StatusHandler(JSONObject jSONObject, String str, File file, String str2) {
            if (file == null) {
                Logger.w(ModelConfig.TAG, "failed to download model from url " + str + ", reason: " + str2);
                return;
            }
            jSONObject.put("file", (Object) file);
            sendMessage(obtainMessage(2, jSONObject));
        }
    }
}
