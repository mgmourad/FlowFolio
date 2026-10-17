# Phase 1 milestone 3 measurement: frame timing and memory while turning pages in the reader.
# Usage (from android/):  .\tools\measure-reader.ps1 -Serial <adb serial> -Layout spread|single [-Turns 300] [-Label name]
# Needs the debug app installed with the test textbook imported (see CLAUDE.md). Results: tools/results/<label>/.
param(
    [string]$Serial = "",
    [ValidateSet("single", "spread")][string]$Layout = "single",
    [int]$Turns = 300,
    [string]$Label = "",
    [string]$BookId = "93652b69c43197d10dc83438b813b5f7515e61e7ea4336da8636ff902cb4aba8"
)
$ErrorActionPreference = "Stop"
$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$adbArgs = @(); if ($Serial) { $adbArgs = @("-s", $Serial) }
function Adb { & $adb @adbArgs @args }
$pkg = "com.flowfolio.dev"

$fold = ((Adb shell dumpsys device_state | Select-String "mCommittedState" | Select-Object -First 1) -replace ".*name='([A-Z_]+)'.*", '$1').Trim()
if (-not $Label) { $Label = "$($fold.ToLower())-$Layout" }
$out = Join-Path $PSScriptRoot "results\$Label"
New-Item -ItemType Directory -Force $out | Out-Null

# The app's current window size (rotation-aware). Logical display 0 is always the active panel on the Fold:
# the cover screen when folded, the inner screen when unfolded.
$displayId = 0
$windows = (Adb shell dumpsys window displays) -join "`n"
$block = ($windows -split "Display: mDisplayId=") | Where-Object { $_ -match "^$displayId\b" } | Select-Object -First 1
if ($block -notmatch "cur=(\d+)x(\d+)") { throw "could not read the size of display $displayId" }
$w = [int]$Matches[1]; $h = [int]$Matches[2]
Write-Host "Fold=$fold display=$displayId ${w}x$h layout=$Layout turns=$Turns"

function Memory([string]$tag) {
    $mem = Adb shell dumpsys meminfo $pkg
    $mem | Set-Content (Join-Path $out "meminfo-$tag.txt")
    $total = ($mem | Select-String "TOTAL PSS:\s+(\d+)").Matches[0].Groups[1].Value
    $native = ($mem | Select-String "^\s+Native Heap:\s+(\d+)").Matches[0].Groups[1].Value
    $graphics = ($mem | Select-String "^\s+Graphics:\s+(\d+)").Matches[0].Groups[1].Value
    [pscustomobject]@{ at = $tag; totalPssMB = [math]::Round($total / 1024, 1); nativeHeapMB = [math]::Round($native / 1024, 1); graphicsMB = [math]::Round($graphics / 1024, 1) }
}

Adb shell input keyevent KEYCODE_WAKEUP
Start-Sleep 1
if ((Adb shell dumpsys window | Select-String "isKeyguardShowing=true")) {
    throw "The phone is locked. Unlock it (and keep it unlocked) before measuring; results would be empty."
}
Adb shell am force-stop $pkg
Adb shell am start -W -n "$pkg/com.flowfolio.MainActivity" --es openBookId $BookId --es layout $Layout | Out-Null
Start-Sleep 4
Adb shell dumpsys gfxinfo $pkg reset | Out-Null

# Forward page turn: drag from inside the right page leftwards (never from the screen edge: that is the back gesture).
$x1 = [int]($w * 0.85); $x2 = [int]($w * $(if ($Layout -eq "spread") { 0.45 } else { 0.2 })); $y1 = [int]($h * 0.6); $y2 = [int]($h * 0.55)
$samples = @()
for ($i = 1; $i -le $Turns; $i++) {
    Adb shell input swipe $x1 $y1 $x2 $y2 300
    Start-Sleep -Milliseconds 250
    if ($i -eq 50) { $samples += Memory "turn50" }
}
$samples += Memory "turn$Turns"
if ((Adb shell dumpsys window | Select-String "isKeyguardShowing=true")) {
    throw "The phone locked during the run; results are not valid. Unlock and run again."
}
$gfx = Adb shell dumpsys gfxinfo $pkg
$gfx | Set-Content (Join-Path $out "gfxinfo.txt")
$pick = { param($p) ($gfx | Select-String $p | Select-Object -First 1).ToString().Trim() }
$summary = [pscustomobject]@{
    label = $Label; fold = $fold; window = "${w}x$h"; layout = $Layout; turns = $Turns
    frames = & $pick "Total frames rendered"; janky = & $pick "Janky frames:"; p50 = & $pick "^50th percentile"
    p95 = & $pick "^95th percentile"; p99 = & $pick "^99th percentile"
    memory = $samples
}
$summary | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $out "summary.json")
$summary | ConvertTo-Json -Depth 4
