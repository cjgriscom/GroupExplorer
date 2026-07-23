// pbin — Packed Binary Generator format encoder / decoder
// See PBIN.md for format specification.

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>
#include <zlib.h>

// ================================================================
// BigInt — minimal arbitrary-precision unsigned integer
// ================================================================

class BigInt {
    std::vector<uint32_t> w; // least-significant word first
public:
    BigInt() = default;
    explicit BigInt(uint32_t v) { if (v) w.push_back(v); }

    bool is_zero() const { return w.empty(); }

    // this = this * mul + add   (mul, add fit in uint32)
    void mul_add(uint32_t mul, uint32_t add) {
        uint64_t carry = add;
        for (auto &d : w) {
            carry += static_cast<uint64_t>(d) * mul;
            d = static_cast<uint32_t>(carry);
            carry >>= 32;
        }
        if (carry) w.push_back(static_cast<uint32_t>(carry));
    }

    // this = this / divisor;  returns remainder
    uint32_t divmod(uint32_t divisor) {
        uint64_t rem = 0;
        for (int i = static_cast<int>(w.size()) - 1; i >= 0; --i) {
            rem = (rem << 32) | w[i];
            w[i] = static_cast<uint32_t>(rem / divisor);
            rem %= divisor;
        }
        while (!w.empty() && w.back() == 0) w.pop_back();
        return static_cast<uint32_t>(rem);
    }

    // Serialize as big-endian byte array (minimum 1 byte for zero)
    std::vector<uint8_t> to_bytes() const {
        if (w.empty()) return {0};
        std::vector<uint8_t> out;
        out.reserve(w.size() * 4);
        for (int i = static_cast<int>(w.size()) - 1; i >= 0; --i) {
            out.push_back((w[i] >> 24) & 0xFF);
            out.push_back((w[i] >> 16) & 0xFF);
            out.push_back((w[i] >>  8) & 0xFF);
            out.push_back( w[i]        & 0xFF);
        }
        size_t start = 0;
        while (start + 1 < out.size() && out[start] == 0) ++start;
        return {out.begin() + start, out.end()};
    }

    static BigInt from_bytes(const uint8_t *data, size_t len) {
        BigInt r;
        if (len == 0 || data == nullptr) return r;
        size_t pad = ((len + 3) / 4) * 4;
        size_t leading = pad - len;
        std::vector<uint8_t> buf(pad, 0);
        std::copy(data, data + len, buf.begin() + static_cast<ptrdiff_t>(leading));
        r.w.resize(pad / 4);
        for (size_t i = 0; i < pad / 4; ++i) {
            size_t bi = (pad / 4 - 1 - i) * 4;
            r.w[i] = (static_cast<uint32_t>(buf[bi])     << 24) |
                     (static_cast<uint32_t>(buf[bi + 1]) << 16) |
                     (static_cast<uint32_t>(buf[bi + 2]) <<  8) |
                      static_cast<uint32_t>(buf[bi + 3]);
        }
        while (!r.w.empty() && r.w.back() == 0) r.w.pop_back();
        return r;
    }
};

// ================================================================
// Fenwick tree — order-statistic operations on the set {1..N}
// ================================================================

class FenwickTree {
    std::vector<int> t;
    int n = 0, logn = 0;
public:
    void init(int n_) {
        n = n_;
        t.assign(n + 1, 0);
        for (int i = 1; i <= n; ++i) {
            t[i] += 1;
            int j = i + (i & -i);
            if (j <= n) t[j] += t[i];
        }
        logn = 0;
        while ((1 << (logn + 1)) <= n) ++logn;
    }

    int rank(int x) const {
        int s = 0;
        for (int i = x; i > 0; i -= i & -i) s += t[i];
        return s - 1;
    }

    int kth(int k) const {
        int pos = 0, rem = k + 1;
        for (int i = logn; i >= 0; --i) {
            int nxt = pos + (1 << i);
            if (nxt <= n && t[nxt] < rem) {
                rem -= t[nxt];
                pos = nxt;
            }
        }
        return pos + 1;
    }

    void remove(int x) {
        for (int i = x; i <= n; i += i & -i) t[i] -= 1;
    }
};

