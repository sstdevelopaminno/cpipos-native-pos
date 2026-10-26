# CpIPOS Native POS 2.0: configure Firebase Android client locally.
# Keeps google-services.json out of the public GitHub repository.
[CmdletBinding()]
param(
    [string]$Source = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$target = Join-Path $repo "app\google-services.json"
$expectedPackage = "com.cpipos.pos.next.dev"

function Resolve-FirebaseConfig {
    param([string]$Requested)

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($Requested)) {
        $candidates += $Requested
    }
    $candidates += @(
        (Join-Path $repo "google-services.json"),
        (Join-Path $env:USERPROFILE "Downloads\google-services.json"),
        (Join-Path $env:USERPROFILE "Desktop\google-services.json")
    )

    foreach ($folder in @(
        (Join-Path $env:USERPROFILE "Downloads"),
        (Join-Path $env:USERPROFILE "Desktop")
    )) {
        if (Test-Path $folder) {
            $latest = Get-ChildItem -Path $folder -Filter "google-services*.json" -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 3
            foreach ($file in $latest) { $candidates += $file.FullName }
        }
    }

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        try {
            $resolved = (Resolve-Path $candidate -ErrorAction Stop).Path
            if (Test-Path $resolved -PathType Leaf) { return $resolved }
        } catch {}
    }
    throw "google-services.json not found. Download it from Firebase for package $expectedPackage, then place it in Downloads or run -Source <path>."
}

$source = Resolve-FirebaseConfig -Requested $Source
$config = Get-Content -Raw -Path $source | ConvertFrom-Json
$clients = @($config.client)
$matches = @(
    foreach ($client in $clients) {
        $packageName = [string]$client.client_info.android_client_info.package_name
        if ($packageName -eq $expectedPackage) { $client }
    }
)
if ($matches.Count -ne 1) {
    $found = @(
        foreach ($client in $clients) {
            [string]$client.client_info.android_client_info.package_name
        }
    ) -join ", "
    throw "Firebase config package mismatch. Expected '$expectedPackage'; found '$found'."
}
if ([string]$config.project_info.project_id -ne "cpipos") {
    throw "Firebase project mismatch. Expected project_id 'cpipos'."
}

Copy-Item -LiteralPath $source -Destination $target -Force
Write-Host "Firebase configured locally:" -ForegroundColor Green
Write-Host "  Source : $source"
Write-Host "  Target : $target"
Write-Host "  Project: $($config.project_info.project_id)"
Write-Host "  Package: $expectedPackage"
Write-Host "This file remains ignored by Git and is not uploaded to the public repository." -ForegroundColor Yellow
