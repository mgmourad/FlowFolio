import sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
f = TTFont(sys.argv[1])
axes = {a.axisTag: (a.minValue, a.defaultValue, a.maxValue) for a in f["fvar"].axes} if "fvar" in f else {}
print("axes", axes)
loc = {}
if "wght" in axes: loc["wght"] = int(sys.argv[2]) if len(sys.argv) > 2 else 600
if "opsz" in axes: loc["opsz"] = min(axes["opsz"][2], 144)
for k in ("SOFT", "WONK"):
    if k in axes: loc[k] = axes[k][1]
if loc: f = instantiateVariableFont(f, loc)
gs = f.getGlyphSet()
name = f.getBestCmap()[ord("F")]
pen = SVGPathPen(gs); gs[name].draw(pen)
b = BoundsPen(gs); gs[name].draw(b)
print("upm", f["head"].unitsPerEm, "bounds", b.bounds)
print("PATH", pen.getCommands())
