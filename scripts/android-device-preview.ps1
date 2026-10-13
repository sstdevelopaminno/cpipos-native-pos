# CpIPOS Native POS 2.0: install the locally built DEBUG preview on a USB-connected Android.
# No Vercel, no Supabase mutations, no production Android 1.0.23 replacement.
# Usage (VS Code PowerShell): .\scripts\android-device-preview.ps1 -Mirror
# For multiple phones: .\scripts\android-device-preview.ps1 -DeviceSerial "<adb serial>" -Mirror
[CmdletBinding()]
param(
    [string]$DeviceSerial = "",
    [switch]$SkipBuild,
    [switch]$ListOnly,
    [switch]$Mirror,
    [switch]$NoLaunch
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
$wrapper = Join-Path $repo "gradlew.bat"
$appId = "com.cpipos.pos.next.dev"

function Find-Adb {
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"))) {
        if (-not [string]::IsNullOrWhiteSpace($sdk)) {
            $candidate = Join-Path $sdk "platform-tools\adb.exe"
            if (Test-Path $candidate) { return $candidate }
        }
    }
    throw "adb.exe not found. Install Android SDK Platform-Tools or set ANDROID_HOME, then reopen VS Code."
}

$adb = Find-Adb
Write-Host "ADB: $adb" -ForegroundColor Cyan
& $adb start-server | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Unable to start ADB." }
$lines = @(& $adb devices -l)
if ($LASTEXITCODE -ne 0) { throw "adb devices failed." }
Write-Host ($lines -join [Environment]::NewLine)
$connected = @(
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
    }
)
if ($ListOnly) { return }
if ($connected.Count -eq 0) {
    throw "No authorized Android device. Unlock phone, enable USB debugging, use a data-capable cable, and accept the Allow USB debugging prompt."
}
if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($connected.Count -ne 1) {
        throw "More than one Android device. Supply -DeviceSerial from adb devices -l."
    }
    $DeviceSerial = $connected[0]
}
if ($connected -notcontains $DeviceSerial) {
    throw "Device '$DeviceSerial' is not connected/authorized. Re-run with -ListOnly."
}
Write-Host "Selected Android device: $DeviceSerial" -ForegroundColor Green

if (-not $SkipBuild) {
    if (-not (Test-Path $wrapper)) { throw "Missing gradlew.bat; open the feature/native-auth-phase1 repository." }
    Write-Host "Building local debug APK (JDK 17 + Android SDK required)..." -ForegroundColor Cyan
    Push-Location $repo
    try {
        & $wrapper --no-daemon :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed. Check JAVA_HOME (JDK 17) and Android SDK platform 37.0." }
    }
    finally { Pop-Location }
}
if (-not (Test-Path $apk)) { throw "Debug APK not found: $apk" }
Write-Host "Installing development app $appId (does not replace com.cpipos.pos)..." -ForegroundColor Cyan
& $adb -s $DeviceSerial install -r -t $apk
if ($LASTEXITCODE -ne 0) {
    throw "APK install failed. Check USB authorization, phone storage, and previous dev-app signing key. Do NOT uninstall production com.cpipos.pos."
}
if (-not $NoLaunch) {
    & $adb -s $DeviceSerial shell monkey -p $appId -c android.intent.category.LAUNCHER 1
    if ($LASTEXITCODE -ne 0) { throw "Installed, but automatic launch failed; open CpIPOS Native dev from the phone." }
}
if ($Mirror) {
    $scrcpy = Get-Command scrcpy.exe -ErrorAction SilentlyContinue
    if ($null -ne $scrcpy) {
        & $scrcpy.Source -s $DeviceSerial
    }
    else {
        Write-Warning "Installed/launched. scrcpy.exe not found in PATH; install scrcpy or run scrcpy -s <serial> separately."
    }
}
Write-Host "Android preview procedure completed for $DeviceSerial." -ForegroundColor Green