// ================================================================
// Varint encoding (protobuf-style, unsigned, LE 7-bit groups)
// ================================================================

static void write_varint(std::vector<uint8_t> &buf, uint32_t val) {
    do {
        uint8_t b = val & 0x7F;
        val >>= 7;
        if (val) b |= 0x80;
        buf.push_back(b);
    } while (val);
}

static uint32_t read_varint(const uint8_t *&p, const uint8_t *end) {
    uint32_t val = 0;
    unsigned shift = 0;
    for (;;) {
        if (p >= end) throw std::runtime_error("truncated varint");
        uint32_t b = *p++;
        val |= (b & 0x7F) << shift;
        if (!(b & 0x80)) break;
        shift += 7;
        if (shift >= 35) throw std::runtime_error("varint overflow");
    }
    return val;
}

// ================================================================
// Data model
// ================================================================

struct CycleSet {
    std::vector<std::vector<int>> cycles;

    std::vector<int> all_points() const {
        std::vector<int> pts;
        for (auto &c : cycles)
            for (int p : c) pts.push_back(p);
        return pts;
    }
};

struct Generator {
    std::vector<CycleSet> cycle_sets;
};

// ================================================================
// Text ↔ data model
// ================================================================

static const char *skip_ws(const char *p, const char *end) {
    while (p < end && std::isspace(static_cast<unsigned char>(*p))) ++p;
    return p;
}

static int parse_int(const char *&p, const char *end) {
    p = skip_ws(p, end);
    if (p >= end || !std::isdigit(static_cast<unsigned char>(*p)))
        throw std::runtime_error("expected integer in cycle notation");
    int val = 0;
    while (p < end && std::isdigit(static_cast<unsigned char>(*p))) {
        val = val * 10 + (*p - '0');
        ++p;
    }
    return val;
}

static void parse_cycle(const char *&p, const char *end, std::vector<int> &cyc) {
    p = skip_ws(p, end);
    if (p >= end || *p != '(')
        throw std::runtime_error("expected '(' starting cycle");
    ++p;
    cyc.clear();
    for (;;) {
        p = skip_ws(p, end);
        if (p >= end)
            throw std::runtime_error("unclosed cycle");
        if (*p == ')') {
            ++p;
            return;
        }
        cyc.push_back(parse_int(p, end));
        p = skip_ws(p, end);
        if (p < end && *p == ',') {
            ++p;
            continue;
        }
        if (p < end && *p == ')') {
            ++p;
            return;
        }
        throw std::runtime_error("malformed cycle");
    }
}

static CycleSet parse_cycle_set(const char *&p, const char *end) {
    CycleSet cs;
    for (;;) {
        p = skip_ws(p, end);
        if (p >= end || *p != '(') break;
        std::vector<int> cyc;
        parse_cycle(p, end, cyc);
        if (!cyc.empty()) cs.cycles.push_back(std::move(cyc));
        p = skip_ws(p, end);
        if (p < end && *p == '(') continue;
        break;
    }
    return cs;
}

static Generator parse_generator(const std::string &line) {
    Generator gen;
    const char *p = line.data();
    const char *end = p + line.size();
    p = skip_ws(p, end);
    if (p >= end) return gen;

    const bool bracketed = (*p == '[');
    if (bracketed) ++p;

    if (bracketed) {
        for (;;) {
            p = skip_ws(p, end);
            if (p >= end) break;
            if (*p == ']') {
                ++p;
                break;
            }
            auto cs = parse_cycle_set(p, end);
            if (!cs.cycles.empty()) gen.cycle_sets.push_back(std::move(cs));
            p = skip_ws(p, end);
            if (p < end && *p == ',') {
                ++p;
                p = skip_ws(p, end);
                if (p < end && *p == '(') continue;
            }
            if (p < end && *p == ']') {
                ++p;
                break;
            }
            if (p >= end) break;
            throw std::runtime_error("malformed bracketed generator");
        }
    } else {
        auto cs = parse_cycle_set(p, end);
        if (!cs.cycles.empty()) gen.cycle_sets.push_back(std::move(cs));
        p = skip_ws(p, end);
        if (p < end)
            throw std::runtime_error("trailing garbage in generator line");
    }
    return gen;
}

