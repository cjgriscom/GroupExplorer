CUDA      ?= /opt/cuda
NVCC      := $(CUDA)/bin/nvcc
JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(command -v javac))))

TARGET    := libcongestion_cuda.so
SRC       := congestion_kernels.cu congestion_jni.cpp
CXXFLAGS  := -O3 -std=c++17 -fPIC
NVCCFLAGS := -O3 -std=c++17 --shared -Xcompiler -fPIC -gencode arch=compute_75,code=sm_75

INCLUDES  := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRC)
	$(NVCC) $(NVCCFLAGS) $(INCLUDES) -o $@ $(SRC)

clean:
	rm -f $(TARGET)
