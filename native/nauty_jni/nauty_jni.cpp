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
#include <cstring>
#include <queue>
#include <stdexcept>
#include <string>
#include <utility>
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

void dedupe_adj(EdgeList &g) {
    for (auto &row : g.adj) {
        std::sort(row.begin(), row.end());
        row.erase(std::unique(row.begin(), row.end()), row.end());
    }
}

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
    dedupe_adj(g);
    return g;
}

/**
 * Same geometry as PlanarStudy.buildGraphFromCombinedGen + NautyNative.pack:
 * each cycle becomes a polygon of edges; vertex labels are remapped to 0..n-1
 * in sorted order of the distinct labels that appear.
 *
 * points: concatenation of all cycles; cycleLens[i] = length of cycle i.
 */
EdgeList build_from_gen(const jint *points, int nPoints,
                        const jint *cycleLens, int nCycles,
                        bool directed) {
    std::vector<int> labels;
    labels.reserve(static_cast<size_t>(nPoints));
    for (int i = 0; i < nPoints; ++i) labels.push_back(points[i]);
    std::sort(labels.begin(), labels.end());
    labels.erase(std::unique(labels.begin(), labels.end()), labels.end());

    EdgeList g;
    g.n = static_cast<int>(labels.size());
    g.directed = directed;
    if (g.n == 0) {
        g.adj.clear();
        return g;
    }
    g.adj.assign(static_cast<size_t>(g.n), {});

    // Dense remap table when labels fit a modest range (typical: 1..N).
    int minLab = labels.front();
    int maxLab = labels.back();
    std::vector<int> dense;
    const bool useDense = (maxLab - minLab) <= 4000000 && maxLab <= 10000000;
    if (useDense) {
        dense.assign(static_cast<size_t>(maxLab + 1), -1);
        for (int i = 0; i < g.n; ++i) dense[static_cast<size_t>(labels[static_cast<size_t>(i)])] = i;
    }

    auto remap = [&](int label) -> int {
        if (useDense) return dense[static_cast<size_t>(label)];
        auto it = std::lower_bound(labels.begin(), labels.end(), label);
        return static_cast<int>(it - labels.begin());
    };

    int off = 0;
    for (int c = 0; c < nCycles; ++c) {
        int L = cycleLens[c];
        if (L < 0 || off + L > nPoints) {
            throw std::runtime_error("cycle length overrun");
        }
        if (L >= 2) {
            for (int i = 0; i < L; ++i) {
                int u = remap(points[off + i]);
                int v = remap(points[off + ((i + 1) % L)]);
                if (u == v) continue;
                g.adj[static_cast<size_t>(u)].push_back(v);
                if (!directed) g.adj[static_cast<size_t>(v)].push_back(u);
            }
        }
        off += L;
    }
    if (off != nPoints) throw std::runtime_error("points length mismatch");
    dedupe_adj(g);
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
    if (g.n <= 0) {
        if (hashOut) {
            hashOut[0] = hashOut[1] = hashOut[2] = 0;
        }
        if (grpsize1) *grpsize1 = 1;
        if (grpsize2) *grpsize2 = 0;
        return 0;
    }

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

/** Symmetrize adjacency for weak connectivity / undirected geometry Aut. */
EdgeList as_undirected(const EdgeList &g) {
    if (!g.directed) return g;
    EdgeList u;
    u.n = g.n;
    u.directed = false;
    u.adj = g.adj;
    for (int i = 0; i < g.n; ++i) {
        for (int v : g.adj[static_cast<size_t>(i)]) {
            u.adj[static_cast<size_t>(v)].push_back(i);
        }
    }
    dedupe_adj(u);
    return u;
}

bool is_disjoint_or_incomplete(const EdgeList &g, int nPoints) {
    if (g.n < nPoints) return true;
    if (g.n <= 1) return false;
    EdgeList view = as_undirected(g);
    std::vector<char> seen(static_cast<size_t>(view.n), 0);
    std::queue<int> q;
    q.push(0);
    seen[0] = 1;
    int count = 1;
    while (!q.empty()) {
        int u = q.front();
        q.pop();
        for (int v : view.adj[static_cast<size_t>(u)]) {
            if (!seen[static_cast<size_t>(v)]) {
                seen[static_cast<size_t>(v)] = 1;
                ++count;
                q.push(v);
            }
        }
    }
    return count < view.n;
}

struct NativeGraph {
    static constexpr uint32_t MAGIC = 0x4E475248u; // 'NGRH'
    uint32_t magic = MAGIC;
    EdgeList g;
};

NativeGraph *from_handle(jlong handle) {
    if (handle == 0) return nullptr;
    auto *ng = reinterpret_cast<NativeGraph *>(static_cast<uintptr_t>(handle));
    if (ng->magic != NativeGraph::MAGIC) return nullptr;
    return ng;
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

extern "C" JNIEXPORT jintArray JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeCanonicalHashFromGen(
        JNIEnv *env, jclass,
        jintArray points, jintArray cycleLens,
        jboolean directed, jboolean useTraces) {
    if (points == nullptr || cycleLens == nullptr) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "null generator arrays");
        return nullptr;
    }
    jsize nPoints = env->GetArrayLength(points);
    jsize nCycles = env->GetArrayLength(cycleLens);
    jint *pp = env->GetIntArrayElements(points, nullptr);
    if (pp == nullptr) return nullptr;
    jint *lp = env->GetIntArrayElements(cycleLens, nullptr);
    if (lp == nullptr) {
        env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        return nullptr;
    }

    jint hash[3] = {0, 0, 0};
    try {
        EdgeList g = build_from_gen(pp, nPoints, lp, nCycles, directed == JNI_TRUE);
        env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        pp = nullptr;
        lp = nullptr;
        run_canonical(g, useTraces == JNI_TRUE, hash, nullptr, nullptr);
    } catch (const std::exception &e) {
        if (pp) env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        if (lp) env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, e.what());
        return nullptr;
    } catch (...) {
        if (pp) env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        if (lp) env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/RuntimeException");
        if (ex) env->ThrowNew(ex, "nativeCanonicalHashFromGen failed");
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

// ================================================================
// Opaque native graph handle — build once from generators, reuse for
// canonical hash / connectivity / |Aut|.
// ================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeCreateFromGen(
        JNIEnv *env, jclass,
        jintArray points, jintArray cycleLens, jboolean directed) {
    if (points == nullptr || cycleLens == nullptr) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "null generator arrays");
        return 0;
    }
    jsize nPoints = env->GetArrayLength(points);
    jsize nCycles = env->GetArrayLength(cycleLens);
    jint *pp = env->GetIntArrayElements(points, nullptr);
    if (pp == nullptr) return 0;
    jint *lp = env->GetIntArrayElements(cycleLens, nullptr);
    if (lp == nullptr) {
        env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        return 0;
    }

    NativeGraph *ng = nullptr;
    try {
        ng = new NativeGraph();
        ng->g = build_from_gen(pp, nPoints, lp, nCycles, directed == JNI_TRUE);
        env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        pp = nullptr;
        lp = nullptr;
    } catch (const std::exception &e) {
        delete ng;
        if (pp) env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        if (lp) env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, e.what());
        return 0;
    } catch (...) {
        delete ng;
        if (pp) env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        if (lp) env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/RuntimeException");
        if (ex) env->ThrowNew(ex, "nativeCreateFromGen failed");
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(ng));
}