static std::string generator_to_string(const Generator &gen, bool bare) {
    std::string r;
    if (!bare) r += '[';
    bool first_cs = true;
    for (auto &cs : gen.cycle_sets) {
        if (!first_cs) r += ',';
        first_cs = false;
        for (auto &cyc : cs.cycles) {
            r += '(';
            for (size_t i = 0; i < cyc.size(); ++i) {
                if (i) r += ',';
                r += std::to_string(cyc[i]);
            }
            r += ')';
        }
    }
    if (!bare) r += ']';
    return r;
}

// ================================================================
// Factorial packing
// ================================================================

static BigInt factorial_encode(const std::vector<int> &points, int N,
                               FenwickTree &ft) {
    ft.init(N);
    BigInt acc;
    for (size_t i = 0; i < points.size(); ++i) {
        uint32_t remaining = static_cast<uint32_t>(N - static_cast<int>(i));
        uint32_t idx = static_cast<uint32_t>(ft.rank(points[i]));
        acc.mul_add(remaining, idx);
        ft.remove(points[i]);
    }
    return acc;
}

static std::vector<int> factorial_decode(BigInt acc, int N, int k,
                                          FenwickTree &ft) {
    std::vector<uint32_t> indices(k);
    for (int i = k - 1; i >= 0; --i) {
        uint32_t remaining = static_cast<uint32_t>(N - i);
        indices[i] = acc.divmod(remaining);
    }
    ft.init(N);
    std::vector<int> points(k);
    for (int i = 0; i < k; ++i) {
        points[i] = ft.kth(static_cast<int>(indices[i]));
        ft.remove(points[i]);
    }
    return points;
}

// ================================================================
// Binary generator encoding / decoding (uncompressed payload)
// ================================================================

static std::vector<uint8_t> encode_generator(const Generator &gen, int N) {
    std::vector<uint8_t> buf;
    write_varint(buf, static_cast<uint32_t>(gen.cycle_sets.size()));
    FenwickTree ft;
    for (auto &cs : gen.cycle_sets) {
        write_varint(buf, static_cast<uint32_t>(cs.cycles.size()));
        std::vector<int> points;
        for (auto &cyc : cs.cycles) {
            write_varint(buf, static_cast<uint32_t>(cyc.size()));
            for (int p : cyc) points.push_back(p);
        }
        if (points.empty()) {
            write_varint(buf, 0);
        } else {
            BigInt packed = factorial_encode(points, N, ft);
            auto bytes = packed.to_bytes();
            write_varint(buf, static_cast<uint32_t>(bytes.size()));
            buf.insert(buf.end(), bytes.begin(), bytes.end());
        }
    }
    return buf;
}

static Generator decode_generator(const uint8_t *data, size_t len, int N) {
    Generator gen;
    const uint8_t *p = data;
    const uint8_t *end = data + len;
    uint32_t ncs = read_varint(p, end);
    FenwickTree ft;
    for (uint32_t s = 0; s < ncs; ++s) {
        CycleSet cs;
        uint32_t ncyc = read_varint(p, end);
        std::vector<uint32_t> sizes(ncyc);
        uint32_t total = 0;
        for (uint32_t j = 0; j < ncyc; ++j) {
            sizes[j] = read_varint(p, end);
            total += sizes[j];
        }
        uint32_t bi_len = read_varint(p, end);
        std::vector<int> points;
        if (total > 0 && bi_len > 0) {
            if (p + bi_len > end) throw std::runtime_error("truncated bigint");
            BigInt packed = BigInt::from_bytes(p, bi_len);
            p += bi_len;
            points = factorial_decode(packed, N, static_cast<int>(total), ft);
        } else {
            p += bi_len;
        }
        size_t off = 0;
        for (uint32_t j = 0; j < ncyc; ++j) {
            cs.cycles.emplace_back(points.begin() + off,
                                   points.begin() + off + sizes[j]);
            off += sizes[j];
        }
        gen.cycle_sets.push_back(std::move(cs));
    }
    return gen;
}

// ================================================================
// zlib helpers
// ================================================================

