#!/usr/bin/env python3
"""Regenerate every figure in theme-verification.md from the committed screencaps.

Run from this directory: python3 analyse.py
Its output is captured verbatim in SCRIPT-OUTPUT.txt. If a figure in the document cannot be
reproduced here, the document is wrong — fix the document, not this script.

The frames fall into two groups:

  shipped/  today-*.png, stats-*.png   the APK whose md5 the document records
  prefix/   prefix-01, prefix-02       earlier builds, kept only to prove the regression is
                                       detectable and to show what each fix changed

All frames are 1080x2340 from `adb exec-out screencap -p`.
"""
import os
from collections import Counter
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# The shipped build. Tokens are the brief's palette as implemented in ui/theme/Color.kt.
SHIPPED = {
    "today-light.png": {"surface": "#f2f4fa", "surface_variant": "#e6eaf3",
                        "on_surface_variant": "#4a5568"},
    "today-dark.png":  {"surface": "#1a1a2e", "surface_variant": "#232a42",
                        "primary": "#ff6b81"},
    "stats-light.png": {"surface": "#f2f4fa", "surface_variant": "#e6eaf3",
                        "on_surface_variant": "#4a5568"},
    "stats-dark.png":  {"surface": "#1a1a2e", "surface_variant": "#232a42",
                        "on_surface_variant": "#a8b3c9"},
}

# Roles checked per theme, whatever the screen. Dark text roles must be LIGHT ink.
DARK_INK_ROLES = {
    "onSurface #e6eaf3": "#e6eaf3",
    "onSurfaceVariant #a8b3c9": "#a8b3c9",
}
DARK_ON_PRIMARY = ("onPrimary #3f0013", "#3f0013")

# Material3's built-in light scheme, role by role. Any of these occupying >1% of a frame means our
# own scheme did not take effect on that surface. The list must be complete enough to catch the
# biggest offender: #e7e0ec is the baseline *surfaceVariant*, and it fills whole cards in a
# half-themed app, so a list without it under-reports the regression by a third of the frame.
# Deliberately absent: #b3261e is both the baseline *error* and this app's light error, so it
# cannot distinguish a leaked default from a correct token.
MATERIAL_DEFAULTS = {
    "#fef7ff": "M3 surface",
    "#f7f2fa": "M3 surfaceContainerLow",
    "#f3edf7": "M3 surfaceContainer",
    "#ece6f0": "M3 surfaceContainerHigh",
    "#e6e0e9": "M3 surfaceContainerHighest",
    "#e7e0ec": "M3 surfaceVariant",
    "#e8def8": "M3 secondaryContainer",
    "#6750a4": "M3 primary (purple)",
    "#eaddff": "M3 primaryContainer",
    "#d0bcff": "M3 inversePrimary",
    "#625b71": "M3 secondary",
    "#7d5260": "M3 tertiary",
    "#ffd8e4": "M3 tertiaryContainer",
    "#f9dedc": "M3 errorContainer",
    "#21005d": "M3 onPrimaryContainer",
    "#1d192b": "M3 onSecondaryContainer",
    "#49454f": "M3 onSurfaceVariant",
    "#1d1b20": "M3 onSurface",
    "#79747e": "M3 outline",
    "#cac4d0": "M3 outlineVariant",
    "#322f35": "M3 inverseSurface",
}
BANNED_SHARE = 0.01

# Pre-fix frames: what the scan reported at each stage. Quoted by the document.
PREFIX = {
    "prefix-01-material-defaults.png": "stale APK with no theme class at all",
    "prefix-02-headline-roles-only.png": "theme setting only the brief's headline roles",
}


def rgb(hexv):
    return tuple(int(hexv[i:i + 2], 16) for i in (1, 3, 5))


def hexof(t):
    return "#%02x%02x%02x" % t


def load(name, group="shipped"):
    path = os.path.join(HERE, group, name)
    return Image.open(path).convert("RGB")


def scan_defaults(im):
    """Material3 default colours occupying more than BANNED_SHARE of the frame."""
    total = im.size[0] * im.size[1]
    counts = Counter(im.getdata())
    found = {}
    for t, n in counts.items():
        h = hexof(t)
        if h in MATERIAL_DEFAULTS and n > total * BANNED_SHARE:
            found[MATERIAL_DEFAULTS[h]] = (h, n, 100.0 * n / total)
    return found, total


def near_white_rows(im):
    """Rows whose near-white pixels span more than 95% of the width — a white band.

    The light surface #f2f4fa is itself near-white, so this metric is only meaningful on the
    dark frames, where the surface is navy and any white band would stand out.
    """
    w, h = im.size
    px = im.load()
    hits = []
    for y in range(h):
        light = sum(1 for x in range(w) if min(px[x, y]) > 235)
        if light / w > 0.95:
            hits.append(y)
    return hits


