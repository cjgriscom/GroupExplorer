# libpbin_factorial

JNI accelerator for PBIN factorial-number-system decode (`PbinFile` / `PbinFactorialNative`).

Uses **GMP** (`mpz_tdiv_q_ui`) for the hot single-limb division loop, plus a Fenwick tree
for order-statistic unranking — the same algorithm as `pbin/pbin.cpp`, with a faster bigint.

## Build

```bash
./native/pbin_factorial/build.sh
```

Requires: `g++`, JDK (`jni.h`), `libgmp` (`-lgmp`).

Produces `native/pbin_factorial/libpbin_factorial.so` and copies it to `lib/`.

## Use

Loaded automatically by `PbinFactorialNative` when present. Select backend:

| `-Dpbin.factorial=` | Implementation |
|---------------------|----------------|
| `native` (default if `.so` loads) | GMP via JNI |
| `limb` | Pure-Java `long[]` limbs |
| `java` | `java.math.BigInteger` |

Benchmark:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=io.chandler.gap.PbinReader \
  -Dexec.args="--bench PlanarStudy/he2/d300-np-andquot2-2-cycles-2-cycles-2-cycles_R1-filtered.txt.pbin.survivors.txt.pbin"
```
