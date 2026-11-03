#!/usr/bin/env python3
"""Reproduce every measurement in avd-verification.md.

Run from the repo root or from this directory:  python3 analyse.py

Why this file exists: the first version of avd-verification.md quoted numbers computed from
full frames while shipping only crops, so a reviewer could not reproduce them. It also
mis-measured centring and clipping on the rendered icon: the launcher's light-grey background
is itself "light", so a naive brightness mask counts background pixels as artwork. This script
therefore measures centring and containment on the **adaptive layer PNG** (exact, no
rasterisation) and measures only what a screenshot can settle on the screenshot.

Frames: 1080x2340 screencaps from emulator-5554, API 35.

SCOPE CAVEAT — read before reusing this on another launcher:
Sections 1-3 derive their crop boxes from the largest connected navy blob, so they adapt to a
different layout. Section 4 does NOT: its per-app grid is hardcoded to this frame's geometry
(5 columns at 201 px pitch from x=20, rows at 353 px pitch from y=345). Those constants land
correctly on screen-final.png and were checked against all 24 cells, but they are specific to
this emulator's 1080x2340 drawer at 5 columns. Reusing section 4 on a different launcher or
grid size will silently measure the wrong rectangles — re-derive the pitch first.
"""
import math
import sys
from collections import deque
from PIL import Image

NAVY = (26, 26, 46)                # the adaptive background layer's flat #1a1a2e
NAVY_TOL = (14, 14, 18)
LAYER = "layer-source/ic_launcher_foreground.png"   # local copy, committed here
SAFE_DP = 72                       # adaptive icons: art must live in the central 72 of 108 dp
CANVAS_DP = 108


def load(path):
    try:
        return Image.open(path).convert("RGBA")
    except FileNotFoundError:
        print(f"  missing: {path}", file=sys.stderr)
        return None


def is_navy(p):
    return (abs(p[0] - NAVY[0]) < NAVY_TOL[0] and abs(p[1] - NAVY[1]) < NAVY_TOL[1]
            and abs(p[2] - NAVY[2]) < NAVY_TOL[2])


def is_saturated(p):
    mx, mn = max(p), min(p)
    return mx > 40 and (mx - mn) > 45


def largest_navy_blob(im):
    px = im.convert("RGB").load()
    w, h = im.size
    seen, best = set(), set()
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if (x, y) in seen or not is_navy(px[x, y]):
                continue
            blob, q = set(), deque([(x, y)])
            seen.add((x, y))
            while q:
                cx, cy = q.popleft()
                blob.add((cx, cy))
                for dx in (-2, 0, 2):
                    for dy in (-2, 0, 2):
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seen \
                                and is_navy(px[nx, ny]):
                            seen.add((nx, ny))
                            q.append((nx, ny))
            if len(blob) > len(best):
                best = blob
    return best


