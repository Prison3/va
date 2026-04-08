#include "Hooks.h"
#include "Foundation/SandboxFs.h"
#include "Foundation/VNativeCore.h"




HOOK_JNI(jint, getCallingUid, JNIEnv *env, jobject obj) {
    int orig = orig_getCallingUid(env, obj);
    return VNativeCore::getCallingUid(env, orig);
}


void BinderHook::install(JNIEnv *env) {
    const char *clazz = "android/os/Binder";
    VJniHook::HookJniFun(env, clazz, "getCallingUid", "()I", (void *) new_getCallingUid,
                        (void **) (&orig_getCallingUid), true);
}