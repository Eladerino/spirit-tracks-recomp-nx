# st-common.ps1 -- shared by the numbered Spirit Tracks scripts (dot-sourced; not run on its own).
# Expects the calling script to have a [switch] $DryRun parameter, and may have a [string] $Variant one.

$ErrorActionPreference = 'Stop'
# The scripts live in the repo (st-recomp\scripts); the workspace is the folder the repo is in: the ROM, the finished
# game folder, the logs and the APKs go there, outside the repo. Phantom Hourglass's workspace can be the same folder:
# every name here is Spirit Tracks's own (st-recomp, SpiritTracks*, st-roms, logs\spirit-tracks, apks\SpiritTracks-*).
$Repo = Split-Path -Parent $PSScriptRoot
$Root = Split-Path -Parent $Repo

# ---- which game: the clean USA dump, or one of the two patches made from it (d0k3's v0.9c, BPS files) ----------------
#   qol    D-pad + QoL (the default): D-pad controls, L/R blow the flute, faster train
#   dpad   D-pad controls only
#   clean  the retail game, as dumped
# -Variant on any script, else the ST_VARIANT environment variable, else qol. Each one is recompiled and built on its
# own (build\release, build\release-dpad, build\release-clean) into its own game folder, so all three can sit side by side.
if (-not $Variant) { $Variant = $env:ST_VARIANT }
if (-not $Variant) { $Variant = 'qol' }
$Variant = "$Variant".ToLower()
$Variants = @{
    'qol'   = @{ Label = 'USA, D-pad + QoL patch v0.9c'; Preset = 'release'; Game = 'SpiritTracks'
                 Patch = 'loz_st_dpad_qol-0.9c-ua.bps'; Size = 94097260; Crc = '44c7c45b' }
    'dpad'  = @{ Label = 'USA, D-pad patch v0.9c'; Preset = 'release-dpad'; Game = 'SpiritTracks-DPad'
                 Patch = 'dpadonly\loz_st_dpad_0.9c-ua.bps'; Size = 94096060; Crc = 'a966458d' }
    'clean' = @{ Label = 'USA, clean'; Preset = 'release-clean'; Game = 'SpiritTracks-Clean'; Patch = ''; Size = 134217728; Crc = '38392357' }
}
if (-not $Variants.ContainsKey($Variant)) { Write-Host "unknown variant '$Variant' (qol, dpad or clean)" -ForegroundColor Red; exit 1 }
$V = $Variants[$Variant]
$CleanSha1 = '1f0921b5e99db9d92d24710f1b004e4f3c997b73'   # Legend of Zelda, The - Spirit Tracks (USA, Australia) (En,Fr,Es).nds
# USA Rev 1 is the same size but different code: zeldaret/st and the D-pad patches only cover Rev 0, so it is refused.
$Rev1Sha1 = '9953bce6ce32c35d084300c32b555b40aa013047'    # Legend of Zelda, The - Spirit Tracks (USA) (En,Fr,Es) (Rev 1).nds
$CleanSize = 134217728
# Where the patches are looked for: the patch's own folder as it unzips, or the workspace itself.
$PatchDirs = @((Join-Path $Root 'loz_st_dpad_qol-v0.9c'), $Root)
$PatchedDir = Join-Path $Root 'st-roms'                 # the patched ROMs the scripts make (yours: never share them)
$BuildPreset = $V.Preset
$BuildDir = Join-Path $Repo "build\$BuildPreset"
$GameDir = Join-Path $Root $V.Game                      # the finished, ready-to-play folder (step 3)
# A melonDS save to start from (copied once, never moved): bkie.sav or a Spirit Tracks .sav in the workspace; melonDS's
# .ml1.sav first (it is the newer of the two it writes).
$MelonSave = Join-Path $Root 'bkie.sav'   # (where to put one; it need not exist)
$saves = @(Get-ChildItem -Path $Root -Filter '*.sav' -File -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -match '(?i)spirit|bkie' } | Sort-Object { if ($_.Name -match '\.ml\d\.sav$') { 0 } else { 1 } })
if ($saves.Count -gt 0) { $MelonSave = $saves[0].FullName }

$LogDir = Join-Path $Root 'logs\spirit-tracks'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$Progress = Join-Path $LogDir 'progress.log'
# The calling script's name (this file is dot-sourced, so PSCommandPath is the caller); no .NET calls (constrained mode).
$ScriptName = 'st'
if ($MyInvocation.PSCommandPath) { $ScriptName = (Split-Path -Leaf $MyInvocation.PSCommandPath) -replace '\.ps1$', '' }
$Log = Join-Path $LogDir ("$ScriptName-$Stamp" + $(if ($DryRun) { '-dryrun' } else { '' }) + '.log')

