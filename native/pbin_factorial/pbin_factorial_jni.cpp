// JNI: factorial number-system decode for PBIN generators.
// Uses GMP (mpz_tdiv_q_ui) for the hot single-limb division loop.

#include <jni.h>
#include <gmp.h>

#include <cstdint>
#include <cstring>
#include <vector>

namespace {

class FenwickTree {
    std::vector<int> t;
    int n = 0, logn = 0;
public:
    void init(int n_) {
        n = n_;
        t.assign(n + 1, 0);
        for (int i = 1; i <= n; ++i) {
            t[i] += 1;
            int j = i + (i & -i);
            if (j <= n) t[j] += t[i];
        }
        logn = 0;
        while ((1 << (logn + 1)) <= n) ++logn;
    }

    int kth(int k) const {
        int pos = 0, rem = k + 1;
        for (int i = logn; i >= 0; --i) {
            int nxt = pos + (1 << i);
            if (nxt <= n && t[nxt] < rem) {
                rem -= t[nxt];
                pos = nxt;
            }
        }
        return pos + 1;
    }

    void remove(int x) {
        for (int i = x; i <= n; i += i & -i) t[i] -= 1;
    }
};

thread_local FenwickTree tl_ft;
thread_local std::vector<uint32_t> tl_indices;
thread_local std::vector<int> tl_points;
thread_local bool tl_mpz_inited = false;
thread_local mpz_t tl_acc;

void ensure_mpz() {
    if (!tl_mpz_inited) {
        mpz_init(tl_acc);
        tl_mpz_inited = true;
    }
}

void factorial_decode_into(const uint8_t *bytes, size_t len, int N, int k,
                           int *out) {
    ensure_mpz();
    // big-endian unsigned import (order=1, size=1, endian=1)
    mpz_import(tl_acc, len, 1, 1, 1, 0, bytes);

    tl_indices.resize(static_cast<size_t>(k));
    for (int i = k - 1; i >= 0; --i) {
        unsigned long remaining = static_cast<unsigned long>(N - i);
        tl_indices[static_cast<size_t>(i)] =
                static_cast<uint32_t>(mpz_tdiv_q_ui(tl_acc, tl_acc, remaining));
    }

    tl_ft.init(N);
    for (int i = 0; i < k; ++i) {
        int p = tl_ft.kth(static_cast<int>(tl_indices[static_cast<size_t>(i)]));
        tl_ft.remove(p);
        out[i] = p;
    }
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_chandler_gap_PbinFactorialNative_nativeAvailable(JNIEnv *, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_io_chandler_gap_PbinFactorialNative_nativeFactorialDecode(
        JNIEnv *env, jclass,
        jbyteArray bytes, jint N, jint k) {
    if (bytes == nullptr || N <= 0 || k < 0 || k > N) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid factorialDecode arguments");
        return nullptr;
    }
    jsize len = env->GetArrayLength(bytes);
    jbyte *raw = env->GetByteArrayElements(bytes, nullptr);
    if (raw == nullptr) return nullptr;

    tl_points.resize(static_cast<size_t>(k));
    factorial_decode_into(reinterpret_cast<const uint8_t *>(raw),
                          static_cast<size_t>(len), N, k, tl_points.data());
    env->ReleaseByteArrayElements(bytes, raw, JNI_ABORT);

    jintArray out = env->NewIntArray(k);
    if (out == nullptr) return nullptr;
    env->SetIntArrayRegion(out, 0, k, tl_points.data());
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_io_chandler_gap_PbinFactorialNative_nativeFactorialDecodeInto(
        JNIEnv *env, jclass,
        jbyteArray bytes, jint N, jint k, jintArray out) {
    if (bytes == nullptr || out == nullptr || N <= 0 || k < 0 || k > N) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid factorialDecodeInto arguments");
        return;
    }
    if (env->GetArrayLength(out) < k) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "out array too small");
        return;
    }
    jsize len = env->GetArrayLength(bytes);
    jbyte *raw = env->GetByteArrayElements(bytes, nullptr);
    if (raw == nullptr) return;

    tl_points.resize(static_cast<size_t>(k));
    factorial_decode_into(reinterpret_cast<const uint8_t *>(raw),
                          static_cast<size_t>(len), N, k, tl_points.data());
    env->ReleaseByteArrayElements(bytes, raw, JNI_ABORT);
    env->SetIntArrayRegion(out, 0, k, tl_points.data());
}

extern "C" JNIEXPORT void JNICALL
Java_io_chandler_gap_PbinFactorialNative_nativeFactorialDecodeSliceInto(
        JNIEnv *env, jclass,
        jbyteArray bytes, jint offset, jint length,
        jint N, jint k, jintArray out) {
    if (bytes == nullptr || out == nullptr || N <= 0 || k < 0 || k > N
            || offset < 0 || length < 0) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid factorialDecodeSliceInto arguments");
        return;
    }
    jsize arrLen = env->GetArrayLength(bytes);
    if (offset > arrLen || length > arrLen - offset) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "byte slice out of bounds");
        return;
    }
    if (env->GetArrayLength(out) < k) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "out array too small");
        return;
    }

    jbyte *raw = env->GetByteArrayElements(bytes, nullptr);
    if (raw == nullptr) return;

    tl_points.resize(static_cast<size_t>(k));
    factorial_decode_into(
            reinterpret_cast<const uint8_t *>(raw) + offset,
            static_cast<size_t>(length), N, k, tl_points.data());
    env->ReleaseByteArrayElements(bytes, raw, JNI_ABORT);
    env->SetIntArrayRegion(out, 0, k, tl_points.data());
}