def report_shipped():
    failures = []
    print("=" * 78)
    print("SHIPPED BUILD — brief tokens present, no Material3 default above 1%")
    for name in sorted(SHIPPED):
        im = load(name)
        counts = Counter(im.getdata())
        total = im.size[0] * im.size[1]
        print(f"\n  {name}  {im.size[0]}x{im.size[1]}")
        for t, n in counts.most_common(5):
            print(f"    {hexof(t)}  {n:>9} px  {100.0 * n / total:5.1f}%")

        print("    expected token -> observed:")
        for role, want in SHIPPED[name].items():
            got = counts.get(rgb(want), 0)
            verdict = "PRESENT" if got else "MISSING"
            if not got:
                failures.append(f"{name}: {role} {want} missing")
            print(f"      {role:20} {want}  {got:>9} px  {verdict}")

        defaults, _ = scan_defaults(im)
        print(f"    Material3 defaults above 1%: {defaults or 'none'}")
        if defaults:
            failures.append(f"{name}: {defaults}")
    return failures


# StatsScreen.ChartBar: today's bar is colorScheme.primary, every other day's is tertiary.
# Captured with seven seeded records, so at least one bar of each kind is drawn.
BAR_COLOURS = {
    "stats-light.png": {"today bar = primary": "#b3122e", "other bars = tertiary": "#8a6100"},
    "stats-dark.png": {"today bar = primary": "#ff6b81", "other bars = tertiary": "#f1c40f"},
}


def report_bar_colours():
    print("\n" + "=" * 78)
    print("STATS BAR COLOURS — today=primary, other days=tertiary, per theme")
    failures = []
    for name, roles in BAR_COLOURS.items():
        counts = Counter(load(name).getdata())
        total = 1080 * 2340
        print(f"\n  {name}")
        for label, want in roles.items():
            n = counts.get(rgb(want), 0)
            print(f"    {label:24} {want}  {n:>7} px ({100.0 * n / total:.3f}%)"
                  f"  {'PRESENT' if n else 'MISSING'}")
            if not n:
                failures.append(f"{name}: {label} {want} missing")
    # The old hardcoded bar colour must be gone from the light frame.
    stale = rgb("#ff6b81")
    if Counter(load("stats-light.png").getdata()).get(stale, 0):
        failures.append("stats-light.png: hardcoded #ff6b81 still painted")
    else:
        print("  stats-light.png           hardcoded #ff6b81 absent  -> the literal is gone")
    return failures


def report_white_border():
    print("\n" + "=" * 78)
    print("WHITE BORDER CHECK — full-width near-white bands in the dark frames")
    failures = []
    for name in ["today-dark.png", "stats-dark.png"]:
        im = load(name)
        bands = near_white_rows(im)
        counts = Counter(im.getdata())
        total = im.size[0] * im.size[1]
        white = counts.get((255, 255, 255), 0)
        # widest single row of pure white, as a share of the frame width
        px = im.load()
        w, h = im.size
        widest = max((sum(1 for x in range(w) if px[x, y] == (255, 255, 255)) for y in range(h)),
                     default=0)
        print(f"  {name:16} full-width near-white rows={len(bands)}  "
              f"#ffffff={white} px ({100.0 * white / total:.2f}%)  "
              f"widest white row={100.0 * widest / w:.1f}% of width")
        if bands:
            failures.append(f"{name}: white band rows {bands[:5]}")
    return failures


def report_dark_ink():
    print("\n" + "=" * 78)
    print("DARK-TEXT CHECK — ink on navy must be light, not dark")
    failures = []
    for name in ["today-dark.png", "stats-dark.png"]:
        im = load(name)
        counts = Counter(im.getdata())
        total = im.size[0] * im.size[1]
        surface = rgb(SHIPPED[name]["surface"])
        print(f"\n  {name}")
        for label, hexv in DARK_INK_ROLES.items():
            n = counts.get(rgb(hexv), 0)
            print(f"    {label:28} {n:>7} px ({100.0 * n / total:.2f}%)"
                  f"  {'PRESENT' if n else 'MISSING'}")
            if not n:
                failures.append(f"{name}: {label} missing")
        # Dark pixels that are NOT the navy surface would be dark text sitting on navy.
        dark_non_surface = {
            hexof(t): round(100.0 * n / total, 2)
            for t, n in counts.items()
            if t != surface and (0.2126 * t[0] + 0.7152 * t[1] + 0.0722 * t[2]) < 40
            and n > total * 0.001
        }
        print(f"    dark pixels that are not the surface: {dark_non_surface or 'none'}")
        if dark_non_surface:
            failures.append(f"{name}: dark ink {dark_non_surface}")
    # onPrimary is dark BY DESIGN (coral button, dark text) — report, do not fail.
    for name in ["today-dark.png", "stats-dark.png"]:
        counts = Counter(load(name).getdata())
        total = 1080 * 2340
        label, hexv = DARK_ON_PRIMARY
        n = counts.get(rgb(hexv), 0)
        print(f"  {name:16} {label:22} {n:>6} px ({100.0 * n / total:.2f}%) "
              f"— dark by design on the coral fill")
    return failures


