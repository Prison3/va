package com.android.actor.models;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.baidu.paddle.fastdeploy.LitePowerMode;
import com.baidu.paddle.fastdeploy.RuntimeOption;
import com.baidu.paddle.fastdeploy.vision.ClassifyResult;
//import com.baidu.paddle.fastdeploy.vision.classification.RotationClasModel;
import com.android.actor.ActApp;
import com.android.actor.models.ModelConfig;
import com.android.actor.monitor.Logger;
import java.io.File;
import java.util.UUID;

public class RotationAI {
//    private static final String TAG = "RotationAI";
//    private static RotationAI instance = null;
//    private static boolean isDirty = false;
//    private RotationClasModel predictor = new RotationClasModel();
//
//    public boolean isReady() {
//        return ModelConfig.instance.isReady(ModelConfig.ModelType.Rotation) && this.predictor.initialized() && !isDirty;
//    }
//
//    public static void setDirty() {
//        Logger.d(TAG, "set rotation model dirty to prepare updating");
//        isDirty = true;
//    }
//
//    public static synchronized RotationAI getInstance() {
//        RotationAI rotationAI;
//        synchronized (RotationAI.class) {
//            if (instance == null) {
//                instance = new RotationAI();
//            }
//            rotationAI = instance;
//        }
//        return rotationAI;
//    }
//
//    public boolean release() {
//        if (this.predictor.initialized()) {
//            return this.predictor.release();
//        }
//        return false;
//    }
//
//    public boolean reload() {
//        if (this.predictor.initialized() && !this.predictor.release()) {
//            Logger.e(TAG, "failed to release old rotation model");
//            return false;
//        } else if (ModelConfig.instance.isReady(ModelConfig.ModelType.Rotation)) {
//            isDirty = false;
//            String modelDir = ModelConfig.instance.getModelDir(ModelConfig.ModelType.Rotation);
//            String str = modelDir + File.separator + "model.pdmodel";
//            String str2 = modelDir + File.separator + "model.pdiparams";
//            String str3 = modelDir + File.separator + "infer_cfg.yml";
//            RuntimeOption runtimeOption = new RuntimeOption();
//            runtimeOption.setCpuThreadNum(2);
//            runtimeOption.setLitePowerMode(LitePowerMode.LITE_POWER_HIGH);
//            runtimeOption.enableLiteFp16();
//            return this.predictor.init(str, str2, str3, "", runtimeOption);
//        } else {
//            return false;
//        }
//    }
//
//    public int identify(byte[] bArr, boolean z) {
//        ClassifyResult predict;
//        Bitmap decodeByteArray = BitmapFactory.decodeByteArray(bArr, 0, bArr.length);
//        if (!z) {
//            predict = this.predictor.predict(decodeByteArray, false, 0.5f);
//        } else {
//            predict = this.predictor.predict(decodeByteArray, imageCachePath(), 0.5f);
//        }
//        if (predict.initialized()) {
//            return 360 - predict.mLabelIds[0];
//        }
//        return -1;
//    }
//
//    public static String imageCachePath() {
//        return FlyApp.getInstance().getCacheDir().getAbsolutePath() + File.separator + "rotation-" + UUID.randomUUID().toString() + ".jpg";
//    }
}
