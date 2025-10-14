#include <aaudio/AAudio.h>
#include <atomic>
#include <cstring>
#include <android/log.h>
#include <jni.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "VARKYNTH_AAUDIO", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "VARKYNTH_AAUDIO", __VA_ARGS__)

struct Ring {
    uint8_t* buf;
    size_t cap;
    std::atomic<size_t> w{0}, r{0};
    Ring(size_t c): cap(c){ buf = (uint8_t*) malloc(c); }
    ~Ring(){ free(buf); }
    // write returns bytes written
    size_t write(const uint8_t* src, size_t n){
        size_t wpos = w.load(std::memory_order_relaxed);
        size_t rpos = r.load(std::memory_order_acquire);
        size_t free = (rpos + cap - wpos - 1) % cap;
        size_t todo = n < free ? n : free;
        size_t first = std::min(todo, cap - (wpos % cap));
        memcpy(buf + (wpos % cap), src, first);
        memcpy(buf, src + first, todo - first);
        w.store((wpos + todo) % cap, std::memory_order_release);
        return todo;
    }
    // read returns bytes read
    size_t read(uint8_t* dst, size_t n){
        size_t wpos = w.load(std::memory_order_acquire);
        size_t rpos = r.load(std::memory_order_relaxed);
        size_t avail = (wpos + cap - rpos) % cap;
        size_t todo = n < avail ? n : avail;
        size_t first = std::min(todo, cap - (rpos % cap));
        memcpy(dst, buf + (rpos % cap), first);
        memcpy(dst + first, buf, todo - first);
        r.store((rpos + todo) % cap, std::memory_order_release);
        return todo;
    }
    void flush(){ r.store(0); w.store(0); }
};

struct Engine {
    AAudioStream* stream = nullptr;
    Ring* ring = nullptr;
    int32_t sampleRate = 48000;
    int32_t channels = 2;
    std::atomic<int64_t> framesPlayed{0};
    float volL=1.f, volR=1.f;
};

static aaudio_data_callback_result_t dataCallback(
        AAudioStream* stream, void* userData, void* audioData, int32_t numFrames){
    Engine* e = (Engine*)userData;
    int bytesPerFrame = sizeof(int16_t) * e->channels;
    int32_t need = numFrames * bytesPerFrame;
    int32_t got = 0;
    uint8_t* out = (uint8_t*)audioData;
    while (got < need){
        size_t r = e->ring->read(out + got, need - got);
        if (r == 0) break; // underrun → заполним нулями
        got += (int32_t)r;
    }
    if (got < need) {
        memset(out + got, 0, (size_t)(need - got));
    } else {
        // применим простейшую громкость к int16
        int16_t* s = (int16_t*)out;
        for (int i=0;i<numFrames;i++){
            float L = s[2*i+0] * e->volL;
            float R = (e->channels>1) ? s[2*i+1] * e->volR : L;
            s[2*i+0] = (int16_t) (L > 32767.f ? 32767.f : (L < -32768.f ? -32768.f : L));
            if (e->channels>1)
                s[2*i+1] = (int16_t) (R > 32767.f ? 32767.f : (R < -32768.f ? -32768.f : R));
        }
    }
    e->framesPlayed += numFrames;
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void errorCallback(AAudioStream* stream, void* userData, aaudio_result_t error){
    LOGE("AAudio error: %d", error);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naInit
  (JNIEnv*, jclass, jint sampleRate, jint channels){
    auto* e = new Engine();
    e->sampleRate = sampleRate;
    e->channels = channels;
    e->ring = new Ring(1024*1024); // 1MB кольцевой буфер

    AAudioStreamBuilder* b = nullptr;
    aaudio_result_t res = AAudio_createStreamBuilder(&b);
    if (res != AAUDIO_OK) { delete e; return 0; }
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setSampleRate(b, sampleRate);
    AAudioStreamBuilder_setChannelCount(b, channels);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED); // эксклюзив может не дать ОС
    AAudioStreamBuilder_setDataCallback(b, dataCallback, e);
    AAudioStreamBuilder_setErrorCallback(b, errorCallback, e);

    res = AAudioStreamBuilder_openStream(b, &e->stream);
    AAudioStreamBuilder_delete(b);
    if (res != AAUDIO_OK) { delete e->ring; delete e; return 0; }
    return (jlong)e;
}

JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naStart
  (JNIEnv*, jclass, jlong h){
    auto* e = (Engine*)h;
    if (!e || !e->stream) return;
    AAudioStream_requestStart(e->stream);
}

JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naStop
  (JNIEnv*, jclass, jlong h){
    auto* e = (Engine*)h;
    if (!e || !e->stream) return;
    AAudioStream_requestStop(e->stream);
    e->ring->flush();
}

JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naRelease
  (JNIEnv*, jclass, jlong h){
    auto* e = (Engine*)h;
    if (!e) return;
    if (e->stream){ AAudioStream_close(e->stream); e->stream = nullptr; }
    delete e->ring; e->ring = nullptr;
    delete e;
}

JNIEXPORT jint JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naWrite
  (JNIEnv* env, jclass, jlong h, jobject jbuf, jint len){
    auto* e = (Engine*)h;
    if (!e || !e->ring) return 0;
    uint8_t* src = (uint8_t*)env->GetDirectBufferAddress(jbuf);
    if (!src) return 0;
    size_t wrote = e->ring->write(src, (size_t)len);
    return (jint)wrote;
}

JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naFlush
  (JNIEnv*, jclass, jlong h){
    auto* e = (Engine*)h; if (e && e->ring) e->ring->flush();
}

JNIEXPORT jint JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naGetPlaybackHeadMs
  (JNIEnv*, jclass, jlong h){
    auto* e = (Engine*)h;
    if (!e) return 0;
    int64_t frames = e->framesPlayed.load();
    return (jint)((frames * 1000) / (e->sampleRate));
}

JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_NativeAudioEngine_naSetVolume
  (JNIEnv*, jclass, jlong h, jfloat l, jfloat r){
    auto* e = (Engine*)h; if (!e) return;
    e->volL = l; e->volR = r;
}

} // extern "C"