#include <cuda_runtime.h>
#include <cstddef>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

namespace {

constexpr float EPS = 0.01f;
constexpr float EPS_SQ = EPS * EPS;
constexpr float THRESHOLD = 1e-4f;
constexpr float SCORE_SCALE = 1000.0f;
constexpr float BOX_SIZE = 1000.0f;

__global__ void zeroDispKernel(float *disp, int stride, int n, int numStates) {
    int state = blockIdx.x;
    if (state >= numStates) return;
    for (int i = threadIdx.x; i < n * 3; i += blockDim.x) {
        disp[(size_t)state * stride + i] = 0.0f;
    }
}

__global__ void repulsionKernel(const float *pos, float *disp, int posStride, int dispStride,
                                int n, int numStates, int blocksPerState,
                                float kSq, const int *active) {
    int state = blockIdx.x / blocksPerState;
    if (state >= numStates || !active[state]) return;
    int stateBlock = blockIdx.x - state * blocksPerState;
    int i = stateBlock * blockDim.x + threadIdx.x;
    const float *pBase = pos + (size_t)state * posStride;
    float *dBase = disp + (size_t)state * dispStride;

    __shared__ float tileX[256];
    __shared__ float tileY[256];
    __shared__ float tileZ[256];

    float dix = 0.0f, diy = 0.0f, diz = 0.0f;
    float pix = 0.0f, piy = 0.0f, piz = 0.0f;
    if (i < n) {
        pix = pBase[i * 3 + 0];
        piy = pBase[i * 3 + 1];
        piz = pBase[i * 3 + 2];
    }

    for (int tileStart = 0; tileStart < n; tileStart += blockDim.x) {
        int jLoad = tileStart + threadIdx.x;
        if (jLoad < n) {
            tileX[threadIdx.x] = pBase[jLoad * 3 + 0];
            tileY[threadIdx.x] = pBase[jLoad * 3 + 1];
            tileZ[threadIdx.x] = pBase[jLoad * 3 + 2];
        }
        __syncthreads();
        if (i < n) {
            int tileCount = min((int)blockDim.x, n - tileStart);
            for (int tj = 0; tj < tileCount; tj++) {
                int j = tileStart + tj;
                if (j == i) continue;
                float dx = pix - tileX[tj];
                float dy = piy - tileY[tj];
                float dz = piz - tileZ[tj];
                float distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < EPS_SQ) distSq = EPS_SQ;
                float factor = kSq / distSq;
                dix += dx * factor;
                diy += dy * factor;
                diz += dz * factor;
            }
        }
        __syncthreads();
    }

    if (i < n) {
        dBase[i * 3 + 0] = dix;
        dBase[i * 3 + 1] = diy;
        dBase[i * 3 + 2] = diz;
    }
}

__global__ void attractionKernel(const float *pos, float *disp, const int *edgeI, const int *edgeJ,
                                 const int *edgeOffsets, const int *edgeCounts, int numSeeds,
                                 int posStride, int dispStride, int numStates, float invK,
                                 const int *active) {
    int state = blockIdx.x;
    if (state >= numStates || !active[state]) return;
    int graph = state / numSeeds;
    int edgeOffset = edgeOffsets[graph];
    int edgeCount = edgeCounts[graph];

    const float *pBase = pos + (size_t)state * posStride;
    float *dBase = disp + (size_t)state * dispStride;

    for (int e = threadIdx.x; e < edgeCount; e += blockDim.x) {
        int i = edgeI[edgeOffset + e];
        int j = edgeJ[edgeOffset + e];
        float dx = pBase[i * 3 + 0] - pBase[j * 3 + 0];
        float dy = pBase[i * 3 + 1] - pBase[j * 3 + 1];
        float dz = pBase[i * 3 + 2] - pBase[j * 3 + 2];
        float distance = sqrtf(dx * dx + dy * dy + dz * dz);
        if (distance < EPS) distance = EPS;
        float factor = distance * invK;
        float fx = dx * factor;
        float fy = dy * factor;
        float fz = dz * factor;
        atomicAdd(dBase + i * 3 + 0, -fx);
        atomicAdd(dBase + i * 3 + 1, -fy);
        atomicAdd(dBase + i * 3 + 2, -fz);
        atomicAdd(dBase + j * 3 + 0, fx);
        atomicAdd(dBase + j * 3 + 1, fy);
        atomicAdd(dBase + j * 3 + 2, fz);
    }
}

