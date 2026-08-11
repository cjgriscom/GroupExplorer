// JNI bridge to a TLS-enabled libnauty (built by ./build.sh and statically
// linked into this .so). Canonical hash matches dreadnaut's `z` command
// (hashgraph_sg with seeds 2922320, 19883109, 489317).
//
// When HAVE_TLS=1 (required), Traces/nauty may be called concurrently from
// multiple Java threads with no process-wide mutex.

#define MAXN 0
#include <nauty.h>
#include <nausparse.h>
#include <traces.h>

#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#if !HAVE_TLS
#error "libnauty_jni requires a TLS-enabled nauty (configure --enable-tls). Run native/nauty_jni/build.sh"
#endif

namespace {

// Same seeds as dreadnaut.c case 'z'.
constexpr long HASH_SEED0 = 2922320L;
constexpr long HASH_SEED1 = 19883109L;
constexpr long HASH_SEED2 = 489317L;

struct EdgeList {
    int n = 0;
    bool directed = false;
    std::vector<std::vector<int>> adj;
};

EdgeList build_adj(int n, const jint *edges, int nEdges, bool directed) {
    EdgeList g;
    g.n = n;
    g.directed = directed;
    g.adj.assign(static_cast<size_t>(n), {});
    for (int i = 0; i < nEdges; ++i) {
        int u = edges[2 * i];
        int v = edges[2 * i + 1];
        if (u < 0 || v < 0 || u >= n || v >= n || u == v) continue;
        g.adj[static_cast<size_t>(u)].push_back(v);
        if (!directed) g.adj[static_cast<size_t>(v)].push_back(u);
    }
    for (auto &row : g.adj) {
        std::sort(row.begin(), row.end());
        row.erase(std::unique(row.begin(), row.end()), row.end());
    }
    return g;
}

void fill_sparsegraph(const EdgeList &g, sparsegraph &sg) {
    size_t nde = 0;
    for (auto &row : g.adj) nde += row.size();
    SG_ALLOC(sg, g.n, static_cast<size_t>(nde), "SG_ALLOC");
    sg.nv = g.n;
    sg.nde = static_cast<size_t>(nde);
    size_t pos = 0;
    for (int i = 0; i < g.n; ++i) {
        sg.v[i] = pos;
        sg.d[i] = static_cast<int>(g.adj[static_cast<size_t>(i)].size());
        for (int nb : g.adj[static_cast<size_t>(i)]) {
            sg.e[pos++] = nb;
        }
    }
}

void hash_canong(sparsegraph &cg, jint *out3) {
    long h0 = hashgraph_sg(&cg, HASH_SEED0);
    long h1 = hashgraph_sg(&cg, HASH_SEED1);
    long h2 = hashgraph_sg(&cg, HASH_SEED2);
    out3[0] = static_cast<jint>(h0);
    out3[1] = static_cast<jint>(h1);
    out3[2] = static_cast<jint>(h2);
}

jstring grpsize_to_jstring(JNIEnv *env, double grpsize1, int grpsize2) {
    std::vector<char> buf(64);
    int written = snprintf(buf.data(), buf.size(), "%.0f", grpsize1);
    if (written < 0) return env->NewStringUTF("0");
    std::string s(buf.data());
    if (grpsize2 > 0) s.append(static_cast<size_t>(grpsize2), '0');
    return env->NewStringUTF(s.c_str());
}

int run_canonical(const EdgeList &g, bool useTraces, jint *hashOut,
                  double *grpsize1, int *grpsize2) {
    SG_DECL(sg);
    SG_DECL(cg);
    fill_sparsegraph(g, sg);

    DYNALLSTAT(int, lab, lab_sz);
    DYNALLSTAT(int, ptn, ptn_sz);
    DYNALLSTAT(int, orbits, orbits_sz);
    DYNALLOC1(int, lab, lab_sz, g.n, "lab");
    DYNALLOC1(int, ptn, ptn_sz, g.n, "ptn");
    DYNALLOC1(int, orbits, orbits_sz, g.n, "orbits");

    size_t nde = sg.nde;
    SG_ALLOC(cg, g.n, nde, "cg");

    if (useTraces && !g.directed) {
        DEFAULTOPTIONS_TRACES(options);
        TracesStats stats;
        options.getcanon = TRUE;
        options.defaultptn = TRUE;
        Traces(&sg, lab, ptn, orbits, &options, &stats, &cg);
        if (grpsize1) *grpsize1 = stats.grpsize1;
        if (grpsize2) *grpsize2 = stats.grpsize2;
    } else {
        DEFAULTOPTIONS_SPARSEGRAPH(options);
        statsblk stats;
        options.getcanon = TRUE;
        options.defaultptn = TRUE;
        options.digraph = g.directed ? TRUE : FALSE;
        sparsenauty(&sg, lab, ptn, orbits, &options, &stats, &cg);
        if (grpsize1) *grpsize1 = stats.grpsize1;
        if (grpsize2) *grpsize2 = stats.grpsize2;
    }

    if (hashOut) hash_canong(cg, hashOut);

    SG_FREE(sg);
    SG_FREE(cg);
    return 0;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeAvailable(JNIEnv *, jclass) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeHasTls(JNIEnv *, jclass) {
    return HAVE_TLS ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeVersion(JNIEnv *env, jclass) {
    char buf[64];
    snprintf(buf, sizeof(buf), "%s (TLS=%d)", NAUTYVERSION, HAVE_TLS);
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeCanonicalHash(
        JNIEnv *env, jclass,
        jint n, jintArray edges, jboolean directed, jboolean useTraces) {
    if (n <= 0 || edges == nullptr) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid graph for nauty");
        return nullptr;
    }
    jsize elen = env->GetArrayLength(edges);
    if (elen % 2 != 0) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "edges length must be even");
        return nullptr;
    }
    int nEdges = elen / 2;
    jint *ep = env->GetIntArrayElements(edges, nullptr);
    if (ep == nullptr) return nullptr;

    jint hash[3] = {0, 0, 0};
    try {
        EdgeList g = build_adj(n, ep, nEdges, directed == JNI_TRUE);
        env->ReleaseIntArrayElements(edges, ep, JNI_ABORT);
        ep = nullptr;
        run_canonical(g, useTraces == JNI_TRUE, hash, nullptr, nullptr);
    } catch (...) {
        if (ep) env->ReleaseIntArrayElements(edges, ep, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/RuntimeException");
        if (ex) env->ThrowNew(ex, "native nauty canonicalHash failed");
        return nullptr;
    }

    jintArray out = env->NewIntArray(3);
    if (out) env->SetIntArrayRegion(out, 0, 3, hash);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeCanonicalAndGrpsize(
        JNIEnv *env, jclass,
        jint n, jintArray edges, jboolean directed, jboolean useTraces) {
    if (n <= 0 || edges == nullptr) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid graph for nauty");
        return nullptr;
    }
    jsize elen = env->GetArrayLength(edges);
    if (elen % 2 != 0) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "edges length must be even");
        return nullptr;
    }
    int nEdges = elen / 2;
    jint *ep = env->GetIntArrayElements(edges, nullptr);
    if (ep == nullptr) return nullptr;

    jint hash[3] = {0, 0, 0};
    double g1 = 0;
    int g2 = 0;
    try {
        EdgeList g = build_adj(n, ep, nEdges, directed == JNI_TRUE);
        env->ReleaseIntArrayElements(edges, ep, JNI_ABORT);
        ep = nullptr;
        run_canonical(g, useTraces == JNI_TRUE, hash, &g1, &g2);
    } catch (...) {
        if (ep) env->ReleaseIntArrayElements(edges, ep, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/RuntimeException");
        if (ex) env->ThrowNew(ex, "native nauty failed");
        return nullptr;
    }

    jstring gs = grpsize_to_jstring(env, g1, g2);
    const char *gsChars = env->GetStringUTFChars(gs, nullptr);
    char buf[128];
    snprintf(buf, sizeof(buf), "%d %d %d|%s",
             hash[0], hash[1], hash[2], gsChars ? gsChars : "0");
    if (gsChars) env->ReleaseStringUTFChars(gs, gsChars);
    return env->NewStringUTF(buf);
}
