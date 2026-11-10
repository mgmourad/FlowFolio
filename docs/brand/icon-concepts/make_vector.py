"""Writes FlowFolio's adaptive launcher icon as Android vector drawables (ADR-0052 icon, owner 2026-11-10).

The same artwork as make_icon.py: terracotta background; an open book with set type on the left page, three flowing
lines on the right, and a Fraunces SemiBold "F" whose stem runs on as the bookmark ribbon into a V notch.

    python -I make_vector.py <fraunces-F-path.txt> <res-dir>

writes drawable/ic_launcher_background.xml, ic_launcher_foreground.xml and ic_launcher_monochrome.xml.
The F's outline comes from glyph.py (fontTools, at wght 600, opsz 144).
"""

import sys

glyph = open(sys.argv[1]).read().strip()
res = sys.argv[2]

# The book, as in make_icon.py.
s = 0.0134
stem_l, stem_r = 200, 480
right = 77.2
x0 = right - 1068.5 * s
top = 37.4
y0 = top + 1400 * s
cut = y0 - 95 * s
rl, rr = x0 + stem_l * s, x0 + stem_r * s
rtop = y0 - 110 * s
rbot = y0 + 330 * s
notch = y0 - 70 * s
k, cy = 1.08, 56.5

NS = 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"'
HEAD = f'<vector {NS}\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n'

LEFT_PAGE = "M29,38 Q41,34 54,40 L54,78 Q41,72 29,75 Z"
RIGHT_PAGE = "M79,38 Q67,34 54,40 L54,78 Q67,72 79,75 Z"
TYPE_LINES = [
    "M34,45 Q42,42.5 49.5,46",
    "M34,51 Q42,48.5 49.5,52",
    "M34,57 Q42,54.5 49.5,58",
    "M34,63 Q40,61 46,63.5",
]
WAVES = [
    "M58.5,63 C62.5,60 65.5,66 69.5,63 S75,60 75,62",
    "M58.5,67.5 C62.5,64.5 65.5,70.5 69.5,67.5 S75,64.5 75,66.5",
    "M58.5,72 C62.5,69 65.5,75 69.5,72 S75,69 75,71",
]
RIBBON = f"M{rl:.3f},{rtop:.3f} L{rr:.3f},{rtop:.3f} L{rr:.3f},{rbot:.3f} L{(rl + rr) / 2:.3f},{notch:.3f} L{rl:.3f},{rbot:.3f} Z"
SHADOW = "M33,79.5 a21,2.6 0 1,0 42,0 a21,2.6 0 1,0 -42,0 Z"


def linear(path, x1, y1, x2, y2, start, end):
    return (
        f'        <path android:pathData="{path}">\n'
        '            <aapt:attr name="android:fillColor">\n'
        f'                <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
        f'android:endX="{x2}" android:endY="{y2}" android:startColor="{start}" android:endColor="{end}"/>\n'
        '            </aapt:attr>\n'
        '        </path>\n'
    )


def stroke(path, color, width, alpha=1.0):
    return (
        f'        <path android:pathData="{path}" android:strokeColor="{color}" android:strokeAlpha="{alpha}" '
        f'android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round"/>\n'
    )


def book(mono):
    """The book group: pages, lines, waves and the F, scaled up and centred on the canvas."""
    out = f'    <group android:pivotX="54" android:pivotY="{cy}" android:scaleX="{k}" android:scaleY="{k}" android:translateY="{54 - cy}">\n'
    if mono:
        out += f'        <path android:pathData="{LEFT_PAGE}" android:fillColor="#FFFFFF" android:fillAlpha="0.42"/>\n'
        out += f'        <path android:pathData="{RIGHT_PAGE}" android:fillColor="#FFFFFF" android:fillAlpha="0.42"/>\n'
        line_color, line_alpha, wave_color, ink = "#FFFFFF", 1.0, "#FFFFFF", "#FFFFFF"
    else:
        out += f'        <path android:pathData="{SHADOW}" android:fillColor="#5A1A04" android:fillAlpha="0.26"/>\n'
        out += linear(LEFT_PAGE, 29, 0, 54, 0, "#FFFFF8EE", "#FFEBDCC6")
        out += linear(RIGHT_PAGE, 79, 0, 54, 0, "#FFFFF8EE", "#FFEBDCC6")
        out += stroke("M54,40 L54,78", "#C9B497", 0.8)
        line_color, line_alpha, wave_color, ink = "#2A2118", 0.42, "#C2410C", "#111111"
    for d in TYPE_LINES:
        out += stroke(d, line_color, 2, line_alpha)
    for d in WAVES:
        out += stroke(d, wave_color, 2.2)
    # The F above its cut (its bottom serif goes), in font units flipped into the icon's.
    out += '        <group>\n'
    out += f'            <clip-path android:pathData="M0,0 L108,0 L108,{cut:.3f} L0,{cut:.3f} Z"/>\n'
    out += f'            <group android:translateX="{x0:.3f}" android:translateY="{y0:.3f}" android:scaleX="{s}" android:scaleY="-{s}">\n'
    out += f'                <path android:pathData="{glyph}" android:fillColor="{ink}"/>\n'
    out += '            </group>\n'
    out += '        </group>\n'
    out += f'        <path android:pathData="{RIBBON}" android:fillColor="{ink}"/>\n'
    out += '    </group>\n'
    return out


background = HEAD + (
    '    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">\n'
    '        <aapt:attr name="android:fillColor">\n'
    '            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" '
    'android:endY="108" android:startColor="#FFE2602A" android:endColor="#FFA3330B"/>\n'
    '        </aapt:attr>\n'
    '    </path>\n'
    '    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">\n'
    '        <aapt:attr name="android:fillColor">\n'
    '            <gradient android:type="radial" android:centerX="30.24" android:centerY="21.6" '
    'android:gradientRadius="81" android:startColor="#38FFFFFF" android:endColor="#00FFFFFF"/>\n'
    '        </aapt:attr>\n'
    '    </path>\n'
) + '</vector>\n'

note = "<!-- Generated by docs/brand/icon-concepts/make_vector.py: edit the script, not this file. -->\n"
open(f"{res}/drawable/ic_launcher_background.xml", "w").write(note + background)
open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(note + HEAD + book(mono=False) + "</vector>\n")
open(f"{res}/drawable/ic_launcher_monochrome.xml", "w").write(note + HEAD + book(mono=True) + "</vector>\n")
print("written to", res)
