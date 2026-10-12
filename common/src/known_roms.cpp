#include <array>
#include <cstdio>

#include "ntr/rom.hpp"

namespace ntr {

uint32_t crc32(std::span<const uint8_t> data) {
    static const auto table = [] {
        std::array<uint32_t, 256> t{};
        for (uint32_t i = 0; i < 256; i++) {
            uint32_t c = i;
            for (int k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
            t[i] = c;
        }
        return t;
    }();
    uint32_t c = 0xFFFFFFFFu;
    for (uint8_t b : data) c = table[(c ^ b) & 0xFF] ^ (c >> 8);
    return ~c;
}

std::string crc32_hex(uint32_t crc) {
    char buf[9];
    std::snprintf(buf, sizeof buf, "%08x", crc);
    return buf;
}

// Spirit Tracks (USA). The clean dump is the one the zeldaret/st decomp's config (frontend/config/bkie) describes. The
// D-pad and D-pad + QoL patches (d0k3, Greiga Master / StraDaMa, v0.9c) are made from it by the build scripts
// (tools/bps_patch.py). D-pad + QoL is listed by SHA-1; D-pad only is known by the CRC-32 and size its patch states
// until its SHA-1 is listed. Each one is recompiled on its own: a game module only runs with the ROM it was built from.
static const KnownRom kKnownRoms[] = {
    {"1f0921b5e99db9d92d24710f1b004e4f3c997b73", "38392357", 134217728, "BKIE", "USA",
     "The Legend of Zelda - Spirit Tracks (USA, Australia) (En,Fr,Es)", true},
    {"", "a966458d", 94096060, "BKIE", "USA (D-pad patch)",
     "The Legend of Zelda - Spirit Tracks (USA) (D-pad patch v0.9c)", true},
    {"6896c1db2b3a725cb991f711a875fc2228b7620e", "44c7c45b", 94097260, "BKIE", "USA (D-pad + QoL patch)",
     "The Legend of Zelda - Spirit Tracks (USA) (D-pad + QoL patch v0.9c)", true},
};

std::span<const KnownRom> known_roms() { return kKnownRoms; }

const KnownRom* find_known_rom(const Sha1Digest& digest) {
    std::string hex = to_hex(digest);
    for (const KnownRom& r : kKnownRoms)
        if (hex == r.sha1_hex) return &r;
    return nullptr;
}

const KnownRom* find_known_rom(std::span<const uint8_t> rom) {
    if (const KnownRom* r = find_known_rom(sha1(rom))) return r;
    const std::string crc = crc32_hex(crc32(rom));
    for (const KnownRom& r : kKnownRoms)
        if (!*r.sha1_hex && crc == r.crc32_hex && rom.size() == r.size) return &r;
    return nullptr;
}

}  // namespace ntr
