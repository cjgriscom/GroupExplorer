#!/usr/bin/env bash
# Fetch nauty, build a TLS-enabled static lib, link it into libnauty_jni.so.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/native/nauty_jni"
cd "$HERE"

NAUTY_VERSION="${NAUTY_VERSION:-2_9_3}"
NAUTY_TAR="nauty${NAUTY_VERSION}.tar.gz"
# Primary: BDM ANU mirror (valid TLS). Fallback: Roma (may need -k).
NAUTY_URL_PRIMARY="${NAUTY_URL_PRIMARY:-https://users.cecs.anu.edu.au/~bdm/nauty/${NAUTY_TAR}}"
NAUTY_URL_FALLBACK="${NAUTY_URL_FALLBACK:-https://pallini.di.uniroma1.it/${NAUTY_TAR}}"

VENDOR="$HERE/vendor"
SRC_DIR="$VENDOR/nauty${NAUTY_VERSION}"
PREFIX="$VENDOR/prefix"
JOBS="${JOBS:-$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)}"

JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"

download_nauty() {
    mkdir -p "$VENDOR"
    local tarpath="$VENDOR/$NAUTY_TAR"
    if [[ -f "$tarpath" ]]; then
        echo "Using cached $tarpath"
        return
    fi
    echo "Downloading $NAUTY_URL_PRIMARY ..."
    if ! curl -fsSL -o "$tarpath.partial" "$NAUTY_URL_PRIMARY"; then
        echo "Primary mirror failed; trying fallback (insecure SSL allowed)..."
        curl -kfsSL -o "$tarpath.partial" "$NAUTY_URL_FALLBACK"
    fi
    mv "$tarpath.partial" "$tarpath"
}

extract_nauty() {
    if [[ -f "$SRC_DIR/configure" ]]; then
        echo "Source already extracted: $SRC_DIR"
        return
    fi
    echo "Extracting $VENDOR/$NAUTY_TAR ..."
    rm -rf "$SRC_DIR"
    tar -xzf "$VENDOR/$NAUTY_TAR" -C "$VENDOR"
    # Tarball top-level is nauty2_9_3/
    if [[ ! -d "$SRC_DIR" ]]; then
        echo "error: expected $SRC_DIR after extract" >&2
        exit 1
    fi
}

build_nauty_tls() {
    mkdir -p "$PREFIX"
    # Rebuild if missing TLS marker or library.
    if [[ -f "$PREFIX/lib/libnauty.a" && -f "$PREFIX/include/nauty.h" ]]; then
        if grep -q '#define HAVE_TLS 1' "$PREFIX/include/nauty.h"; then
            echo "TLS libnauty already built at $PREFIX"
            return
        fi
        echo "Existing prefix lacks TLS; rebuilding..."
        rm -rf "$PREFIX"
        mkdir -p "$PREFIX"
    fi

    echo "Configuring nauty with --enable-tls (static, PIC)..."
    (
        cd "$SRC_DIR"
        # Out-of-tree would be nicer, but nauty'configure is happiest in-tree.
        if [[ -f makefile || -f Makefile ]]; then
            make distclean >/dev/null 2>&1 || true
        fi
        ./configure \
            --prefix="$PREFIX" \
            --includedir="$PREFIX/include" \
            --enable-tls \
            --enable-static \
            --disable-shared \
            CFLAGS="-O3 -fPIC -march=native"
        echo "Building libnauty.la (-j$JOBS)..."
        make -j"$JOBS" libnauty.la
        echo "Installing headers + static library into $PREFIX ..."
        # Full `make install` builds every tool; install just what we need.
        mkdir -p "$PREFIX/lib" "$PREFIX/include"
        cp -f .libs/libnauty.a "$PREFIX/lib/libnauty.a"
        # Public headers used by JNI (and their includes).
        cp -f nauty.h nausparse.h traces.h gtools.h schreier.h \
              naututil.h naurng.h naugroup.h nausha.h \
              nautinv.h nautycliquer.h nauchromatic.h nauconnect.h \
              "$PREFIX/include/" 2>/dev/null || true
        # Some versions ship extra headers referenced by the above.
        for h in gutils.h namedgraphs.h naugstrings.h nautaux.h planarity.h; do
            [[ -f "$h" ]] && cp -f "$h" "$PREFIX/include/"
        done
    )

    if ! grep -q '#define HAVE_TLS 1' "$PREFIX/include/nauty.h"; then
        echo "error: built nauty.h does not define HAVE_TLS 1" >&2
        exit 1
    fi
    echo "TLS nauty OK: $(grep 'NAUTYVERSION' "$PREFIX/include/nauty.h" | head -1)"
}

build_jni() {
    echo "Building libnauty_jni.so (statically linked TLS libnauty)..."
    make -C "$HERE" clean
    make -C "$HERE" \
        JAVA_HOME="$JAVA_HOME" \
        NAUTY_PREFIX="$PREFIX"
    mkdir -p "$ROOT/lib"
    cp -f "$HERE/libnauty_jni.so" "$ROOT/lib/libnauty_jni.so"
    echo "Built $ROOT/lib/libnauty_jni.so"
    # Quick symbol / TLS sanity
    if command -v nm >/dev/null; then
        if nm -D "$ROOT/lib/libnauty_jni.so" | grep -q ' T Traces$'; then
            echo "  Traces symbol: present (bundled)"
        fi
    fi
    if ldd "$ROOT/lib/libnauty_jni.so" 2>/dev/null | grep -q libnauty; then
        echo "warning: still dynamically linked to system libnauty" >&2
    else
        echo "  no system libnauty dependency (static bundle)"
    fi
}

download_nauty
extract_nauty
build_nauty_tls
build_jni

echo
echo "Done. Use: -Ddreadnaut.backend=native  (default when TLS lib is loaded)"
echo "Bench: mvn -q exec:java -Dexec.mainClass=io.chandler.gap.graph.DreadnautBench \\"
echo "         -Dexec.args=\"PlanarStudy/he2/...survivors.txt.pbin\""
