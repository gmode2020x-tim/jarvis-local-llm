[CmdletBinding()]
param(
    [string]$HomeAssistantConfig = 'Z:\',
    [Parameter(Mandatory = $true)]
    [string]$ProxmoxHost,
    [int]$HomeAssistantVmId = 100,
    [switch]$SkipRestart
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repositoryRoot 'home-assistant\custom_components\gmode_trip_recorder'
$destination = Join-Path $HomeAssistantConfig 'custom_components\gmode_trip_recorder'
$liveComponent = Join-Path $destination '__init__.py'

if (-not (Test-Path -LiteralPath $source)) {
    throw "Versioned component not found: $source"
}
if (-not (Test-Path -LiteralPath $destination)) {
    throw "Live component directory not found: $destination"
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $destination "__init__.py.bak-mobile-$timestamp"
Copy-Item -LiteralPath $liveComponent -Destination $backup
Copy-Item -LiteralPath (Join-Path $source '__init__.py') -Destination $liveComponent
Copy-Item -LiteralPath (Join-Path $source 'manifest.json') -Destination (Join-Path $destination 'manifest.json')

$sourceHash = (Get-FileHash -LiteralPath (Join-Path $source '__init__.py') -Algorithm SHA256).Hash
$liveHash = (Get-FileHash -LiteralPath $liveComponent -Algorithm SHA256).Hash
if ($sourceHash -ne $liveHash) {
    throw 'Live component hash does not match the versioned source.'
}

$checkCommand = "qm guest exec $HomeAssistantVmId -- ha core check"
& ssh -o BatchMode=yes -o ConnectTimeout=8 "root@$ProxmoxHost" $checkCommand
if ($LASTEXITCODE -ne 0) {
    throw "Home Assistant configuration check failed. Restore from $backup before restarting."
}

if (-not $SkipRestart) {
    $restartCommand = "qm guest exec $HomeAssistantVmId -- ha core restart"
    & ssh -o BatchMode=yes -o ConnectTimeout=8 "root@$ProxmoxHost" $restartCommand
    if ($LASTEXITCODE -ne 0) {
        throw 'Home Assistant restart command failed.'
    }
}

Write-Host "Deployed GMODE Trip Recorder. Backup: $backup"