static std::vector<uint8_t> zlib_compress(const std::vector<uint8_t> &data) {
    uLongf bound = compressBound(static_cast<uLong>(data.size()));
    std::vector<uint8_t> out(bound);
    int ret = compress2(out.data(), &bound,
                        data.data(), static_cast<uLong>(data.size()),
                        Z_BEST_COMPRESSION);
    if (ret != Z_OK) throw std::runtime_error("zlib compress failed");
    out.resize(bound);
    return out;
}

static std::vector<uint8_t> zlib_decompress(const uint8_t *data, size_t len) {
    z_stream strm{};
    if (inflateInit(&strm) != Z_OK)
        throw std::runtime_error("inflateInit failed");
    strm.next_in  = const_cast<Bytef *>(data);
    strm.avail_in = static_cast<uInt>(len);
    std::vector<uint8_t> out;
    uint8_t buf[8192];
    int ret;
    do {
        strm.next_out  = buf;
        strm.avail_out = sizeof(buf);
        ret = inflate(&strm, Z_NO_FLUSH);
        if (ret != Z_OK && ret != Z_STREAM_END) {
            inflateEnd(&strm);
            throw std::runtime_error("zlib inflate failed (code " +
                                     std::to_string(ret) + ")");
        }
        out.insert(out.end(), buf, buf + sizeof(buf) - strm.avail_out);
    } while (ret != Z_STREAM_END);
    inflateEnd(&strm);
    return out;
}

// ================================================================
// PBIN file I/O
// ================================================================

static constexpr char     PBIN_MAGIC[4] = {'P','B','I','N'};
// v1: uint32 directory offsets (files must be < 4 GiB)
// v2: uint64 directory offsets (supports files >= 4 GiB)
static constexpr uint8_t  PBIN_VERSION  = 0x02;
static constexpr uint8_t  PBIN_VERSION_V1 = 0x01;
static constexpr size_t   IO_BUF_SIZE   = 1 << 20; // 1 MiB

static uint32_t read_u32_le(const uint8_t *p) {
    return static_cast<uint32_t>(p[0])       |
          (static_cast<uint32_t>(p[1]) << 8) |
          (static_cast<uint32_t>(p[2]) << 16)|
          (static_cast<uint32_t>(p[3]) << 24);
}

static uint64_t read_u64_le(const uint8_t *p) {
    return static_cast<uint64_t>(read_u32_le(p)) |
          (static_cast<uint64_t>(read_u32_le(p + 4)) << 32);
}

static void write_u32_le(std::ostream &os, uint32_t v) {
    char b[4] = {
        static_cast<char>( v        & 0xFF),
        static_cast<char>((v >>  8) & 0xFF),
        static_cast<char>((v >> 16) & 0xFF),
        static_cast<char>((v >> 24) & 0xFF),
    };
    os.write(b, 4);
}

static void write_u64_le(std::ostream &os, uint64_t v) {
    write_u32_le(os, static_cast<uint32_t>(v));
    write_u32_le(os, static_cast<uint32_t>(v >> 32));
}

static void write_varint(std::ostream &os, uint32_t val) {
    do {
        uint8_t b = val & 0x7F;
        val >>= 7;
        if (val) b |= 0x80;
        os.put(static_cast<char>(b));
    } while (val);
}

static void trim_line(std::string &line) {
    while (!line.empty() &&
           std::isspace(static_cast<unsigned char>(line.back())))
        line.pop_back();
}

// Strip trailing comments: first '#' with index > 0 (GraphVisualizer rule).
static void strip_comment(std::string &line) {
    for (size_t i = 0; i < line.size(); ++i) {
        if (line[i] == '#' && i > 0) {
            size_t end = i;
            while (end > 0 &&
                   std::isspace(static_cast<unsigned char>(line[end - 1])))
                --end;
            line.resize(end);
            return;
        }
    }
}

static void light_scan_line(const char *p, const char *end,
                            int &max_point, bool &all_bare) {
    p = skip_ws(p, end);
    if (p >= end) return;
    if (*p == '[') all_bare = false;
    while (p < end) {
        if (std::isdigit(static_cast<unsigned char>(*p))) {
            int val = 0;
            while (p < end && std::isdigit(static_cast<unsigned char>(*p))) {
                val = val * 10 + (*p - '0');
                ++p;
            }
            if (val > max_point) max_point = val;
        } else {
            ++p;
        }
    }
}