__global__ void updatePositionsKernel(float *pos, const float *disp, int posStride, int dispStride,
                                      int n, int numStates, float t, float *totalDispOut,
                                      const int *converged, const int *active) {
    int state = blockIdx.x;
    if (state >= numStates || converged[state] || !active[state]) return;
    float *pBase = pos + (size_t)state * posStride;
    const float *dBase = disp + (size_t)state * dispStride;

    float localTotal = 0.0f;
    for (int i = threadIdx.x; i < n; i += blockDim.x) {
        float d0 = dBase[i * 3 + 0];
        float d1 = dBase[i * 3 + 1];
        float d2 = dBase[i * 3 + 2];
        float dispLength = sqrtf(d0 * d0 + d1 * d1 + d2 * d2);
        if (dispLength < EPS) dispLength = EPS;
        float scale = fminf(dispLength, t) / dispLength;
        pBase[i * 3 + 0] += d0 * scale;
        pBase[i * 3 + 1] += d1 * scale;
        pBase[i * 3 + 2] += d2 * scale;
        localTotal += dispLength;
    }
    atomicAdd(totalDispOut + state, localTotal);
}

__global__ void markConvergedKernel(const float *totalDisp, int *converged, int n, int numStates) {
    int state = blockIdx.x * blockDim.x + threadIdx.x;
    if (state >= numStates || converged[state]) return;
    if (totalDisp[state] / n < THRESHOLD) {
        converged[state] = 1;
    }
}

__global__ void normPositionsKernel(float *pos, int posStride, int n, int numStates) {
    int state = blockIdx.x;
    if (state >= numStates) return;
    float *pBase = pos + (size_t)state * posStride;

    float minX = 1e30f, maxX = -1e30f;
    float minY = 1e30f, maxY = -1e30f;
    float minZ = 1e30f, maxZ = -1e30f;
    for (int i = 0; i < n; i++) {
        minX = fminf(minX, pBase[i * 3 + 0]);
        maxX = fmaxf(maxX, pBase[i * 3 + 0]);
        minY = fminf(minY, pBase[i * 3 + 1]);
        maxY = fmaxf(maxY, pBase[i * 3 + 1]);
        minZ = fminf(minZ, pBase[i * 3 + 2]);
        maxZ = fmaxf(maxZ, pBase[i * 3 + 2]);
    }
    float maxScale = fmaxf(maxX - minX, fmaxf(maxY - minY, maxZ - minZ));
    for (int i = threadIdx.x; i < n; i += blockDim.x) {
        if (maxX - minX > 0.0f) {
            pBase[i * 3 + 0] = (pBase[i * 3 + 0] - minX) / maxScale * 0.8f * BOX_SIZE + 0.1f * BOX_SIZE;
        } else {
            pBase[i * 3 + 0] = 0.5f * BOX_SIZE;
        }
        if (maxY - minY > 0.0f) {
            pBase[i * 3 + 1] = (pBase[i * 3 + 1] - minY) / maxScale * 0.8f * BOX_SIZE + 0.1f * BOX_SIZE;
        } else {
            pBase[i * 3 + 1] = 0.5f * BOX_SIZE;
        }
        if (maxZ - minZ > 0.0f) {
            pBase[i * 3 + 2] = (pBase[i * 3 + 2] - minZ) / maxScale * 0.8f * BOX_SIZE + 0.1f * BOX_SIZE;
        } else {
            pBase[i * 3 + 2] = 0.5f * BOX_SIZE;
        }
    }
}

__device__ inline float orientation(float ax, float ay, float bx, float by, float cx, float cy) {
    return (by - ay) * (cx - bx) - (bx - ax) * (cy - by);
}

__device__ inline bool onSegment(float ax, float ay, float bx, float by, float cx, float cy) {
    return fminf(ax, bx) <= cx && cx <= fmaxf(ax, bx)
        && fminf(ay, by) <= cy && cy <= fmaxf(ay, by);
}