extern "C" JNIEXPORT void JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeFree(JNIEnv *, jclass, jlong handle) {
    NativeGraph *ng = from_handle(handle);
    if (!ng) return;
    ng->magic = 0;
    delete ng;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeCanonicalHashHandle(
        JNIEnv *env, jclass, jlong handle, jboolean useTraces) {
    NativeGraph *ng = from_handle(handle);
    if (!ng) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid native graph handle");
        return nullptr;
    }
    jint hash[3] = {0, 0, 0};
    try {
        run_canonical(ng->g, useTraces == JNI_TRUE, hash, nullptr, nullptr);
    } catch (...) {
        jclass ex = env->FindClass("java/lang/RuntimeException");
        if (ex) env->ThrowNew(ex, "nativeCanonicalHashHandle failed");
        return nullptr;
    }
    jintArray out = env->NewIntArray(3);
    if (out) env->SetIntArrayRegion(out, 0, 3, hash);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeGrpsizeHandle(
        JNIEnv *env, jclass, jlong handle,
        jboolean forceUndirected, jboolean useTraces) {
    NativeGraph *ng = from_handle(handle);
    if (!ng) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid native graph handle");
        return nullptr;
    }
    double g1 = 0;
    int g2 = 0;
    try {
        EdgeList owned;
        const EdgeList *run = &ng->g;
        if (forceUndirected == JNI_TRUE && ng->g.directed) {
            owned = as_undirected(ng->g);
            run = &owned;
        }
        run_canonical(*run, useTraces == JNI_TRUE, nullptr, &g1, &g2);
    } catch (...) {
        jclass ex = env->FindClass("java/lang/RuntimeException");
        if (ex) env->ThrowNew(ex, "nativeGrpsizeHandle failed");
        return nullptr;
    }
    return grpsize_to_jstring(env, g1, g2);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeIsDisjointOrIncomplete(
        JNIEnv *env, jclass, jlong handle, jint nPoints) {
    NativeGraph *ng = from_handle(handle);
    if (!ng) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid native graph handle");
        return JNI_TRUE;
    }
    return is_disjoint_or_incomplete(ng->g, nPoints) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_chandler_gap_graph_NautyNative_nativeVertexCount(
        JNIEnv *env, jclass, jlong handle) {
    NativeGraph *ng = from_handle(handle);
    if (!ng) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "invalid native graph handle");
        return -1;
    }
    return ng->g.n;
}