static bool read_logical_line(std::ifstream &ifs, std::string &line);

static void light_pass1(const std::string &in_path,
                        uint32_t &M, int &max_point, bool &all_bare) {
    M = 0;
    max_point = 0;
    all_bare = true;
    std::ifstream ifs(in_path);
    if (!ifs) throw std::runtime_error("cannot open " + in_path);
    std::vector<char> in_buf(IO_BUF_SIZE);
    ifs.rdbuf()->pubsetbuf(in_buf.data(),
                           static_cast<std::streamsize>(in_buf.size()));
    std::string line;
    while (read_logical_line(ifs, line)) {
        strip_comment(line);
        trim_line(line);
        if (line.empty()) continue;
        ++M;
        light_scan_line(line.data(), line.data() + line.size(),
                        max_point, all_bare);
    }
}

// Read one logical input line, joining physical lines that end with '\'.
static bool read_logical_line(std::ifstream &ifs, std::string &line) {
    line.clear();
    for (;;) {
        std::string part;
        if (!std::getline(ifs, part)) {
            return !line.empty();
        }
        trim_line(part);
        if (part.empty() && line.empty()) {
            continue;
        }

        bool continues = false;
        if (!part.empty() && part.back() == '\\') {
            part.pop_back();
            trim_line(part);
            continues = true;
        }

        line += part;
        if (!continues) {
            return true;
        }
    }
}

static std::vector<uint8_t> build_block_payload(
        const std::vector<std::vector<uint8_t>> &block_raw) {
    std::vector<uint8_t> payload;
    write_varint(payload, static_cast<uint32_t>(block_raw.size()));
    for (const auto &raw : block_raw)
        write_varint(payload, static_cast<uint32_t>(raw.size()));
    for (const auto &raw : block_raw)
        payload.insert(payload.end(), raw.begin(), raw.end());
    return payload;
}

static unsigned default_thread_count() {
    const unsigned n = std::thread::hardware_concurrency();
    return n > 0 ? n : 4;
}

// Read varints from an ifstream at the current position.
static uint32_t read_varint_stream(std::ifstream &ifs) {
    uint32_t val = 0;
    unsigned shift = 0;
    for (;;) {
        char c;
        if (!ifs.get(c)) throw std::runtime_error("truncated varint");
        uint32_t b = static_cast<uint8_t>(c);
        val |= (b & 0x7F) << shift;
        if (!(b & 0x80)) break;
        shift += 7;
        if (shift >= 35) throw std::runtime_error("varint overflow");
    }
    return val;
}

static uint32_t read_u32_le_stream(std::ifstream &ifs) {
    char b[4];
    ifs.read(b, 4);
    if (ifs.gcount() != 4) throw std::runtime_error("truncated u32");
    return read_u32_le(reinterpret_cast<uint8_t *>(b));
}

static uint64_t read_u64_le_stream(std::ifstream &ifs) {
    char b[8];
    ifs.read(b, 8);
    if (ifs.gcount() != 8) throw std::runtime_error("truncated u64");
    return read_u64_le(reinterpret_cast<uint8_t *>(b));
}

// Optionally compress a payload according to compression mode
static std::vector<uint8_t> maybe_compress(const std::vector<uint8_t> &data,
                                            uint8_t compression) {
    if (compression == 1)
        return zlib_compress(data);
    return data; // mode 0: store as-is
}

// Optionally decompress a payload according to compression mode
static std::vector<uint8_t> maybe_decompress(const uint8_t *data, size_t len,
                                              uint8_t compression) {
    if (compression == 1)
        return zlib_decompress(data, len);
    return {data, data + len};
}

// ── encode (light pass 1, threaded pass 2) ──────────────────

