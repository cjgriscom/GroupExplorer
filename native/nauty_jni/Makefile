# Builds libnauty_jni.so against a TLS-enabled static libnauty from ./vendor/prefix
# (populated by ./build.sh). Do not link the system -lnauty.

JAVA_HOME    ?= $(shell dirname $$(dirname $$(readlink -f $$(command -v javac))))
NAUTY_PREFIX ?= $(CURDIR)/vendor/prefix

TARGET   := libnauty_jni.so
SRC      := nauty_jni.cpp
CXX      ?= g++
CXXFLAGS := -O3 -std=c++17 -fPIC -march=native -DNDEBUG
INCLUDES := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux -I$(NAUTY_PREFIX)/include
# Whole-archive not required: we only need referenced nauty objects, but
# Traces/sparsenauty pull most of the archive anyway. -lm for nauty.
LIBS     := $(NAUTY_PREFIX)/lib/libnauty.a -lm

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRC) $(NAUTY_PREFIX)/lib/libnauty.a $(NAUTY_PREFIX)/include/nauty.h
	@grep -q '#define HAVE_TLS 1' $(NAUTY_PREFIX)/include/nauty.h \
		|| (echo "error: $(NAUTY_PREFIX)/include/nauty.h is not TLS-enabled; run ./build.sh" >&2; exit 1)
	$(CXX) $(CXXFLAGS) $(INCLUDES) -shared -o $@ $(SRC) $(LIBS)

clean:
	rm -f $(TARGET)
