// pbin — Packed Binary Generator format encoder / decoder
// See PBIN.md for format specification.

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
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

static std::vector<int> parse_cycle_str(const std::string &s) {
    std::string cleaned;
    for (char c : s)
        if (c != '(' && c != ')') cleaned += c;
    std::vector<int> pts;
    if (cleaned.empty()) return pts;
    std::istringstream iss(cleaned);
    std::string tok;
    while (std::getline(iss, tok, ','))
        pts.push_back(std::stoi(tok));
    return pts;
}

static CycleSet parse_cycle_set_str(const std::string &s) {
    CycleSet cs;
    size_t pos = 0;
    while (pos < s.size()) {
        size_t nxt = s.find(")(", pos);
        std::string part = (nxt == std::string::npos)
            ? s.substr(pos) : s.substr(pos, nxt - pos);
        auto cyc = parse_cycle_str(part);
        if (!cyc.empty()) cs.cycles.push_back(std::move(cyc));
        if (nxt == std::string::npos) break;
        pos = nxt + 2;
    }
    return cs;
}

static Generator parse_generator(const std::string &line) {
    Generator gen;
    std::string s = line;
    while (!s.empty() && std::isspace(static_cast<unsigned char>(s.front()))) s.erase(s.begin());
    while (!s.empty() && std::isspace(static_cast<unsigned char>(s.back())))  s.pop_back();
    if (s.empty()) return gen;

    bool bracketed = (s.front() == '[');
    if (bracketed) s = s.substr(1, s.size() - 2);

    if (bracketed) {
        size_t pos = 0;
        while (pos < s.size()) {
            size_t nxt = s.find("),(", pos);
            std::string part = (nxt == std::string::npos)
                ? s.substr(pos) : s.substr(pos, nxt - pos);
            auto cs = parse_cycle_set_str(part);
            if (!cs.cycles.empty()) gen.cycle_sets.push_back(std::move(cs));
            if (nxt == std::string::npos) break;
            pos = nxt + 3;
        }
    } else {
        auto cs = parse_cycle_set_str(s);
        if (!cs.cycles.empty()) gen.cycle_sets.push_back(std::move(cs));
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
static constexpr uint8_t  PBIN_VERSION  = 0x01;

static void write_u32_le(std::vector<uint8_t> &buf, uint32_t v) {
    buf.push_back( v        & 0xFF);
    buf.push_back((v >>  8) & 0xFF);
    buf.push_back((v >> 16) & 0xFF);
    buf.push_back((v >> 24) & 0xFF);
}

static void put_u32_le(std::vector<uint8_t> &buf, size_t pos, uint32_t v) {
    buf[pos + 0] =  v        & 0xFF;
    buf[pos + 1] = (v >>  8) & 0xFF;
    buf[pos + 2] = (v >> 16) & 0xFF;
    buf[pos + 3] = (v >> 24) & 0xFF;
}

static uint32_t read_u32_le(const uint8_t *p) {
    return static_cast<uint32_t>(p[0])       |
          (static_cast<uint32_t>(p[1]) << 8) |
          (static_cast<uint32_t>(p[2]) << 16)|
          (static_cast<uint32_t>(p[3]) << 24);
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

// ── v2 encode ───────────────────────────────────────────────

static void encode_file(const std::string &in_path,
                         const std::string &out_path,
                         uint8_t compression,
                         uint32_t block_size) {
    std::ifstream ifs(in_path);
    if (!ifs) throw std::runtime_error("cannot open " + in_path);
    std::vector<std::string> lines;
    {
        std::string line;
        while (std::getline(ifs, line)) {
            while (!line.empty() &&
                   std::isspace(static_cast<unsigned char>(line.back())))
                line.pop_back();
            if (!line.empty()) lines.push_back(std::move(line));
        }
    }
    ifs.close();
    if (lines.empty()) throw std::runtime_error("input file is empty");

    std::vector<Generator> gens;
    gens.reserve(lines.size());
    int max_point = 0;
    bool all_bare = true;
    for (auto &l : lines) {
        if (!l.empty() && l.front() == '[') all_bare = false;
        auto gen = parse_generator(l);
        for (auto &cs : gen.cycle_sets)
            for (auto &cyc : cs.cycles)
                for (int p : cyc)
                    if (p > max_point) max_point = p;
        gens.push_back(std::move(gen));
    }

    int N = max_point;
    uint32_t M = static_cast<uint32_t>(gens.size());

    // Encode each generator to raw bytes
    std::vector<std::vector<uint8_t>> raw_gens(M);
    for (uint32_t i = 0; i < M; ++i)
        raw_gens[i] = encode_generator(gens[i], N);

    // Group into blocks and compress
    uint32_t num_blocks = (M + block_size - 1) / block_size;
    std::vector<std::vector<uint8_t>> block_blobs(num_blocks);

    for (uint32_t b = 0; b < num_blocks; ++b) {
        uint32_t start = b * block_size;
        uint32_t end   = std::min(start + block_size, M);
        uint32_t count = end - start;

        // Build block payload: count + sizes[] + concatenated gen data
        std::vector<uint8_t> payload;
        write_varint(payload, count);
        for (uint32_t i = start; i < end; ++i)
            write_varint(payload, static_cast<uint32_t>(raw_gens[i].size()));
        for (uint32_t i = start; i < end; ++i)
            payload.insert(payload.end(),
                           raw_gens[i].begin(), raw_gens[i].end());

        block_blobs[b] = maybe_compress(payload, compression);
    }

    // Assemble file
    std::vector<uint8_t> file;
    file.insert(file.end(), PBIN_MAGIC, PBIN_MAGIC + 4);
    file.push_back(PBIN_VERSION);
    file.push_back(all_bare ? 0x01 : 0x00);
    file.push_back(compression);
    write_varint(file, block_size);
    write_varint(file, static_cast<uint32_t>(N));
    write_varint(file, M);

    // Directory placeholder
    size_t dir_off = file.size();
    for (uint32_t b = 0; b < num_blocks; ++b) write_u32_le(file, 0);

    // Append blocks, fill directory
    for (uint32_t b = 0; b < num_blocks; ++b) {
        put_u32_le(file, dir_off + b * 4,
                   static_cast<uint32_t>(file.size()));
        file.insert(file.end(),
                    block_blobs[b].begin(), block_blobs[b].end());
    }

    std::ofstream ofs(out_path, std::ios::binary);
    if (!ofs) throw std::runtime_error("cannot create " + out_path);
    ofs.write(reinterpret_cast<const char *>(file.data()),
              static_cast<std::streamsize>(file.size()));
    ofs.close();

    std::ifstream sz_ifs(in_path, std::ios::binary | std::ios::ate);
    auto in_size = sz_ifs.tellg();
    sz_ifs.close();

    const char *comp_name[] = {"none", "zlib"};
    std::cerr << "Encoded " << M << " generators (N=" << N
              << ", block=" << block_size
              << ", compression=" << comp_name[compression] << ")\n"
              << "  " << in_path << " (" << in_size << " B) -> "
              << out_path << " (" << file.size() << " B)\n";
}

static void decode_file(const std::string &in_path,
                         const std::string &out_path) {
    std::ifstream ifs(in_path, std::ios::binary | std::ios::ate);
    if (!ifs) throw std::runtime_error("cannot open " + in_path);
    size_t file_size = static_cast<size_t>(ifs.tellg());
    ifs.seekg(0);
    std::vector<uint8_t> file(file_size);
    ifs.read(reinterpret_cast<char *>(file.data()),
             static_cast<std::streamsize>(file_size));
    ifs.close();

    const uint8_t *p   = file.data();
    const uint8_t *end = file.data() + file_size;

    if (file_size < 7 || std::memcmp(p, PBIN_MAGIC, 4) != 0)
        throw std::runtime_error("not a PBIN file");
    p += 4;

    uint8_t version = *p++;
    if (version != PBIN_VERSION)
        throw std::runtime_error("unsupported PBIN version " +
                                 std::to_string(version));

    uint8_t flags       = *p++;
    uint8_t compression = *p++;
    bool bare = (flags & 0x01) != 0;

    uint32_t block_size = read_varint(p, end);
    uint32_t N          = read_varint(p, end);
    uint32_t M          = read_varint(p, end);
    uint32_t num_blocks = (M + block_size - 1) / block_size;

    if (p + num_blocks * 4 > end)
        throw std::runtime_error("truncated v2 directory");
    std::vector<uint32_t> offsets(num_blocks);
    for (uint32_t b = 0; b < num_blocks; ++b) {
        offsets[b] = read_u32_le(p);
        p += 4;
    }

    std::ofstream ofs(out_path);
    if (!ofs) throw std::runtime_error("cannot create " + out_path);

    for (uint32_t b = 0; b < num_blocks; ++b) {
        const uint8_t *bs = file.data() + offsets[b];
        const uint8_t *be = (b + 1 < num_blocks)
            ? file.data() + offsets[b + 1] : end;
        if (bs > end || be > end || bs >= be)
            throw std::runtime_error("bad v2 block offset at " +
                                     std::to_string(b));

        auto payload = maybe_decompress(bs, static_cast<size_t>(be - bs),
                                        compression);
        const uint8_t *bp   = payload.data();
        const uint8_t *bend = payload.data() + payload.size();

        uint32_t count = read_varint(bp, bend);
        std::vector<uint32_t> sizes(count);
        for (uint32_t i = 0; i < count; ++i)
            sizes[i] = read_varint(bp, bend);

        for (uint32_t i = 0; i < count; ++i) {
            if (bp + sizes[i] > bend)
                throw std::runtime_error("truncated generator in block " +
                                         std::to_string(b));
            auto gen = decode_generator(bp, sizes[i],
                                        static_cast<int>(N));
            bp += sizes[i];
            bool use_bare = bare && gen.cycle_sets.size() == 1;
            ofs << generator_to_string(gen, use_bare) << '\n';
        }
    }
    ofs.close();

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
        << "  -b BLOCKSIZE  Generators per block (default: 16)\n";
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
            encode_file(input, output, compression, block_size);
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
