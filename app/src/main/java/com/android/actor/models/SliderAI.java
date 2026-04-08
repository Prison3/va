package com.android.actor.models;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.baidu.paddle.fastdeploy.LitePowerMode;
import com.baidu.paddle.fastdeploy.RuntimeOption;
import com.baidu.paddle.fastdeploy.vision.DetectionResult;
import com.baidu.paddle.fastdeploy.vision.detection.PicoDet;
import com.android.actor.ActApp;
import com.android.actor.models.ModelConfig;
import com.android.actor.monitor.Logger;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

public class SliderAI {
    static final /* synthetic */ boolean $assertionsDisabled = false;
    private static final String TAG = "SliderAI";
    private static SliderAI instance = null;
    private static boolean isDirty = false;
    private PicoDet predictor = new PicoDet();

    public boolean isReady() {
        Logger.d(TAG, "is slider model ready? " + ModelConfig.instance.isReady(ModelConfig.ModelType.Slider) + ", predictor initialized? " + this.predictor.initialized() + ", is dirty? " + isDirty);
        return ModelConfig.instance.isReady(ModelConfig.ModelType.Slider) && this.predictor.initialized() && !isDirty;
    }

    public static void setDirty() {
        Logger.d(TAG, "set slider model dirty to prepare updating");
        isDirty = true;
    }

    public static synchronized SliderAI getInstance() {
        SliderAI sliderAI;
        synchronized (SliderAI.class) {
            if (instance == null) {
                instance = new SliderAI();
            }
            sliderAI = instance;
        }
        return sliderAI;
    }

    public boolean release() {
        if (this.predictor.initialized()) {
            return this.predictor.release();
        }
        return false;
    }

    public boolean reload() {
        if (this.predictor.initialized() && !this.predictor.release()) {
            Logger.e(TAG, "failed to release old slider model");
            return false;
        } else if (ModelConfig.instance.isReady(ModelConfig.ModelType.Slider)) {
            Logger.d(TAG, "reload slider model");
            isDirty = false;
            String modelDir = ModelConfig.instance.getModelDir(ModelConfig.ModelType.Slider);
//            String modelDir = "/sdcard/picodet_m_416_coco_lcnet_slider_v4";
            String str = modelDir + File.separator + "model.pdmodel";
            String str2 = modelDir + File.separator + "model.pdiparams";
            String str3 = modelDir + File.separator + "infer_cfg.yml";
            String str4 = str + File.separator + "label_list.txt";
            RuntimeOption runtimeOption = new RuntimeOption();
            runtimeOption.setCpuThreadNum(2);
            runtimeOption.setLitePowerMode(LitePowerMode.LITE_POWER_HIGH);
            runtimeOption.enableLiteFp16();
            return this.predictor.init(str, str2, str3, str4, runtimeOption);
        } else {
            return false;
        }
    }

    public int identify(byte[] bArr, int i, int i2, int i3, int i4, boolean z) {
        DetectionResult predict;
        Bitmap decodeByteArray = BitmapFactory.decodeByteArray(bArr, 0, bArr.length);
        if (!z) {
            predict = this.predictor.predict(decodeByteArray, false, 0.5f);
        } else {
            predict = this.predictor.predict(decodeByteArray, imageCachePath(), 0.5f);
        }
        if (predict.initialized()) {
            List<float[]> filterBoxes = filterBoxes(predict.mBoxes, predict.mScores, i, i2, i3, i4);
            if (filterBoxes.size() < 2) {
                return 0;
            }
            float[][] fArr = new float[filterBoxes.size()][];
            for (int j = 0; j < filterBoxes.size(); j++) {
                Logger.d(TAG, "slider box " + j + ": " + Arrays.toString(filterBoxes.get(j)));
                fArr[j] = filterBoxes.get(j);
            }
            float f = (fArr[0][0] + fArr[0][2]) / 2.0f;
            float f2 = (fArr[1][0] + fArr[1][2]) / 2.0f;
            return f2 > f ? (int) (f2 - f) : (int) (f - f2);
        }
        return -1;
    }

    public static String imageCachePath() {
        return ActApp.getInstance().getCacheDir().getAbsolutePath() + File.separator + "slider-" + UUID.randomUUID().toString() + ".jpg";
    }

