# Runs the Phase 2 EPUB pagination spike on a connected device and collects results.
# Usage (from android/):  .\spikes\epub-pagination\run-spike.ps1 [-Serial <adb serial>] [-SkipBuild]
#                         [-Engines robust,native,hybrid,web] [-Curl native,web] [-Turns 60]
param(
    [string]$Serial = "",
    [switch]$SkipBuild,
    [string[]]$Engines = @("robust", "native", "hybrid", "web"),
    [string[]]$Curl = @(),
    [string]$CurlBook = "synthetic-textbook.epub",
    [int]$Turns = 60
)
$ErrorActionPreference = "Stop"
$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$adbArgs = @(); if ($Serial) { $adbArgs = @("-s", $Serial) }
function Adb { & $adb @adbArgs @args }

$pkg = "com.flowfolio.spike.epub"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
Adb shell input keyevent KEYCODE_WAKEUP
Start-Sleep 1
if ((Adb shell dumpsys window | Select-String "isKeyguardShowing=true")) {
    throw "The phone is locked. Unlock it (and keep it unlocked) before running the spike."
}
$model = (Adb shell getprop ro.product.model).Trim() -replace '[^A-Za-z0-9_-]', '_'
$out = Join-Path $PSScriptRoot "results\$model"
New-Item -ItemType Directory -Force $out | Out-Null

if (-not $SkipBuild) {
    Push-Location (Join-Path $root "android")
    .\gradlew.bat :spikes:epub-pagination:assembleRelease -Pspikes --console=plain -q
    Pop-Location
}
Adb install -r (Join-Path $PSScriptRoot "build\outputs\apk\release\epub-pagination-release.apk") | Out-Null
$remote = "/sdcard/Android/data/$pkg/files"
# Let the app create its own folders (adb-created folders are invisible to it), then push into them.
Adb shell am start -W -n "$pkg/.BenchActivity" --es engine init | Out-Null
Start-Sleep 3
foreach ($dir in "epub-idpf", "epub-generated") {
    Get-ChildItem (Join-Path $root "samples\corpus\cache\$dir") -Filter *.epub | ForEach-Object {
        Adb push $_.FullName "$remote/books/$($_.Name)" | Out-Null
    }
}

function Front {
    $top = (Adb shell dumpsys activity activities | Select-String "topResumedActivity" | Select-Object -First 1).Line
    if ($top -notmatch [regex]::Escape("$pkg/")) { throw "The spike app is not in front; stopping (no input sent)." }
}

foreach ($engine in $Engines) {
    Write-Host "== $engine"
    Adb shell am force-stop $pkg
    Adb logcat -c
    Adb shell am start -W -n "$pkg/.BenchActivity" --es engine $engine | Out-Null
    $deadline = (Get-Date).AddMinutes(30)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep 5
        Adb shell input keyevent 59 | Out-Null # keeps the screen on (a harmless shift key)
        if ((Adb logcat -d -s EpubSpike:I) -match "SPIKE_DONE $engine") { break }
    }
    Adb pull "$remote/results/$engine.json" (Join-Path $out "$engine.json") | Out-Null
}
Adb shell "ls $remote/results/*.png" 2>$null | ForEach-Object { Adb pull $_.Trim() $out | Out-Null }

foreach ($engine in $Curl) {
    Write-Host "== curl $engine"
    Adb shell am force-stop $pkg
    Adb logcat -c
    Adb shell am start -W -n "$pkg/.CurlActivity" --es engine $engine --es book $CurlBook | Out-Null
    $deadline = (Get-Date).AddMinutes(10)
    while ((Get-Date) -lt $deadline -and -not ((Adb logcat -d -s EpubSpike:I) -match "CURL_READY")) { Start-Sleep 2 }
    Start-Sleep 3
    $size = (Adb shell wm size -d 0) -replace '.*: ', ''
    $w, $h = $size.Trim().Split('x') | ForEach-Object { [int]$_ }
    Adb shell dumpsys gfxinfo $pkg reset | Out-Null
    for ($i = 1; $i -le $Turns; $i++) {
        if ($i % 10 -eq 1) { Front; Adb shell input keyevent 59 | Out-Null }
        Adb shell input swipe ([int]($w * 0.9)) ([int]($h * 0.6)) ([int]($w * 0.55)) ([int]($h * 0.58)) 300
        Start-Sleep -Milliseconds 300
    }
    Adb shell dumpsys gfxinfo $pkg | Set-Content (Join-Path $out "curl-$engine-gfxinfo.txt")
    Adb shell dumpsys meminfo $pkg | Set-Content (Join-Path $out "curl-$engine-meminfo.txt")
    Select-String -Path (Join-Path $out "curl-$engine-gfxinfo.txt") -Pattern "Total frames|Janky frames:|50th|95th|99th" | Select-Object -First 5 | ForEach-Object { $_.Line.Trim() }
}
Adb shell am force-stop $pkg
Write-Host "Results: $out"