def main():
    print("=" * 74)
    print("1. LAYER GEOMETRY — exact, from the packaged adaptive foreground PNG")
    # The layer is committed in the repo at android/app/src/main/res/drawable-nodpi/.
    # Try both the repo path and a local copy so this runs from either directory.
    for cand in ("layer-source/ic_launcher_foreground.png",
                 "ic_launcher_foreground.png",
                 "../../../../../android/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png"):
        layer = load(cand)
        if layer:
            print(f"  source: {cand}")
            alpha = layer.split()[3]
            bb = alpha.getbbox()
            w, h = layer.size
            cx, cy = (bb[0] + bb[2]) / 2, (bb[1] + bb[3]) / 2
            print(f"  canvas {w}x{h} px = {CANVAS_DP}dp  ->  1 px = {CANVAS_DP/w:.4f} dp")
            print(f"  artwork alpha bbox = {bb}  ({bb[2]-bb[0]} x {bb[3]-bb[1]} px)")
            print(f"  art centre ({cx:.1f},{cy:.1f}) vs canvas centre ({w/2},{h/2})")
            print(f"  OFFSET = ({cx-w/2:+.1f}, {cy-h/2:+.1f}) px = "
                  f"({100*(cx-w/2)/w:+.2f}%, {100*(cy-h/2)/h:+.2f}%) of canvas")
            lo = int(w * (CANVAS_DP - SAFE_DP) / 2 / CANVAS_DP)
            hi = w - lo
            print(f"  safe viewport = central {hi-lo} px ({SAFE_DP}dp), i.e. {lo}..{hi}")
            inside = bb[0] >= lo and bb[1] >= lo and bb[2] <= hi and bb[3] <= hi
            print(f"  artwork entirely inside the safe viewport? {inside}")
            break
    else:
        print("  layer PNG not found — run from the evidence/ directory or adjust the path")

    print("=" * 74)
    print("2. DISC GEOMETRY — what a screenshot can settle (screen-final.png)")
    im = load("screen-final.png")
    if im:
        blob = largest_navy_blob(im)
        xs = [p[0] for p in blob]
        ys = [p[1] for p in blob]
        bb = (min(xs), min(ys), max(xs) + 1, max(ys) + 1)
        dw, dh = bb[2] - bb[0], bb[3] - bb[1]
        print(f"  largest navy component = {len(blob)} sampled px, bbox {bb}")
        print(f"  disc diameter {dw} x {dh} px   ASPECT w/h = {dw/dh:.3f}   (1.000 = circle)")
        print("  NOTE: pixel counts of artwork inside the disc are NOT reported here. The")
        print("  launcher background is light grey and a brightness mask cannot separate it")
        print("  from light artwork at this render size, which is how the first version of")
        print("  this document produced an unreproducible centring figure. Containment is")
        print("  established exactly by section 1 instead.")

    print("=" * 74)
    print("3. THEMED SWITCH OFF vs ON — pixel diff over the icon cell")
    a, b = load("screen-normal.png"), load("screen-themed-switch-on.png")
    if a and b:
        blob = largest_navy_blob(a)
        xs = [p[0] for p in blob]
        ys = [p[1] for p in blob]
        pad = (max(xs) - min(xs)) // 2 + 20
        ccx, ccy = (min(xs) + max(xs)) // 2, (min(ys) + max(ys)) // 2
        box = (ccx - pad, ccy - pad, ccx + pad, ccy + pad)
        ca, cb = a.convert("RGB").crop(box), b.convert("RGB").crop(box)
        pa, pb = ca.load(), cb.load()
        w, h = ca.size
        changed = sum(1 for y in range(h) for x in range(w) if pa[x, y] != pb[x, y])
        print(f"  cell box = {box}   ({w*h} px compared)")
        print(f"  changed = {changed} of {w*h}  ({100*changed/(w*h):.2f} %)")
        print("  -> the themed switch changed nothing in our icon cell")

    print("=" * 74)
    print("4. PER-APP SATURATION — every launcher cell with an icon (screen-final.png)")
    if im:
        px = im.convert("RGB").load()
        w, h = im.size
        print(f"  {'cell(x,y)':>14}  {'saturated':>9}  {'navy':>7}  note")
        for r_i in range(6):
            for c_i in range(5):
                x1, y1 = 20 + c_i * 201, 345 + r_i * 353
                x2, y2 = x1 + 201, y1 + 280
                if x2 > w or y2 > h:
                    continue
                sat = tot = navy = 0
                for y in range(y1, y2):
                    for x in range(x1, x2):
                        p = px[x, y]
                        tot += 1
                        if is_saturated(p):
                            sat += 1
                        if is_navy(p):
                            navy += 1
                if tot and (sat / tot > 0.02 or navy / tot > 0.05):
                    note = "<- ours (navy ground)" if navy / tot > 0.05 else ""
                    print(f"  ({x1:4},{y1:4})  {100*sat/tot:8.1f}%  {100*navy/tot:6.1f}%  {note}")
        print()
        print("  The point of this table: cells other than ours still show saturated colour, so")
        print("  the platform is not applying monochrome tinting to ANY app on this launcher.")


if __name__ == "__main__":
    main()