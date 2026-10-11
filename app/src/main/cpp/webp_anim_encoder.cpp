#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <webp/encode.h>
#include <webp/mux.h>
#include <cstdio>
#include <algorithm>

#define LOG_TAG "seven_tv_webp"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

constexpr int CANVAS_WIDTH = 512;
constexpr int CANVAS_HEIGHT = 512;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_seventv_stickers_core_media_animated_NativeWebPAnimEncoder_encode(
    JNIEnv* env,
    jobject /* thiz */,
    jobjectArray bitmaps,
    jintArray timestampsMs,
    jfloat quality,
    jstring outputPath
) {
    if (env == nullptr || bitmaps == nullptr || timestampsMs == nullptr || outputPath == nullptr) {
        LOGE("Invalid null argument provided to encode");
        return JNI_FALSE;
    }

    const jsize num_frames = env->GetArrayLength(bitmaps);
    const jsize num_timestamps = env->GetArrayLength(timestampsMs);

    if (num_frames <= 0) {
        LOGE("Bitmap array must contain at least one frame");
        return JNI_FALSE;
    }

    if (num_frames != num_timestamps) {
        LOGE("Frame count (%d) must match timestamps count (%d)", num_frames, num_timestamps);
        return JNI_FALSE;
    }

    jint* timestamps = env->GetIntArrayElements(timestampsMs, nullptr);
    if (timestamps == nullptr) {
        LOGE("Failed to obtain timestamps array elements");
        return JNI_FALSE;
    }

    const float clamped_quality = std::max(0.0f, std::min(100.0f, static_cast<float>(quality)));

    WebPAnimEncoderOptions anim_config;
    if (!WebPAnimEncoderOptionsInit(&anim_config)) {
        LOGE("Failed to initialize WebPAnimEncoderOptions");
        env->ReleaseIntArrayElements(timestampsMs, timestamps, JNI_ABORT);
        return JNI_FALSE;
    }
    anim_config.anim_params.loop_count = 0; // Infinite loop required by WhatsApp stickers

    WebPAnimEncoder* enc = WebPAnimEncoderNew(CANVAS_WIDTH, CANVAS_HEIGHT, &anim_config);
    if (enc == nullptr) {
        LOGE("Failed to allocate WebPAnimEncoder (%dx%d)", CANVAS_WIDTH, CANVAS_HEIGHT);
        env->ReleaseIntArrayElements(timestampsMs, timestamps, JNI_ABORT);
        return JNI_FALSE;
    }

    WebPConfig config;
    if (!WebPConfigInit(&config)) {
        LOGE("Failed to initialize WebPConfig");
        WebPAnimEncoderDelete(enc);
        env->ReleaseIntArrayElements(timestampsMs, timestamps, JNI_ABORT);
        return JNI_FALSE;
    }
    config.quality = clamped_quality;
    config.method = 4;
    if (!WebPValidateConfig(&config)) {
        LOGE("WebPConfig validation failed");
        WebPAnimEncoderDelete(enc);
        env->ReleaseIntArrayElements(timestampsMs, timestamps, JNI_ABORT);
        return JNI_FALSE;
    }

    int prev_timestamp = -1;
    int last_timestamp = 0;
    int second_last_timestamp = -1;
    bool encode_failed = false;

    for (jsize i = 0; i < num_frames; ++i) {
        jobject bitmap_obj = env->GetObjectArrayElement(bitmaps, i);
        if (bitmap_obj == nullptr) {
            LOGE("Frame %d bitmap is null", i);
            encode_failed = true;
            break;
        }

        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bitmap_obj, &info) < 0) {
            LOGE("AndroidBitmap_getInfo failed for frame %d", i);
            env->DeleteLocalRef(bitmap_obj);
            encode_failed = true;
            break;
        }

        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
            LOGE("Frame %d bitmap format must be RGBA_8888 (got format %d)", i, info.format);
            env->DeleteLocalRef(bitmap_obj);
            encode_failed = true;
            break;
        }

        if (info.width != CANVAS_WIDTH || info.height != CANVAS_HEIGHT) {
            LOGE("Frame %d dimensions (%dx%d) do not match required canvas (%dx%d)",
                 i, info.width, info.height, CANVAS_WIDTH, CANVAS_HEIGHT);
            env->DeleteLocalRef(bitmap_obj);
            encode_failed = true;
            break;
        }

        void* pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bitmap_obj, &pixels) < 0 || pixels == nullptr) {
            LOGE("AndroidBitmap_lockPixels failed for frame %d", i);
            env->DeleteLocalRef(bitmap_obj);
            encode_failed = true;
            break;
        }

        WebPPicture pic;
        if (!WebPPictureInit(&pic)) {
            LOGE("WebPPictureInit failed for frame %d", i);
            AndroidBitmap_unlockPixels(env, bitmap_obj);
            env->DeleteLocalRef(bitmap_obj);
            encode_failed = true;
            break;
        }

        pic.width = info.width;
        pic.height = info.height;
        pic.use_argb = 1;

        const int import_ok = WebPPictureImportRGBA(
            &pic,
            reinterpret_cast<const uint8_t*>(pixels),
            info.stride
        );

        AndroidBitmap_unlockPixels(env, bitmap_obj);
        env->DeleteLocalRef(bitmap_obj);

        if (!import_ok) {
            LOGE("WebPPictureImportRGBA failed for frame %d", i);
            WebPPictureFree(&pic);
            encode_failed = true;
            break;
        }

        int frame_timestamp = timestamps[i];
        if (i > 0 && frame_timestamp <= prev_timestamp) {
            frame_timestamp = prev_timestamp + 100;
        } else if (i == 0 && frame_timestamp < 0) {
            frame_timestamp = 0;
        }

        const int add_ok = WebPAnimEncoderAdd(enc, &pic, frame_timestamp, &config);
        WebPPictureFree(&pic);

        if (!add_ok) {
            LOGE("WebPAnimEncoderAdd failed for frame %d at timestamp %d", i, frame_timestamp);
            encode_failed = true;
            break;
        }

        second_last_timestamp = prev_timestamp;
        prev_timestamp = frame_timestamp;
        last_timestamp = frame_timestamp;
    }

    env->ReleaseIntArrayElements(timestampsMs, timestamps, JNI_ABORT);

    if (encode_failed) {
        WebPAnimEncoderDelete(enc);
        return JNI_FALSE;
    }

    // Finalize animation sequence: mark end timestamp with frame = nullptr
    int last_duration = (num_frames > 1 && second_last_timestamp >= 0)
        ? (last_timestamp - second_last_timestamp)
        : 100;
    if (last_duration <= 0) {
        last_duration = 100;
    }
    const int end_time = last_timestamp + last_duration;

    if (!WebPAnimEncoderAdd(enc, nullptr, end_time, nullptr)) {
        LOGE("WebPAnimEncoderAdd flush failed at end timestamp %d", end_time);
        WebPAnimEncoderDelete(enc);
        return JNI_FALSE;
    }

    WebPData webp_data;
    WebPDataInit(&webp_data);

    if (!WebPAnimEncoderAssemble(enc, &webp_data)) {
        LOGE("WebPAnimEncoderAssemble failed");
        WebPAnimEncoderDelete(enc);
        return JNI_FALSE;
    }

    const char* output_path_str = env->GetStringUTFChars(outputPath, nullptr);
    if (output_path_str == nullptr) {
        LOGE("GetStringUTFChars failed for outputPath");
        WebPDataClear(&webp_data);
        WebPAnimEncoderDelete(enc);
        return JNI_FALSE;
    }

    FILE* out_file = fopen(output_path_str, "wb");
    if (out_file == nullptr) {
        LOGE("Failed to open destination file: %s", output_path_str);
        env->ReleaseStringUTFChars(outputPath, output_path_str);
        WebPDataClear(&webp_data);
        WebPAnimEncoderDelete(enc);
        return JNI_FALSE;
    }

    const size_t total_size = webp_data.size;
    const size_t written = fwrite(webp_data.bytes, 1, total_size, out_file);
    fclose(out_file);

    env->ReleaseStringUTFChars(outputPath, output_path_str);
    WebPDataClear(&webp_data);
    WebPAnimEncoderDelete(enc);

    if (written != total_size) {
        LOGE("Failed to write full WebP data: wrote %zu / %zu bytes", written, total_size);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

} // extern "C"
