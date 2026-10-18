# Builds the Android adaptive launcher icon layers from the master artwork (docs/brand/flowfolio-icon-master.png).
# Usage (from repo root):  pwsh tools/icon/make_launcher_icons.ps1
#
# Adaptive icons: the launcher masks a 108 dp foreground layer to its own shape (circle, squircle, rounded square).
# Every shape keeps at least the central 66 dp circle, so the whole book (pages, ribbon, curled page) is scaled to fit
# inside that circle. Around it the artwork's blue plate is extended to the layer's edges: the plate's own edge colours
# are grown outward and smoothed, so any mask shows the full icon on the plate's colours with no corners or rim.
param(
    [string]$Master = "docs/brand/flowfolio-icon-master.png",
    [string]$ResDir = "android/app/src/main/res",
    [string]$StoreOut = "docs/brand/flowfolio-icon-512.png"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;

public static class IconFill {
    static int A(int c) { return (c >> 24) & 255; }
    static int R(int c) { return (c >> 16) & 255; }
    static int G(int c) { return (c >> 8) & 255; }
    static int B(int c) { return c & 255; }

    // The plate: opaque pixels, eroded so the anti-aliased rim and any white halo are left out.
    public static bool[] Plate(int[] px, int w, int h, int erode) {
        var m = new bool[w * h];
        for (int i = 0; i < m.Length; i++) m[i] = A(px[i]) >= 250;
        for (int k = 0; k < erode; k++) {
            var n = new bool[w * h];
            for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                n[i] = m[i] && m[i - 1] && m[i + 1] && m[i - w] && m[i + w];
            }
            m = n;
        }
        return m;
    }

    // Bounding box of the book: plate pixels that are not plate blue (pages, ribbon, curl).
    public static int[] BookBox(int[] px, bool[] plate, int w, int h) {
        int x0 = w, y0 = h, x1 = 0, y1 = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (!plate[i]) continue;
            int c = px[i];
            bool blue = B(c) > R(c) + 40 && B(c) > G(c) + 15;
            if (blue) continue;
            if (x < x0) x0 = x; if (y < y0) y0 = y; if (x > x1) x1 = x; if (y > y1) y1 = y;
        }
        return new int[] { x0, y0, x1, y1 };
    }

    // Places the art at (ox, oy) on an S x S canvas and fills everything outside the plate by growing the plate's
    // edge colours outward one ring at a time (each new pixel averages its already-filled neighbours), then
    // smooths the grown area with a growing blur so it reads as a continuation of the plate's gradient.
    public static int[] Extend(int[] px, bool[] plate, int w, int h, int S, int ox, int oy) {
        var rgb = new float[S * S * 3];
        var dist = new int[S * S];
        for (int i = 0; i < dist.Length; i++) dist[i] = -1;
        var frontier = new List<int>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int sx = x + ox, sy = y + oy;
            if (sx < 0 || sy < 0 || sx >= S || sy >= S || !plate[y * w + x]) continue;
            int j = sy * S + sx, c = px[y * w + x];
            rgb[j * 3] = R(c); rgb[j * 3 + 1] = G(c); rgb[j * 3 + 2] = B(c); dist[j] = 0;
        }
        for (int j = 0; j < dist.Length; j++) if (dist[j] == 0) frontier.Add(j);
        int d = 0;
        while (frontier.Count > 0) {
            d++;
            var next = new List<int>();
            var seen = new HashSet<int>();
            foreach (int j in frontier) {
                int x = j % S, y = j / S;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= S || ny >= S) continue;
                    int k = ny * S + nx;
                    if (dist[k] < 0 && seen.Add(k)) next.Add(k);
                }
            }
            foreach (int k in next) {
                int x = k % S, y = k / S; float r = 0, g = 0, b = 0; int n = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= S || ny >= S) continue;
                    int q = ny * S + nx;
                    if (dist[q] < 0 || dist[q] == d) continue;
                    r += rgb[q * 3]; g += rgb[q * 3 + 1]; b += rgb[q * 3 + 2]; n++;
                }
                if (n > 0) { rgb[k * 3] = r / n; rgb[k * 3 + 1] = g / n; rgb[k * 3 + 2] = b / n; }
            }
            foreach (int k in next) dist[k] = d;
            frontier = next;
        }
        var blurred = (float[])rgb.Clone();
        for (int pass = 0; pass < 3; pass++) blurred = Box(blurred, S, 40);
        var outPx = new int[S * S];
        for (int j = 0; j < outPx.Length; j++) {
            // Inside the plate keep the art; outside, blend toward the smoothed fill over the first 40 px.
            float t = dist[j] <= 0 ? 0f : Math.Min(1f, dist[j] / 40f);
            int r = (int)Math.Round(rgb[j * 3] * (1 - t) + blurred[j * 3] * t);
            int g = (int)Math.Round(rgb[j * 3 + 1] * (1 - t) + blurred[j * 3 + 1] * t);
            int b = (int)Math.Round(rgb[j * 3 + 2] * (1 - t) + blurred[j * 3 + 2] * t);
            outPx[j] = (255 << 24) | (Clamp(r) << 16) | (Clamp(g) << 8) | Clamp(b);
        }
        return outPx;
    }

    static int Clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    static float[] Box(float[] src, int S, int rad) {
        var tmp = new float[src.Length]; var dst = new float[src.Length];
        for (int y = 0; y < S; y++) for (int c = 0; c < 3; c++) {
            float acc = 0; int n = 0;
            for (int x = -rad; x <= rad; x++) { int xx = Math.Max(0, Math.Min(S - 1, x)); acc += src[(y * S + xx) * 3 + c]; n++; }
            for (int x = 0; x < S; x++) {
                tmp[(y * S + x) * 3 + c] = acc / n;
                int add = Math.Min(S - 1, x + rad + 1), rem = Math.Max(0, x - rad);
                acc += src[(y * S + add) * 3 + c] - src[(y * S + rem) * 3 + c];
            }
        }
        for (int x = 0; x < S; x++) for (int c = 0; c < 3; c++) {
            float acc = 0; int n = 0;
            for (int y = -rad; y <= rad; y++) { int yy = Math.Max(0, Math.Min(S - 1, y)); acc += tmp[(yy * S + x) * 3 + c]; n++; }
            for (int y = 0; y < S; y++) {
                dst[(y * S + x) * 3 + c] = acc / n;
                int add = Math.Min(S - 1, y + rad + 1), rem = Math.Max(0, y - rad);
                acc += tmp[(add * S + x) * 3 + c] - tmp[(rem * S + x) * 3 + c];
            }
        }
        return dst;
    }
}
'@

