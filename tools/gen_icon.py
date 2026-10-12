#!/usr/bin/env python3
"""Draws the program's icon (frontend/res/app.ico): gold railway tracks running to the horizon on a rounded square.

Original art made from shapes in this script (no game artwork). Pure Python, no image library:

    python tools/gen_icon.py frontend/res/app.ico
    python tools/gen_icon.py --android android/app/src/main/res     (the launcher icons, mipmap-*/ic_launcher.png)
"""
import struct
import sys
import zlib

SIZES = (16, 24, 32, 48, 64, 128, 256)


def inside_rounded_square(x, y, r):
    cx = min(max(x, r), 1 - r)
    cy = min(max(y, r), 1 - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def color_at(x, y):
    """RGBA of the icon at (x, y) in 0..1 (y down)."""
    if not inside_rounded_square(x, y, 0.18):
        return (0, 0, 0, 0)
    horizon = 0.42
    if y < horizon:  # sky: light at the horizon
        t = y / horizon
        sky_top, sky_low = (40, 110, 190), (150, 205, 240)
        return (*tuple(int(a + (b - a) * t) for a, b in zip(sky_top, sky_low)), 255)
    # Ground, with a railway running to the horizon: two gold rails meeting at the vanishing point, wooden sleepers.
    d = (y - horizon) / (1 - horizon)  # 0 at the horizon, 1 at the bottom
    grass_far, grass_near = (70, 140, 70), (30, 90, 40)
    ground = tuple(int(a + (b - a) * d) for a, b in zip(grass_far, grass_near))
    gauge = 0.03 + 0.30 * d  # half the distance between the rails, in perspective
    rail_w = 0.006 + 0.03 * d
    off = abs(x - 0.5)
    if abs(off - gauge) <= rail_w:
        return (240, 200, 90, 255)
    # Sleepers: evenly spaced in depth (1/d spacing), a little wider than the track.
    if off <= gauge * 1.25 and d > 0.04:
        z = 1.0 / d
        if (z * 1.6) % 1.0 < 0.35:
            return (120, 80, 45, 255)
    if off <= gauge * 1.25:  # the ballast between the sleepers
        return (150, 140, 120, 255)
    return (*ground, 255)


def render(size):
    ss = 4  # supersampling for smooth edges
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            acc = [0, 0, 0, 0]
            for sy in range(ss):
                for sx in range(ss):
                    r, g, b, a = color_at((px + (sx + 0.5) / ss) / size, (py + (sy + 0.5) / ss) / size)
                    acc[0] += r * a
                    acc[1] += g * a
                    acc[2] += b * a
                    acc[3] += a
            n = ss * ss
            a = acc[3] / n
            if a:
                row += bytes((int(acc[0] / acc[3]), int(acc[1] / acc[3]), int(acc[2] / acc[3]), int(a)))
            else:
                row += bytes(4)
        rows.append(bytes(row))
    return rows


def png(size, rows):
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


ANDROID = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--android":
        import os
        for density, size in ANDROID.items():
            d = os.path.join(sys.argv[2], "mipmap-" + density)
            os.makedirs(d, exist_ok=True)
            with open(os.path.join(d, "ic_launcher.png"), "wb") as f:
                f.write(png(size, render(size)))
        print(f"wrote {len(ANDROID)} launcher icons under {sys.argv[2]}")
        return
    out = sys.argv[1] if len(sys.argv) > 1 else "frontend/res/app.ico"
    images = [png(s, render(s)) for s in SIZES]
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = b""
    for s, data in zip(SIZES, images):
        entries += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    with open(out, "wb") as f:
        f.write(header + entries + b"".join(images))
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
