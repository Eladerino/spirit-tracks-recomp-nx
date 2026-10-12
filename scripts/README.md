# Building Spirit Tracks, step by step

The scripts build everything into the *workspace*: the folder that holds this repository (`st-recomp\`). Put your clean
USA dump there (any file name; it is found by its SHA-1, `1f0921b5...`) and, for the D-pad versions, the patch folder
`loz_st_dpad_qol-v0.9c\`. They only ever read the ROM and the patch. The workspace can be Phantom Hourglass's too:
nothing here has the same name as its files.

```
workspace\
  st-recomp\                 the repository (these scripts are in st-recomp\scripts)
  your-dump.nds              your clean USA ROM
  loz_st_dpad_qol-v0.9c\     the D-pad / D-pad + QoL patches (BPS)
  st-roms\                   made by the scripts: the patched ROMs (yours only: do not share them)
  SpiritTracks\              made by step 3: the game (D-pad + QoL), ready to play (do not share it: it contains the game)
  SpiritTracks-DPad\, SpiritTracks-Clean\   the same for -Variant dpad / clean
  apks\                      made by RUN-ANDROID: the phone app, SpiritTracks-<variant>-*.apk (the same)
  logs\spirit-tracks\        every step's log, and the test's screenshots
```

Run them from PowerShell, in order, or `RUN-ALL.ps1` for steps 1-4. Each one stops with a FAIL line if something is
wrong; `logs\spirit-tracks\progress.log` has every step, `logs\spirit-tracks\<script>-<time>.log` the full output. Add
`-DryRun` to any of them to check everything without installing or building anything.

```
powershell -NoProfile -ExecutionPolicy Bypass -File st-recomp\scripts\RUN-ALL.ps1
```

Every script takes `-Variant`: `qol` (the default: the D-pad + QoL patch), `dpad` (the D-pad patch only) or `clean` (the
retail game). Each one is recompiled and built on its own (`build\release`, `build\release-dpad`,
`build\release-clean`) into its own game folder, so they never mix. To check the original game as well:

```
powershell -NoProfile -ExecutionPolicy Bypass -File st-recomp\scripts\RUN-ALL.ps1 -Variant clean
```

| Step | What it does | Time |
|---|---|---|
| `1-INSTALL-TOOLS.ps1` | Installs Git, Python, CMake 3.28+, Ninja, LLVM and Visual Studio Build Tools (C++) with winget; skips what is there. Build Tools asks for admin once (about 3 GB). | 10-30 min, once |
| `2-BUILD.ps1` | Makes the patched ROM from your dump (`tools\bps_patch.py`, checked by CRC-32) unless it is there, recompiles it to C and builds the game and the program (`-Clean` for a fresh build). Needs internet the first time (SDL3). The default variant also builds a test copy of the program with the phone's renderer. | 10-30 min the first time, then a minute |
| `3-MAKE-GAME-FOLDER.ps1` | Makes `SpiritTracks\` (or `-DPad`, `-Clean`) with `PLAY.cmd`: the game, ready to play. Never overwrites the save there. | seconds |
| `4-TEST.ps1` | Plays the game for about a minute by itself on a copy of the save (a save state is saved and loaded too, and the GPU renderers are tried) and takes screenshots into `logs\spirit-tracks\test-<variant>-<time>\`. | 2 min |

Then double-click `SpiritTracks\PLAY.cmd`. The game's own save is `SpiritTracks\data\saves\bkie.sav`: to carry on from
a save you already have (from an emulator: 1 MB, the cartridge's 8 Mbit flash), put it there as `bkie.sav` before the
first start, or put it in the workspace as `bkie.sav` (or any `.sav` with "Spirit" in its name) and step 3 copies it
into each new game folder once.

## Android (after the PC build)

```
powershell -NoProfile -ExecutionPolicy Bypass -File st-recomp\scripts\RUN-ANDROID.ps1
```

| Step | What it does |
|---|---|
| `ANDROID-TOOLS.ps1` | Uses the Java 17 and Android SDK you have (Android Studio's, `JAVA_HOME`, `ANDROID_HOME`), or installs them: Java with winget, the SDK (platform 35, NDK 28.2, adb, about 1.5 GB) into the tools folder. Shows Android's SDK licence for you to accept. Once. |
| `5-BUILD-ANDROID.ps1` | Builds the APK into `apks\` from the PC build's C (the same `-Variant`: build it on the PC first). The APK takes only the ROM it was built from. |
| `6-INSTALL-PHONE.ps1` | Installs it on the phone, copies the variant's ROM in (replacing another variant's), and a save only if the phone has none yet; opens the app. |

The phone needs USB debugging on (Settings, About phone: tap Build number seven times; then Developer options, USB
debugging) and to be plugged in; accept the "Allow USB debugging?" prompt on it. Without the phone, `RUN-ANDROID.ps1
-NoPhone` builds the APK only: copy it to the phone and install it, and its start screen imports the ROM and a save.

The tools folder is `ST_TOOLS_CACHE` (or `PH_TOOLS_CACHE`) if you set it, else `..\recomp-backups\tools-cache` beside the workspace, else
`tools-cache` in the workspace. An optional `debug.keystore` there signs the APK (the same key on every PC, so an update
installs over the app); without one Android's own debug key is used.

## On the phone

Touch the game directly: it is the stylus. The D-pad is on the left; the small buttons are the menu (≡), TOP (the top
screen), ⟳ (portrait: both screens, like a DS held open), MIC (hold: blow into the microphone) and LID (close the DS).
The menu switches every control on or off, adds A B X Y, L R and Start/Select, sets their opacity and size, and has
the renderer (GPU for 2x-6x), the picture size, the FPS counter (drag it anywhere) and the save state slots.
