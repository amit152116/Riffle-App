#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <chrono>
#include <atomic>
#include <memory>
#include <cmath>
#include <android/log.h>

#include <essentia/algorithmfactory.h>
#include <essentia/essentiamath.h>
#include <essentia/pool.h>

#include <chromaprint.h>
#include <speex_resampler.h>
#include <sha256.h>

#define LOG_TAG "RiffleDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace std;
using namespace essentia;
using namespace essentia::standard;

namespace {

// Mirrors ../riffle/riffle/features.py's EXTRACTION_CONFIG dict, one field per
// key. Single source of truth for these values (used to be scattered
// constants/literals at each call site) — if this ever needs to be hashed
// for a config_hash/re-analysis-detection feature (riffle's
// extraction_config_hash()), it's one struct to hash instead of a hunt.
struct ExtractionConfig {
    int sampleRate;
    int frameSize;
    int hopSize;
    const char* windowType;
    int mfccBands;
    const char* rhythmMethod;
};

constexpr ExtractionConfig kExtractionConfig{
        /*sampleRate=*/44100,
        /*frameSize=*/2048,
        /*hopSize=*/1024,
        /*windowType=*/"hann",
        /*mfccBands=*/13,
        /*rhythmMethod=*/"multifeature",
};

std::string sha256Hex(const std::string& input) {
    SHA256_CTX ctx;
    sha256_init(&ctx);
    sha256_update(&ctx, reinterpret_cast<const BYTE*>(input.data()), input.size());
    BYTE digest[SHA256_BLOCK_SIZE];
    sha256_final(&ctx, digest);

    static const char* kHexDigits = "0123456789abcdef";
    std::string hex(SHA256_BLOCK_SIZE * 2, '0');
    for (int i = 0; i < SHA256_BLOCK_SIZE; ++i) {
        hex[i * 2] = kHexDigits[(digest[i] >> 4) & 0xF];
        hex[i * 2 + 1] = kHexDigits[digest[i] & 0xF];
    }
    return hex;
}

std::string jsonInt(const char* key, int value) {
    return "\"" + std::string(key) + "\":" + std::to_string(value);
}

std::string jsonString(const char* key, const char* value) {
    return "\"" + std::string(key) + "\":\"" + value + "\"";
}

// Matches Python's `json.dumps(EXTRACTION_CONFIG, sort_keys=True,
// separators=(",", ":"))` byte for byte: same compact separators, same value
// types (bare ints, quoted strings), and critically the same key order —
// alphabetical, because that's what `sort_keys=True` produces. That order is
// the one thing that must stay hand-maintained here (nothing enforces it
// automatically); it's encoded as the order of this vector, not buried in a
// hand-typed string, so extending this list can't silently drop a comma or a
// quote the way one long concatenated expression could.
std::string extractionConfigJson(const ExtractionConfig& c) {
    const std::vector<std::string> fields = {
            jsonInt("frame_size", c.frameSize),
            jsonInt("hop_size", c.hopSize),
            jsonInt("mfcc_bands", c.mfccBands),
            jsonString("rhythm_method", c.rhythmMethod),
            jsonInt("sample_rate", c.sampleRate),
            jsonString("window_type", c.windowType),
    };

    std::string json = "{";
    for (size_t i = 0; i < fields.size(); ++i) {
        if (i > 0) json += ",";
        json += fields[i];
    }
    json += "}";
    return json;
}

// Mirrors ../riffle/riffle/features.py::extraction_config_hash(): full
// (untruncated) SHA-256 hex digest of the config JSON above. A change to
// kExtractionConfig changes this value, the same signal riffle uses to
// detect "re-analysis needed" for audio_features rows.
const std::string& extractionConfigHash() {
    static const std::string hash = sha256Hex(extractionConfigJson(kExtractionConfig));
    return hash;
}

// Replaces MonoLoader's resampling (ffmpeg+libsamplerate, unavailable on
// Android). Kotlin hands us PCM at whatever rate MediaCodec decoded; band-
// limiting + resampling to what Essentia's algorithms assume happens here,
// via a real polyphase resampler (see speex_resampler/README.md) rather than
// a hand-rolled FIR, quality 10 (speex's max, best passband/stopband).
// Speex quality 6 instead of the maximum 10: measured on 16 real tracks (see docs), features drift by ~0.02% of
// the between-track spread, BPM/key are unchanged, pairwise-similarity ranking is preserved (correlation 0.99999)
// and fingerprints stay 99.5%+ bit-identical, for ~24% less analysis time (4x less on the resample stage itself).
constexpr int kResampleQuality = 6;

std::vector<Real> resampleToTargetRate(const std::vector<Real>& input, int fromRate) {
    if (fromRate == kExtractionConfig.sampleRate || input.empty()) {
        return input;
    }

    int err = RESAMPLER_ERR_SUCCESS;
    SpeexResamplerState* resampler = speex_resampler_init(
            /*nb_channels=*/1, static_cast<spx_uint32_t>(fromRate),
            static_cast<spx_uint32_t>(kExtractionConfig.sampleRate), /*quality=*/kResampleQuality, &err);
    if (resampler == nullptr || err != RESAMPLER_ERR_SUCCESS) {
        throw std::runtime_error("speex_resampler_init failed");
    }

    auto inLen = static_cast<spx_uint32_t>(input.size());
    auto outCapacity = static_cast<spx_uint32_t>(
            (static_cast<uint64_t>(input.size()) * kExtractionConfig.sampleRate) / fromRate + 64);
    std::vector<Real> output(outCapacity);
    spx_uint32_t outLen = outCapacity;

    int rc = speex_resampler_process_float(resampler, 0, input.data(), &inLen,
            output.data(), &outLen);
    speex_resampler_destroy(resampler);
    if (rc != RESAMPLER_ERR_SUCCESS) {
        throw std::runtime_error("speex_resampler_process_float failed");
    }
    output.resize(outLen);
    return output;
}

std::mutex gEssentiaMutex;        // serialises whole extractions while gSerialExtraction is set
std::mutex gFactoryMutex;         // AlgorithmFactory::create touches a shared registry
// Validated on device: concurrent extractions are bit-identical to serial ones (see PerformanceExperimentsTest.e4).
std::atomic<bool> gSerialExtraction{false};

// Essentia algorithm instances are independent once created; only the factory registry is shared.
template <typename... Args>
Algorithm* createAlgo(AlgorithmFactory& factory, const std::string& name, Args&&... args) {
    std::lock_guard<std::mutex> lock(gFactoryMutex);
    return factory.create(name, std::forward<Args>(args)...);
}
std::once_flag gInitFlag;

void ensureEssentiaInitialized() {
    std::call_once(gInitFlag, []() {
        essentia::init();
        // Essentia's Logger queues messages in one global std::deque that is documented as not thread-safe
        // (debugging.h). Concurrent extractions that hit a warning (e.g. the streaming network's buffer-state
        // print) corrupted the heap and aborted the app. We never read Essentia's stderr output, so turn all
        // levels off: Logger then returns before touching the shared queue.
        essentia::infoLevelActive = false;
        essentia::warningLevelActive = false;
        essentia::errorLevelActive = false;
    });
}

// mirrors features.py::_compute_energy
float computeEnergy(float rawEnergy, size_t nSamples) {
    if (nSamples == 0) return 0.0f;
    float meanSquare = rawEnergy / static_cast<float>(nSamples);
    return std::min(std::sqrt(meanSquare), 1.0f);
}

// mirrors features.py::_correct_bpm
float correctBpm(float bpm) {
    if (bpm < 60.0f) return bpm * 2.0f;
    if (bpm > 200.0f) return bpm / 2.0f;
    return bpm;
}

// ../riffle/riffle/fingerprint.py's DEFAULT_CONFIG uses fpcalc's CLI algorithm
// number 2, which is stored verbatim as the `algorithm` column. fpcalc's CLI
// numbering is off-by-one from libchromaprint's own enum (see fingerprint.py
// ::internal_algorithm, verified there against a real fpcalc round trip):
// CLI 2 == CHROMAPRINT_ALGORITHM_TEST2 == internal enum value 1.
constexpr int kFingerprintCliAlgorithm = 2;
constexpr int kFingerprintInternalAlgorithm = CHROMAPRINT_ALGORITHM_TEST2;

// Mirrors fingerprint.py::fingerprint_file: raw (not base64) fingerprint,
// fed directly from already-decoded PCM rather than shelling out to fpcalc
// (which isn't available on Android; libchromaprint itself has no ffmpeg
// dependency, since we hand it PCM samples we already have).
std::vector<int32_t> computeChromaprint(const std::vector<Real>& audio, int sampleRate) {
    ChromaprintContext* ctx = chromaprint_new(kFingerprintInternalAlgorithm);
    if (ctx == nullptr) {
        throw std::runtime_error("chromaprint_new failed");
    }

    std::vector<int32_t> result;
    try {
        std::vector<int16_t> pcm(audio.size());
        for (size_t i = 0; i < audio.size(); ++i) {
            float clamped = std::max(-1.0f, std::min(1.0f, audio[i]));
            pcm[i] = static_cast<int16_t>(clamped * 32767.0f);
        }

        if (chromaprint_start(ctx, sampleRate, /*num_channels=*/1) != 1) {
            throw std::runtime_error("chromaprint_start failed");
        }
        if (!pcm.empty() &&
            chromaprint_feed(ctx, pcm.data(), static_cast<int>(pcm.size())) != 1) {
            throw std::runtime_error("chromaprint_feed failed");
        }
        if (chromaprint_finish(ctx) != 1) {
            throw std::runtime_error("chromaprint_finish failed");
        }

        uint32_t* rawFingerprint = nullptr;
        int rawSize = 0;
        if (chromaprint_get_raw_fingerprint(ctx, &rawFingerprint, &rawSize) != 1) {
            throw std::runtime_error("chromaprint_get_raw_fingerprint failed");
        }
        result.assign(rawFingerprint, rawFingerprint + rawSize);
        chromaprint_dealloc(rawFingerprint);
    } catch (...) {
        chromaprint_free(ctx);
        throw;
    }
    chromaprint_free(ctx);
    return result;
}

// Streaming counterpart of computeChromaprint(): identical int16 conversion, Chromaprint
// algorithm and 44100 Hz target rate, but input arrives in chunks and is resampled by a
// stateful speex resampler (same quality 10 as resampleToTargetRate). At 44100 Hz no
// resampler is created, so the result equals the batch path exactly.
struct FingerprintSession {
    ChromaprintContext* chroma = nullptr;
    SpeexResamplerState* resampler = nullptr; // null when the input is already at target rate
    int fromRate = 0;
    std::vector<Real> resampled;
    std::vector<int16_t> pcm;
    bool finished = false;

