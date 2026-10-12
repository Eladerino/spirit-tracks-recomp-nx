# Spirit Tracks port: notes

Started 2026-10-12 as a copy of [phantom-hourglass-recomp](https://github.com/dguillot-gh/phantom-hourglass-recomp)
(its state at e206c39, fresh history): the same FFCCEOT-Recomp engine (recompiler, runtime, melonDS glue; GPL-3.0,
credited in NOTICE.md), the same program, menu, save states and Android app, with the Phantom Hourglass parts swapped
for Spirit Tracks's. Build on the home PC with `scripts\RUN-ALL.ps1` (see `scripts\README.md`).

## What changed from Phantom Hourglass

| Part | Spirit Tracks |
|---|---|
| Game code / config | BKIE (USA), `frontend/config/bkie/` |
| `functions.txt` | 32,522 functions from zeldaret/st (`config/usa/arm9`, commit 1f284ce) by `tools/import_dsd.py`: main 2,153, ITCM 227, 114 overlays (ov097 and ov100 have none); 42 DS Protect functions flagged `!dsprot` |
| `hooks.txt` | OS_Save/Load/InitContext, OS_IrqHandler (below), and the six DSProt_* wrappers |
| `st_hooks.cpp` | OS_IRQTable / ThreadSwitch addresses; `st_dsprot_detect` (r0 = 0) and `st_dsprot_detect_not` (calls the callback, r0 = 1) |
| ROMs | clean USA by SHA-1; the D-pad and D-pad + QoL patches (made locally by `tools/bps_patch.py`) by CRC-32 + size until their SHA-1s are known (`common/src/known_roms.cpp`) |
| Engine | `NTR_ABI_VERSION` 2: `NtrGameInfo.rom_sha1`, the SHA-1 of the ROM the module was recompiled from; the program refuses another ROM (a patched ROM's code is not the clean one's) |
| Save | 8 Mbit flash (1 MB): melonDS's ROM list gives BKIE save type 7; the launcher imports 1 MB saves |
| Names | SpiritTracks.exe, st_game.dll / libst_game.so, com.strecomp.st (StInput, StMenu), ST_* CMake variables, logcat tag strecomp |
| Scripts | `-Variant qol|dpad|clean` (presets release / release-dpad / release-clean, game folders SpiritTracks / -DPad / -Clean), logs in `logs\spirit-tracks`, patched ROMs in `st-roms\`, APKs `SpiritTracks-<variant>-*.apk` |
| Icon | railway tracks (`tools/gen_icon.py`) |

## Addresses (BKIE)

The decomp names the NitroSDK OS functions only `OS_func_00nn`. Matched to Phantom Hourglass's (AZEE, same SDK), where
they were identified by their instructions:

| Function | BKIE | AZEE | How |
|---|---|---|---|
| OS_InitContext | main 0x0202708C (0x6C) `OS_func_0062` | 0x0200DC98 | the only run of three functions sized 0x6C, 0x4C, 0x44 in main, in that order |
| OS_SaveContext | main 0x020270F8 (0x4C) `OS_func_0063` | 0x0200DD04 | " |
| OS_LoadContext | main 0x02027144 (0x44) `OS_func_0064` | 0x0200DD50 | " |
| OS_IrqHandler | ITCM 0x01FFBD70 (0x58) | 0x01FFA48C | the only ITCM function of 0x58 followed by one of 0x15C; its last two words are relocations to DTCM 0x027E0198 and ITCM 0x01FFBDC8 (AZEE: 0x027E0014, 0x01FFA4E4) |
| OS_IRQTable | DTCM 0x027E0198 | 0x027E0014 | the literal above |
| OS_IrqHandler_ThreadSwitch | ITCM 0x01FFBDC8 (0x15C) | 0x01FFA4E4 | the literal above (the LR OS_IrqHandler sets) |

**To check on the first build** (the ROM was not on the work PC): `ntr-recomp`'s report and a disassembly of these
five functions should show PH's patterns (`add r0,#0x48` / CP_SaveContext in OS_SaveContext; `mov ip,#0x4000000;
add ip,#0x210` in OS_IrqHandler). A wrong address shows up as a hang right after the logos.

## DS Protect

Overlay 1 carries DS Protect (`libs/dsprot` in the decomp): DetectFlashcart / DetectEmulator / DetectDummy and their
"Not" twins, each wrapped by DSProt_* that decrypts the check, runs it and encrypts it again; the wrappers themselves are
decoded at static-init time. All 42 functions with a `dsprot` property are flagged `!dsprot`: the recompiler emits a
stub for them instead of translating encrypted bytes. The six DSProt_* wrappers are hooked (`hooks.txt`) to answer as a
genuine cartridge: Detect<X> returns 0 without calling the callback, DetectNot<X> calls the callback and returns 1
(dsprotMain's EXPECT_FALSE / EXPECT_TRUE results with every check passing). FFCC (the engine's first game) did the same
with `ntr_dsprot_not_detected`.

## The patched ROMs

d0k3's *DPAD & QoL Hack v0.9c* (USA BPS, source CRC-32 38392357 = the clean dump). The patched ROM is a rebuilt, trimmed
file (94 MB): the header, the ARM9 (patch actions from 0x4000 on, and long target-only runs from 0x6AA42 on: a
recompressed ARM9) and the file layout all change. The decomp's config describes the clean game, so:

- the clean variant is the reference (`RUN-ALL.ps1 -Variant clean`);
- for the patched ones, check on the first build that the recompiler's report has no large rise in undefined
  instructions or discovered functions compared with the clean build, and that new hack code (if it sits outside the
  listed functions) is found (`recomp_report.txt` in each build folder). If needed, add the hack's functions to a
  variant-specific list (the recompiler's discovery already follows BL targets).

## History

- 2026-10-12: project created from ph-recomp; config imported from zeldaret/st; hooks matched; DS Protect hooks;
  variants and BPS patcher; module bound to its ROM. Dry runs of every script OK on the work PC; C++ (PC + NDK) and Java
  syntax-checked. Not built yet.

## Next

1. First home build: `RUN-ALL.ps1` (D-pad + QoL), then `RUN-ALL.ps1 -Variant clean`; compare both `recomp_report.txt`.
2. List the patched ROMs' SHA-1s (printed by `rominfo`, or in the error box) in `known_roms.cpp` and the guard.
3. Android: `RUN-ANDROID.ps1`.
4. Screenshots for the README.
