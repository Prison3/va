#ifndef ROCKETACTOR_BITMAPUTIL_H
#define ROCKETACTOR_BITMAPUTIL_H

#include "jni.h"
#include <android/bitmap.h>

class BitmapUtil {
public:
    static jboolean rgb2gray(JNIEnv *env, jobject bmp);

    static jboolean compareFullScreen(JNIEnv *env, jobject bmp1, jobject bmp2, jintArray array);
};


#endif //ROCKETACTOR_BITMAPUTIL_H
