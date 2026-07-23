#include <jni.h>
#include <cstdint>
#include <cstring>

extern "C" {
int congestion_cuda_init();
void congestion_cuda_shutdown();
const char *congestion_cuda_device_name();
int congestion_cuda_evaluate_batch(
    int numGraphs, int numSeeds, int nVertices,
    const int *graphEdgeOffsets, const int *graphEdgeCounts,
    const int *edgeI, const int *edgeJ, const uint8_t *adjacency,
    const float *initialPos, const int *checkpoints, int numCheckpoints,
    const float *thresholds,
    const float *rotMatrices, int numRotations, int maxCheckpoint, float *scoresOut);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_chandler_gap_graph_CongestionCuda_nativeInit(JNIEnv *, jclass) {
    return congestion_cuda_init() == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_chandler_gap_graph_CongestionCuda_nativeShutdown(JNIEnv *, jclass) {
    congestion_cuda_shutdown();
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_chandler_gap_graph_CongestionCuda_nativeDeviceName(JNIEnv *env, jclass) {
    return env->NewStringUTF(congestion_cuda_device_name());
}

extern "C" JNIEXPORT jint JNICALL
Java_io_chandler_gap_graph_CongestionCuda_nativeEvaluateBatch(
    JNIEnv *env, jclass,
    jint numGraphs, jint numSeeds, jint nVertices,
    jintArray graphEdgeOffsets, jintArray graphEdgeCounts,
    jintArray edgeI, jintArray edgeJ, jbyteArray adjacency,
    jfloatArray initialPos, jintArray checkpoints,
    jfloatArray thresholds,
    jfloatArray rotMatrices, jint numRotations, jint maxCheckpoint,
    jfloatArray scoresOut) {

    jsize offLen = env->GetArrayLength(graphEdgeOffsets);
    jsize edgeLen = env->GetArrayLength(edgeI);
    jsize adjLen = env->GetArrayLength(adjacency);
    jsize posLen = env->GetArrayLength(initialPos);
    jsize cpLen = env->GetArrayLength(checkpoints);
    jsize scoreLen = env->GetArrayLength(scoresOut);
    jsize rotLen = env->GetArrayLength(rotMatrices);

    jint *hOffsets = env->GetIntArrayElements(graphEdgeOffsets, nullptr);
    jint *hCounts = env->GetIntArrayElements(graphEdgeCounts, nullptr);
    jint *hEdgeI = env->GetIntArrayElements(edgeI, nullptr);
    jint *hEdgeJ = env->GetIntArrayElements(edgeJ, nullptr);
    jbyte *hAdj = env->GetByteArrayElements(adjacency, nullptr);
    jfloat *hPos = env->GetFloatArrayElements(initialPos, nullptr);
    jint *hCp = env->GetIntArrayElements(checkpoints, nullptr);
    jfloat *hThresholds = env->GetFloatArrayElements(thresholds, nullptr);
    jfloat *hRot = env->GetFloatArrayElements(rotMatrices, nullptr);
    jfloat *hScores = env->GetFloatArrayElements(scoresOut, nullptr);

    int rc = congestion_cuda_evaluate_batch(
        numGraphs, numSeeds, nVertices,
        reinterpret_cast<const int *>(hOffsets),
        reinterpret_cast<const int *>(hCounts),
        reinterpret_cast<const int *>(hEdgeI),
        reinterpret_cast<const int *>(hEdgeJ),
        reinterpret_cast<const uint8_t *>(hAdj),
        hPos,
        reinterpret_cast<const int *>(hCp),
        cpLen,
        hThresholds,
        hRot,
        numRotations,
        maxCheckpoint,
        hScores);

    env->ReleaseIntArrayElements(graphEdgeOffsets, hOffsets, JNI_ABORT);
    env->ReleaseIntArrayElements(graphEdgeCounts, hCounts, JNI_ABORT);
    env->ReleaseIntArrayElements(edgeI, hEdgeI, JNI_ABORT);
    env->ReleaseIntArrayElements(edgeJ, hEdgeJ, JNI_ABORT);
    env->ReleaseByteArrayElements(adjacency, hAdj, JNI_ABORT);
    env->ReleaseFloatArrayElements(initialPos, hPos, JNI_ABORT);
    env->ReleaseIntArrayElements(checkpoints, hCp, JNI_ABORT);
    env->ReleaseFloatArrayElements(thresholds, hThresholds, JNI_ABORT);
    env->ReleaseFloatArrayElements(rotMatrices, hRot, JNI_ABORT);
    env->ReleaseFloatArrayElements(scoresOut, hScores, 0);

    (void)offLen;
    (void)edgeLen;
    (void)adjLen;
    (void)posLen;
    (void)scoreLen;
    (void)rotLen;
    return rc;
}
