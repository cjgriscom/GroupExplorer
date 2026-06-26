# PBIN — Packed Binary Generator Format

PBIN is a compact binary format for storing permutation group generators expressed
in cycle notation.  It uses **factorial number system packing** so that cycle sets
containing *k* unique points drawn from the universe 1…N are stored in exactly
⌈log₂(N!/(N−k)!)⌉ bits, plus a small amount of structural metadata.  Generators
are grouped into **blocks** and optionally compressed together for better ratios.

## Text input formats

Two line-oriented text formats are accepted:

| Style | Example | Meaning |
|-------|---------|---------|
| **Bare** (single cycle set) | `(1,6,13)(2,7,15)(3,9,17)` | One cycle set per line |
| **Bracketed** (multi–cycle set) | `[(1,2,3)(4,5,6),(6,2,3),(1,4)(2,3,5,6)]` | Multiple cycle sets separated by `,` between `)` and `(` |

A file may use either style; the encoder auto-detects and records which style to
reproduce on decode.

---

## Binary layout

All multi-byte integers are **little-endian** unless noted.  Variable-length
integers ("varint") use protobuf-style encoding: each byte contributes 7 payload
bits; the high bit is 1 when more bytes follow, 0 on the last byte.  Least
significant group first.

```
┌─────────────────── HEADER ───────────────────┐
│ bytes[4]  magic         "PBIN"               │
│ uint8     version       0x01                 │
│ uint8     flags         bit 0: bare format   │
│ uint8     compression   0=none, 1=zlib       │
│ varint    blockSize     generators per block  │
│ varint    N             max 1-indexed point   │
│ varint    M             total generators      │
├─────────────────── DIRECTORY ────────────────┤
│ uint32[B] offsets       B = ⌈M / blockSize⌉  │
│                         byte offset from file │
│                         start to each block   │
├─────────────────── BLOCKS ───────────────────┤
│ block[0]                                      │
│ block[1]                                      │
│ …                                            │
│ block[B−1]                                    │
└──────────────────────────────────────────────┘
```

### Flags byte

| Bit | Meaning |
|-----|---------|
| 0   | **1** = input used bare format (single cycle set per line, no brackets). **0** = bracketed format. |
| 1–7 | Reserved (0) |

### Compression modes

| Value | Mode |
|-------|------|
| 0     | None — blocks are stored uncompressed |
| 1     | zlib (RFC 1950, `compress2` / `Inflater`) |

---

## Block format

Each entry in the block section is optionally compressed (according to the
compression mode).  The decompressed payload contains one or more generators:

```
varint          count               generators in this block (≤ blockSize)
varint[count]   genSize[0..count−1] byte length of each encoded generator
bytes           gen[0] data         (genSize[0] bytes)
bytes           gen[1] data         (genSize[1] bytes)
…
bytes           gen[count−1] data   (genSize[count−1] bytes)
```

The last block may contain fewer than `blockSize` generators.

---

## Encoded generator

Each generator's encoded data (within a block) has this structure:

```
varint  nCycleSets

For each cycle set:
    varint          nCycles
    varint[nCycles] cycleSize[0..nCycles−1]
    varint          bigIntByteLen
    bytes[bigIntByteLen]  factorial-packed point sequence (big-endian unsigned)
```

### Factorial packing algorithm

Given a cycle set whose cycles, read left-to-right, yield the ordered point
sequence **p₀, p₁, …, p_{k−1}** (each point in 1…N, all distinct within
the cycle set):

**Encode:**

```
available ← {1, 2, …, N}          (sorted set)
acc ← 0                            (BigInt)
for i = 0 to k−1:
    idx  ← 0-based rank of pᵢ among available
    acc  ← acc × (N − i) + idx
    remove pᵢ from available
store acc as big-endian byte array
```

**Decode:**

```
Extract index digits from acc (right-to-left):
    for i = k−1 down to 0:
        indices[i] ← acc mod (N − i)
        acc        ← acc div (N − i)

Reconstruct points:
    available ← {1, 2, …, N}
    for i = 0 to k−1:
        pᵢ ← the indices[i]-th element of available
        remove pᵢ from available
```

The packed value ranges from 0 to N!/(N−k)! − 1, requiring
⌈log₂(N!/(N−k)!)⌉ bits.  This is stored as
`bigIntByteLen` = ⌈bits/8⌉ bytes in big-endian order (the value 0 is stored
as a single `0x00` byte with `bigIntByteLen` = 1; empty cycle sets use
`bigIntByteLen` = 0).

---

## Command-line tool

```
pbin -e  [options] <file.txt>   Encode → file.txt.pbin (delete original)
pbin -d  <file.pbin>            Decode → file.txt      (delete original)
pbin -ek [options] <file.txt>   Encode, keep original
pbin -dk <file.pbin>            Decode, keep original

Encode options:
  -c MODE       Compression: 0=none, 1=zlib (default: 1)
  -b BLOCKSIZE  Generators per block (default: 16)
```

Both the encoder and decoder are streaming: only one block of generators is
held in memory at a time. The encoder makes two passes over the text input
(count/scan, then encode). The decoder reads the header and directory, then
seeks to each block in turn.

In Java, use `PbinFile.open(path)` for random access without loading the
entire file. `PbinFile.get(index)` decodes one generator on demand, caching
the current block only.
