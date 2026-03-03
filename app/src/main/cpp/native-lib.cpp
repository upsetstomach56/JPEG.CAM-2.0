#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#define LOG_TAG "NDK_ENGINE"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT void JNICALL
Java_com_github_ma1co_pmcademo_app_LutEngine_applyLutNative(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap,
        jintArray lutR_arr,
        jintArray lutG_arr,
        jintArray lutB_arr,
        jint lutSize) {

    AndroidBitmapInfo info;
    void* pixels;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    jint* lutR = env->GetIntArrayElements(lutR_arr, NULL);
    jint* lutG = env->GetIntArrayElements(lutG_arr, NULL);
    jint* lutB = env->GetIntArrayElements(lutB_arr, NULL);

    uint32_t* line = (uint32_t*) pixels;
    int width = info.width;
    int height = info.height;
    int lutSize2 = lutSize * lutSize;
    int lutMax = lutSize - 1;

    // THE INTEGER MATH SPEED LOOP
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            uint32_t pixel = line[y * width + x];
            
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // Scale up by 128 (7 bit shift) for integer math instead of floats
            int fX = (r * lutMax * 128) / 255;
            int fY = (g * lutMax * 128) / 255;
            int fZ = (b * lutMax * 128) / 255;

            int x0 = fX >> 7;
            int y0 = fY >> 7;
            int z0 = fZ >> 7;

            int x1 = x0 + 1; if (x1 > lutMax) x1 = lutMax;
            int y1 = y0 + 1; if (y1 > lutMax) y1 = lutMax;
            int z1 = z0 + 1; if (z1 > lutMax) z1 = lutMax;

            // Get the remainder for weighting
            int dx = fX & 0x7F; 
            int dy = fY & 0x7F;
            int dz = fZ & 0x7F;

            int idx_x = 128 - dx;
            int idy = 128 - dy;
            int idz = 128 - dz;

            // Weights (shifted by 21 bits total: 7+7+7)
            int w000 = idx_x * idy * idz;
            int w100 = dx * idy * idz;
            int w010 = idx_x * dy * idz;
            int w110 = dx * dy * idz;
            int w001 = idx_x * idy * dz;
            int w101 = dx * idy * dz;
            int w011 = idx_x * dy * dz;
            int w111 = dx * dy * dz;

            // Find positions in the 3D LUT array
            int i000 = x0 + y0 * lutSize + z0 * lutSize2;
            int i100 = x1 + y0 * lutSize + z0 * lutSize2;
            int i010 = x0 + y1 * lutSize + z0 * lutSize2;
            int i110 = x1 + y1 * lutSize + z0 * lutSize2;
            int i001 = x0 + y0 * lutSize + z1 * lutSize2;
            int i101 = x1 + y0 * lutSize + z1 * lutSize2;
            int i011 = x0 + y1 * lutSize + z1 * lutSize2;
            int i111 = x1 + y1 * lutSize + z1 * lutSize2;

            // Multiply, sum, and bit-shift down by 21 to get the final color
            int outR = (lutR[i000]*w000 + lutR[i100]*w100 + lutR[i010]*w010 + lutR[i110]*w110 + lutR[i001]*w001 + lutR[i101]*w101 + lutR[i011]*w011 + lutR[i111]*w111) >> 21;
            int outG = (lutG[i000]*w000 + lutG[i100]*w100 + lutG[i010]*w010 + lutG[i110]*w110 + lutG[i001]*w001 + lutG[i101]*w101 + lutG[i011]*w011 + lutG[i111]*w111) >> 21;
            int outB = (lutB[i000]*w000 + lutB[i100]*w100 + lutB[i010]*w010 + lutB[i110]*w110 + lutB[i001]*w001 + lutB[i101]*w101 + lutB[i011]*w011 + lutB[i111]*w111) >> 21;

            line[y * width + x] = (0xFF << 24) | (outR << 16) | (outG << 8) | outB;
        }
    }

    // Clean up memory
    env->ReleaseIntArrayElements(lutR_arr, lutR, JNI_ABORT);
    env->ReleaseIntArrayElements(lutG_arr, lutG, JNI_ABORT);
    env->ReleaseIntArrayElements(lutB_arr, lutB, JNI_ABORT);
    AndroidBitmap_unlockPixels(env, bitmap);
}