# Runs the Phase 1 PDF spike on a connected device and collects results.
# Usage (from android/):  .\spikes\pdf-renderer\run-spike.ps1 [-Serial <adb serial>] [-SkipBuild] [-Curl] [-Spread]
param(
    [string]$Serial = "",
    [switch]$SkipBuild,
    [switch]$Curl,
    [switch]$Spread,
    [string[]]$Engines = @("pdfium", "framework", "androidx", "quality", "onnx"),
    [string]$Label = "run"
)
$ErrorActionPreference = "Stop"
$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$adbArgs = @(); if ($Serial) { $adbArgs = @("-s", $Serial) }
function Adb { & $adb @adbArgs @args }

$pkg = "com.flowfolio.spike.pdf"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$book = Join-Path $root "samples\corpus\cache\university-physics-volume-1.pdf"
$model = (Adb shell getprop ro.product.model).Trim() -replace '[^A-Za-z0-9_-]', '_'
$out = Join-Path $PSScriptRoot "results\$model-$Label"
New-Item -ItemType Directory -Force $out | Out-Null

if (-not $SkipBuild) {
    Push-Location (Join-Path $root "android")
    .\gradlew.bat :spikes:pdf-renderer:assembleRelease -Pspikes --console=plain -q
    Pop-Location
}
Adb install -r (Join-Path $PSScriptRoot "build\outputs\apk\release\pdf-renderer-release.apk") | Out-Null
$remote = "/sdcard/Android/data/$pkg/files"
# Let the app create its own folders (adb-created folders are invisible to it), then push into them.
Adb shell rm -rf "$remote/models" | Out-Null
Adb shell am start -W -n "$pkg/.BenchActivity" --es engine init | Out-Null
Start-Sleep 2
Adb push $book "$remote/books/university-physics-volume-1.pdf" | Out-Null
$models = Join-Path $root "samples\models\all-MiniLM-L6-v2"
Get-ChildItem $models -Filter *.onnx | ForEach-Object { Adb push $_.FullName "$remote/models/$($_.Name)" | Out-Null }

function Fold-State { ((Adb shell dumpsys device_state | Select-String "mCommittedState" | Select-Object -First 1) -replace ".*name='([A-Z_]+)'.*", '$1').Trim() }
$startState = Fold-State
Write-Host "Fold state: $startState"
function Assert-Fold {
    $now = Fold-State
    if ($now -ne $startState) { throw "Fold state changed from $startState to $now; results would be mixed. Re-run." }
}

function Run-Bench([string]$engine) {
    Assert-Fold
    Write-Host "== $engine"
    Adb shell input keyevent KEYCODE_WAKEUP
    Adb shell am force-stop $pkg
    Adb logcat -c
    Adb shell am start -W -n "$pkg/.BenchActivity" --es engine $engine | Out-Null
    # Blocks until the activity logs SPIKE_DONE (or the 15-minute timeout trips).
    $job = Start-Job { param($a, $s) & $a @s logcat -s FFSpike:I -e SPIKE_DONE -m 1 } -ArgumentList $adb, $adbArgs
    if (-not (Wait-Job $job -Timeout 900)) { Stop-Job $job; throw "timeout waiting for $engine" }
    Receive-Job $job | Write-Host
    Adb logcat -d -s FFSpike:I FFPdfium:E AndroidRuntime:E | Select-Object -Last 40 | Set-Content (Join-Path $out "$engine.log")
}

foreach ($e in $Engines) { Run-Bench $e }
Adb pull "$remote/results/." $out | Out-Null

if ($Curl) {
    $size = (Adb shell wm size) -replace '.*: ', '' | Select-Object -Last 1
    $w, $h = $size.Trim().Split('x') | ForEach-Object { [int]$_ }
    foreach ($eng in "pdfium", "framework") {
        $spreadFlag = if ($Spread) { "true" } else { "false" }
        $tag = "curl-$eng" + $(if ($Spread) { "-spread" } else { "" })
        Write-Host "== $tag"
        Adb shell input keyevent KEYCODE_WAKEUP
        Adb shell am force-stop $pkg
        Adb shell am start -W -n "$pkg/.CurlActivity" --es engine $eng --ez spread $spreadFlag --ei start 100 | Out-Null
        Start-Sleep 4
        Adb shell dumpsys gfxinfo $pkg reset | Out-Null
        for ($i = 0; $i -lt 50; $i++) {
            # Drag from the right edge to the left over 350 ms: one page turn.
            Adb shell input swipe ([int]($w * 0.92)) ([int]($h * 0.5)) ([int]($w * 0.08)) ([int]($h * 0.55)) 350
            Start-Sleep -Milliseconds 250
        }
        Assert-Fold
        Adb shell dumpsys gfxinfo $pkg | Set-Content (Join-Path $out "$tag-gfxinfo.txt")
        Adb shell dumpsys meminfo $pkg | Set-Content (Join-Path $out "$tag-meminfo.txt")
    }
}
Write-Host "Results in $out"
