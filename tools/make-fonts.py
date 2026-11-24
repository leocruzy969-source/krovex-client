"""Generates the bundled UI fonts (static Regular + Bold TTFs and the Minecraft font JSONs).

Run from krovex-mod/:   python tools/make-fonts.py
Sources live in tools/font-sources/ (variable fonts + a few statics, all OFL) and are NOT shipped in the mod;
only the generated files in mod/src/main/resources/assets/krovex-client/font/ are.

Per family key <k> it writes:
  <k>_regular.ttf, <k>_bold.ttf     static instances, trimmed to Latin + punctuation (see KEEP)
  <k>_<n>.json      regular at n GUI px for n in REGULAR_SIZES   (<k>_9 = normal text, <k>_7 = tiny labels)
  <k>_b<n>.json     bold    at n GUI px for n in BOLD_SIZES      (headings, primary buttons, sky lyrics)
Text is always drawn at one of these NATIVE sizes (never scaled), and OVERSAMPLE is 2 on purpose: FreeType
places glyphs on multiples of 1/oversample GUI px, so at GUI scale 2 (the default most people use) oversample 2
puts every letter on a whole screen pixel. Oversample 4 put them on half pixels = uneven, soft-looking text.
The family names are mapped to these keys in KrovexFontRegistry.java.
"""
import json
import math
import pathlib
import shutil

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "font-sources"
OUT = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "krovex-client" / "font"

FAMILIES_VARIABLE = {
    "inter":            ("inter.ttf",          {"wght": 500, "opsz": 14}, {"wght": 700, "opsz": 14}),
    "inter_display":    ("inter.ttf",          {"wght": 500, "opsz": 32}, {"wght": 700, "opsz": 32}),
    "roboto":           ("roboto.ttf",         {"wght": 500, "wdth": 100}, {"wght": 700, "wdth": 100}),
    "roboto_condensed": ("roboto.ttf",         {"wght": 500, "wdth": 75},  {"wght": 700, "wdth": 75}),
    "jetbrains_mono":   ("jetbrains_mono.ttf", {"wght": 500}, {"wght": 700}),
    # added 2026-11-23 (all OFL, from github.com/google/fonts)
    "fredoka":          ("fredoka.ttf",        {"wght": 500, "wdth": 100}, {"wght": 700, "wdth": 100}),   # the bubbly one
    "baloo2":           ("baloo2.ttf",         {"wght": 500}, {"wght": 800}),
    "nunito":           ("nunito.ttf",         {"wght": 600}, {"wght": 900}),
    "quicksand":        ("quicksand.ttf",      {"wght": 500}, {"wght": 700}),
    "comfortaa":        ("comfortaa.ttf",      {"wght": 500}, {"wght": 700}),
    "orbitron":         ("orbitron.ttf",       {"wght": 500}, {"wght": 800}),
    "rubik":            ("rubik.ttf",          {"wght": 500}, {"wght": 700}),
}
FAMILIES_STATIC = {
    "manrope": ("manrope_medium.ttf", "manrope_extrabold.ttf"),
    "poppins": ("poppins_medium.ttf", "poppins_bold.ttf"),
    # single-weight display fonts: the same file serves as regular and bold
    "pressstart2p": ("pressstart2p.ttf", "pressstart2p.ttf"),
    "bangers": ("bangers.ttf", "bangers.ttf"),
    "pacifico": ("pacifico.ttf", "pacifico.ttf"),
    "righteous": ("righteous.ttf", "righteous.ttf"),
}

REGULAR_SIZES = [7, 8, 9, 10, 12, 14, 16]
BOLD_SIZES = [9, 10, 12, 14, 16, 20, 24, 28, 32]
OVERSAMPLE = 2

# Only these characters are kept in the shipped fonts (Latin + punctuation + arrows): the files shrink a lot and
# load faster. Anything else is drawn in Minecraft's own font instead (see KrovexFontRegistry.coverable, which
# must list the same ranges).
KEEP = (list(range(0x20, 0x250)) + list(range(0x2010, 0x2028)) + list(range(0x2030, 0x203B))
        + [0x20AC, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193, 0x2212, 0x221E, 0x2022])