function Step([string] $text) {
    $prefix = if ($DryRun) { '[dry-run] ' } else { '' }
    $line = "$(Get-Date -Format 'HH:mm:ss') $ScriptName $prefix$text"
    Write-Host $line -ForegroundColor Cyan
    Add-Content -Path $Progress -Value $line
    Add-Content -Path $Log -Value "==== $line"
}
function Warn([string] $text) {
    Write-Host "WARNING: $text" -ForegroundColor Yellow
    Add-Content -Path $Log -Value "WARNING: $text"
}
function Fail([string] $text) {
    Step "FAIL: $text"
    Write-Host "Log: $Log" -ForegroundColor Yellow
    exit 1
}
# Runs a native command, appending its output to the log (and the screen); fails on a nonzero exit.
# In a dry run it only says what it would run.
function Run([string] $what, [scriptblock] $cmd) {
    if ($DryRun) { Step "would run: $what  ->  $($cmd.ToString().Trim())"; return }
    Step $what
    # Windows PowerShell turns a native tool's stderr into error records: with 'Stop' the first warning would abort.
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    # (Not Tee-Object: Windows PowerShell's writes UTF-16 into a log the other lines write as ASCII.)
    & $cmd 2>&1 | ForEach-Object { $s = "$_"; Write-Host $s; Add-Content -Path $Log -Value $s }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $old
    if ($code -ne 0) { Fail "$what (exit $code)" }
}
# Like Run, for an optional step: returns whether it worked instead of failing.
function Run-Optional([string] $what, [scriptblock] $cmd) {
    if ($DryRun) { Step "would run: $what  ->  $($cmd.ToString().Trim())"; return $true }
    Step $what
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $cmd 2>&1 | ForEach-Object { $s = "$_"; Write-Host $s; Add-Content -Path $Log -Value $s }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $old
    return ($code -eq 0)
}

# ---- the ROM ----------------------------------------------------------------------------------------------------------
function Find-Python {
    foreach ($n in 'python', 'py') { $c = Get-Command $n -ErrorAction SilentlyContinue; if ($c) { return $c.Source } }
    return $null
}
# Your clean USA dump, found by its SHA-1 (any file name) in the workspace or st-roms\.
function Find-CleanRom {
    $candidates = @(Get-ChildItem -Path $Root, $PatchedDir -Filter '*.nds' -File -ErrorAction SilentlyContinue |
                    Where-Object { $_.Length -eq $CleanSize })
    $script:Rev1Rom = $null
    foreach ($c in $candidates) {
        $h = (Get-FileHash -Algorithm SHA1 -LiteralPath $c.FullName).Hash.ToLower()
        if ($h -eq $CleanSha1) { return $c.FullName }
        if ($h -eq $Rev1Sha1) { $script:Rev1Rom = $c.FullName }
    }
    return $null
}
function Find-Patch {
    foreach ($d in $PatchDirs) {
        $p = Join-Path $d $V.Patch
        if (Test-Path -LiteralPath $p) { return $p }
    }
    return $null
}
# A ROM already patched to this variant (yours, or one made by an earlier run), found by its size and the CRC-32 its
# patch states, in st-roms\ or the workspace. Needs Python; returns $null if there is none.
function Find-PatchedRom {
    $py = Find-Python
    if (-not $py) { return $null }
    $candidates = @(Get-ChildItem -Path $PatchedDir, $Root -Filter '*.nds' -File -ErrorAction SilentlyContinue |
                    Where-Object { $_.Length -eq $V.Size })
    foreach ($c in $candidates) {
        $crc = "$(& $py -I (Join-Path $Repo 'tools\bps_patch.py') --crc32 $c.FullName)".Trim()
        if ($crc -eq $V.Crc) { return $c.FullName }
    }
    return $null
}
# The ROM of the chosen variant: the clean dump itself; for a patched variant, a ROM you already patched the same way,
# else the patched copy made from the clean dump in st-roms\ (made once by tools\bps_patch.py, which checks the patch's
# CRC-32s; the clean dump is only read). Returns its path.
function Find-Rom {
    if ($Variant -ne 'clean') {
        $ready = Find-PatchedRom
        if ($ready) { Step "game: Spirit Tracks ($($V.Label)), already patched: $ready"; return $ready }
    }
    $clean = Find-CleanRom
    if (-not $clean) {
        $msg = "no clean Spirit Tracks (USA) dump in $Root (SHA-1 $CleanSha1, $CleanSize bytes)$(if ($Variant -ne 'clean') { ', and no ROM already patched to this variant (CRC-32 ' + $V.Crc + ')' }): copy yours there, any file name"
        if ($script:Rev1Rom) { $msg = "$($script:Rev1Rom) is the USA Rev 1 ROM (SHA-1 $Rev1Sha1). This port needs USA Rev 0, usually named '(USA, Australia) (En,Fr,Es)' without 'Rev 1' (SHA-1 $CleanSha1): the decomp's symbols and the D-pad patches only match Rev 0. Copy that one into $Root" }
        if ($DryRun) { Warn $msg; $clean = Join-Path $Root 'your-spirit-tracks-usa.nds' } else { Fail $msg }
    } else {
        Step "clean dump: $clean"
    }
    if ($Variant -eq 'clean') { Step "game: Spirit Tracks ($($V.Label))"; return $clean }

    $out = Join-Path $PatchedDir "SpiritTracks-USA-$Variant.nds"
    $patch = Find-Patch
    if (-not $patch) {
        $msg = "the patch $($V.Patch) is not in $($PatchDirs -join ' or '): unzip loz_st_dpad_qol-v0.9c there (or use -Variant clean)"
        if ($DryRun) { Warn $msg; $patch = Join-Path $PatchDirs[0] $V.Patch } else { Fail $msg }
    }
    $py = Find-Python
    if (-not $py) { if ($DryRun) { Warn 'no Python yet (1-INSTALL-TOOLS.ps1 installs it)'; $py = 'python' } else { Fail 'Python is needed: run 1-INSTALL-TOOLS.ps1' } }
    $tool = Join-Path $Repo 'tools\bps_patch.py'
    $ok = $false
    if (Test-Path -LiteralPath $out) {
        $ok = Run-Optional "check $out against $patch" { & $py -I $tool --check $out $patch }
    }
    if (-not $ok) {
        New-Item -ItemType Directory -Force -Path $PatchedDir | Out-Null
        Run "patch the clean dump with $patch -> $out" { & $py -I $tool $clean $patch $out }
    }
    Step "game: Spirit Tracks ($($V.Label)): $out"
    return $out
}

