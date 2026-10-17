# Builds the Android adaptive launcher icon layers from the master artwork (docs/brand/flowfolio-icon-master.png).
# Usage (from repo root):  pwsh tools/icon/make_launcher_icons.ps1
#
# Adaptive icons: the launcher masks a 108 dp foreground layer to its own shape and shows only the central ~72 dp.
# The artwork is drawn 90 dp wide, so its rounded plate edge (and any anti-aliasing halo) lies outside every mask
# and the launcher cuts a clean shape from the plate's interior. The book stays within the visible area.
param(
    [string]$Master = "docs/brand/flowfolio-icon-master.png",
    [string]$ResDir = "android/app/src/main/res",
    [string]$StoreOut = "docs/brand/flowfolio-icon-512.png"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$art = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Master))
$densities = [ordered]@{ "mdpi" = 1.0; "hdpi" = 1.5; "xhdpi" = 2.0; "xxhdpi" = 3.0; "xxxhdpi" = 4.0 }
$layerDp = 108.0
$artDp = 90.0

function New-Graphics($bitmap) {
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    return $g
}

foreach ($d in $densities.GetEnumerator()) {
    $px = [int]($layerDp * $d.Value)
    $artW = $artDp * $d.Value
    $artH = $artW * $art.Height / $art.Width
    $layer = New-Object System.Drawing.Bitmap $px, $px, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = New-Graphics $layer
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($art, [single](($px - $artW) / 2), [single](($px - $artH) / 2), [single]$artW, [single]$artH)
    $g.Dispose()
    $dir = Join-Path $ResDir "mipmap-$($d.Key)"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $layer.Save((Join-Path $dir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $layer.Dispose()
    Write-Host "mipmap-$($d.Key): ${px}px"
}

# Store icon: square 512 px, artwork centred, faint white halo pixels (semi-transparent, near-white) removed.
$size = 512
$store = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = New-Graphics $store
$g.Clear([System.Drawing.Color]::Transparent)
$scale = [Math]::Min($size / $art.Width, $size / $art.Height)
$w = $art.Width * $scale; $h = $art.Height * $scale
$g.DrawImage($art, [single](($size - $w) / 2), [single](($size - $h) / 2), [single]$w, [single]$h)
$g.Dispose()
$removed = 0
for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $c = $store.GetPixel($x, $y)
        if ($c.A -gt 0 -and $c.A -lt 200 -and $c.R -gt 200 -and $c.G -gt 200 -and $c.B -gt 200) {
            $store.SetPixel($x, $y, [System.Drawing.Color]::Transparent); $removed++
        }
    }
}
$store.Save((Resolve-Path -LiteralPath (Split-Path $StoreOut)).Path + "\" + (Split-Path $StoreOut -Leaf), [System.Drawing.Imaging.ImageFormat]::Png)
$store.Dispose()
$art.Dispose()
Write-Host "store icon: removed $removed halo pixels"
