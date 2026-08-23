param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME to JDK 17 or pass -JavaHome.' }

$keytool = Join-Path $JavaHome 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw "keytool not found: $keytool" }

$keyDirectory = Join-Path $ProjectRoot 'keystore'
$keyPath = Join-Path $keyDirectory 'gmode-upload.jks'
$propertiesPath = Join-Path $ProjectRoot 'keystore.properties'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw 'Upload key or keystore.properties already exists. Nothing was overwritten.'
}

New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
$random = [byte[]]::new(36)
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $generator.GetBytes($random) } finally { $generator.Dispose() }
$password = [Convert]::ToBase64String($random).TrimEnd('=').Replace('+', '-').Replace('/', '_')

$arguments = @(
    '-genkeypair',
    '-v',
    '-keystore', $keyPath,
    '-storepass', $password,
    '-keypass', $password,
    '-alias', 'gmode-upload',
    '-keyalg', 'RSA',
    '-keysize', '4096',
    '-validity', '10000',
    '-dname', 'CN=GMODE Trip Recorder, OU=GMODE, O=GMODE, L=Ontario, ST=Ontario, C=CA'
)
& $keytool @arguments
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

$properties = @(
    'storeFile=keystore/gmode-upload.jks',
    "storePassword=$password",
    'keyAlias=gmode-upload',
    "keyPassword=$password"
)
[System.IO.File]::WriteAllLines($propertiesPath, $properties, [System.Text.UTF8Encoding]::new($false))

Write-Host "Created ignored upload key: $keyPath"
Write-Host "Created ignored signing properties: $propertiesPath"
Write-Host 'Back up both files together in a secure password manager or encrypted offline archive.'
