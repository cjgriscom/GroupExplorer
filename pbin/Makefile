CXX      ?= g++
CXXFLAGS ?= -std=c++17 -O2 -Wall -Wextra -Wpedantic
LDLIBS    = -lz

PREFIX   ?= /usr/local

pbin: pbin.cpp
	$(CXX) $(CXXFLAGS) -o $@ $< $(LDLIBS)

clean:
	rm -f pbin

install: pbin
	install -d $(DESTDIR)$(PREFIX)/bin
	install -m 755 pbin $(DESTDIR)$(PREFIX)/bin/

.PHONY: clean install
