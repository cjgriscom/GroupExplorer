# libnauty_jni (TLS-safe bundled nauty)

JNI bridge that **downloads nauty**, builds it with `--enable-tls`, and
**statically links** `libnauty.a` into `libnauty_jni.so`. No dependency on the
system `libnauty` (which is usually non-TLS).

Produces the same dreadnaut `z` certificate words as the `dreadnaut` executable,
and also hosts {@code CongestionGraphPack} native pack helpers (CSR + adjacency).

## Build

```bash
./native/nauty_jni/build.sh
```

Requires: `curl`, `tar`, `gcc`/`g++`, `make`, JDK (`jni.h`).

| Env var | Default | Meaning |
|---------|---------|---------|
| `NAUTY_VERSION` | `2_9_3` | Tarball version suffix |
| `NAUTY_URL_PRIMARY` | ANU BDM mirror | Download URL |
| `JOBS` | `nproc` | Parallel make jobs |

Artifacts (gitignored):

- `vendor/nauty2_9_3.tar.gz` — cached source
- `vendor/nauty2_9_3/` — extracted + configured tree
- `vendor/prefix/` — `lib/libnauty.a` + headers (`HAVE_TLS=1`)
- `libnauty_jni.so` → also copied to `../../lib/`

## Use

```text
-Ddreadnaut.backend=native   # force
-Ddreadnaut.backend=process  # force external dreadnaut
```

Default: **native** when this TLS-built `.so` loads, else process.

## Benchmark

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=io.chandler.gap.graph.DreadnautBench \
  -Dexec.args="PlanarStudy/he2/d300-np-andquot2-2-cycles-2-cycles-2-cycles_R1-filtered.txt.pbin.survivors.txt.pbin"
```