    ~FingerprintSession() {
        if (resampler != nullptr) speex_resampler_destroy(resampler);
        if (chroma != nullptr) chromaprint_free(chroma);
    }
};

void feedChromaprint(FingerprintSession& s, const Real* data, size_t n) {
    if (n == 0) return;
    s.pcm.resize(n);
    for (size_t i = 0; i < n; ++i) {
        float clamped = std::max(-1.0f, std::min(1.0f, data[i]));
        s.pcm[i] = static_cast<int16_t>(clamped * 32767.0f);
    }
    if (chromaprint_feed(s.chroma, s.pcm.data(), static_cast<int>(n)) != 1) {
        throw std::runtime_error("chromaprint_feed failed");
    }
}

FingerprintSession* fingerprintBegin(int sampleRate) {
    if (sampleRate <= 0) throw std::runtime_error("invalid sample rate");
    auto session = std::make_unique<FingerprintSession>();
    session->fromRate = sampleRate;
    session->chroma = chromaprint_new(kFingerprintInternalAlgorithm);
    if (session->chroma == nullptr) throw std::runtime_error("chromaprint_new failed");
    if (chromaprint_start(session->chroma, kExtractionConfig.sampleRate, /*num_channels=*/1) != 1) {
        throw std::runtime_error("chromaprint_start failed");
    }
    if (sampleRate != kExtractionConfig.sampleRate) {
        int err = RESAMPLER_ERR_SUCCESS;
        session->resampler = speex_resampler_init(
                1, static_cast<spx_uint32_t>(sampleRate),
                static_cast<spx_uint32_t>(kExtractionConfig.sampleRate), /*quality=*/kResampleQuality, &err);
        if (session->resampler == nullptr || err != RESAMPLER_ERR_SUCCESS) {
            throw std::runtime_error("speex_resampler_init failed");
        }
    }
    return session.release();
}

void fingerprintFeed(FingerprintSession& s, const Real* data, size_t n) {
    if (s.finished) throw std::runtime_error("fingerprint session already finished");
    if (s.resampler == nullptr) {
        feedChromaprint(s, data, n);
        return;
    }
    size_t consumed = 0;
    while (consumed < n) {
        auto inLen = static_cast<spx_uint32_t>(n - consumed);
        auto outCapacity = static_cast<spx_uint32_t>(
                (static_cast<uint64_t>(inLen) * kExtractionConfig.sampleRate) / s.fromRate + 64);
        s.resampled.resize(outCapacity);
        spx_uint32_t outLen = outCapacity;
        if (speex_resampler_process_float(s.resampler, 0, data + consumed, &inLen,
                s.resampled.data(), &outLen) != RESAMPLER_ERR_SUCCESS) {
            throw std::runtime_error("speex_resampler_process_float failed");
        }
        feedChromaprint(s, s.resampled.data(), outLen);
        if (inLen == 0 && outLen == 0) break; // no progress possible; avoid spinning
        consumed += inLen;
    }
}

std::vector<int32_t> fingerprintFinish(FingerprintSession& s) {
    if (s.finished) throw std::runtime_error("fingerprint session already finished");
    s.finished = true;
    if (chromaprint_finish(s.chroma) != 1) throw std::runtime_error("chromaprint_finish failed");
    uint32_t* raw = nullptr;
    int rawSize = 0;
    if (chromaprint_get_raw_fingerprint(s.chroma, &raw, &rawSize) != 1) {
        throw std::runtime_error("chromaprint_get_raw_fingerprint failed");
    }
    std::vector<int32_t> result(raw, raw + rawSize);
    chromaprint_dealloc(raw);
    return result;
}

struct ExtractedFeatures {
    float bpm = 0.0f;
    float bpmConfidence = 0.0f;
    std::string keyName;
    std::string scale;
    float keyStrength = 0.0f;
    float loudnessLufs = 0.0f;
    float danceability = 0.0f;
    float energy = 0.0f;
    float spectralCentroid = 0.0f;
    float onsetRate = 0.0f;
    float dynamicComplexity = 0.0f;
    float dissonance = 0.0f;
    float zcr = 0.0f;
    std::vector<float> mfccMean;
    float duration = 0.0f;
    std::vector<int32_t> fingerprint;
    int fingerprintAlgorithm = -1; // -1 = not computed
    std::string configHash;
};

// Mirrors ../riffle/riffle/features.py extract_file() step for step, plus
// the fingerprint from ../riffle/riffle/fingerprint.py (a separate pass in
// Python, over the original file via fpcalc; folded in here since this app
// returns one AudioFeatures object per native call). MonoLoader is replaced
// by Kotlin's own decode (see EssentiaExtractor.kt) + resampleToTargetRate
// above; every algorithm below assumes `sampleRate` (Essentia's Real).
// Per-stage wall-clock timings of the last runPipeline() on this thread (read via nativeLastStageTimings).
thread_local std::string gLastStageTimings;

struct StageTimer {
    using Clock = std::chrono::steady_clock;
    Clock::time_point last = Clock::now();
    std::string prev = "resample";
    std::string log;
    void mark(const char* next) {
        auto now = Clock::now();
        log += prev + "=" + std::to_string(std::chrono::duration<double, std::milli>(now - last).count()) + "ms ";
        prev = next;
        last = now;
    }
    void finish() {
        mark("end");
        gLastStageTimings = log;
    }
};

ExtractedFeatures runPipeline(const std::vector<Real>& rawAudio, int inputSampleRate,
                              bool withFingerprint = true) {
    StageTimer st;
    if (rawAudio.empty()) {
        throw std::runtime_error("empty audio buffer");
    }

    std::vector<Real> audio = resampleToTargetRate(rawAudio, inputSampleRate);
    if (audio.empty()) {
        throw std::runtime_error("resampling produced no samples");
    }
    constexpr int sampleRate = kExtractionConfig.sampleRate;

    AlgorithmFactory& factory = AlgorithmFactory::instance();
    ExtractedFeatures result;
    result.duration = static_cast<float>(audio.size()) / static_cast<float>(sampleRate);
    result.mfccMean.assign(kExtractionConfig.mfccBands, 0.0f);
    result.configHash = extractionConfigHash();

    st.mark("rhythm");
    // --- RhythmExtractor2013 (multifeature) -> bpm, confidence ---
    {
        unique_ptr<Algorithm> rhythmExtractor(createAlgo(factory, "RhythmExtractor2013",
                "method", kExtractionConfig.rhythmMethod));
        Real bpm, confidence;
        std::vector<Real> ticks, estimates, bpmIntervals;
        rhythmExtractor->input("signal").set(audio);
        rhythmExtractor->output("bpm").set(bpm);
        rhythmExtractor->output("ticks").set(ticks);
        rhythmExtractor->output("confidence").set(confidence);
        rhythmExtractor->output("estimates").set(estimates);
        rhythmExtractor->output("bpmIntervals").set(bpmIntervals);
        rhythmExtractor->compute();
        result.bpm = correctBpm(static_cast<float>(bpm));
        result.bpmConfidence = static_cast<float>(confidence);
    }

    st.mark("key");
    // --- KeyExtractor -> key, scale, strength ---
    {
        unique_ptr<Algorithm> keyExtractor(createAlgo(factory, "KeyExtractor"));
        std::string key, scale;
        Real strength;
        keyExtractor->input("audio").set(audio);
        keyExtractor->output("key").set(key);
        keyExtractor->output("scale").set(scale);
        keyExtractor->output("strength").set(strength);
        keyExtractor->compute();
        result.keyName = key;
        result.scale = scale;
        result.keyStrength = static_cast<float>(strength);
    }

    st.mark("loudness");
    // --- StereoMuxer(audio, audio) + LoudnessEBUR128 -> integrated LUFS ---
    {
        unique_ptr<Algorithm> stereoMuxer(createAlgo(factory, "StereoMuxer"));
        std::vector<StereoSample> stereoAudio;
        stereoMuxer->input("left").set(audio);
        stereoMuxer->input("right").set(audio);
        stereoMuxer->output("audio").set(stereoAudio);
        stereoMuxer->compute();

        unique_ptr<Algorithm> loudness(createAlgo(factory, "LoudnessEBUR128",
                "sampleRate", static_cast<Real>(sampleRate)));
        std::vector<Real> momentary, shortTerm;
        Real integrated, loudnessRange;
        loudness->input("signal").set(stereoAudio);
        loudness->output("momentaryLoudness").set(momentary);
        loudness->output("shortTermLoudness").set(shortTerm);
        loudness->output("integratedLoudness").set(integrated);
        loudness->output("loudnessRange").set(loudnessRange);
        loudness->compute();
        result.loudnessLufs = static_cast<float>(integrated);
    }

    st.mark("danceability");
    // --- Danceability ---
    {
        unique_ptr<Algorithm> danceability(createAlgo(factory, "Danceability"));
        Real dance;
        std::vector<Real> dfa;
        danceability->input("signal").set(audio);
        danceability->output("danceability").set(dance);
        danceability->output("dfa").set(dfa);
        danceability->compute();
        result.danceability = static_cast<float>(dance);
    }

    st.mark("energy");
    // --- Energy ---
    {
        unique_ptr<Algorithm> energyAlgo(createAlgo(factory, "Energy"));
        Real rawEnergy;
        energyAlgo->input("array").set(audio);
        energyAlgo->output("energy").set(rawEnergy);
        energyAlgo->compute();
        result.energy = computeEnergy(static_cast<float>(rawEnergy), audio.size());
    }

    st.mark("centroid");
    // --- SpectralCentroidTime ---
    {
        unique_ptr<Algorithm> centroid(createAlgo(factory, "SpectralCentroidTime"));
        Real sc;
        centroid->input("array").set(audio);
        centroid->output("centroid").set(sc);
        centroid->compute();
        result.spectralCentroid = static_cast<float>(sc);
    }

    st.mark("onset");
    // --- OnsetRate ---
    {
        unique_ptr<Algorithm> onsetRateAlgo(createAlgo(factory, "OnsetRate"));
        std::vector<Real> onsets;
        Real onsetRate;
        onsetRateAlgo->input("signal").set(audio);
        onsetRateAlgo->output("onsets").set(onsets);
        onsetRateAlgo->output("onsetRate").set(onsetRate);
        onsetRateAlgo->compute();
        result.onsetRate = static_cast<float>(onsetRate);
    }

    st.mark("dynComplexity");
    // --- DynamicComplexity ---
    {
        unique_ptr<Algorithm> dynComplexity(createAlgo(factory, "DynamicComplexity"));
        Real dc, loudnessDb;
        dynComplexity->input("signal").set(audio);
        dynComplexity->output("dynamicComplexity").set(dc);
        dynComplexity->output("loudness").set(loudnessDb);
        dynComplexity->compute();
        result.dynamicComplexity = static_cast<float>(dc);
    }

    st.mark("zcr");
    // --- ZeroCrossingRate ---
    {
        unique_ptr<Algorithm> zcrAlgo(createAlgo(factory, "ZeroCrossingRate"));
        Real zcr;
        zcrAlgo->input("signal").set(audio);
        zcrAlgo->output("zeroCrossingRate").set(zcr);
        zcrAlgo->compute();
        result.zcr = static_cast<float>(zcr);
    }

    st.mark("perFrame(mfcc+dissonance)");
    // --- Per-frame: Windowing -> Spectrum -> MFCC / SpectralPeaks -> Dissonance ---
    {
        unique_ptr<Algorithm> frameCutter(createAlgo(factory, "FrameCutter",
                "frameSize", kExtractionConfig.frameSize, "hopSize", kExtractionConfig.hopSize));
        unique_ptr<Algorithm> windowing(createAlgo(factory, "Windowing", "type", kExtractionConfig.windowType));
        unique_ptr<Algorithm> spectrumAlgo(createAlgo(factory, "Spectrum"));
        unique_ptr<Algorithm> mfccAlgo(createAlgo(factory, "MFCC",
                "numberCoefficients", kExtractionConfig.mfccBands));
        unique_ptr<Algorithm> spectralPeaks(createAlgo(factory, "SpectralPeaks"));
        unique_ptr<Algorithm> dissonanceAlgo(createAlgo(factory, "Dissonance"));

        std::vector<Real> frame, windowed, spectrum, mfccBands, mfccCoeffs, peakFreqs, peakMags;

        frameCutter->input("signal").set(audio);
        frameCutter->output("frame").set(frame);

        windowing->input("frame").set(frame);
        windowing->output("frame").set(windowed);

        spectrumAlgo->input("frame").set(windowed);
        spectrumAlgo->output("spectrum").set(spectrum);

        mfccAlgo->input("spectrum").set(spectrum);
        mfccAlgo->output("bands").set(mfccBands);
        mfccAlgo->output("mfcc").set(mfccCoeffs);

        spectralPeaks->input("spectrum").set(spectrum);
        spectralPeaks->output("frequencies").set(peakFreqs);
        spectralPeaks->output("magnitudes").set(peakMags);

        std::vector<double> mfccSum(kExtractionConfig.mfccBands, 0.0);
        double dissonanceSum = 0.0;
        size_t mfccFrames = 0;
        size_t dissonanceFrames = 0;

        while (true) {
            frameCutter->compute();
            if (frame.empty()) break;

            windowing->compute();
            spectrumAlgo->compute();

            mfccAlgo->compute();
            for (size_t i = 0; i < mfccCoeffs.size() && i < mfccSum.size(); ++i) {
                mfccSum[i] += mfccCoeffs[i];
            }
            ++mfccFrames;

            spectralPeaks->compute();
            if (peakFreqs.size() >= 2) {
                Real diss;
                dissonanceAlgo->input("frequencies").set(peakFreqs);
                dissonanceAlgo->input("magnitudes").set(peakMags);
                dissonanceAlgo->output("dissonance").set(diss);
                dissonanceAlgo->compute();
                dissonanceSum += diss;
                ++dissonanceFrames;
            }
        }

        if (mfccFrames > 0) {
            for (int i = 0; i < kExtractionConfig.mfccBands; ++i) {
                result.mfccMean[i] = static_cast<float>(mfccSum[i] / static_cast<double>(mfccFrames));
            }
        }
        if (dissonanceFrames > 0) {
            result.dissonance = static_cast<float>(dissonanceSum / static_cast<double>(dissonanceFrames));
        }
    }

    st.mark("chromaprint");
    // --- Chromaprint fingerprint ---
    // Kept independent of the algorithms above: in ../riffle, feature
    // extraction and fingerprinting are separate passes with separate
    // failure handling (a fingerprint failure never blocks audio_features
    // from being written, and vice versa). A failure here falls back to
    // "not computed" rather than aborting the whole extraction.
    if (withFingerprint) {
        try {
            result.fingerprint = computeChromaprint(audio, sampleRate);
            result.fingerprintAlgorithm = kFingerprintCliAlgorithm;
        } catch (const std::exception& e) {
            LOGE("Chromaprint fingerprinting failed: %s", e.what());
            result.fingerprint.clear();
            result.fingerprintAlgorithm = -1;
        }
    }

    st.finish();
    return result;
}

jobject buildAudioFeaturesObject(JNIEnv* env, const ExtractedFeatures& f) {
    jclass audioFeaturesClass = env->FindClass("com/amit152116/riffle/dsp/AudioFeatures");
    if (audioFeaturesClass == nullptr) return nullptr;

    jmethodID constructor = env->GetMethodID(audioFeaturesClass, "<init>",
            "(FFLjava/lang/String;Ljava/lang/String;FFFFFFFFF[FF[IILjava/lang/String;)V");
    if (constructor == nullptr) return nullptr;

    jstring jKeyName = env->NewStringUTF(f.keyName.c_str());
    jstring jScale = env->NewStringUTF(f.scale.c_str());
    jstring jConfigHash = env->NewStringUTF(f.configHash.c_str());

    jfloatArray jMfcc = env->NewFloatArray(static_cast<jsize>(f.mfccMean.size()));
    env->SetFloatArrayRegion(jMfcc, 0, static_cast<jsize>(f.mfccMean.size()), f.mfccMean.data());

    jintArray jFingerprint = env->NewIntArray(static_cast<jsize>(f.fingerprint.size()));
    if (!f.fingerprint.empty()) {
        static_assert(sizeof(int32_t) == sizeof(jint), "int32_t must be binary-compatible with jint");
        env->SetIntArrayRegion(jFingerprint, 0, static_cast<jsize>(f.fingerprint.size()),
                reinterpret_cast<const jint*>(f.fingerprint.data()));
    }

    return env->NewObject(audioFeaturesClass, constructor,
        f.bpm,
        f.bpmConfidence,
        jKeyName,
        jScale,
        f.keyStrength,
        f.loudnessLufs,
        f.danceability,
        f.energy,
        f.spectralCentroid,
        f.onsetRate,
        f.dynamicComplexity,
        f.dissonance,
        f.zcr,
        jMfcc,
        f.duration,
        jFingerprint,
        f.fingerprintAlgorithm,
        jConfigHash
    );
}

} // namespace

