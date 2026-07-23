# CUDA congestion backend

This optional JNI backend accelerates the 3D spring layout and rotated
congestion scoring used by `CongestionBatch`. It targets NVIDIA compute
capability 7.5 (RTX 20-series) and uses FP32 kernels with CPU rechecks near
pruning thresholds.

## Build

Requirements:

- CUDA toolkit (`nvcc`; defaults to `/opt/cuda`)
- JDK with JNI headers
- NVIDIA driver compatible with the toolkit

```bash
native/congestion_cuda/build.sh
```

The batch runner can load the library directly from
`native/congestion_cuda/libcongestion_cuda.so`; the build script also copies it
to `lib/`.

## Run

```bash
mvn -q exec:java \
  -Dexec.mainClass=io.chandler.gap.graph.CongestionBatch \
  -Dexec.args="--gpu --batch-size 256 --threads 16 \
    --seeds 41,129 --checkpoints 100,200,500,1000 \
    --thresholds 7.5,4.9,4.5,4.25 --n-rotations 6 \
    --guard-band 0.1 --randomize --shuffle-seed 1234 \
    --output-file OUTPUT INPUT.pbin"
```

`--guard-band 0.1` is the conservative default: scores within 0.1 of a
threshold are recomputed on the CPU in parallel. Validation on 128 production
generators found no survivor mismatches and a maximum checkpoint-minimum FP32
drift of 0.057337.

For exploratory runs, `--guard-band 0` disables CPU rechecks. On the RTX 2080
Ti this measured about 56 generators/second at batch size 256, but should only
be used when the validated FP32 risk is acceptable.

## Validate and benchmark

```bash
mvn -q exec:java \
  -Dexec.mainClass=io.chandler.gap.graph.CongestionCudaValidate \
  -Dexec.args="INPUT.pbin 128 0"

mvn -q exec:java \
  -Dexec.mainClass=io.chandler.gap.graph.CongestionCudaBenchmark \
  -Dexec.args="INPUT.pbin 256 256 2189370 true 0.1"
```

Set `CONGESTION_CUDA_TIMING=1` to print native spring and scoring kernel timing.
