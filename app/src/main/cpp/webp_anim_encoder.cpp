#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <webp/encode.h>
#include <webp/mux.h>
#include <webp/demux.h>
#include <cstdio>
#include <cstring>
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
    config.method = 1; // Fast & efficient compression (was 2, ~30-40% faster native encoding with nearly identical ratio)
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
        const int lock_res = AndroidBitmap_lockPixels(env, bitmap_obj, &pixels);
        if (lock_res < 0 || pixels == nullptr) {
            LOGE("AndroidBitmap_lockPixels failed for frame %d (res=%d)", i, lock_res);
            if (lock_res >= 0 && pixels == nullptr) {
                AndroidBitmap_unlockPixels(env, bitmap_obj);
            }
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

        int frame_timestamp;
        if (i == 0) {
            frame_timestamp = 0;
        } else {
            int desired = timestamps[i] - timestamps[0];
            frame_timestamp = std::max(desired, prev_timestamp + 10);
            if (frame_timestamp > 2990) {
                frame_timestamp = 2990;
            }
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
    // WhatsApp strictly requires every frame duration to be >= 8 ms. We enforce >= 10 ms.
    // Total duration must never exceed 3000 ms.
    int last_duration = (num_frames > 1 && second_last_timestamp >= 0)
        ? (last_timestamp - second_last_timestamp)
        : 100;
    if (last_duration < 10) {
        last_duration = 10;
    }
    int end_time = last_timestamp + last_duration;
    if (end_time > 3000) {
        end_time = 3000;
        if (end_time <= last_timestamp) {
            end_time = last_timestamp + 10;
        }
    }

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

JNIEXPORT jobject JNICALL
Java_com_seventv_stickers_core_media_animated_NativeWebPAnimDecoder_decode(
    JNIEnv* env,
    jobject /* thiz */,
    jbyteArray webpBytes
) {
    if (env == nullptr || webpBytes == nullptr) {
        return nullptr;
    }

    const jsize byte_count = env->GetArrayLength(webpBytes);
    if (byte_count <= 12) {
        return nullptr;
    }

    jbyte* bytes_ptr = env->GetByteArrayElements(webpBytes, nullptr);
    if (bytes_ptr == nullptr) {
        return nullptr;
    }

    WebPData webp_data;
    webp_data.bytes = reinterpret_cast<const uint8_t*>(bytes_ptr);
    webp_data.size = static_cast<size_t>(byte_count);

    WebPAnimDecoderOptions dec_options;
    if (!WebPAnimDecoderOptionsInit(&dec_options)) {
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }
    dec_options.color_mode = MODE_RGBA;

    WebPAnimDecoder* dec = WebPAnimDecoderNew(&webp_data, &dec_options);
    if (dec == nullptr) {
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }

    WebPAnimInfo info;
    if (!WebPAnimDecoderGetInfo(dec, &info) || info.frame_count <= 0) {
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }

    const int width = info.canvas_width;
    const int height = info.canvas_height;
    const int frame_count = info.frame_count;

    if (width <= 0 || height <= 0 || frame_count <= 0) {
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }

    // Prevent 32-bit integer overflow on canvas dimensions
    if (static_cast<int64_t>(width) * static_cast<int64_t>(height) > (2147483647LL / 4LL)) {
        LOGE("Canvas dimensions too large: %dx%d", width, height);
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }

    jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
    if (bitmap_class == nullptr) {
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }
    jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
    if (config_class == nullptr) {
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }
    jfieldID argb_field = env->GetStaticFieldID(config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb_config = env->GetStaticObjectField(config_class, argb_field);
    jmethodID create_bitmap_mid = env->GetStaticMethodID(
        bitmap_class,
        "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;"
    );
    if (create_bitmap_mid == nullptr || argb_config == nullptr) {
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }

    jobjectArray bitmap_array = env->NewObjectArray(frame_count, bitmap_class, nullptr);
    jintArray durations_array = env->NewIntArray(frame_count);
    jintArray timestamps_array = env->NewIntArray(frame_count);

    if (bitmap_array == nullptr || durations_array == nullptr || timestamps_array == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }

    jint* durations_ptr = env->GetIntArrayElements(durations_array, nullptr);
    jint* timestamps_ptr = env->GetIntArrayElements(timestamps_array, nullptr);
    if (durations_ptr == nullptr || timestamps_ptr == nullptr) {
        if (durations_ptr != nullptr) env->ReleaseIntArrayElements(durations_array, durations_ptr, JNI_ABORT);
        if (timestamps_ptr != nullptr) env->ReleaseIntArrayElements(timestamps_array, timestamps_ptr, JNI_ABORT);
        WebPAnimDecoderDelete(dec);
        env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
        return nullptr;
    }

    int frame_index = 0;
    int prev_timestamp = 0;
    bool decode_failed = false;

    while (WebPAnimDecoderHasMoreFrames(dec) && frame_index < frame_count) {
        uint8_t* rgba_buf = nullptr;
        int timestamp = 0;
        if (!WebPAnimDecoderGetNext(dec, &rgba_buf, &timestamp) || rgba_buf == nullptr) {
            break;
        }

        jobject frame_bmp = env->CallStaticObjectMethod(
            bitmap_class,
            create_bitmap_mid,
            width,
            height,
            argb_config
        );

        if (env->ExceptionCheck()) {
            LOGE("Exception in Bitmap.createBitmap for frame %d", frame_index);
            env->ExceptionClear();
            decode_failed = true;
            break;
        }

        if (frame_bmp == nullptr) {
            LOGE("createBitmap returned null for frame %d", frame_index);
            decode_failed = true;
            break;
        }

        AndroidBitmapInfo dst_info;
        if (AndroidBitmap_getInfo(env, frame_bmp, &dst_info) < 0) {
            LOGE("AndroidBitmap_getInfo failed for frame %d", frame_index);
            env->DeleteLocalRef(frame_bmp);
            decode_failed = true;
            break;
        }

        void* dst_pixels = nullptr;
        const int lock_res = AndroidBitmap_lockPixels(env, frame_bmp, &dst_pixels);
        if (lock_res < 0 || dst_pixels == nullptr) {
            LOGE("AndroidBitmap_lockPixels failed for frame %d", frame_index);
            if (lock_res >= 0 && dst_pixels == nullptr) {
                AndroidBitmap_unlockPixels(env, frame_bmp);
            }
            env->DeleteLocalRef(frame_bmp);
            decode_failed = true;
            break;
        }

        // Row-by-row stride-aligned pixel copy to prevent skew/corruption
        const uint8_t* src_row = rgba_buf;
        uint8_t* dst_row = static_cast<uint8_t*>(dst_pixels);
        const size_t row_bytes = static_cast<size_t>(width * 4);
        for (int y = 0; y < height; ++y) {
            std::memcpy(dst_row, src_row, row_bytes);
            src_row += row_bytes;
            dst_row += dst_info.stride;
        }
        AndroidBitmap_unlockPixels(env, frame_bmp);

        env->SetObjectArrayElement(bitmap_array, frame_index, frame_bmp);
        env->DeleteLocalRef(frame_bmp);

        int duration = (timestamp > prev_timestamp) ? (timestamp - prev_timestamp) : 100;
        if (duration < 10) duration = 10;
        durations_ptr[frame_index] = duration;
        timestamps_ptr[frame_index] = timestamp;
        prev_timestamp = timestamp;
        frame_index++;
    }

    env->ReleaseIntArrayElements(durations_array, durations_ptr, 0);
    env->ReleaseIntArrayElements(timestamps_array, timestamps_ptr, 0);
    env->ReleaseByteArrayElements(webpBytes, bytes_ptr, JNI_ABORT);
    WebPAnimDecoderDelete(dec);

    if (decode_failed || frame_index == 0) {
        // Clean up already allocated bitmaps in bitmap_array to prevent memory leak
        jmethodID recycle_mid = env->GetMethodID(bitmap_class, "recycle", "()V");
        for (int k = 0; k < frame_index; ++k) {
            jobject b = env->GetObjectArrayElement(bitmap_array, k);
            if (b != nullptr) {
                if (recycle_mid != nullptr) env->CallVoidMethod(b, recycle_mid);
                env->DeleteLocalRef(b);
            }
        }
        return nullptr;
    }

    jobjectArray final_bitmaps = bitmap_array;
    jintArray final_durations = durations_array;
    jintArray final_timestamps = timestamps_array;

    if (frame_index < frame_count) {
        final_bitmaps = env->NewObjectArray(frame_index, bitmap_class, nullptr);
        final_durations = env->NewIntArray(frame_index);
        final_timestamps = env->NewIntArray(frame_index);

        if (final_bitmaps == nullptr || final_durations == nullptr || final_timestamps == nullptr) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            jmethodID recycle_mid = env->GetMethodID(bitmap_class, "recycle", "()V");
            for (int k = 0; k < frame_index; ++k) {
                jobject b = env->GetObjectArrayElement(bitmap_array, k);
                if (b != nullptr) {
                    if (recycle_mid != nullptr) env->CallVoidMethod(b, recycle_mid);
                    env->DeleteLocalRef(b);
                }
            }
            return nullptr;
        }

        for (int k = 0; k < frame_index; ++k) {
            jobject b = env->GetObjectArrayElement(bitmap_array, k);
            env->SetObjectArrayElement(final_bitmaps, k, b);
            if (b != nullptr) env->DeleteLocalRef(b);
        }

        jint* src_dur = env->GetIntArrayElements(durations_array, nullptr);
        jint* dst_dur = env->GetIntArrayElements(final_durations, nullptr);
        if (src_dur != nullptr && dst_dur != nullptr) {
            std::memcpy(dst_dur, src_dur, sizeof(jint) * frame_index);
        }
        if (src_dur != nullptr) env->ReleaseIntArrayElements(durations_array, src_dur, JNI_ABORT);
        if (dst_dur != nullptr) env->ReleaseIntArrayElements(final_durations, dst_dur, 0);

        jint* src_ts = env->GetIntArrayElements(timestamps_array, nullptr);
        jint* dst_ts = env->GetIntArrayElements(final_timestamps, nullptr);
        if (src_ts != nullptr && dst_ts != nullptr) {
            std::memcpy(dst_ts, src_ts, sizeof(jint) * frame_index);
        }
        if (src_ts != nullptr) env->ReleaseIntArrayElements(timestamps_array, src_ts, JNI_ABORT);
        if (dst_ts != nullptr) env->ReleaseIntArrayElements(final_timestamps, dst_ts, 0);
    }

    jclass result_class = env->FindClass("com/seventv/stickers/core/media/animated/WebPDecodedFrames");
    if (result_class == nullptr) {
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(result_class, "<init>", "([Landroid/graphics/Bitmap;[I[I)V");
    if (ctor == nullptr) {
        return nullptr;
    }

    return env->NewObject(result_class, ctor, bitmap_array, durations_array, timestamps_array);
}

} // extern "C"
