#include <malloc.h>
#include <memory.h>
#include <cstdlib>
#include <string>
#include <android/log.h>
#include "BitmapUtil.h"

#define LINE_MATCH_COUNT(percent) percent < 0.01 // 每行误差点数
#define LINE_MATCH_GRAY(grayAverage) grayAverage < 10 // 每行误差平均灰度值

#define ROCKET_LOG "Rocket_Actor_ndk"
const static int CONTINUE_LINE_MAX = 10; // 上下部分连续相同区域
const static int SCAN_OFFSET_RANGE = 100; // 滑动部分比对窗口
const static int TOP_BAR_HEIGHT = 100; // 跳过顶部像素点
const static int BOTTOM_BAR_HEIGHT = 100; // 底部阈值

static int rgb2grayInt(int color) {
    int red = ((color & 0x00FF0000) >> 16);
    int green = ((color & 0x0000FF00) >> 8);
    int blue = color & 0x000000FF;
    return (red + green + blue) / 3;
}


static int32_t *lockAndGetBitmapBuf(JNIEnv *env, jobject bmp) {
    void *srcBuf;
    if (ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_lockPixels(env, bmp, &srcBuf)) {
        return nullptr;
    }
    return (int32_t *) srcBuf;
}

static bool rowMatch(int *line1, int *line2, int width) {
    int differentCount = 0;
    int differentSum = 0;
    for (int i = 0; i < width; i++) {
        if (line1[i] != line2[i]) {
            differentCount++;
            differentSum += abs(line1[i] - line2[i]);
        }
    }
    return LINE_MATCH_COUNT((float) differentCount / width) ||
           LINE_MATCH_GRAY(differentSum / differentCount);
}

static bool colMatch(int **rect1, int **rect2, int x, int top, int bottom) {
    int differentCount = 0;
    int differentSum = 0;
    for (int j = top; j < bottom; j++) {
        if (rect1[j][x] != rect2[j][x]) {
            differentCount++;
            differentSum += abs(rect1[j][x] - rect2[j][x]);
        }
    }
    return LINE_MATCH_COUNT((float) differentCount / (bottom - top)) ||
           LINE_MATCH_GRAY(differentSum / differentCount);

}

static void setInteger(JNIEnv *jenv, jobject integer, int result) {
    jclass intClass = jenv->FindClass("java/lang/Integer");
    jfieldID intId = jenv->GetFieldID(intClass, "value", "I");
    jenv->SetIntField(integer, intId, result);
}

static int compareTopRect(int **img1, int **img2, int width, int height) {
    int topY = TOP_BAR_HEIGHT;
    bool *continueMap = (bool *) malloc(sizeof(bool *) * height);
    for (int i = TOP_BAR_HEIGHT; i < height; i++) {
        continueMap[i] = rowMatch(img1[i], img2[i], width);
    }
    int continueCount = 0;
    for (int i = TOP_BAR_HEIGHT; i < TOP_BAR_HEIGHT + CONTINUE_LINE_MAX; i++) {
        continueCount += continueMap[i];
    }
    for (int i = TOP_BAR_HEIGHT + CONTINUE_LINE_MAX; i < height; i++) {
        if (continueCount < 1) {
            topY = i;
            break;
        } else {
            continueCount -= continueMap[i - CONTINUE_LINE_MAX];
            continueCount += continueMap[i];
        }
    }
    free(continueMap);
    __android_log_print(ANDROID_LOG_INFO, ROCKET_LOG, "found top y: %d ", topY);
    return topY;
}

static int compareBottomRect(int **img1, int **img2, int width, int height, int topY) {
    int bottomY = height;
    bool *continueMap = (bool *) malloc(sizeof(bool *) * height);
    for (int i = height - 1; i > topY; i--) {
        if (!rowMatch(img1[i], img2[i], width)) {
            continueMap[i] = rowMatch(img1[i], img2[i], width);
        }
    }
    int continueCount = 0;
    for (int i = height - 1; i > height - 1 - CONTINUE_LINE_MAX; i--) {
        continueCount += continueMap[i];
    }
    for (int i = height - 1 - CONTINUE_LINE_MAX; i > topY; i--) {
        if (continueCount < 1) {
            bottomY = i;
            break;
        } else {
            continueCount -= continueMap[i + CONTINUE_LINE_MAX];
            continueCount += continueMap[i];
        }
    }
    free(continueMap);
    __android_log_print(ANDROID_LOG_INFO, ROCKET_LOG, "found bottom y: %d ", bottomY);
    return bottomY;
}


