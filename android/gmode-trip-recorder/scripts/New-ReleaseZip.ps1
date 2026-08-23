param(
    [Parameter(Mandatory = $false)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version = '2.0.0'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$releaseDirectory = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'releases'))
$archiveName = "GMODE-Trip-Recorder-v$Version-install.zip"
$archivePath = [System.IO.Path]::GetFullPath((Join-Path $releaseDirectory $archiveName))
$packageRoot = "GMODE-Trip-Recorder-v$Version"
$apkName = "GMODE-Trip-Recorder-v$Version-sideload.apk"
$apkPath = Join-Path $releaseDirectory $apkName

if (-not $archivePath.StartsWith($releaseDirectory + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to write outside the release directory: $archivePath"
}

$packageFiles = @(
    @{ Source = $apkPath; Entry = "$packageRoot/$apkName" },
    @{ Source = (Join-Path $projectRoot 'README.md'); Entry = "$packageRoot/README.md" },
    @{ Source = (Join-Path $projectRoot 'PRIVACY_POLICY.md'); Entry = "$packageRoot/PRIVACY_POLICY.md" },
    @{ Source = (Join-Path $projectRoot 'docs\USER_GUIDE.md'); Entry = "$packageRoot/docs/USER_GUIDE.md" },
    @{ Source = (Join-Path $projectRoot 'docs\HOME_ASSISTANT_SETUP.md'); Entry = "$packageRoot/docs/HOME_ASSISTANT_SETUP.md" },
    @{ Source = (Join-Path $projectRoot 'play-store\screenshots\01-attitude-dashboard.png'); Entry = "$packageRoot/play-store/screenshots/01-attitude-dashboard.png" },
    @{ Source = (Join-Path $projectRoot 'play-store\screenshots\02-limit-warning.png'); Entry = "$packageRoot/play-store/screenshots/02-limit-warning.png" },
    @{ Source = (Join-Path $projectRoot 'play-store\screenshots\03-speed-street.png'); Entry = "$packageRoot/play-store/screenshots/03-speed-street.png" },
    @{ Source = (Join-Path $projectRoot 'play-store\screenshots\04-water-course.png'); Entry = "$packageRoot/play-store/screenshots/04-water-course.png" },
    @{ Source = (Join-Path $projectRoot 'screenshots\GMODE-v1.10.0-hybrid-home-settings.png'); Entry = "$packageRoot/screenshots/GMODE-v1.10.0-hybrid-home-settings.png" },
    @{ Source = (Join-Path $projectRoot 'screenshots\GMODE-v1.7.0-side-button-settings.png'); Entry = "$packageRoot/screenshots/GMODE-v1.7.0-side-button-settings.png" }
)

foreach ($file in $packageFiles) {
    if (-not (Test-Path -LiteralPath $file.Source -PathType Leaf)) {
        throw "Required package file is missing: $($file.Source)"
    }
}

if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}

$apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$archive = [System.IO.Compression.ZipFile]::Open($archivePath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $packageFiles) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $file.Source,
            $file.Entry,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }

    $checksumEntry = $archive.CreateEntry("$packageRoot/GMODE-Trip-Recorder-v$Version-APK-SHA256.txt")
    $writer = [System.IO.StreamWriter]::new($checksumEntry.Open())
    try {
        $writer.WriteLine("$apkHash  $apkName")
    }
    finally {
        $writer.Dispose()
    }
}
finally {
    $archive.Dispose()
}

$readArchive = [System.IO.Compression.ZipFile]::OpenRead($archivePath)
try {
    $apkEntry = $readArchive.GetEntry("$packageRoot/$apkName")
    if ($null -eq $apkEntry) {
        throw 'The finished ZIP does not contain the APK.'
    }

    $apkStream = $apkEntry.Open()
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $embeddedHash = ([BitConverter]::ToString($sha256.ComputeHash($apkStream))).Replace('-', '').ToLowerInvariant()
        }
        finally {
            $sha256.Dispose()
        }
    }
    finally {
        $apkStream.Dispose()
    }

    if ($embeddedHash -ne $apkHash) {
        throw 'The APK inside the finished ZIP does not match the release APK.'
    }

    $entryCount = $readArchive.Entries.Count
}
finally {
    $readArchive.Dispose()
}

[pscustomobject]@{
    Archive = $archivePath
    Bytes = (Get-Item -LiteralPath $archivePath).Length
    Entries = $entryCount
    SHA256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    EmbeddedApkVerified = $true
}
