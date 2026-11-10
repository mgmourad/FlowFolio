import sys
glyph = open(sys.argv[1]).read().strip()
out = sys.argv[2]
mono = len(sys.argv) > 3 and sys.argv[3] == "mono"
s = 0.0134            # font units -> icon units (Fraunces SemiBold)
stem_l, stem_r = 200, 480
right = 77.2          # the F's top-right flag sits just inside the right page's top corner
x0 = right - 1068.5 * s
top = 37.4            # the top bar on the page's top edge
y0 = top + 1400 * s   # icon y of the glyph's baseline
cut = y0 - 95 * s     # the glyph above this; its bottom serif goes
rl, rr = x0 + stem_l * s, x0 + stem_r * s     # the ribbon is the stem itself, at its own width
rtop = y0 - 110 * s
rbot = y0 + 330 * s   # the stem runs on below the baseline...
notch = y0 - 70 * s   # ...and ends in a deep V, cut up past the baseline
k, cy = 1.08, 56.5    # the book: 10% bigger, centred on the canvas
ink = "#FFFFFF" if mono else "#111111"
bg = '' if mono else '''  <rect width="108" height="108" fill="url(#bgA)"/>
  <rect width="108" height="108" fill="url(#lightA)"/>
'''
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bgA" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#E2602A"/>
      <stop offset="1" stop-color="#A3330B"/>
    </linearGradient>
    <radialGradient id="lightA" cx="0.28" cy="0.2" r="0.75">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="pageL" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#FFF8EE"/>
      <stop offset="1" stop-color="#EBDCC6"/>
    </linearGradient>
    <linearGradient id="pageR" x1="1" y1="0" x2="0" y2="0">
      <stop offset="0" stop-color="#FFF8EE"/>
      <stop offset="1" stop-color="#EBDCC6"/>
    </linearGradient>
    <clipPath id="noFoot"><rect x="0" y="0" width="108" height="{cut:.3f}"/></clipPath>
  </defs>
{bg}  <g transform="translate(54 54) scale({k}) translate(-54 -{cy})">
    {'' if mono else '<ellipse cx="54" cy="79.5" rx="21" ry="2.6" fill="#5A1A04" opacity="0.26"/>'}
    <path d="M29 38 Q41 34 54 40 L54 78 Q41 72 29 75 Z" fill="{'#FFFFFF' if mono else 'url(#pageL)'}"/>
    <path d="M79 38 Q67 34 54 40 L54 78 Q67 72 79 75 Z" fill="{'#FFFFFF' if mono else 'url(#pageR)'}"/>
    {'' if mono else '<path d="M54 40 L54 78" stroke="#C9B497" stroke-width="0.8"/>'}
    <g stroke="{'#000000' if mono else '#2A2118'}" stroke-opacity="{1 if mono else 0.42}" stroke-width="2" stroke-linecap="round" fill="none">
      <path d="M34 45 Q42 42.5 49.5 46"/>
      <path d="M34 51 Q42 48.5 49.5 52"/>
      <path d="M34 57 Q42 54.5 49.5 58"/>
      <path d="M34 63 Q40 61 46 63.5"/>
    </g>
    <g stroke="{'#000000' if mono else '#C2410C'}" stroke-width="2.2" stroke-linecap="round" fill="none">
      <path d="M58.5 63 C62.5 60 65.5 66 69.5 63 S75 60 75 62"/>
      <path d="M58.5 67.5 C62.5 64.5 65.5 70.5 69.5 67.5 S75 64.5 75 66.5"/>
      <path d="M58.5 72 C62.5 69 65.5 75 69.5 72 S75 69 75 71"/>
    </g>
    <g fill="{'#000000' if mono else ink}">
      <g clip-path="url(#noFoot)"><path transform="translate({x0:.3f} {y0:.3f}) scale({s} -{s})" d="{glyph}"/></g>
      <path d="M{rl:.3f} {rtop} L{rr:.3f} {rtop} L{rr:.3f} {rbot:.3f} L{(rl+rr)/2:.3f} {notch:.3f} L{rl:.3f} {rbot:.3f} Z"/>
    </g>
  </g>
</svg>
'''
open(out, "w").write(svg)
print(out)