// ================================================================
// CongestionGraphPack — polygon gen → CSR + adjacency + Java-Random positions
// Vertex order: sorted distinct labels (stabilized; not HashSet iteration).
// ================================================================

namespace {

/** Exact match for {@code java.util.Random} (48-bit LCG). */
class JavaRandom {
    uint64_t seed_;
public:
    explicit JavaRandom(int64_t seed) {
        seed_ = (static_cast<uint64_t>(seed) ^ 0x5DEECE66DULL) & ((1ULL << 48) - 1);
    }
    int next(int bits) {
        seed_ = (seed_ * 0x5DEECE66DULL + 0xBULL) & ((1ULL << 48) - 1);
        return static_cast<int>(seed_ >> (48 - bits));
    }
    double nextDouble() {
        const int64_t a = static_cast<int64_t>(next(26));
        const int64_t b = static_cast<int64_t>(next(27));
        return ((a << 27) + b) * (1.0 / static_cast<double>(1LL << 53));
    }
};

struct CongestionPacked {
    std::vector<int> nodeIds; // sorted original labels
    std::vector<int> edgeU;
    std::vector<int> edgeV;
    std::vector<uint8_t> adjacency; // n*n, symmetric
};

CongestionPacked pack_congestion_from_gen(const jint *points, int nPoints,
                                          const jint *cycleLens, int nCycles,
                                          uint8_t *adjacencyOut,
                                          bool ownAdjacency) {
    std::vector<int> labels;
    labels.reserve(static_cast<size_t>(nPoints));
    for (int i = 0; i < nPoints; ++i) labels.push_back(points[i]);
    std::sort(labels.begin(), labels.end());
    labels.erase(std::unique(labels.begin(), labels.end()), labels.end());

    CongestionPacked out;
    out.nodeIds = labels;
    const int n = static_cast<int>(labels.size());
    if (n == 0) return out;

    int minLab = labels.front();
    int maxLab = labels.back();
    std::vector<int> dense(static_cast<size_t>(maxLab - minLab + 1), -1);
    for (int i = 0; i < n; ++i)
        dense[static_cast<size_t>(labels[static_cast<size_t>(i)] - minLab)] = i;

    std::vector<std::pair<int, int>> edges;
    edges.reserve(static_cast<size_t>(nPoints));
    int off = 0;
    for (int c = 0; c < nCycles; ++c) {
        int L = cycleLens[c];
        if (L < 0 || off + L > nPoints)
            throw std::runtime_error("cycle length overrun in congestion pack");
        if (L >= 2) {
            for (int i = 0; i < L; ++i) {
                int lu = points[off + i];
                int lv = points[off + ((i + 1) % L)];
                if (lu < minLab || lu > maxLab || lv < minLab || lv > maxLab)
                    throw std::runtime_error("vertex out of label range");
                int u = dense[static_cast<size_t>(lu - minLab)];
                int v = dense[static_cast<size_t>(lv - minLab)];
                if (u < 0 || v < 0) throw std::runtime_error("unmapped vertex");
                if (u == v) continue;
                if (u > v) std::swap(u, v);
                edges.emplace_back(u, v);
            }
        }
        off += L;
    }
    if (off != nPoints) throw std::runtime_error("points length mismatch");

    std::sort(edges.begin(), edges.end());
    edges.erase(std::unique(edges.begin(), edges.end()), edges.end());

    out.edgeU.resize(edges.size());
    out.edgeV.resize(edges.size());
    for (size_t e = 0; e < edges.size(); ++e) {
        out.edgeU[e] = edges[e].first;
        out.edgeV[e] = edges[e].second;
    }

    auto paint = [&](uint8_t *adj) {
        std::memset(adj, 0, static_cast<size_t>(n) * static_cast<size_t>(n));
        for (size_t e = 0; e < edges.size(); ++e) {
            int u = edges[e].first, v = edges[e].second;
            adj[static_cast<size_t>(u) * static_cast<size_t>(n) + static_cast<size_t>(v)] = 1;
            adj[static_cast<size_t>(v) * static_cast<size_t>(n) + static_cast<size_t>(u)] = 1;
        }
    };
    if (adjacencyOut != nullptr) {
        paint(adjacencyOut);
    } else if (ownAdjacency) {
        out.adjacency.assign(static_cast<size_t>(n) * static_cast<size_t>(n), 0);
        paint(out.adjacency.data());
    }
    return out;
}

static void paint_adjacency(uint8_t *adj, int n,
                            const std::vector<int> &edgeU,
                            const std::vector<int> &edgeV) {
    std::memset(adj, 0, static_cast<size_t>(n) * static_cast<size_t>(n));
    for (size_t e = 0; e < edgeU.size(); ++e) {
        int u = edgeU[e], v = edgeV[e];
        adj[static_cast<size_t>(u) * static_cast<size_t>(n) + static_cast<size_t>(v)] = 1;
        adj[static_cast<size_t>(v) * static_cast<size_t>(n) + static_cast<size_t>(u)] = 1;
    }
}

void fill_initial_positions(float *out, int n, int64_t seed) {
    JavaRandom rng(seed);
    const int m = n * 3;
    for (int i = 0; i < m; ++i)
        out[i] = static_cast<float>(rng.nextDouble());
}

jobjectArray new_object_array(JNIEnv *env, int len) {
    jclass objCls = env->FindClass("java/lang/Object");
    return env->NewObjectArray(len, objCls, nullptr);
}

jintArray to_jint_array(JNIEnv *env, const std::vector<int> &v) {
    jintArray a = env->NewIntArray(static_cast<jsize>(v.size()));
    if (!a) return nullptr;
    if (!v.empty()) {
        void *p = env->GetPrimitiveArrayCritical(a, nullptr);
        if (p) {
            std::memcpy(p, v.data(), v.size() * sizeof(int));
            env->ReleasePrimitiveArrayCritical(a, p, 0);
        } else {
            env->SetIntArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
        }
    }
    return a;
}

jbyteArray to_jbyte_array(JNIEnv *env, const std::vector<uint8_t> &v) {
    jbyteArray a = env->NewByteArray(static_cast<jsize>(v.size()));
    if (!a) return nullptr;
    if (!v.empty()) {
        void *p = env->GetPrimitiveArrayCritical(a, nullptr);
        if (p) {
            std::memcpy(p, v.data(), v.size());
            env->ReleasePrimitiveArrayCritical(a, p, 0);
        } else {
            env->SetByteArrayRegion(a, 0, static_cast<jsize>(v.size()),
                                    reinterpret_cast<const jbyte *>(v.data()));
        }
    }
    return a;
}

jfloatArray to_jfloat_array(JNIEnv *env, const std::vector<float> &v) {
    jfloatArray a = env->NewFloatArray(static_cast<jsize>(v.size()));
    if (!a) return nullptr;
    if (!v.empty()) {
        void *p = env->GetPrimitiveArrayCritical(a, nullptr);
        if (p) {
            std::memcpy(p, v.data(), v.size() * sizeof(float));
            env->ReleasePrimitiveArrayCritical(a, p, 0);
        } else {
            env->SetFloatArrayRegion(a, 0, static_cast<jsize>(v.size()), v.data());
        }
    }
    return a;
}

} // namespace

