#include <jni.h>
#define main verificationTestMain
#include "verification_read_guard_test.cpp"
#undef main

extern "C" JNIEXPORT jint JNICALL Java_com_lcpatch_guard_MainActivity_runChecks(JNIEnv *env, jobject, jstring directory) {
    const char *path = env->GetStringUTFChars(directory, nullptr);
    if (!path) return -1;
    char program[] = "verification_guard_test";
    char *arguments[] = {program, const_cast<char *>(path)};
    checks = 0;
    imageRange = {reinterpret_cast<uintptr_t>(selectedRead), reinterpret_cast<uintptr_t>(Java_com_lcpatch_guard_MainActivity_runChecks)};
    if (imageRange.end <= imageRange.begin) { env->ReleaseStringUTFChars(directory, path); return -2; }
    const int result = verificationTestMain(2, arguments);
    env->ReleaseStringUTFChars(directory, path);
    return result;
}