function Get-Pixels([System.Drawing.Bitmap]$bmp) {
    $rect = New-Object System.Drawing.Rectangle 0, 0, $bmp.Width, $bmp.Height
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $px = New-Object int[] ($bmp.Width * $bmp.Height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $px, 0, $px.Length)
    $bmp.UnlockBits($data)
    return , $px
}

function New-BitmapFrom([int[]]$px, [int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $rect = New-Object System.Drawing.Rectangle 0, 0, $size, $size
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    [System.Runtime.InteropServices.Marshal]::Copy($px, 0, $data.Scan0, $px.Length)
    $bmp.UnlockBits($data)
    return $bmp
}

function New-Graphics($bitmap) {
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return $g
}

$art = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Master))
$w = $art.Width; $h = $art.Height
$px = Get-Pixels $art
$plate = [IconFill]::Plate($px, $w, $h, 16)
$box = [IconFill]::BookBox($px, $plate, $w, $h)
$cx = ($box[0] + $box[2]) / 2.0; $cy = ($box[1] + $box[3]) / 2.0
$radius = [Math]::Sqrt([Math]::Pow($box[2] - $box[0], 2) + [Math]::Pow($box[3] - $box[1], 2)) / 2.0
# The book's circumscribing circle fills the 66 dp safe circle less 1 dp, so the master spans 108/(2*32.5/radius) px.
$layerDp = 108.0; $safeRadiusDp = 32.5
$span = $layerDp * $radius / $safeRadiusDp
$canvas = [int][Math]::Ceiling($span) + 4
$ox = [int][Math]::Round($canvas / 2.0 - $cx); $oy = [int][Math]::Round($canvas / 2.0 - $cy)
Write-Host ("book box {0},{1}-{2},{3}; radius {4:N0}px; canvas {5}px" -f $box[0], $box[1], $box[2], $box[3], $radius, $canvas)
$filled = [IconFill]::Extend($px, $plate, $w, $h, $canvas, $ox, $oy)
$full = New-BitmapFrom $filled $canvas

$densities = [ordered]@{ "mdpi" = 1.0; "hdpi" = 1.5; "xhdpi" = 2.0; "xxhdpi" = 3.0; "xxxhdpi" = 4.0 }
foreach ($d in $densities.GetEnumerator()) {
    $size = [int]($layerDp * $d.Value)
    $layer = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = New-Graphics $layer
    $src = New-Object System.Drawing.RectangleF ([single](($canvas - $span) / 2)), ([single](($canvas - $span) / 2)), ([single]$span), ([single]$span)
    $dst = New-Object System.Drawing.RectangleF 0, 0, $size, $size
    $g.DrawImage($full, $dst, $src, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $dir = Join-Path $ResDir "mipmap-$($d.Key)"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $layer.Save((Join-Path $dir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $layer.Dispose()
    Write-Host "mipmap-$($d.Key): ${size}px"
}
$full.Dispose()

# Store icon: square 512 px, the master's plate as drawn (stores apply their own mask), halo pixels removed.
$size = 512
$store = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = New-Graphics $store
$g.Clear([System.Drawing.Color]::Transparent)
$scale = [Math]::Min($size / $w, $size / $h)
$sw = $w * $scale; $sh = $h * $scale
$g.DrawImage($art, [single](($size - $sw) / 2), [single](($size - $sh) / 2), [single]$sw, [single]$sh)
$g.Dispose()
for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $c = $store.GetPixel($x, $y)
        if ($c.A -gt 0 -and $c.A -lt 200 -and $c.R -gt 200 -and $c.G -gt 200 -and $c.B -gt 200) {
            $store.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
        }
    }
}
$store.Save((Resolve-Path -LiteralPath (Split-Path $StoreOut)).Path + "\" + (Split-Path $StoreOut -Leaf), [System.Drawing.Imaging.ImageFormat]::Png)
$store.Dispose()
$art.Dispose()