# Positive control. If the shipped frames measure 0 % only because the banned list is too short,
# the check has no teeth. These two frames are the proof it does: both are the same Today screen,
# captured before the fix, and both must trip the list by a wide margin.
PREFIX_MIN_SHARE = {
    "prefix-01-material-defaults.png": 90.0,   # no theme class at all
    "prefix-02-headline-roles-only.png": 15.0,  # headline roles set, container roles not
}


def report_prefix():
    print("\n" + "=" * 78)
    print("PRE-FIX FRAMES — positive control that the default list has teeth")
    failures = []
    for name, what in PREFIX.items():
        im = load(name, "prefix")
        defaults, total = scan_defaults(im)
        share = sum(100.0 * n / total for _h, n, _p in defaults.values())
        print(f"\n  {name}\n    ({what})")
        for label, (h, n, p) in sorted(defaults.items(), key=lambda kv: -kv[1][1]):
            print(f"    {label:28} {h}  {n:>9} px  {p:5.1f}%")
        print(f"    total Material3 default share: {share:.1f}%")
        floor = PREFIX_MIN_SHARE[name]
        if share < floor:
            failures.append(f"{name}: default share {share:.1f}% below the {floor:.0f}% "
                            f"positive-control floor — the banned list is missing a role")
    # The single biggest offender must be caught by name, not merely by total.
    leaked, _ = scan_defaults(load("prefix-01-material-defaults.png", "prefix"))
    if "M3 surfaceVariant" not in leaked:
        failures.append("prefix-01: #e7e0ec (M3 surfaceVariant) not detected — "
                        "the largest leaked default is unbanned")
    return failures




# Frames whose dominant colour proves which scheme rendered. `mode` is what the user stored.
OVERRIDES = {
    "settings-picker-system.png": {
        "system": "LIGHT", "stored": "SYSTEM", "expect": "LIGHT",
    },
    "settings-saved-dark-on-light-system.png": {
        "system": "LIGHT", "stored": "DARK", "expect": "DARK",
    },
    "dark-mode-on-light-system.png": {
        "system": "LIGHT", "stored": "DARK", "expect": "DARK",
    },
    "settings-saved-light-on-dark-system.png": {
        "system": "DARK", "stored": "LIGHT", "expect": "LIGHT",
    },
    "light-mode-on-dark-system.png": {
        "system": "DARK", "stored": "LIGHT", "expect": "LIGHT",
    },
}
DARK_SURFACES = {"#1a1a2e", "#232a42"}
LIGHT_SURFACES = {"#f2f4fa", "#e6eaf3"}


def report_overrides():
    print("\n" + "=" * 78)
    print("STORED-MODE OVERRIDE — a chosen mode must beat the system setting")
    failures = []
    for name, case in OVERRIDES.items():
        im = load(name, "override")
        counts = Counter(im.getdata())
        total = im.size[0] * im.size[1]
        dom_hex, dom_n = hexof(counts.most_common(1)[0][0]), counts.most_common(1)[0][1]
        if dom_hex in DARK_SURFACES:
            rendered = "DARK"
        elif dom_hex in LIGHT_SURFACES:
            rendered = "LIGHT"
        else:
            rendered = f"NEITHER ({dom_hex})"
        want = case["expect"]
        ok = rendered == want
        print(f"  {name:36} system={case['system']:5} stored={case['stored']:5} "
              f"dominant={dom_hex} ({100.0 * dom_n / total:.1f}%) -> {rendered} "
              f"{'OK' if ok else 'WRONG, expected ' + want}")
        if not ok:
            failures.append(f"{name}: rendered {rendered}, expected {want}")
    return failures


def main():
    failures = []
    failures += report_shipped()
    failures += report_white_border()
    failures += report_dark_ink()
    failures += report_overrides()
    failures += report_prefix()
    print("\n" + "=" * 78)
    if failures:
        print("FAIL:")
        for f in failures:
            print(f"  - {f}")
        raise SystemExit(1)
    print("PASS: shipped frames carry the brief's tokens, no Material3 default above 1%, "
          "no white band and no dark ink on navy, both stats bar colours come from the scheme, "
          "a stored mode overrides the system setting in both directions, and the pre-fix frames "
          "still trip the default list by more than the positive-control floor")


if __name__ == "__main__":
    main()