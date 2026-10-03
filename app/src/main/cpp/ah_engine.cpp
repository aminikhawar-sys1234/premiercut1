#include <jni.h>
#include <android/log.h>
#include <GLES3/gl3.h>
#include <EGL/egl.h>

#define LOG_TAG "AHEngineNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("AHEngine Native OpenGL ES 3.0 Library Loaded successfully (libah_engine.so)");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_engine_NativeEngineLoader_nativeInit(JNIEnv* env, jobject thiz) {
    LOGI("AHEngine Native Renderer Initialized");
    return JNI_TRUE;
}