__device__ inline bool segmentsIntersect(float ax, float ay, float bx, float by,
                                         float cx, float cy, float dx, float dy) {
    float o1 = orientation(ax, ay, bx, by, cx, cy);
    float o2 = orientation(ax, ay, bx, by, dx, dy);
    if (o1 * o2 < 0.0f) {
        float o3 = orientation(cx, cy, dx, dy, ax, ay);
        float o4 = orientation(cx, cy, dx, dy, bx, by);
        if (o3 * o4 < 0.0f) return true;
    }
    if (fabsf(o1) < 1e-6f && onSegment(ax, ay, cx, cy, bx, by)) return true;
    if (fabsf(o2) < 1e-6f && onSegment(ax, ay, dx, dy, bx, by)) return true;
    float o3 = orientation(cx, cy, dx, dy, ax, ay);
    if (fabsf(o3) < 1e-6f && onSegment(cx, cy, ax, ay, dx, dy)) return true;
    float o4 = orientation(cx, cy, dx, dy, bx, by);
    if (fabsf(o4) < 1e-6f && onSegment(cx, cy, bx, by, dx, dy)) return true;
    return false;
}

__device__ inline void ijFromPairIndex(int p, int n, int &i, int &j) {
    float a = (float)(2 * n - 1);
    i = (int)floorf((a - sqrtf(a * a - 8.0f * p)) * 0.5f);
    if (i < 0) i = 0;
    if (i > n - 2) i = n - 2;
    int rowStart = i * (2 * n - i - 1) / 2;
    while (rowStart > p && i > 0) {
        i--;
        rowStart = i * (2 * n - i - 1) / 2;
    }
    int nextStart = (i + 1) * (2 * n - i - 2) / 2;
    while (p >= nextStart && i < n - 2) {
        i++;
        rowStart = nextStart;
        nextStart = (i + 1) * (2 * n - i - 2) / 2;
    }
    j = i + 1 + (p - rowStart);
}

__global__ void projectPositionsKernel(
        const float *pos3d, float *pos2d, const float *rot,
        int rotIdx, int n, int pos3dStride, int pos2dStride,
        int numStates, const int *active) {
    int state = blockIdx.x;
    if (state >= numStates || !active[state]) return;
    const float *src = pos3d + (size_t)state * pos3dStride;
    float *dst = pos2d + (size_t)state * pos2dStride;
    const float *r = rot + rotIdx * 9;
    for (int i = threadIdx.x; i < n; i += blockDim.x) {
        float x = src[i * 3 + 0];
        float y = src[i * 3 + 1];
        float z = src[i * 3 + 2];
        dst[i * 2 + 0] = r[0] * x + r[1] * y + r[2] * z;
        dst[i * 2 + 1] = r[3] * x + r[4] * y + r[5] * z;
    }
}