static void encode_file(const std::string &in_path,
                         const std::string &out_path,
                         uint8_t compression,
                         uint32_t block_size,
                         unsigned num_threads) {
    if (num_threads == 0) num_threads = default_thread_count();

    uint32_t M = 0;
    int max_point = 0;
    bool all_bare = true;
    light_pass1(in_path, M, max_point, all_bare);
    if (M == 0) throw std::runtime_error("input file is empty");

    const int N = max_point;
    const uint32_t num_blocks = (M + block_size - 1) / block_size;
    const uint32_t batch_lines = num_threads * block_size * 4;

    std::ofstream ofs(out_path, std::ios::binary);
    if (!ofs) throw std::runtime_error("cannot create " + out_path);
    std::vector<char> out_buf(IO_BUF_SIZE);
    ofs.rdbuf()->pubsetbuf(out_buf.data(),
                           static_cast<std::streamsize>(out_buf.size()));

    ofs.write(PBIN_MAGIC, 4);
    ofs.put(static_cast<char>(PBIN_VERSION));
    ofs.put(static_cast<char>(all_bare ? 0x01 : 0x00));
    ofs.put(static_cast<char>(compression));
    write_varint(ofs, block_size);
    write_varint(ofs, static_cast<uint32_t>(N));
    write_varint(ofs, M);

    const std::streampos dir_off = ofs.tellp();
    for (uint32_t b = 0; b < num_blocks; ++b)
        write_u64_le(ofs, 0);

    std::ifstream ifs(in_path);
    if (!ifs) throw std::runtime_error("cannot reopen " + in_path);
    std::vector<char> in_buf(IO_BUF_SIZE);
    ifs.rdbuf()->pubsetbuf(in_buf.data(),
                           static_cast<std::streamsize>(in_buf.size()));

    uint32_t block_idx = 0;

    auto write_block = [&](const std::vector<uint8_t> &blob) {
        const uint64_t off = static_cast<uint64_t>(ofs.tellp());
        ofs.seekp(dir_off + static_cast<std::streamoff>(block_idx) * 8);
        write_u64_le(ofs, off);
        ofs.seekp(static_cast<std::streamoff>(off));
        ofs.write(reinterpret_cast<const char *>(blob.data()),
                  static_cast<std::streamsize>(blob.size()));
        ++block_idx;
    };

    std::string line;
    while (true) {
        std::vector<std::string> lines;
        lines.reserve(batch_lines);
        while (lines.size() < batch_lines && read_logical_line(ifs, line)) {
            strip_comment(line);
            trim_line(line);
            if (line.empty()) continue;
            lines.push_back(std::move(line));
        }
        if (lines.empty()) break;

        std::vector<std::vector<uint8_t>> encoded(lines.size());
        std::atomic<size_t> next_line{0};
        const auto encode_worker = [&]() {
            for (;;) {
                const size_t i =
                    next_line.fetch_add(1, std::memory_order_relaxed);
                if (i >= lines.size()) break;
                encoded[i] =
                    encode_generator(parse_generator(lines[i]), N);
            }
        };

        std::vector<std::thread> workers;
        workers.reserve(num_threads);
        for (unsigned t = 0; t < num_threads; ++t)
            workers.emplace_back(encode_worker);
        for (auto &w : workers) w.join();

        struct PendingBlock {
            std::vector<uint8_t> payload;
        };
        std::vector<PendingBlock> pending;
        pending.reserve((lines.size() + block_size - 1) / block_size);

        for (size_t i = 0; i < lines.size(); i += block_size) {
            const size_t end =
                std::min(i + static_cast<size_t>(block_size), lines.size());
            std::vector<std::vector<uint8_t>> block_raw;
            block_raw.reserve(end - i);
            for (size_t j = i; j < end; ++j)
                block_raw.push_back(std::move(encoded[j]));
            pending.push_back({build_block_payload(block_raw)});
        }

        std::vector<std::vector<uint8_t>> blobs(pending.size());
        std::atomic<size_t> next_block{0};
        const auto compress_worker = [&]() {
            for (;;) {
                const size_t b =
                    next_block.fetch_add(1, std::memory_order_relaxed);
                if (b >= pending.size()) break;
                blobs[b] = maybe_compress(pending[b].payload, compression);
            }
        };

        workers.clear();
        workers.reserve(num_threads);
        for (unsigned t = 0; t < num_threads; ++t)
            workers.emplace_back(compress_worker);
        for (auto &w : workers) w.join();

        for (size_t b = 0; b < blobs.size(); ++b)
            write_block(blobs[b]);
    }

    ofs.close();
    ifs.close();

    std::ifstream sz_ifs(in_path, std::ios::binary | std::ios::ate);
    const auto in_size = sz_ifs.tellg();
    sz_ifs.close();
    std::ifstream out_sz(out_path, std::ios::binary | std::ios::ate);
    const auto out_size = out_sz.tellg();
    out_sz.close();

    const char *comp_name[] = {"none", "zlib"};
    std::cerr << "Encoded " << M << " generators (N=" << N
              << ", block=" << block_size
              << ", compression=" << comp_name[compression]
              << ", threads=" << num_threads << ")\n"
              << "  " << in_path << " (" << in_size << " B) -> "
              << out_path << " (" << out_size << " B)\n";
}

