#!/usr/bin/env python3
"""Apply a BPS patch (beat format, as made by Flips/beat) to your own ROM, checking every checksum.

    python tools/bps_patch.py <source.nds> <patch.bps> <output.nds>
    python tools/bps_patch.py --info <patch.bps>
    python tools/bps_patch.py --check <patched.nds> <patch.bps>     (is this the patch's output? exit 0 if so)

Used by the build scripts to make the D-pad / D-pad + QoL variants of Spirit Tracks from your clean USA dump, in the
workspace (never in the repository). The source and target CRC-32s stored in the patch are verified, so a wrong
source ROM or a damaged patch stops with an error instead of producing a broken game. Standard library only.
"""
from __future__ import annotations

import argparse
import sys
import zlib
from pathlib import Path


class BpsError(Exception):
    pass


def _number(data: bytes, pos: int) -> tuple[int, int]:
    value, shift = 0, 1
    while True:
        if pos >= len(data):
            raise BpsError("truncated patch")
        x = data[pos]
        pos += 1
        value += (x & 0x7F) * shift
        if x & 0x80:
            return value, pos
        shift <<= 7
        value += shift


def read_header(patch: bytes) -> dict:
    if len(patch) < 4 + 12 or patch[:4] != b"BPS1":
        raise BpsError("not a BPS patch")
    if zlib.crc32(patch[:-4]) & 0xFFFFFFFF != int.from_bytes(patch[-4:], "little"):
        raise BpsError("the patch file is damaged (its own CRC-32 does not match)")
    pos = 4
    source_size, pos = _number(patch, pos)
    target_size, pos = _number(patch, pos)
    meta_size, pos = _number(patch, pos)
    pos += meta_size
    return {
        "source_size": source_size,
        "target_size": target_size,
        "source_crc32": int.from_bytes(patch[-12:-8], "little"),
        "target_crc32": int.from_bytes(patch[-8:-4], "little"),
        "actions_at": pos,
    }


def apply(source: bytes, patch: bytes) -> bytes:
    h = read_header(patch)
    if len(source) != h["source_size"] or zlib.crc32(source) & 0xFFFFFFFF != h["source_crc32"]:
        raise BpsError("this patch is for a different ROM (source size or CRC-32 does not match)")
    target = bytearray(h["target_size"])
    pos, end = h["actions_at"], len(patch) - 12
    out = src_rel = tgt_rel = 0
    while pos < end:
        word, pos = _number(patch, pos)
        action, length = word & 3, (word >> 2) + 1
        if out + length > len(target):
            raise BpsError("patch writes past the end of the target")
        if action == 0:  # SourceRead
            target[out:out + length] = source[out:out + length]
        elif action == 1:  # TargetRead
            target[out:out + length] = patch[pos:pos + length]
            pos += length
        else:
            delta, pos = _number(patch, pos)
            delta = -(delta >> 1) if delta & 1 else delta >> 1
            if action == 2:  # SourceCopy
                src_rel += delta
                target[out:out + length] = source[src_rel:src_rel + length]
                src_rel += length
            else:  # TargetCopy: may overlap the bytes it writes, so byte by byte
                tgt_rel += delta
                for i in range(length):
                    target[out + i] = target[tgt_rel + i]
                tgt_rel += length
        out += length
    if out != len(target) or zlib.crc32(target) & 0xFFFFFFFF != h["target_crc32"]:
        raise BpsError("the patched ROM does not match the patch's CRC-32")
    return bytes(target)


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--info", action="store_true", help="print the patch's sizes and CRC-32s only")
    ap.add_argument("--check", action="store_true", help="check that a file is this patch's output (size and CRC-32)")
    ap.add_argument("files", nargs="+")
    a = ap.parse_args(argv)
    try:
        if a.info:
            h = read_header(Path(a.files[0]).read_bytes())
            print(f"source {h['source_size']} bytes crc32 {h['source_crc32']:08x}")
            print(f"target {h['target_size']} bytes crc32 {h['target_crc32']:08x}")
            return 0
        if a.check:
            if len(a.files) != 2:
                ap.error("need <patched.nds> <patch.bps>")
            h = read_header(Path(a.files[1]).read_bytes())
            data = Path(a.files[0]).read_bytes()
            if len(data) != h["target_size"] or zlib.crc32(data) & 0xFFFFFFFF != h["target_crc32"]:
                print(f"{a.files[0]} is not this patch's output")
                return 1
            print(f"{a.files[0]}: OK (crc32 {h['target_crc32']:08x})")
            return 0
        if len(a.files) != 3:
            ap.error("need <source.nds> <patch.bps> <output.nds>")
        src, bps, dst = (Path(f) for f in a.files)
        target = apply(src.read_bytes(), bps.read_bytes())
        tmp = dst.with_name(dst.name + ".tmp")
        tmp.write_bytes(target)
        tmp.replace(dst)
        print(f"wrote {dst} ({len(target)} bytes, crc32 {zlib.crc32(target) & 0xFFFFFFFF:08x})")
        return 0
    except (BpsError, OSError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