__global__ void congestionScoreKernel(
    const float *pos2d, const int *edgeI, const int *edgeJ, const uint8_t *adjacency,
    int adjacencyStride, const int *edgeOffsets, const int *edgeCounts, int numSeeds,
    int n, int posStride, int numStates, int blocksPerState,
    int *crossingsOut, float *crowdingOut, int *crowdingPairsOut,
    const int *active) {
    int state = blockIdx.x / blocksPerState;
    if (state >= numStates || !active[state]) return;
    int stateBlock = blockIdx.x - state * blocksPerState;
    int graph = state / numSeeds;
    int edgeOffset = edgeOffsets[graph];
    int edgeCount = edgeCounts[graph];
    const float *pBase = pos2d + (size_t)state * posStride;
    const uint8_t *adjRowBase = adjacency + (size_t)graph * adjacencyStride;

    float optimal = 1.0f / sqrtf((float)n);
    float optimalSq = optimal * optimal;

    int crossings = 0;
    int edgePairs = edgeCount * (edgeCount - 1) / 2;
    int firstPair = stateBlock * blockDim.x + threadIdx.x;
    int pairStride = blocksPerState * blockDim.x;
    for (int p = firstPair; p < edgePairs; p += pairStride) {
        int i, j;
        ijFromPairIndex(p, edgeCount, i, j);

        int u0 = edgeI[edgeOffset + i], v0 = edgeJ[edgeOffset + i];
        int u1 = edgeI[edgeOffset + j], v1 = edgeJ[edgeOffset + j];
        if (u0 == u1 || u0 == v1 || v0 == u1 || v0 == v1) continue;

        float ax = pBase[u0 * 2], ay = pBase[u0 * 2 + 1];
        float bx = pBase[v0 * 2], by = pBase[v0 * 2 + 1];
        float cx = pBase[u1 * 2], cy = pBase[u1 * 2 + 1];
        float dx = pBase[v1 * 2], dy = pBase[v1 * 2 + 1];

        if (fmaxf(fminf(ax, bx), fminf(cx, dx)) > fminf(fmaxf(ax, bx), fmaxf(cx, dx))
            || fmaxf(fminf(ay, by), fminf(cy, dy)) > fminf(fmaxf(ay, by), fmaxf(cy, dy))) {
            continue;
        }
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) {
            crossings++;
        }
    }

    __shared__ int sCrossings;
    __shared__ float sCrowding;
    __shared__ int sCrowdingPairs;
    if (threadIdx.x == 0) {
        sCrossings = 0;
        sCrowding = 0.0f;
        sCrowdingPairs = 0;
    }
    __syncthreads();
    atomicAdd(&sCrossings, crossings);

    float localCrowding = 0.0f;
    int localPairs = 0;
    int vertexPairs = n * (n - 1) / 2;
    for (int p = firstPair; p < vertexPairs; p += pairStride) {
        int i, j;
        ijFromPairIndex(p, n, i, j);
        if (adjRowBase[i * n + j]) continue;

        float px = pBase[i * 2], py = pBase[i * 2 + 1];
        float qx = pBase[j * 2], qy = pBase[j * 2 + 1];
        float ddx = px - qx, ddy = py - qy;
        float distSq = ddx * ddx + ddy * ddy;
        if (distSq < optimalSq) {
            float dist = sqrtf(distSq);
            float ratio = optimal / fmaxf(dist, 1e-9f);
            float term = (ratio - 1.0f) * (ratio - 1.0f);
            localCrowding += term;
            localPairs++;
        }
    }
    atomicAdd(&sCrowding, localCrowding);
    atomicAdd(&sCrowdingPairs, localPairs);
    __syncthreads();

    if (threadIdx.x == 0) {
        atomicAdd(crossingsOut + state, sCrossings);
        atomicAdd(crowdingOut + state, sCrowding);
        atomicAdd(crowdingPairsOut + state, sCrowdingPairs);
    }
}

__global__ void finalizeScoresKernel(
        const int *crossings, const float *crowding, const int *crowdingPairs,
        const int *edgeCounts, int numSeeds, int numStates,
        int checkpointIndex, int numCheckpoints, int rotationIndex, int numRotations,
        float *scoresOut, const int *active) {
    int state = blockIdx.x * blockDim.x + threadIdx.x;
    if (state >= numStates || !active[state]) return;
    int graph = state / numSeeds;
    int seed = state - graph * numSeeds;
    int edgeCount = edgeCounts[graph];
    float maxCrossings = (float)edgeCount * (edgeCount - 1) / 2.0f;
    float crossingScore = maxCrossings > 0.0f ? crossings[state] / maxCrossings : 0.0f;
    float crowdingScore = crowdingPairs[state] > 0
            ? crowding[state] / crowdingPairs[state] : 0.0f;
    int scoreIndex = ((graph * numCheckpoints + checkpointIndex) * numSeeds + seed)
            * numRotations + rotationIndex;
    scoresOut[scoreIndex] = (crossingScore + crowdingScore) * SCORE_SCALE;
}

void runSpringIterations(float *dPos, float *dDisp, const int *dEdgeI, const int *dEdgeJ,
                         const int *dEdgeOffsets, const int *dEdgeCounts, int numGraphs, int numSeeds,
                         int n, int numStates, int startIter, int endIter, int totalIterations,
                         int *dConverged, float *dTotalDisp, const int *dActive) {
    float k = 1.0f / sqrtf((float)n);
    float kSq = k * k;
    float invK = 1.0f / k;
    int posStride = n * 3;
    int dispStride = n * 3;
    const int threads = 256;
    int repulsionBlocksPerState = (n + threads - 1) / threads;

    for (int iter = startIter; iter < endIter; iter++) {
        float t = 0.1f - (0.1f / (totalIterations + 1)) * iter;
        cudaMemset(dTotalDisp, 0, (size_t)numStates * sizeof(float));
        zeroDispKernel<<<numStates, threads>>>(dDisp, dispStride, n, numStates);
        repulsionKernel<<<numStates * repulsionBlocksPerState, threads>>>(
                dPos, dDisp, posStride, dispStride, n, numStates,
                repulsionBlocksPerState, kSq, dActive);
        attractionKernel<<<numStates, threads>>>(dPos, dDisp, dEdgeI, dEdgeJ, dEdgeOffsets, dEdgeCounts,
                                                 numSeeds, posStride, dispStride, numStates, invK, dActive);
        updatePositionsKernel<<<numStates, threads>>>(dPos, dDisp, posStride, dispStride, n, numStates, t,
                                                    dTotalDisp, dConverged, dActive);
        markConvergedKernel<<<(numStates + 255) / 256, 256>>>(dTotalDisp, dConverged, n, numStates);
    }
}