# ---- tools ----------------------------------------------------------------------------------------------------------
function Add-Path([string] $dir) { if ((Test-Path $dir) -and ($env:PATH -notlike "*$dir*")) { $env:PATH = "$dir;$env:PATH" } }
function Refresh-Path {
    # PATH as the installers left it (machine + user), without .NET calls (constrained-language safe).
    $m = (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment' -ErrorAction SilentlyContinue).Path
    $u = (Get-ItemProperty 'HKCU:\Environment' -ErrorAction SilentlyContinue).Path
    if ($m -or $u) { $env:PATH = "$m;$u" }
    Add-Path 'C:\Program Files\Git\cmd'
    Add-Path 'C:\Program Files\CMake\bin'
    Add-Path 'C:\Program Files\LLVM\bin'
    Add-Path (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links')
}
$Vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
function Find-VS {
    if (-not (Test-Path $Vswhere)) { return $null }
    $p = & $Vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if ($p) { return "$p".Trim() }
    return $null
}
# The Windows SDK (kernel32.lib and the other system libraries the linker needs). Build Tools' C++ workload does not
# always bring it (2026-10-14, a fresh laptop): checked on its own.
function Find-WinSdk {
    $kits = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\Lib'
    $lib = @(Get-ChildItem -Path (Join-Path $kits '*\um\x64\kernel32.lib') -ErrorAction SilentlyContinue) | Select-Object -First 1
    if ($lib) { return $lib.FullName }
    return $null
}
function Get-CMakeVersionOk {
    if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) { return $false }
    $first = "$((& cmake --version) | Select-Object -First 1)"
    if ($first -match '(\d+)\.(\d+)') { return ([int]$matches[1] -gt 3) -or ([int]$matches[1] -eq 3 -and [int]$matches[2] -ge 28) }
    return $false
}
# The winget ids of the tools still missing.
function Get-MissingTools {
    Refresh-Path
    $missing = @()
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { $missing += 'Git.Git' }
    if (-not (Get-Command python -ErrorAction SilentlyContinue) -and -not (Get-Command py -ErrorAction SilentlyContinue)) { $missing += 'Python.Python.3.12' }
    if (-not (Get-CMakeVersionOk)) { $missing += 'Kitware.CMake' }
    if (-not (Get-Command ninja -ErrorAction SilentlyContinue)) { $missing += 'Ninja-build.Ninja' }
    if (-not (Get-Command clang-cl -ErrorAction SilentlyContinue)) { $missing += 'LLVM.LLVM' }
    if (-not (Find-VS)) { $missing += 'Microsoft.VisualStudio.2022.BuildTools' }
    elseif (-not (Find-WinSdk)) { $missing += 'WindowsSDK' }
    return $missing
}
# Loads vcvars64 (MSVC headers, libraries, Windows SDK) into this session, keeping CMake 3.28+ and LLVM first.
function Use-MSVC {
    $vs = Find-VS
    if (-not $vs) {
        if ($DryRun) { Step 'no Visual Studio 2022 Build Tools yet: would load vcvars64.bat from it'; return }
        Fail 'Visual Studio 2022 Build Tools (C++) not found: run 1-INSTALL-TOOLS.ps1'
    }
    $vcvars = Join-Path $vs 'VC\Auxiliary\Build\vcvars64.bat'
    Step "MSVC environment: $vcvars"
    cmd /c "`"$vcvars`" >nul 2>&1 && set" | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') { Set-Item -Path "env:$($matches[1])" -Value $matches[2] }
    }
    $env:PATH = "C:\Program Files\CMake\bin;C:\Program Files\LLVM\bin;$env:PATH"
    if (-not (Find-WinSdk)) {
        if ($DryRun) { Step 'no Windows SDK yet (1-INSTALL-TOOLS.ps1 adds it)' }
        else { Fail 'the Windows SDK is missing (kernel32.lib): run 1-INSTALL-TOOLS.ps1 (NDS Manager: Set up this PC)' }
    }
    foreach ($t in 'cmake', 'ninja', 'clang-cl', 'lld-link') {
        $c = Get-Command $t -ErrorAction SilentlyContinue
        if (-not $c) {
            if ($DryRun) { Step "$t not found yet (1-INSTALL-TOOLS.ps1 installs it)"; continue }
            Fail "$t not on PATH: run 1-INSTALL-TOOLS.ps1"
        }
        Add-Content -Path $Log -Value "$t = $($c.Source)"
    }
}

# ---- Android: a folder of tools (Java 17, the Android SDK with this NDK, Gradle's cache, adb) ----------------------
# ST_TOOLS_CACHE names it; else ..\recomp-backups\tools-cache beside the workspace (one shared with other projects), else
# tools-cache in the workspace.
$NdkVersion = '28.2.13676358'
$ToolsCache = $env:ST_TOOLS_CACHE
if (-not $ToolsCache) { $ToolsCache = $env:PH_TOOLS_CACHE }
if (-not $ToolsCache) {
    $ToolsCache = Join-Path (Split-Path -Parent $Root) 'recomp-backups\tools-cache'
    if (-not (Test-Path $ToolsCache)) { $ToolsCache = Join-Path $Root 'tools-cache' }
}
$ApkDir = Join-Path $Root 'apks'
$AndroidDir = Join-Path $Repo 'android'
$Package = 'com.strecomp.st'
function Find-Java {
    $c = @()
    Get-Item (Join-Path $ToolsCache 'jdk-17*') -ErrorAction SilentlyContinue | ForEach-Object { $c += Join-Path $_.FullName 'bin\java.exe' }
    if ($env:JAVA_HOME) { $c += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    foreach ($p in 'C:\Program Files\Microsoft\jdk-17*', 'C:\Program Files\Eclipse Adoptium\jdk-17*', 'C:\Program Files\Android\Android Studio\jbr') {
        Get-Item $p -ErrorAction SilentlyContinue | ForEach-Object { $c += Join-Path $_.FullName 'bin\java.exe' }
    }
    $c | Where-Object { Test-Path $_ } | Select-Object -First 1
}
function Find-Sdk {
    @((Join-Path $ToolsCache 'android-sdk'), $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
        Where-Object { $_ -and (Test-Path (Join-Path $_ "ndk\$NdkVersion")) } | Select-Object -First 1
}
function Find-Adb {
    $sdk = @((Join-Path $ToolsCache 'android-sdk'), $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
        Where-Object { $_ -and (Test-Path (Join-Path $_ 'platform-tools\adb.exe')) } | Select-Object -First 1
    if ($sdk) { return Join-Path $sdk 'platform-tools\adb.exe' }
    $c = Get-Command adb -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    return $null
}

# ---- a build folder made at another path ----------------------------------------------------------------------------
# The drive has a different letter on each PC (F:, J:, D:): CMake refuses a build folder whose cache was made for another
# source path, and another PC's compilers would not fit it anyway. Such a folder is removed, so it is made again here.
function Clear-IfMoved([string] $dir) {
    if (-not (Test-Path $dir)) { return $false }
    $here = ($Repo -replace '\\', '/') -replace '/+$', ''
    $caches = @(Get-ChildItem -Path $dir -Filter 'CMakeCache.txt' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 8)
    foreach ($c in $caches) {
        $m = Select-String -Path $c.FullName -Pattern '^CMAKE_HOME_DIRECTORY:INTERNAL=(.*)$' | Select-Object -First 1
        if (-not $m) { continue }
        $made = ($m.Matches[0].Groups[1].Value -replace '\\', '/') -replace '/+$', ''
        if (-not ($made -like "$here*")) {
            if ($DryRun) { Step "would remove $dir (made at $made, now $here)"; return $true }
            Step "removing $($dir): it was made at $made (the drive had another letter there); it is made again here"
            Remove-Item -Recurse -Force $dir
            return $true
        }
    }
    return $false
}
