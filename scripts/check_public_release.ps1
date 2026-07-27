param(
    [switch]$FailOnPrivateLan = $true
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Patterns = @(
    "sk-[A-Za-z0-9_-]{20,}",
    "ghp_[A-Za-z0-9_]{20,}",
    "github_pat_[A-Za-z0-9_]{20,}",
    "xox[baprs]-[A-Za-z0-9-]{20,}",
    "-----BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY-----"
)

if ($FailOnPrivateLan) {
    $Patterns += "192\.168\.50\.56"
    $Patterns += "192\.168\.50\.4"
}

$GitFiles = & git -C $Root ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) {
    throw "git ls-files failed."
}

$Files = $GitFiles |
    Where-Object { $_ -and $_ -ne "scripts/check_public_release.ps1" } |
    ForEach-Object { Get-Item -LiteralPath (Join-Path $Root $_) } |
    Where-Object { -not $_.PSIsContainer }

$Hits = foreach ($Pattern in $Patterns) {
    $Files | Select-String -Pattern $Pattern -ErrorAction SilentlyContinue |
        Select-Object Path, LineNumber, Line
}

if ($Hits) {
    $Hits | Format-Table -AutoSize
    throw "Public release check found private or secret-looking content."
}

Write-Host "Public release check passed."
