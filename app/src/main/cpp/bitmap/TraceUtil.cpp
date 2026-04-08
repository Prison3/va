#include <cstdint>
#include <malloc.h>
#include <cstring>
#include "jni.h"
#include <cmath>

#define PI 3.14159265

static void filter(float *array, int arrayLen, const int *filter, int filterLen) {
    // 1, 1, [1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 10, 10
    bool even = filterLen % 2 == 0; // [ 1, 2, 5, 2, 1]
    int filterOffset = even ? filterLen / 2 - 1 : (filterLen - 1) / 2; // 2
    float *temp = (float *) malloc(sizeof(float *) * (arrayLen + filterLen - 1));
    int sumWeight = 0;
    for (int i = 0; i < filterLen; i++) {
        sumWeight += filter[i];
    }
    for (int i = 0; i < filterOffset; i++) { // 0, 1
        temp[i] = array[0];
    }
    for (int i = arrayLen + filterLen; i < arrayLen + filterLen - 1; i++) { // 12, 13
        temp[i] = array[arrayLen - 1];
    }
    memcpy((void *) (temp + filterOffset), array, arrayLen * sizeof(int));
    for (int i = 0; i < arrayLen; i++) {
        float sumArray = 0;
        for (int j = 0; j < filterLen; j++) {
            sumArray += temp[i + j] * filter[j];
        }
        array[i] = sumArray / sumWeight;
    }
    delete temp;
}

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL
Java_com_android_actor_utils_NDK_filterArray(
        JNIEnv *env, jclass clazz, jfloatArray jArray, jintArray jFilter) {
    float *a = env->GetFloatArrayElements(jArray, 0);
    int al = env->GetArrayLength(jArray);
    int *f = env->GetIntArrayElements(jFilter, 0);
    int fl = env->GetArrayLength(jFilter);
    filter(a, al, f, fl);
    env->SetFloatArrayRegion(jArray, 0, al, a);
}

JNIEXPORT jfloatArray JNICALL
Java_com_android_actor_utils_NDK_interpolateLiner(
        JNIEnv *env, jclass clazz, jfloat y0, jfloat y1, jint count) {
    jfloatArray array = env->NewFloatArray(count);
    float *temp = (float *) malloc(sizeof(float *) * (count));
    for (int i = 0; i < count; i++) {
        temp[i] = ((float) i / count) * (y1 - y0) + y0;
    }
    env->SetFloatArrayRegion(array, 0, count, temp);
    delete temp;
    return array;
}

JNIEXPORT jfloatArray JNICALL
Java_com_android_actor_utils_NDK_interpolateAccelerateDecelerate(
        JNIEnv *env, jclass clazz, jfloat y0, jfloat y1, jint count) {
    jfloatArray array = env->NewFloatArray(count);
    float *temp = (float *) malloc(sizeof(float *) * (count));
    for (int i = 0; i < count; i++) {
        temp[i] = ((float) cos(((float) i / count + 1) * PI) / 2 + 0.5) * (y1 - y0) + y0;
    }
    env->SetFloatArrayRegion(array, 0, count, temp);
    delete temp;
    return array;
}

JNIEXPORT jfloatArray JNICALL
Java_com_android_actor_utils_NDK_interpolateAccelerate(
        JNIEnv *env, jclass clazz, jfloat y0, jfloat y1, jint n) {
    jfloatArray array = env->NewFloatArray(n);
    float *temp = (float *) malloc(sizeof(float *) * (n));
    for (int i = 0; i < n; i++) {
        temp[i] = (((float) i / n) * ((float) i / n)) * (y1 - y0) + y0;
    }
    env->SetFloatArrayRegion(array, 0, n, temp);
    delete temp;
    return array;
}

JNIEXPORT jfloatArray JNICALL
Java_com_android_actor_utils_NDK_interpolateDecelerate(
        JNIEnv *env, jclass clazz, jfloat y0, jfloat y1, jint n) {
    jfloatArray array = env->NewFloatArray(n);
    float *temp = (float *) malloc(sizeof(float *) * (n));
    for (int i = 0; i < n; i++) {
        temp[i] = (1 - ((float) i / n - 1) * ((float) i / n - 1)) * (y1 - y0) + y0;
    }
    env->SetFloatArrayRegion(array, 0, n, temp);
    delete temp;
    return array;
}

JNIEXPORT jfloatArray JNICALL
Java_com_android_actor_utils_NDK_interpolateBezierCurveTwo(
        JNIEnv *env, jclass clazz, jfloat y0, jfloat y1, jfloat y2, jint n) {
    jfloatArray array = env->NewFloatArray(n);
    float *temp = (float *) malloc(sizeof(float *) * (n));
    for (int i = 0; i < n; i++) {
        float t = (float) i / n;
        temp[i] = (1 - t) * (1 - t) * y0 + 2 * t * (1 - t) * y1 + t * t * y2;
    }
    env->SetFloatArrayRegion(array, 0, n, temp);
    delete temp;
    return array;
}

#ifdef __cplusplus
}
#endif