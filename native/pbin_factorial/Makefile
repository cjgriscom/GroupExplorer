JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(command -v javac))))

TARGET   := libpbin_factorial.so
SRC      := pbin_factorial_jni.cpp
CXX      ?= g++
CXXFLAGS := -O3 -std=c++17 -fPIC -march=native -DNDEBUG
INCLUDES := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
LIBS     := -lgmp

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(SRC)
	$(CXX) $(CXXFLAGS) $(INCLUDES) -shared -o $@ $(SRC) $(LIBS)

clean:
	rm -f $(TARGET)