struct DeviceBuffers {
    float *dPos = nullptr;
    float *dScorePos = nullptr;
    float *dProjected = nullptr;
    float *dDisp = nullptr;
    float *dScores = nullptr;
    float *dRot = nullptr;
    int *dEdgeI = nullptr;
    int *dEdgeJ = nullptr;
    int *dEdgeOffsets = nullptr;
    int *dEdgeCounts = nullptr;
    uint8_t *dAdj = nullptr;
    int *dConverged = nullptr;
    int *dActive = nullptr;
    float *dTotalDisp = nullptr;
    int *dCrossings = nullptr;
    float *dCrowding = nullptr;
    int *dCrowdingPairs = nullptr;

    int capN = 0;
    int capGraphs = 0;
    int capStates = 0;
    int capEdges = 0;
    int capScores = 0;
    int capRot = 0;
};

static DeviceBuffers gDev;
static bool gInit = false;
static char gDeviceName[256] = "unknown";

void freeBuffers() {
    cudaFree(gDev.dPos);
    cudaFree(gDev.dScorePos);
    cudaFree(gDev.dProjected);
    cudaFree(gDev.dDisp);
    cudaFree(gDev.dScores);
    cudaFree(gDev.dRot);
    cudaFree(gDev.dEdgeI);
    cudaFree(gDev.dEdgeJ);
    cudaFree(gDev.dEdgeOffsets);
    cudaFree(gDev.dEdgeCounts);
    cudaFree(gDev.dAdj);
    cudaFree(gDev.dConverged);
    cudaFree(gDev.dActive);
    cudaFree(gDev.dTotalDisp);
    cudaFree(gDev.dCrossings);
    cudaFree(gDev.dCrowding);
    cudaFree(gDev.dCrowdingPairs);
    gDev = DeviceBuffers{};
}

int ensureCapacity(int n, int numStates, int totalEdges, int numGraphs, int scoreCount, int numRotations) {
    if (n <= gDev.capN && numGraphs <= gDev.capGraphs
        && numStates <= gDev.capStates && totalEdges <= gDev.capEdges
        && scoreCount <= gDev.capScores && numRotations <= gDev.capRot) {
        return 0;
    }
    freeBuffers();
    gDev.capN = n;
    gDev.capGraphs = numGraphs;
    gDev.capStates = numStates;
    gDev.capEdges = totalEdges;
    gDev.capScores = scoreCount;
    gDev.capRot = numRotations;
    cudaMalloc(&gDev.dPos, (size_t)numStates * n * 3 * sizeof(float));
    cudaMalloc(&gDev.dScorePos, (size_t)numStates * n * 3 * sizeof(float));
    cudaMalloc(&gDev.dProjected, (size_t)numStates * n * 2 * sizeof(float));
    cudaMalloc(&gDev.dDisp, (size_t)numStates * n * 3 * sizeof(float));
    cudaMalloc(&gDev.dEdgeI, (size_t)totalEdges * sizeof(int));
    cudaMalloc(&gDev.dEdgeJ, (size_t)totalEdges * sizeof(int));
    cudaMalloc(&gDev.dEdgeOffsets, (size_t)(numGraphs + 1) * sizeof(int));
    cudaMalloc(&gDev.dEdgeCounts, (size_t)numGraphs * sizeof(int));
    cudaMalloc(&gDev.dAdj, (size_t)numGraphs * n * n * sizeof(uint8_t));
    cudaMalloc(&gDev.dConverged, (size_t)numStates * sizeof(int));
    cudaMalloc(&gDev.dActive, (size_t)numStates * sizeof(int));
    cudaMalloc(&gDev.dTotalDisp, (size_t)numStates * sizeof(float));
    cudaMalloc(&gDev.dCrossings, (size_t)numStates * sizeof(int));
    cudaMalloc(&gDev.dCrowding, (size_t)numStates * sizeof(float));
    cudaMalloc(&gDev.dCrowdingPairs, (size_t)numStates * sizeof(int));
    cudaMalloc(&gDev.dScores, (size_t)scoreCount * sizeof(float));
    cudaMalloc(&gDev.dRot, (size_t)numRotations * 9 * sizeof(float));
    return 0;
}

} // namespace