def shrink(path):
    from fontTools import subset
    options = subset.Options()
    options.layout_features = ["kern", "liga", "calt", "ccmp", "mark", "mkmk"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.hinting = False
    font = TTFont(path)
    sub = subset.Subsetter(options)
    sub.populate(unicodes=KEEP)
    sub.subset(font)
    font.save(path)


def provider(ttf, size, oversample):
    # NOTE: never reference "minecraft:default" here. It includes the whole Unifont (65k+ glyphs) and Minecraft
    # walks every glyph of every font on the render thread at each resource reload: with ~110 fonts that froze the
    # game for ~50 s ("Not responding"). include/default is only the ~1k bitmap glyphs, and krovex_symbols.ttf
    # covers the handful of symbols the UI draws.
    return {"providers": [
        {"type": "ttf", "file": f"krovex-client:{ttf}", "shift": [0, 1], "size": size, "oversample": oversample},
        {"type": "ttf", "file": "krovex-client:krovex_symbols.ttf", "shift": [0, 1], "size": size, "oversample": oversample},
        {"type": "reference", "id": "minecraft:include/space"},
        {"type": "reference", "id": "minecraft:include/default"}]}


def make_symbols(path):
    """A tiny TTF with the few symbols the menus draw (note, chevron, stars, check, ...): no Unifont fallback needed."""
    from fontTools.fontBuilder import FontBuilder
    from fontTools.pens.ttGlyphPen import TTGlyphPen

    def circle(cx, cy, r, n=20, rev=False):
        pts = [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]
        return pts if rev else pts[::-1]          # TrueType: outer contours run clockwise

    def rect(x0, y0, x1, y1, rev=False):
        pts = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
        return pts if rev else pts[::-1]

    def star(cx, cy, ro, ri, rev=False):
        pts = []
        for i in range(10):
            a = math.pi / 2 + i * math.pi / 5
            r = ro if i % 2 == 0 else ri
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        return pts if rev else pts[::-1]

    glyphs = {
        0x266A: [circle(280, 210, 170), rect(430, 210, 520, 820), [(520, 820), (760, 650), (760, 520), (520, 690)][::-1]],
        0x2304: [[(80, 600), (180, 700), (500, 380), (820, 700), (920, 600), (500, 180)][::-1]],
        0x2605: [star(500, 420, 430, 180)],
        0x2606: [star(500, 420, 430, 180), star(500, 420, 270, 115, rev=True)],
        0x2713: [[(110, 380), (220, 290), (380, 470), (780, 750), (850, 640), (400, 190)][::-1]],
        0x22EF: [circle(180, 330, 80), circle(500, 330, 80), circle(820, 330, 80)],
        0x25B1: [[(220, 170), (940, 170), (780, 690), (60, 690)][::-1], [(300, 260), (760, 260), (680, 600), (220, 600)]],
        0x25A7: [rect(90, 120, 910, 760), rect(190, 220, 810, 660, rev=True), [(230, 260), (330, 260), (770, 620), (670, 620)][::-1]],
        0x25C7: [[(500, 90), (930, 420), (500, 750), (70, 420)][::-1], [(500, 220), (270, 420), (500, 620), (730, 420)]],
        0x2694: [[(120, 740), (200, 780), (880, 140), (800, 100)][::-1], [(880, 740), (800, 780), (120, 140), (200, 100)][::-1]],
        0x24D8: [circle(500, 400, 440), circle(500, 400, 360, rev=True), rect(450, 330, 550, 640), rect(450, 200, 550, 290)],
        0x2191: [[(440, 120), (560, 120), (560, 560), (780, 560), (500, 830), (220, 560), (440, 560)][::-1]],
    }
    order = [".notdef"] + [f"uni{cp:04X}" for cp in glyphs]
    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap({cp: f"uni{cp:04X}" for cp in glyphs})
    table = {}
    pen = TTGlyphPen(None)
    table[".notdef"] = pen.glyph()
    for cp, contours in glyphs.items():
        pen = TTGlyphPen(None)
        for contour in contours:
            pen.moveTo(contour[0])
            for pt in contour[1:]:
                pen.lineTo(pt)
            pen.closePath()
        table[f"uni{cp:04X}"] = pen.glyph()
    fb.setupGlyf(table)
    fb.setupHorizontalMetrics({n: (1000, 0) for n in order})
    fb.setupHorizontalHeader(ascent=800, descent=-200)
    fb.setupNameTable({"familyName": "Krovex Symbols", "styleName": "Regular"})
    fb.setupOS2(sTypoAscender=800, sTypoDescender=-200, usWinAscent=800, usWinDescent=200)
    fb.setupPost()
    fb.save(path)


def write_json(name, data):
    (OUT / f"{name}.json").write_text(json.dumps(data, separators=(",", ":")), encoding="ascii")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for stale in list(OUT.glob("*.ttf")) + list(OUT.glob("*.json")):
        stale.unlink()
    make_symbols(OUT / "krovex_symbols.ttf")
    for key, (src, regular, bold) in FAMILIES_VARIABLE.items():
        for suffix, axes in (("regular", regular), ("bold", bold)):
            font = instancer.instantiateVariableFont(TTFont(SRC / src), axes, inplace=False)
            font.save(OUT / f"{key}_{suffix}.ttf")
    for key, (regular, bold) in FAMILIES_STATIC.items():
        shutil.copy(SRC / regular, OUT / f"{key}_regular.ttf")
        shutil.copy(SRC / bold, OUT / f"{key}_bold.ttf")
    for ttf in OUT.glob("*.ttf"):
        if ttf.name != "krovex_symbols.ttf":
            shrink(ttf)
    for key in [*FAMILIES_VARIABLE, *FAMILIES_STATIC]:
        for size in REGULAR_SIZES:
            write_json(f"{key}_{size}", provider(f"{key}_regular.ttf", size, OVERSAMPLE))
        for size in BOLD_SIZES:
            write_json(f"{key}_b{size}", provider(f"{key}_bold.ttf", size, OVERSAMPLE))
    print("generated:", ", ".join(sorted(p.name for p in OUT.glob("*.ttf"))))


if __name__ == "__main__":
    main()