static void decode_file(const std::string &in_path,
                         const std::string &out_path) {
    std::ifstream ifs(in_path, std::ios::binary);
    if (!ifs) throw std::runtime_error("cannot open " + in_path);
    std::vector<char> in_buf(IO_BUF_SIZE);
    ifs.rdbuf()->pubsetbuf(in_buf.data(), static_cast<std::streamsize>(in_buf.size()));

    ifs.seekg(0, std::ios::end);
    const auto file_size = static_cast<uint64_t>(ifs.tellg());
    ifs.seekg(0);

    char magic[4];
    ifs.read(magic, 4);
    if (ifs.gcount() != 4 || std::memcmp(magic, PBIN_MAGIC, 4) != 0)
        throw std::runtime_error("not a PBIN file");

    char version_c;
    ifs.get(version_c);
    const uint8_t version = static_cast<uint8_t>(version_c);
    if (version != PBIN_VERSION && version != PBIN_VERSION_V1)
        throw std::runtime_error("unsupported PBIN version " +
                                 std::to_string(version));
    const int offset_width = (version == PBIN_VERSION_V1) ? 4 : 8;

    char flags_c, compression_c;
    ifs.get(flags_c);
    ifs.get(compression_c);
    const bool bare = (static_cast<uint8_t>(flags_c) & 0x01) != 0;
    const uint8_t compression = static_cast<uint8_t>(compression_c);

    const uint32_t block_size = read_varint_stream(ifs);
    const uint32_t N          = read_varint_stream(ifs);
    const uint32_t M          = read_varint_stream(ifs);
    const uint32_t num_blocks = (M + block_size - 1) / block_size;

    const auto dir_start = ifs.tellg();
    ifs.seekg(dir_start + static_cast<std::streamoff>(num_blocks) * offset_width);
    if (static_cast<uint64_t>(ifs.tellg()) > file_size)
        throw std::runtime_error("truncated directory");

    ifs.seekg(dir_start);
    std::vector<uint64_t> offsets(num_blocks);
    for (uint32_t b = 0; b < num_blocks; ++b)
        offsets[b] = (offset_width == 4)
            ? static_cast<uint64_t>(read_u32_le_stream(ifs))
            : read_u64_le_stream(ifs);

    std::ofstream ofs(out_path);
    if (!ofs) throw std::runtime_error("cannot create " + out_path);
    std::vector<char> out_buf(IO_BUF_SIZE);
    ofs.rdbuf()->pubsetbuf(out_buf.data(), static_cast<std::streamsize>(out_buf.size()));

    for (uint32_t b = 0; b < num_blocks; ++b) {
        const uint64_t b_start = offsets[b];
        const uint64_t b_end   = (b + 1 < num_blocks)
            ? offsets[b + 1] : file_size;
        if (b_start >= b_end || b_end > file_size)
            throw std::runtime_error("bad block offset at " + std::to_string(b));

        ifs.seekg(static_cast<std::streamoff>(b_start));
        const size_t blen = static_cast<size_t>(b_end - b_start);
        std::vector<uint8_t> block_data(blen);
        ifs.read(reinterpret_cast<char *>(block_data.data()),
                 static_cast<std::streamsize>(blen));
        if (static_cast<size_t>(ifs.gcount()) != blen)
            throw std::runtime_error("truncated block " + std::to_string(b));

        auto payload = maybe_decompress(block_data.data(), blen, compression);
        const uint8_t *bp   = payload.data();
        const uint8_t *bend = payload.data() + payload.size();

        const uint32_t count = read_varint(bp, bend);
        std::vector<uint32_t> sizes(count);
        for (uint32_t i = 0; i < count; ++i)
            sizes[i] = read_varint(bp, bend);

        for (uint32_t i = 0; i < count; ++i) {
            if (bp + sizes[i] > bend)
                throw std::runtime_error("truncated generator in block " +
                                         std::to_string(b));
            auto gen = decode_generator(bp, sizes[i], static_cast<int>(N));
            bp += sizes[i];
            const bool use_bare = bare && gen.cycle_sets.size() == 1;
            ofs << generator_to_string(gen, use_bare) << '\n';
        }
    }
    ofs.close();
    ifs.close();

    const char *comp_name[] = {"none", "zlib"};
    std::cerr << "Decoded " << M << " generators (N=" << N
              << ", block=" << block_size
              << ", compression=" << comp_name[compression] << ")\n"
              << "  " << in_path << " (" << file_size << " B) -> "
              << out_path << "\n";
}

