#include <jni.h>
#include <string>

// Declare the functions from our other files
extern void setScreenHoldState(bool isHeld);
extern bool processAccelerometerData(float x, float y, float z);
extern void buildSosPacket(uint32_t msg_id, const char* name, const char* condition, uint8_t* output_buffer);
extern bool processIncomingPacket(const uint8_t* input_buffer, uint8_t* output_buffer_to_forward);

extern "C" {

// 1. Gesture JNI functions mapping to com.aegismesh.sensors.GestureDetector
JNIEXPORT void JNICALL
Java_com_aegismesh_sensors_GestureDetector_nativeSetScreenHoldState(JNIEnv *env, jobject thiz, jboolean is_held) {
    setScreenHoldState(is_held);
}

JNIEXPORT jboolean JNICALL
Java_com_aegismesh_sensors_GestureDetector_nativeProcessAccelerometer(JNIEnv *env, jobject thiz, jfloat x, jfloat y, jfloat z) {
    return processAccelerometerData(x, y, z);
}

// 2. Mesh JNI functions mapping to com.aegismesh.services.MeshService
JNIEXPORT jbyteArray JNICALL
Java_com_aegismesh_services_MeshService_nativeBuildSosPacket(JNIEnv *env, jobject thiz, jint msg_id, jstring name, jstring condition) {
    const char *c_name = env->GetStringUTFChars(name, nullptr);
    const char *c_condition = env->GetStringUTFChars(condition, nullptr);
    
    uint8_t buffer[31] = {0};
    buildSosPacket(msg_id, c_name, c_condition, buffer);
    
    env->ReleaseStringUTFChars(name, c_name);
    env->ReleaseStringUTFChars(condition, c_condition);
    
    jbyteArray result = env->NewByteArray(31);
    env->SetByteArrayRegion(result, 0, 31, reinterpret_cast<jbyte *>(buffer));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_aegismesh_services_MeshService_nativeProcessIncomingPacket(JNIEnv *env, jobject thiz, jbyteArray input_payload) {
    jbyte *c_input = env->GetByteArrayElements(input_payload, nullptr);
    uint8_t output_buffer[31] = {0};

    bool should_forward = processIncomingPacket(reinterpret_cast<uint8_t *>(c_input), output_buffer);
    env->ReleaseByteArrayElements(input_payload, c_input, JNI_ABORT);

    if (should_forward) {
        jbyteArray result = env->NewByteArray(31);
        env->SetByteArrayRegion(result, 0, 31, reinterpret_cast<jbyte *>(output_buffer));
        return result; // Return payload to Java to broadcast
    }
    
    return nullptr; // Null means do not broadcast
}

// Declare the functions from wifi_direct.cpp
extern std::string startProfileServer(int port);
extern bool sendFullProfile(const char* target_ip, int port, const char* profile_json);


// 3. Wi-Fi Direct JNI functions mapping to com.aegismesh.services.MeshService
JNIEXPORT jstring JNICALL
Java_com_aegismesh_services_MeshService_nativeStartWifiServer(JNIEnv *env, jobject thiz, jint port) {
    // This will block the thread until a payload is received
    std::string result = startProfileServer(port);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_aegismesh_services_MeshService_nativeSendProfile(JNIEnv *env, jobject thiz, jstring target_ip, jint port, jstring profile_json) {
    const char *c_ip = env->GetStringUTFChars(target_ip, nullptr);
    const char *c_json = env->GetStringUTFChars(profile_json, nullptr);

    bool success = sendFullProfile(c_ip, port, c_json);

    env->ReleaseStringUTFChars(target_ip, c_ip);
    env->ReleaseStringUTFChars(profile_json, c_json);

    return success;
}

} // extern "C"