extern "C" {

int congestion_cuda_init() {
    if (gInit) return 0;
    cudaError_t err = cudaSetDevice(0);
    if (err != cudaSuccess) {
        fprintf(stderr, "CUDA init failed: %s\n", cudaGetErrorString(err));
        return -1;
    }
    cudaDeviceProp prop;
    cudaGetDeviceProperties(&prop, 0);
    strncpy(gDeviceName, prop.name, sizeof(gDeviceName) - 1);
    gInit = true;
    return 0;
}

void congestion_cuda_shutdown() {
    if (!gInit) return;
    freeBuffers();
    gInit = false;
}

const char *congestion_cuda_device_name() {
    return gDeviceName;
}

int congestion_cuda_evaluate_batch(
    int numGraphs,
    int numSeeds,
    int nVertices,
    const int *graphEdgeOffsets,
    const int *graphEdgeCounts,
    const int *edgeI,
    const int *edgeJ,
    const uint8_t *adjacency,
    const float *initialPos,
    const int *checkpoints,
    int numCheckpoints,
    const float *thresholds,
    const float *rotMatrices,
    int numRotations,
    int maxCheckpoint,
    float *scoresOut) {
    if (!gInit) return -1;

    int numStates = numGraphs * numSeeds;
    int totalEdges = graphEdgeOffsets[numGraphs];
    int scoreCount = numGraphs * numCheckpoints * numSeeds * numRotations;
    if (ensureCapacity(nVertices, numStates, totalEdges, numGraphs, scoreCount, numRotations) != 0) {
        return -2;
    }

    cudaMemcpy(gDev.dPos, initialPos, (size_t)numStates * nVertices * 3 * sizeof(float), cudaMemcpyHostToDevice);
    cudaMemcpy(gDev.dEdgeI, edgeI, (size_t)totalEdges * sizeof(int), cudaMemcpyHostToDevice);
    cudaMemcpy(gDev.dEdgeJ, edgeJ, (size_t)totalEdges * sizeof(int), cudaMemcpyHostToDevice);
    cudaMemcpy(gDev.dEdgeOffsets, graphEdgeOffsets, (size_t)(numGraphs + 1) * sizeof(int), cudaMemcpyHostToDevice);
    cudaMemcpy(gDev.dEdgeCounts, graphEdgeCounts, (size_t)numGraphs * sizeof(int), cudaMemcpyHostToDevice);
    cudaMemcpy(gDev.dAdj, adjacency, (size_t)numGraphs * nVertices * nVertices * sizeof(uint8_t), cudaMemcpyHostToDevice);
    cudaMemcpy(gDev.dRot, rotMatrices, (size_t)numRotations * 9 * sizeof(float), cudaMemcpyHostToDevice);
    cudaMemset(gDev.dConverged, 0, (size_t)numStates * sizeof(int));
    std::vector<int> hActive(numStates, 1);
    cudaMemcpy(gDev.dActive, hActive.data(), (size_t)numStates * sizeof(int), cudaMemcpyHostToDevice);
    cudaMemset(gDev.dScores, 0, (size_t)scoreCount * sizeof(float));

    bool timing = std::getenv("CONGESTION_CUDA_TIMING") != nullptr;
    cudaEvent_t stageStart = nullptr, stageEnd = nullptr;
    float springMs = 0.0f, scoreMs = 0.0f;
    if (timing) {
        cudaEventCreate(&stageStart);
        cudaEventCreate(&stageEnd);
    }

    int prevCheckpoint = 0;
    for (int c = 0; c < numCheckpoints; c++) {
        int target = checkpoints[c];
        if (timing) cudaEventRecord(stageStart);
        if (target > prevCheckpoint) {
            runSpringIterations(gDev.dPos, gDev.dDisp, gDev.dEdgeI, gDev.dEdgeJ, gDev.dEdgeOffsets,
                                gDev.dEdgeCounts, numGraphs, numSeeds, nVertices, numStates,
                                prevCheckpoint, target, maxCheckpoint, gDev.dConverged,
                                gDev.dTotalDisp, gDev.dActive);
        }
        if (timing) {
            cudaEventRecord(stageEnd);
            cudaEventSynchronize(stageEnd);
            float elapsed = 0.0f;
            cudaEventElapsedTime(&elapsed, stageStart, stageEnd);
            springMs += elapsed;
            cudaEventRecord(stageStart);
        }
        prevCheckpoint = target;

        // Java normalizes a copy for scoring; its spring state remains in the
        // original coordinate system for subsequent checkpoint iterations.
        cudaMemcpy(gDev.dScorePos, gDev.dPos,
                   (size_t)numStates * nVertices * 3 * sizeof(float),
                   cudaMemcpyDeviceToDevice);
        normPositionsKernel<<<numStates, 256>>>(
                gDev.dScorePos, nVertices * 3, nVertices, numStates);

        const int scoreBlocksPerState = 32;
        for (int r = 0; r < numRotations; r++) {
            projectPositionsKernel<<<numStates, 256>>>(
                    gDev.dScorePos, gDev.dProjected, gDev.dRot, r,
                    nVertices, nVertices * 3, nVertices * 2,
                    numStates, gDev.dActive);
            cudaMemset(gDev.dCrossings, 0, (size_t)numStates * sizeof(int));
            cudaMemset(gDev.dCrowding, 0, (size_t)numStates * sizeof(float));
            cudaMemset(gDev.dCrowdingPairs, 0, (size_t)numStates * sizeof(int));
            congestionScoreKernel<<<numStates * scoreBlocksPerState, 256>>>(
                    gDev.dProjected, gDev.dEdgeI, gDev.dEdgeJ, gDev.dAdj,
                    nVertices * nVertices, gDev.dEdgeOffsets, gDev.dEdgeCounts, numSeeds,
                    nVertices, nVertices * 2, numStates, scoreBlocksPerState,
                    gDev.dCrossings, gDev.dCrowding, gDev.dCrowdingPairs, gDev.dActive);
            finalizeScoresKernel<<<(numStates + 255) / 256, 256>>>(
                    gDev.dCrossings, gDev.dCrowding, gDev.dCrowdingPairs,
                    gDev.dEdgeCounts, numSeeds, numStates, c, numCheckpoints,
                    r, numRotations, gDev.dScores, gDev.dActive);
        }

        if (thresholds && !isnan(thresholds[c])) {
            cudaMemcpy(scoresOut, gDev.dScores, (size_t)scoreCount * sizeof(float),
                       cudaMemcpyDeviceToHost);
            bool changed = false;
            for (int g = 0; g < numGraphs; g++) {
                if (!hActive[g * numSeeds]) continue;
                int base = ((g * numCheckpoints + c) * numSeeds) * numRotations;
                float best = scoresOut[base];
                for (int i = 1; i < numSeeds * numRotations; i++) {
                    best = fminf(best, scoresOut[base + i]);
                }
                if (best >= thresholds[c]) {
                    for (int s = 0; s < numSeeds; s++) {
                        hActive[g * numSeeds + s] = 0;
                    }
                    changed = true;
                }
            }
            if (changed) {
                cudaMemcpy(gDev.dActive, hActive.data(), (size_t)numStates * sizeof(int),
                           cudaMemcpyHostToDevice);
            }
        }
        if (timing) {
            cudaEventRecord(stageEnd);
            cudaEventSynchronize(stageEnd);
            float elapsed = 0.0f;
            cudaEventElapsedTime(&elapsed, stageStart, stageEnd);
            scoreMs += elapsed;
        }
    }

    cudaMemcpy(scoresOut, gDev.dScores, (size_t)scoreCount * sizeof(float), cudaMemcpyDeviceToHost);
    cudaDeviceSynchronize();
    if (timing) {
        fprintf(stderr, "CUDA timing: spring=%.1f ms score=%.1f ms batch=%d\n",
                springMs, scoreMs, numGraphs);
        cudaEventDestroy(stageStart);
        cudaEventDestroy(stageEnd);
    }
    return 0;
}

} // extern "C"
