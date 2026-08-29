[CmdletBinding()]
param(
    [string]$HomeAssistantConfig = 'Z:\',
    [Parameter(Mandatory)]
    [ValidatePattern('^https?://')]
    [string]$MapServiceUrl
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot 'home-assistant\www\gmode_trip_recorder'
$targetRoot = Join-Path $HomeAssistantConfig 'www\gmode_trip_recorder'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

if (-not (Test-Path -LiteralPath $targetRoot -PathType Container)) {
    throw "Home Assistant map directory not found: $targetRoot"
}

$assets = @(
    'local-basemap.js',
    'vendor\protomaps-leaflet.js',
    'vendor\protomaps-leaflet.LICENSE'
)

foreach ($asset in $assets) {
    $source = Join-Path $sourceRoot $asset
    $target = Join-Path $targetRoot $asset
    $targetParent = Split-Path -Parent $target
    New-Item -ItemType Directory -Force -Path $targetParent | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -Force
}

$localBasemapPath = Join-Path $targetRoot 'local-basemap.js'
$localBasemap = [IO.File]::ReadAllText($localBasemapPath)
$localBasemap = $localBasemap.Replace(
    'http://OSRM_VM_IP:8080/maps/ontario.pmtiles',
    $MapServiceUrl
)
[IO.File]::WriteAllText($localBasemapPath, $localBasemap, [Text.UTF8Encoding]::new($false))

$pages = @(
    'locations_20260717.html',
    'trips_ha_api_20260717.html'
)

$scriptTags = @'
  <script src="/local/gmode_trip_recorder/vendor/protomaps-leaflet.js"></script>
  <script src="/local/gmode_trip_recorder/local-basemap.js"></script>
'@

$tilePattern = 'L\.tileLayer\("https://\{s\}\.basemaps\.cartocdn\.com/dark_all/\{z\}/\{x\}/\{y\}\{r\}\.png",\s*\{[\s\S]*?\}\)\.addTo\((?<target>\w+)\);'

foreach ($page in $pages) {
    $path = Join-Path $targetRoot $page
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Map page not found: $path"
    }

    Copy-Item -LiteralPath $path -Destination "$path.bak-local-pmtiles-$timestamp" -Force
    $content = [IO.File]::ReadAllText($path)

    if ($content -notmatch 'protomaps-leaflet\.js') {
        $leafletTag = '  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>'
        if (-not $content.Contains($leafletTag)) {
            throw "Leaflet script tag not found in $page"
        }
        $content = $content.Replace($leafletTag, "$leafletTag`r`n$scriptTags")
    }

    $replacementCount = [ref]0
    $content = [regex]::Replace(
        $content,
        $tilePattern,
        {
            param($match)
            $replacementCount.Value++
            "GMODE_MAPS.addLocalBasemap($($match.Groups['target'].Value));"
        }
    )

    if ($content -match 'basemaps\.cartocdn\.com') {
        throw "A CARTO tile reference remains in $page"
    }
    if ($content -notmatch 'GMODE_MAPS\.addLocalBasemap') {
        throw "No map layers were converted in $page"
    }

    [IO.File]::WriteAllText($path, $content, [Text.UTF8Encoding]::new($false))
    Write-Output "$page converted to local PMTiles ($($replacementCount.Value) map layer(s)); backup: $path.bak-local-pmtiles-$timestamp"
}
