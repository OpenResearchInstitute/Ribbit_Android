/*
Java native interface to C++ encoder and decoder

Copyright 2023 Ahmet Inan <inan@aicodix.de>
*/

#include <jni.h>
//#define assert(expr) do {} while (0)
#include <cassert>
#include "encoder.hh"
#include "decoder.hh"

static Encoder *encoder;
static Decoder *decoder;

extern "C" JNIEXPORT jboolean JNICALL
Java_institute_openresearch_ribbit_MainActivity_createEncoder(
	JNIEnv *,
	jobject) {
	if (encoder)
		return true;
	encoder = new(std::nothrow) Encoder();
	return encoder != nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_institute_openresearch_ribbit_MainActivity_destroyEncoder(
	JNIEnv *,
	jobject) {
	delete encoder;
	encoder = nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_institute_openresearch_ribbit_MainActivity_readEncoder(
	JNIEnv *env,
	jobject,
	jfloatArray JNI_audioBuffer,
	jint sampleCount) {
	jboolean done = true;
	if (encoder) {
		jfloat *audioBuffer = env->GetFloatArrayElements(JNI_audioBuffer, nullptr);
		if (audioBuffer)
			done = encoder->read(audioBuffer, sampleCount);
		env->ReleaseFloatArrayElements(JNI_audioBuffer, audioBuffer, 0);
	}
	return done;
}

extern "C" JNIEXPORT void JNICALL
Java_institute_openresearch_ribbit_MainActivity_initEncoder(
	JNIEnv *env,
	jobject,
	jbyteArray JNI_payload) {
	if (encoder) {
		jbyte *payload = env->GetByteArrayElements(JNI_payload, nullptr);
		if (payload)
			encoder->init(reinterpret_cast<uint8_t *>(payload));
		env->ReleaseByteArrayElements(JNI_payload, payload, JNI_ABORT);
	}
}

extern "C" JNIEXPORT void JNICALL
Java_institute_openresearch_ribbit_MainActivity_destroyDecoder(
	JNIEnv *,
	jobject) {
	delete decoder;
	decoder = nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_institute_openresearch_ribbit_MainActivity_createDecoder(
	JNIEnv *,
	jobject) {
	if (decoder)
		return true;
	decoder = new(std::nothrow) Decoder();
	return decoder != nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_institute_openresearch_ribbit_MainActivity_fetchDecoder(
	JNIEnv *env,
	jobject,
	jbyteArray JNI_payload) {
	jboolean error = true;
	if (decoder) {
		jbyte *payload = env->GetByteArrayElements(JNI_payload, nullptr);
		if (payload)
			error = decoder->fetch(reinterpret_cast<uint8_t *>(payload));
		env->ReleaseByteArrayElements(JNI_payload, payload, 0);
	}
	return error;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_institute_openresearch_ribbit_MainActivity_feedDecoder(
	JNIEnv *env,
	jobject,
	jfloatArray JNI_audioBuffer,
	jint sampleCount) {
	jboolean fetch = false;
	if (decoder) {
		jfloat *audioBuffer = env->GetFloatArrayElements(JNI_audioBuffer, nullptr);
		if (audioBuffer)
			fetch = decoder->feed(reinterpret_cast<float *>(audioBuffer), sampleCount);
		env->ReleaseFloatArrayElements(JNI_audioBuffer, audioBuffer, JNI_ABORT);
	}
	return fetch;
}

