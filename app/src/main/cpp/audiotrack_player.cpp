#include <jni.h>
#include <android/log.h>
#include <media/AudioTrack.h>
#include <media/AudioSystem.h>
#include <utils/StrongPointer.h>
#include <thread>
#include <mutex>
#include <atomic>

using namespace android;

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VARKYNTH_AT", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "VARKYNTH_AT", __VA_ARGS__)

struct ATPlayer {
    sp<AudioTrack> track;
    std::atomic<bool> playing;
    int sampleRate;
    int channelCount;
    audio_format_t format;
};

extern "C" {

// Создание и инициализация AudioTrack
JNIEXPORT jlong JNICALL
Java_com_VARKYNTH_Player_nativeaudio_AudioTrackEngine_naInit(JNIEnv* env, jclass,
                                                             jint sr, jint chCount) {
    ATPlayer* p = new ATPlayer();
    p->sampleRate = sr;
    p->channelCount = chCount;
    p->format = AUDIO_FORMAT_PCM_16_BIT;
    p->playing = false;

    audio_channel_mask_t mask = (chCount == 1) ? AUDIO_CHANNEL_OUT_MONO : AUDIO_CHANNEL_OUT_STEREO;

    sp<AudioTrack> track = new AudioTrack(
        AUDIO_STREAM_MUSIC,
        sr,
        p->format,
        mask,
        (size_t)0,
        AUDIO_OUTPUT_FLAG_NONE,
        nullptr, // callback
        nullptr, // user
        0,       // notification frames
        AUDIO_SESSION_ALLOCATE
    );

    if (track->initCheck() != NO_ERROR) {
        LOGE("AudioTrack init failed");
        delete p;
        return 0;
    }

    track->setVolume(1.0f, 1.0f);
    track->start();

    p->track = track;
    p->playing = true;

    LOGI("AudioTrack started sr=%d ch=%d", sr, chCount);
    return reinterpret_cast<jlong>(p);
}

// Пишем PCM16 данные
JNIEXPORT jint JNICALL
Java_com_VARKYNTH_Player_nativeaudio_AudioTrackEngine_naWrite(JNIEnv* env, jclass,
                                                              jlong handle, jobject buffer, jint size) {
    ATPlayer* p = reinterpret_cast<ATPlayer*>(handle);
    if (!p || !p->playing || !p->track.get()) return 0;

    void* data = env->GetDirectBufferAddress(buffer);
    if (!data) return 0;

    ssize_t wrote = p->track->write(data, size, true);
    if (wrote < 0) wrote = 0;
    return (jint)wrote;
}

// Пауза
JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_AudioTrackEngine_naPause(JNIEnv*, jclass, jlong handle) {
    ATPlayer* p = reinterpret_cast<ATPlayer*>(handle);
    if (!p || !p->track.get()) return;
    p->track->pause();
    p->playing = false;
}

// Возобновление
JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_AudioTrackEngine_naResume(JNIEnv*, jclass, jlong handle) {
    ATPlayer* p = reinterpret_cast<ATPlayer*>(handle);
    if (!p || !p->track.get()) return;
    p->track->start();
    p->playing = true;
}

// Освобождение
JNIEXPORT void JNICALL
Java_com_VARKYNTH_Player_nativeaudio_AudioTrackEngine_naRelease(JNIEnv*, jclass, jlong handle) {
    ATPlayer* p = reinterpret_cast<ATPlayer*>(handle);
    if (!p) return;
    if (p->track.get()) {
        p->track->stop();
        p->track.clear();
    }
    delete p;
    LOGI("AudioTrack released");
}

}