// ================================================================
// main
// ================================================================

static void usage(const char *prog) {
    std::cerr
        << "Usage:\n"
        << "  " << prog << " -e  [options] <file.txt>   Encode to .pbin (delete original)\n"
        << "  " << prog << " -d  <file.pbin>            Decode to .txt  (delete original)\n"
        << "  " << prog << " -ek [options] <file.txt>   Encode (keep original)\n"
        << "  " << prog << " -dk <file.pbin>            Decode (keep original)\n"
        << "\nEncode options:\n"
        << "  -c MODE       Compression: 0=none, 1=zlib (default: 1)\n"
        << "  -b BLOCKSIZE  Generators per block (default: 16)\n"
        << "  -j THREADS    Parallel encode threads (default: CPU count; 0=auto)\n";
}

int main(int argc, char *argv[]) {
    if (argc < 3) { usage(argv[0]); return 1; }

    std::string mode = argv[1];
    bool keep = mode.find('k') != std::string::npos;
    bool do_encode = mode.find('e') != std::string::npos;
    bool do_decode = mode.find('d') != std::string::npos;

    if (!do_encode && !do_decode) { usage(argv[0]); return 1; }

    // Parse remaining args: optional flags then the filename (last arg)
    uint8_t  compression = 1;
    uint32_t block_size  = 16;
    unsigned num_threads = 0;
    std::string input;

    for (int i = 2; i < argc; ++i) {
        std::string a = argv[i];
        if (a == "-c" && i + 1 < argc) {
            int c = std::atoi(argv[++i]);
            if (c < 0 || c > 1) {
                std::cerr << "Error: compression mode must be 0 or 1\n";
                return 1;
            }
            compression = static_cast<uint8_t>(c);
        } else if (a == "-b" && i + 1 < argc) {
            int b = std::atoi(argv[++i]);
            if (b < 1) {
                std::cerr << "Error: block size must be >= 1\n";
                return 1;
            }
            block_size = static_cast<uint32_t>(b);
        } else if (a == "-j" && i + 1 < argc) {
            int j = std::atoi(argv[++i]);
            if (j < 0) {
                std::cerr << "Error: thread count must be >= 0\n";
                return 1;
            }
            num_threads = static_cast<unsigned>(j);
        } else if (input.empty()) {
            input = a;
        } else {
            std::cerr << "Error: unexpected argument '" << a << "'\n";
            usage(argv[0]);
            return 1;
        }
    }

    if (input.empty()) { usage(argv[0]); return 1; }

    try {
        if (do_encode) {
            std::string output = input + ".pbin";
            encode_file(input, output, compression, block_size, num_threads);
            if (!keep) {
                if (std::remove(input.c_str()) == 0)
                    std::cerr << "Removed " << input << "\n";
            }
        } else {
            if (input.size() <= 5 ||
                input.substr(input.size() - 5) != ".pbin")
                throw std::runtime_error(
                    "input file must have .pbin extension");
            std::string output = input.substr(0, input.size() - 5);
            decode_file(input, output);
            if (!keep) {
                if (std::remove(input.c_str()) == 0)
                    std::cerr << "Removed " << input << "\n";
            }
        }
    } catch (const std::exception &e) {
        std::cerr << "Error: " << e.what() << "\n";
        return 1;
    }
    return 0;
}
