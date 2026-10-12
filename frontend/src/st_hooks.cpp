// Spirit Tracks's hooks (frontend/config/bkie/hooks.txt): the NitroSDK routines the runtime replaces, and DS Protect.
#include "st_hooks.hpp"

#include <cstdint>
#include <cstring>

#include "ntr/nitro.h"
#include "ntr/recomp.h"

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
        {"st_dsprot_detect", reinterpret_cast<void*>(&st_dsprot_detect)},
        {"st_dsprot_detect_not", reinterpret_cast<void*>(&st_dsprot_detect_not)},
        {"ntr_nitro_save_context", reinterpret_cast<void*>(&ntr_nitro_save_context)},
        {"ntr_nitro_load_context", reinterpret_cast<void*>(&ntr_nitro_load_context)},
        {"ntr_nitro_init_context", reinterpret_cast<void*>(&ntr_nitro_init_context)},
    };
    for (const auto& h : kHooks)
        if (std::strcmp(h.name, name) == 0) return h.fn;
    return nullptr;
}

}  // namespace st
