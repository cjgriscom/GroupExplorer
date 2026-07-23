CXX      ?= g++
CXXFLAGS ?= -std=c++17 -O2 -Wall -Wextra -Wpedantic
LDLIBS    = -lz -pthread

PREFIX   ?= /usr/local

pbin: pbin.cpp
	$(CXX) $(CXXFLAGS) -o $@ $< $(LDLIBS)

clean:
	rm -f pbin

install: pbin
	install -d $(DESTDIR)$(PREFIX)/bin
	install -m 755 pbin $(DESTDIR)$(PREFIX)/bin/

test: pbin
	./roundtrip_test.sh

.PHONY: clean install test