namespace {

// Reads a jfloatArray into a std::vector<Real> (Real == float, same layout as jfloat).
std::vector<Real> readFloatArray(JNIEnv* env, jfloatArray samples) {
    static_assert(sizeof(Real) == sizeof(jfloat), "Real must be binary-compatible with jfloat");
    jsize n = env->GetArrayLength(samples);
    std::vector<Real> audio(static_cast<size_t>(n));
    if (n > 0) {
        env->GetFloatArrayRegion(samples, 0, n, reinterpret_cast<jfloat*>(audio.data()));
    }
    return audio;
}

void throwRuntimeException(JNIEnv* env, const char* message) {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
    }
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeExtract(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray samples,
        jint sampleRate,
        jboolean withFingerprint) {

    std::unique_lock<std::mutex> lock(gEssentiaMutex, std::defer_lock);
    if (gSerialExtraction.load()) lock.lock();
    ensureEssentiaInitialized();

    try {
        std::vector<Real> audio = readFloatArray(env, samples);
        ExtractedFeatures features = runPipeline(audio, sampleRate, withFingerprint == JNI_TRUE);
        return buildAudioFeaturesObject(env, features);
    } catch (const std::exception& e) {
        LOGE("Feature extraction failed: %s", e.what());
        throwRuntimeException(env, e.what());
        return nullptr;
    } catch (...) {
        LOGE("Feature extraction failed: unknown error");
        throwRuntimeException(env, "unknown error during Essentia feature extraction");
        return nullptr;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeFingerprintBegin(JNIEnv* env, jobject, jint sampleRate) {
    try { return reinterpret_cast<jlong>(fingerprintBegin(sampleRate)); }
    catch (const std::exception& e) { throwRuntimeException(env, e.what()); return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeFingerprintFeed(
        JNIEnv* env, jobject, jlong handle, jfloatArray samples, jint count) {
    try {
        auto* s = reinterpret_cast<FingerprintSession*>(handle);
        if (s == nullptr || count < 0 || count > env->GetArrayLength(samples)) {
            throw std::runtime_error("invalid fingerprint feed arguments");
        }
        std::vector<Real> chunk(static_cast<size_t>(count));
        if (count > 0) env->GetFloatArrayRegion(samples, 0, count, reinterpret_cast<jfloat*>(chunk.data()));
        fingerprintFeed(*s, chunk.data(), chunk.size());
    } catch (const std::exception& e) { throwRuntimeException(env, e.what()); }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeFingerprintFinish(JNIEnv* env, jobject, jlong handle) {
    std::unique_ptr<FingerprintSession> s(reinterpret_cast<FingerprintSession*>(handle)); // always freed
    try {
        if (!s) throw std::runtime_error("null fingerprint session");
        std::vector<int32_t> fp = fingerprintFinish(*s);
        jintArray out = env->NewIntArray(static_cast<jsize>(fp.size()));
        if (!fp.empty()) env->SetIntArrayRegion(out, 0, static_cast<jsize>(fp.size()), reinterpret_cast<const jint*>(fp.data()));
        return out;
    } catch (const std::exception& e) { throwRuntimeException(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeFingerprintAbort(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<FingerprintSession*>(handle); // deleting a null handle is a no-op
}

extern "C" JNIEXPORT void JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeSetSerialExtraction(JNIEnv*, jobject, jboolean serial) {
    gSerialExtraction.store(serial == JNI_TRUE);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeLastStageTimings(JNIEnv* env, jobject) {
    return env->NewStringUTF(gLastStageTimings.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeConfigHash(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF(extractionConfigHash().c_str());
}

// Test-only: exposes the resampler directly so instrumented tests can verify
// its anti-aliasing behavior without going through the whole feature pipeline.
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_amit152116_riffle_dsp_EssentiaExtractor_nativeResampleForTest(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray samples,
        jint fromRate,
        jint toRate) {
    if (toRate != kExtractionConfig.sampleRate) {
        throwRuntimeException(env, "nativeResampleForTest only supports toRate == 44100");
        return nullptr;
    }

    try {
        std::vector<Real> input = readFloatArray(env, samples);
        std::vector<Real> output = resampleToTargetRate(input, fromRate);
        jfloatArray result = env->NewFloatArray(static_cast<jsize>(output.size()));
        if (!output.empty()) {
            env->SetFloatArrayRegion(result, 0, static_cast<jsize>(output.size()),
                    reinterpret_cast<const jfloat*>(output.data()));
        }
        return result;
    } catch (const std::exception& e) {
        throwRuntimeException(env, e.what());
        return nullptr;
    }
}