static int compareScrollRect(int **img1, int **img2, int topY, int bottomY, int width) {
    // 滑动offset, 考虑滑动比较大，从上往下找, 10个
    int off = 0;
    int foundCount = 0;
    // 查看页面是否有变动
    int colSameCount = 0;
    bool allSame = true;
    for (int i = 0; i < width; i++) {
        if (!colMatch(img1, img2, i, topY, bottomY)) {
            colSameCount++;
            allSame = LINE_MATCH_COUNT((float) colSameCount / width);
            if (!allSame) {
                allSame = false;
                break;
            }
        }
    }
    if (allSame) {
        return 0;
    }

    for (int i = topY + SCAN_OFFSET_RANGE; i < bottomY; i = i + SCAN_OFFSET_RANGE) {
        bool found = false;
        for (int j = bottomY; j > i; j--) {
            bool lineNotMatch = false;
            for (int k = 0; k > -SCAN_OFFSET_RANGE; k--) {
                if (!rowMatch(img2[i + k], img1[j + k], width)) {
                    lineNotMatch = true;
                    break;
                }
            }
            if (!lineNotMatch) {
                off = j - i;
                found = true;
                break;
            }
        }
        if (found) {
            foundCount++;
            if (foundCount > 3) {
                break;
            }
        }
    }
    return off;
}


jboolean BitmapUtil::rgb2gray(JNIEnv *env, jobject bmp) {
    __android_log_print(ANDROID_LOG_INFO, ROCKET_LOG, "rgb2gray");
    AndroidBitmapInfo srcInfo;
    //获取bitmap属性信息
    if (ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_getInfo(env, bmp, &srcInfo)) {
        return false;
    }
    int w = srcInfo.width;
    int h = srcInfo.height;

    //获取bitmap的像素信息,并锁住当前的像素点
    int32_t *srcPixs = lockAndGetBitmapBuf(env, bmp);

    int alpha = 0xFF << 24;
    for (int i = 0; i < h; i++) {
        for (int j = 0; j < w; j++) {
            // 获得像素的颜色
            int color = rgb2grayInt(srcPixs[w * i + j]);
            color = alpha | (color << 16) | (color << 8) | color;
            srcPixs[w * i + j] = color;
        }
    }
    //修改完，解锁操作
    AndroidBitmap_unlockPixels(env, bmp);
    return 1;
}

jboolean
BitmapUtil::compareFullScreen(JNIEnv *env, jobject bmp1, jobject bmp2, jintArray array) {
    int *yConfig = env->GetIntArrayElements(array, 0);
    int topY = yConfig[0];
    int bottomY = yConfig[1];
    __android_log_print(ANDROID_LOG_INFO, ROCKET_LOG, "compareFullScreen topY %d bottomY %d",
                        topY, bottomY);

    AndroidBitmapInfo info1;
    AndroidBitmapInfo info2;
    //获取bitmap属性信息
    if (ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_getInfo(env, bmp1, &info1)
        || ANDROID_BITMAP_RESULT_SUCCESS != AndroidBitmap_getInfo(env, bmp2, &info2)) {
        return false;
    }
    if (info1.width != info2.width || info1.height != info2.height) {
        return false;
    }
    int width = info1.width;
    int height = info1.height;
    //获取bitmap的像素信息,并锁住当前的像素点
    int32_t *pixs1 = lockAndGetBitmapBuf(env, bmp1);
    int32_t *pixs2 = lockAndGetBitmapBuf(env, bmp2);

    int **img1 = (int **) malloc(sizeof(int *) * height);
    int **img2 = (int **) malloc(sizeof(int *) * height);
    for (int i = 0; i < height; i++) {
        img1[i] = (int *) malloc(sizeof(int) * width);
        img2[i] = (int *) malloc(sizeof(int) * width);
        for (int j = 0; j < width; j++) {
            img1[i][j] = rgb2grayInt(pixs1[i * width + j]);
            img2[i][j] = rgb2grayInt(pixs2[i * width + j]);
        }
    }

    // 上部分相同区域
    if (topY <= TOP_BAR_HEIGHT) {
        topY = compareTopRect(img1, img2, width, height);
    }

    // 下部分相同区域
    if (bottomY <= 0 || bottomY >= height - BOTTOM_BAR_HEIGHT) {
        bottomY = compareBottomRect(img1, img2, width, height, topY);
    }

    // 滚动部分
    int offsetY = compareScrollRect(img1, img2, topY, bottomY, width);
    __android_log_print(ANDROID_LOG_INFO, ROCKET_LOG, "found offset y: %d ", offsetY);

    // 后续处理
    AndroidBitmap_unlockPixels(env, bmp1);
    AndroidBitmap_unlockPixels(env, bmp2);

    for (int i = 0; i < height; i++) {
        free(img1[i]);
        free(img2[i]);
    }
    free(img1);
    free(img2);

    yConfig[0] = topY;
    yConfig[1] = bottomY;
    yConfig[2] = offsetY;
    env->ReleaseIntArrayElements(array, yConfig, 0);
    return 1;
}

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jboolean JNICALL
Java_com_android_actor_utils_NDK_rgb2gray(
        JNIEnv *env, jclass clazz, jobject bitmap) {
    return BitmapUtil::rgb2gray(env, bitmap);
}

JNIEXPORT jint JNICALL
Java_com_android_actor_utils_NDK_compareSameSizeImage(
        JNIEnv *env, jclass clazz, jobject bmp1, jobject bmp2, jintArray array) {
    return BitmapUtil::compareFullScreen(env, bmp1, bmp2, array);
}
#ifdef __cplusplus
}
#endif