    public static List<float[]> filterBoxes(float[][] fArr, float[] fArr2, final int i, final int i2, final int i3, int i4) {
        int i5;
        final ArrayList arrayList = new ArrayList();
        for (int i6 = 0; i6 < fArr2.length; i6++) {
            if (fArr2[i6] > 0.5d) {
                arrayList.add(fArr[i6]);
            }
        }
        if (arrayList.size() > 2) {
            if (i > 0) {
                arrayList.removeIf(new Predicate() { // from class: com.dragonfly.agent.models.-$$Lambda$SliderAI$9VRbNIyPdIRNJTrc48s_XdXSCfk
                    @Override // java.util.function.Predicate
                    public final boolean test(Object obj) {
                        return SliderAI.lambda$filterBoxes$0(i, (float[]) obj);
                    }
                });
            }
            if (i2 > 0) {
                arrayList.removeIf(new Predicate() { // from class: com.dragonfly.agent.models.-$$Lambda$SliderAI$AlqUeZyZtlCcBP53D-cBjHzJ5HI
                    @Override // java.util.function.Predicate
                    public final boolean test(Object obj) {
                        return SliderAI.lambda$filterBoxes$1(i2, (float[]) obj);
                    }
                });
            }
            if (arrayList.size() > 2) {
                ArrayList arrayList2 = new ArrayList();
                float f = Float.MAX_VALUE;
                float f2 = Float.MAX_VALUE;
                int i7 = 1;
                int i8 = 0;
                int i9 = 0;
                while (i8 < arrayList.size() - 1) {
                    int i10 = i8 + 1;
                    for (int i11 = i10; i11 < arrayList.size(); i11++) {
                        float[] fArr3 = (float[]) arrayList.get(i8);
                        float[] fArr4 = (float[]) arrayList.get(i11);
                        float abs = Math.abs((fArr3[2] - fArr3[0]) - (fArr4[2] - fArr4[0])) + Math.abs((fArr3[3] - fArr3[1]) - (fArr4[3] - fArr4[1]));
                        if (abs < f2) {
                            i9 = i8;
                            i7 = i11;
                            f2 = abs;
                        }
                    }
                    i8 = i10;
                }
                if (((float[]) arrayList.get(i9))[0] < ((float[]) arrayList.get(i7))[0]) {
                    arrayList2.add(new int[]{i9, i7});
                } else {
                    arrayList2.add(new int[]{i7, i9});
                }
                int i12 = 0;
                while (i12 < arrayList.size() - 1) {
                    int i13 = i12 + 1;
                    for (int i14 = i13; i14 < arrayList.size(); i14++) {
                        float[] fArr5 = (float[]) arrayList.get(i12);
                        float[] fArr6 = (float[]) arrayList.get(i14);
                        if (Math.abs(fArr5[1] - fArr6[1]) + Math.abs(fArr5[3] - fArr6[3]) < i4) {
                            if (fArr5[0] < fArr6[0]) {
                                arrayList2.add(new int[]{i12, i14});
                            } else {
                                arrayList2.add(new int[]{i14, i12});
                            }
                        }
                    }
                    i12 = i13;
                }
                if (arrayList2.size() == 0) {
                    float f3 = Float.MAX_VALUE;
                    int i15 = 0;
                    for (int i16 = 0; i16 < arrayList.size(); i16++) {
                        float[] fArr7 = (float[]) arrayList.get(i16);
                        if (fArr7[0] < f3) {
                            f3 = fArr7[0];
                            i15 = i16;
                        }
                    }
                    float f4 = Float.MAX_VALUE;
                    int i17 = 0;
                    for (int i18 = 0; i18 < arrayList.size(); i18++) {
                        if (i18 != i15) {
                            float[] fArr8 = (float[]) arrayList.get(i15);
                            float[] fArr9 = (float[]) arrayList.get(i18);
                            float abs2 = Math.abs(fArr9[1] - fArr8[1]) + Math.abs(fArr9[3] - fArr8[3]);
                            if (abs2 < f4) {
                                i17 = i18;
                                f4 = abs2;
                            }
                        }
                    }
                    arrayList2.add(new int[]{i15, i17});
                }
                if (arrayList2.size() > 1 && i3 > 0) {
                    arrayList2.removeIf(new Predicate() { // from class: com.dragonfly.agent.models.-$$Lambda$SliderAI$RfZu0pEK9R86lSAJGLa8fsYnzzs
                        @Override // java.util.function.Predicate
                        public final boolean test(Object obj) {
                            return SliderAI.lambda$filterBoxes$2(arrayList, i3, (int[]) obj);
                        }
                    });
                }
                if (arrayList2.size() > 1) {
                    i5 = 0;
                    for (int i19 = 0; i19 < arrayList2.size(); i19++) {
                        int[] iArr = (int[]) arrayList2.get(i19);
                        float[] fArr10 = (float[]) arrayList.get(iArr[0]);
                        float[] fArr11 = (float[]) arrayList.get(iArr[1]);
                        float abs3 = Math.abs((fArr10[2] - fArr10[0]) - (fArr11[2] - fArr11[0])) + Math.abs((fArr10[3] - fArr10[1]) - (fArr11[3] - fArr11[1])) + Math.abs(fArr10[1] - fArr11[1]) + Math.abs(fArr10[3] - fArr11[3]);
                        if (abs3 < f) {
                            i5 = i19;
                            f = abs3;
                        }
                    }
                } else {
                    i5 = 0;
                }
                int[] iArr2 = (int[]) arrayList2.get(i5);
                Iterator it = arrayList.iterator();
                int i20 = 0;
                while (it.hasNext()) {
                    it.next();
                    if (i20 != iArr2[0] && i20 != iArr2[1]) {
                        it.remove();
                    }
                    i20++;
                }
            }
        }
        return arrayList;
    }

    public static /* synthetic */ boolean lambda$filterBoxes$0(int i, float[] fArr) {
        float f = i;
        return fArr[2] - fArr[0] < f || fArr[3] - fArr[1] < f;
    }

    public static /* synthetic */ boolean lambda$filterBoxes$1(int i, float[] fArr) {
        float f = i;
        return fArr[2] - fArr[0] > f || fArr[3] - fArr[1] > f;
    }

    public static /* synthetic */ boolean lambda$filterBoxes$2(List list, int i, int[] iArr) {
        return ((float[]) list.get(iArr[0]))[0] > ((float) i);
    }
}
