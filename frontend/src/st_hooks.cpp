// Spirit Tracks's hooks (frontend/config/bkie/hooks.txt): the NitroSDK routines the runtime replaces, and DS Protect.
#include "st_hooks.hpp"

#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "ntr/nitro.h"
#include "ntr/recomp.h"
#include "st_stick.hpp"

namespace {
// BKIE (USA). The decomp (zeldaret/st) leaves the NitroSDK ones unnamed (OS_func_00nn); each was matched to Phantom
// Hourglass's (same SDK): the same sizes in the same order, and the same literals (ST-PORT.md, "Addresses").
struct Addresses {
    uint32_t os_irq_table;                  // OS_IRQTable (DTCM): the literal OS_IrqHandler loads
    uint32_t os_irq_handler_thread_switch;  // OS_IrqHandler_ThreadSwitch (ITCM): the LR OS_IrqHandler sets
};
constexpr Addresses kUsa = {0x027E0198, 0x01FFBDC8};
const Addresses* A = &kUsa;
}  // namespace

// Main 0x02022BCC: G3i_PerspectiveW_ (r0, r1 = sin and cos of half the field of view, r2 = the aspect ratio, 4.12).
// The game always passes the DS's 4:3 (0x1555): widescreen gives the wide one instead, and tells the geometry engine
// (third_party/CMakeLists.txt patch) that this scene is wide. A scene of orthographic geometry only (ffcc_wide_block)
// stays 4:3.
extern "C" int ffcc_wide_scene;
extern "C" int ffcc_wide_block;
namespace {
std::atomic<uint32_t> g_aspect{0x1555};
}
extern "C" void st_perspective_aspect(NtrCpu* c) {
    const uint32_t aspect = g_aspect.load(std::memory_order_relaxed);
    if (c->r[2] == 0x1555 && aspect != 0x1555 && ffcc_wide_block == 0) {
        c->r[2] = aspect;
        ffcc_wide_scene = 8;
    }
}

// Main 0x020249EC: G3_LoadMtx44 (r0 = the matrix), `before`. Unlike Phantom Hourglass, Spirit Tracks builds its camera's
// projection (MTX_PerspectiveW, above) only when the field of view changes and then loads the stored matrix every
// frame. So the hook above alone widened a scene for 8 frames only (ffcc_wide_scene counts down), never a scene
// continued from a save state or a widescreen change (its matrix was built at 4:3), and never went back to 4:3. Here a
// perspective matrix (_33 = 0, _23 < 0, nothing off the diagonal in the first two rows) whose aspect (_11 / _00) is one
// of ours (4:3 to 21:9) gets the aspect chosen now, in the game's own copy (so it holds until the game rebuilds it),
// and a wide one keeps the scene marked wide.
extern "C" void st_load_mtx44(NtrCpu* c) {
    const uint32_t m = c->r[0];
    if (ntr_rd32(c, m + 0x3C) != 0) return;                                  // _33: not a perspective projection
    if (int32_t(ntr_rd32(c, m + 0x2C)) >= 0) return;                         // _23
    const int32_t m00 = int32_t(ntr_rd32(c, m + 0x00)), m11 = int32_t(ntr_rd32(c, m + 0x14));
    if (m00 <= 0 || m11 <= 0) return;
    if (ntr_rd32(c, m + 0x04) | ntr_rd32(c, m + 0x08) | ntr_rd32(c, m + 0x10) | ntr_rd32(c, m + 0x18)) return;
    const int64_t now = int64_t(m11) * 4096 / m00;                           // the matrix's aspect, 4.12
    if (now < 0x1500 || now > 0x2800) return;                                // (not one of ours: leave it)
    const uint32_t want = ffcc_wide_block == 0 ? g_aspect.load(std::memory_order_relaxed) : 0x1555u;
    if (std::llabs(now - int64_t(want)) > 8) ntr_wr32(c, m + 0x00, uint32_t(int64_t(m11) * 4096 / int64_t(want)));
    if (want != 0x1555u) ffcc_wide_scene = 8;
}

// ITCM 0x01FFBD70: OS_IrqHandler.
extern "C" void st_os_irq_handler(NtrCpu* c) { ntr::nitro::irq_handler(c, A->os_irq_table, A->os_irq_handler_thread_switch); }

// DS Protect (overlay 1): its checks are encrypted in the ROM and decrypted only while they run, so their recompiled
// code would be noise. The game calls them through the DSProt_* wrappers, which these replace with the answers of a
// genuine cartridge. DSProt_Detect<X>(callback): nothing detected, returns 0 and never calls the callback.
extern "C" void st_dsprot_detect(NtrCpu* c) { c->r[0] = 0; }
// DSProt_DetectNot<X>(callback): "not X" holds, so the callback (if any) is called and the result is 1.
extern "C" void st_dsprot_detect_not(NtrCpu* c) {
    const uint32_t callback = c->r[0];
    if (callback) ntr_call(c, callback);
    c->r[0] = 1;
}

namespace st {

bool select_release(const std::string& game_code) {
    if (game_code != "BKIE") return false;
    A = &kUsa;
    return true;
}

void* find_hook(const char* name) {
    static const struct {
        const char* name;
        void* fn;
    } kHooks[] = {
        {"st_os_irq_handler", reinterpret_cast<void*>(&st_os_irq_handler)},
        {"st_perspective_aspect", reinterpret_cast<void*>(&st_perspective_aspect)},
        {"st_load_mtx44", reinterpret_cast<void*>(&st_load_mtx44)},
        {"st_dsprot_detect", reinterpret_cast<void*>(&st_dsprot_detect)},
        {"st_dsprot_detect_not", reinterpret_cast<void*>(&st_dsprot_detect_not)},
        {"st_stick_move", reinterpret_cast<void*>(&st_stick_move)},
        {"ntr_nitro_save_context", reinterpret_cast<void*>(&ntr_nitro_save_context)},
        {"ntr_nitro_load_context", reinterpret_cast<void*>(&ntr_nitro_load_context)},
        {"ntr_nitro_init_context", reinterpret_cast<void*>(&ntr_nitro_init_context)},
    };
    for (const auto& h : kHooks)
        if (std::strcmp(h.name, name) == 0) return h.fn;
    return nullptr;
}

void set_wide_extra(int extra) {
    g_aspect = extra > 0 ? uint32_t(((256 + 2 * extra) * 4096 + 96) / 192) : 0x1555u;
}

}  // namespace st
