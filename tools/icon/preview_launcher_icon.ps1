# Renders how launchers will show the adaptive icon (circle, squircle, rounded square masks) for review.
# Usage (from repo root):  pwsh tools/icon/preview_launcher_icon.ps1 -Out preview.png
param([string]$Foreground = "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png", [string]$Out = "icon-preview.png")
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$fg = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Foreground))
$layer = $fg.Width                      # 432 px = 108 dp at xxxhdpi
$visible = [int]($layer * 72 / 108)     # the launcher shows the central 72 dp
$inset = ($layer - $visible) / 2
$gap = 40
$sheet = New-Object System.Drawing.Bitmap (3 * $visible + 4 * $gap), ($visible + 2 * $gap)
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.Clear([System.Drawing.Color]::FromArgb(255, 236, 239, 244))
$top = [System.Drawing.Color]::FromArgb(255, 0x15, 0x97, 0xE5); $bottom = [System.Drawing.Color]::FromArgb(255, 0x20, 0x22, 0x9A)
for ($i = 0; $i -lt 3; $i++) {
    $x = $gap + $i * ($visible + $gap); $y = $gap
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    switch ($i) {
        0 { $path.AddEllipse($x, $y, $visible, $visible) }
        default {
            $r = if ($i -eq 1) { $visible * 0.42 } else { $visible * 0.22 }   # squircle-ish, then rounded square
            $path.AddArc($x, $y, $r, $r, 180, 90); $path.AddArc($x + $visible - $r, $y, $r, $r, 270, 90)
            $path.AddArc($x + $visible - $r, $y + $visible - $r, $r, $r, 0, 90); $path.AddArc($x, $y + $visible - $r, $r, $r, 90, 90)
            $path.CloseFigure()
        }
    }
    $g.SetClip($path)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush ([System.Drawing.Rectangle]::new($x, $y, $visible, $visible)), $top, $bottom, 90
    $g.FillRectangle($brush, $x, $y, $visible, $visible)
    $g.DrawImage($fg, [System.Drawing.Rectangle]::new($x - $inset, $y - $inset, $layer, $layer))
    $g.ResetClip()
}
$g.Dispose(); $fg.Dispose()
$sheet.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png); $sheet.Dispose()
Write-Host "wrote $Out"