/**
 * Pack one generator.
 * Returns Object[]{ int[] nodeIds, int[] edgeU, int[] edgeV, byte[] adjacency }.
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_chandler_gap_graph_CongestionGraphPack_nativePackOne(
        JNIEnv *env, jclass,
        jintArray points, jintArray cycleLens) {
    if (points == nullptr || cycleLens == nullptr) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "null pack arrays");
        return nullptr;
    }
    jsize nPoints = env->GetArrayLength(points);
    jsize nCycles = env->GetArrayLength(cycleLens);
    jint *pp = env->GetIntArrayElements(points, nullptr);
    jint *lp = env->GetIntArrayElements(cycleLens, nullptr);
    if (pp == nullptr || lp == nullptr) {
        if (pp) env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        if (lp) env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        return nullptr;
    }

    CongestionPacked packed;
    try {
        packed = pack_congestion_from_gen(pp, nPoints, lp, nCycles, nullptr, true);
    } catch (const std::exception &e) {
        env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, e.what());
        return nullptr;
    }
    env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
    env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);

    jobjectArray out = new_object_array(env, 4);
    if (!out) return nullptr;
    env->SetObjectArrayElement(out, 0, to_jint_array(env, packed.nodeIds));
    env->SetObjectArrayElement(out, 1, to_jint_array(env, packed.edgeU));
    env->SetObjectArrayElement(out, 2, to_jint_array(env, packed.edgeV));
    env->SetObjectArrayElement(out, 3, to_jbyte_array(env, packed.adjacency));
    return out;
}

/**
 * Pack a batch of generators (same n required).
 *
 * points / cycleLens: concatenation across graphs.
 * cyclesPerGraph[g]: number of cycles in graph g (sum = cycleLens.length).
 * seeds: Java Random seeds for initial positions.
 *
 * Returns Object[]{
 *   int[1] { nVertices },
 *   int[] graphEdgeOffsets (numGraphs+1),
 *   int[] graphEdgeCounts,
 *   int[] edgeU, int[] edgeV,
 *   byte[] adjacency (numGraphs*n*n),
 *   float[] initialPos (numGraphs*seeds*n*3),
 *   int[][] nodeIdsPerGraph
 * }
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_chandler_gap_graph_CongestionGraphPack_nativePackBatch(
        JNIEnv *env, jclass,
        jintArray points, jintArray cycleLens, jintArray cyclesPerGraph,
        jlongArray seeds) {
    if (points == nullptr || cycleLens == nullptr || cyclesPerGraph == nullptr
            || seeds == nullptr) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "null packBatch arrays");
        return nullptr;
    }

    const int numGraphs = env->GetArrayLength(cyclesPerGraph);
    const int numSeeds = env->GetArrayLength(seeds);
    if (numGraphs <= 0) {
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, "empty packBatch");
        return nullptr;
    }

    jint *pp = env->GetIntArrayElements(points, nullptr);
    jint *lp = env->GetIntArrayElements(cycleLens, nullptr);
    jint *cp = env->GetIntArrayElements(cyclesPerGraph, nullptr);
    jlong *sp = env->GetLongArrayElements(seeds, nullptr);
    if (!pp || !lp || !cp || !sp) {
        if (pp) env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        if (lp) env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        if (cp) env->ReleaseIntArrayElements(cyclesPerGraph, cp, JNI_ABORT);
        if (sp) env->ReleaseLongArrayElements(seeds, sp, JNI_ABORT);
        return nullptr;
    }

    const int nPointsTotal = env->GetArrayLength(points);
    const int nCyclesTotal = env->GetArrayLength(cycleLens);

    std::vector<CongestionPacked> graphs;
    graphs.reserve(static_cast<size_t>(numGraphs));
    try {
        int pointOff = 0;
        int cycleOff = 0;
        for (int g = 0; g < numGraphs; ++g) {
            const int nCyc = cp[g];
            if (nCyc < 0 || cycleOff + nCyc > nCyclesTotal)
                throw std::runtime_error("cyclesPerGraph overrun");
            int nPts = 0;
            for (int c = 0; c < nCyc; ++c) nPts += lp[cycleOff + c];
            if (pointOff + nPts > nPointsTotal)
                throw std::runtime_error("points overrun");
            graphs.push_back(pack_congestion_from_gen(
                    pp + pointOff, nPts, lp + cycleOff, nCyc, nullptr, false));
            pointOff += nPts;
            cycleOff += nCyc;
        }
        if (pointOff != nPointsTotal || cycleOff != nCyclesTotal)
            throw std::runtime_error("trailing generator data");
    } catch (const std::exception &e) {
        env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
        env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
        env->ReleaseIntArrayElements(cyclesPerGraph, cp, JNI_ABORT);
        env->ReleaseLongArrayElements(seeds, sp, JNI_ABORT);
        jclass ex = env->FindClass("java/lang/IllegalArgumentException");
        if (ex) env->ThrowNew(ex, e.what());
        return nullptr;
    }
    env->ReleaseIntArrayElements(points, pp, JNI_ABORT);
    env->ReleaseIntArrayElements(cycleLens, lp, JNI_ABORT);
    env->ReleaseIntArrayElements(cyclesPerGraph, cp, JNI_ABORT);

    const int nVertices = static_cast<int>(graphs[0].nodeIds.size());
    for (int g = 1; g < numGraphs; ++g) {
        if (static_cast<int>(graphs[static_cast<size_t>(g)].nodeIds.size()) != nVertices) {
            env->ReleaseLongArrayElements(seeds, sp, JNI_ABORT);
            jclass ex = env->FindClass("java/lang/IllegalArgumentException");
            if (ex) env->ThrowNew(ex, "mixed vertex counts in batch");
            return nullptr;
        }
    }

    std::vector<int> offsets(static_cast<size_t>(numGraphs + 1));
    std::vector<int> counts(static_cast<size_t>(numGraphs));
    int totalEdges = 0;
    for (int g = 0; g < numGraphs; ++g) {
        offsets[static_cast<size_t>(g)] = totalEdges;
        counts[static_cast<size_t>(g)] = static_cast<int>(graphs[static_cast<size_t>(g)].edgeU.size());
        totalEdges += counts[static_cast<size_t>(g)];
    }
    offsets[static_cast<size_t>(numGraphs)] = totalEdges;

    std::vector<int> edgeU(static_cast<size_t>(totalEdges));
    std::vector<int> edgeV(static_cast<size_t>(totalEdges));
    int eo = 0;
    for (int g = 0; g < numGraphs; ++g) {
        auto &gp = graphs[static_cast<size_t>(g)];
        std::copy(gp.edgeU.begin(), gp.edgeU.end(), edgeU.begin() + eo);
        std::copy(gp.edgeV.begin(), gp.edgeV.end(), edgeV.begin() + eo);
        eo += counts[static_cast<size_t>(g)];
    }

    const size_t adjStride = static_cast<size_t>(nVertices) * static_cast<size_t>(nVertices);
    const jsize adjLen = static_cast<jsize>(static_cast<size_t>(numGraphs) * adjStride);
    jbyteArray adjArr = env->NewByteArray(adjLen);
    if (!adjArr) {
        env->ReleaseLongArrayElements(seeds, sp, JNI_ABORT);
        return nullptr;
    }
    {
        void *adjPtr = env->GetPrimitiveArrayCritical(adjArr, nullptr);
        if (!adjPtr) {
            env->ReleaseLongArrayElements(seeds, sp, JNI_ABORT);
            return nullptr;
        }
        auto *adj = static_cast<uint8_t *>(adjPtr);
        for (int g = 0; g < numGraphs; ++g) {
            paint_adjacency(adj + static_cast<size_t>(g) * adjStride, nVertices,
                            graphs[static_cast<size_t>(g)].edgeU,
                            graphs[static_cast<size_t>(g)].edgeV);
        }
        env->ReleasePrimitiveArrayCritical(adjArr, adjPtr, 0);
    }

    std::vector<float> initialPos(
            static_cast<size_t>(numGraphs) * static_cast<size_t>(numSeeds)
            * static_cast<size_t>(nVertices) * 3u);
    for (int g = 0; g < numGraphs; ++g) {
        for (int s = 0; s < numSeeds; ++s) {
            const size_t base =
                    (static_cast<size_t>(g) * static_cast<size_t>(numSeeds) + static_cast<size_t>(s))
                    * static_cast<size_t>(nVertices) * 3u;
            fill_initial_positions(initialPos.data() + base, nVertices, sp[s]);
        }
    }
    env->ReleaseLongArrayElements(seeds, sp, JNI_ABORT);

    // nodeIdsPerGraph
    jclass intArrCls = env->FindClass("[I");
    jobjectArray nodeIdsArr = env->NewObjectArray(numGraphs, intArrCls, nullptr);
    for (int g = 0; g < numGraphs; ++g) {
        env->SetObjectArrayElement(nodeIdsArr, g,
                                   to_jint_array(env, graphs[static_cast<size_t>(g)].nodeIds));
    }

    jint nVertBox[1] = {nVertices};
    jintArray nVertArr = env->NewIntArray(1);
    env->SetIntArrayRegion(nVertArr, 0, 1, nVertBox);

    jobjectArray out = new_object_array(env, 8);
    env->SetObjectArrayElement(out, 0, nVertArr);
    env->SetObjectArrayElement(out, 1, to_jint_array(env, offsets));
    env->SetObjectArrayElement(out, 2, to_jint_array(env, counts));
    env->SetObjectArrayElement(out, 3, to_jint_array(env, edgeU));
    env->SetObjectArrayElement(out, 4, to_jint_array(env, edgeV));
    env->SetObjectArrayElement(out, 5, adjArr);
    env->SetObjectArrayElement(out, 6, to_jfloat_array(env, initialPos));
    env->SetObjectArrayElement(out, 7, nodeIdsArr);
    return out;
}

