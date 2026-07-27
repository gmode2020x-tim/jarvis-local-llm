param(
    [string]$RepoName = "jarvis-local-llm",
    [string]$Description = "Self-hosted Jarvis assistant package for a local LLM workstation and Ollama host.",
    [ValidateSet("public", "private")]
    [string]$Visibility = "public"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Find-Gh {
    $FromPath = Get-Command gh -ErrorAction SilentlyContinue
    if ($FromPath) {
        return $FromPath.Source
    }
    $Portable = Get-ChildItem -Path (Join-Path $Root ".tools") -Recurse -Filter gh.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    if ($Portable) {
        return $Portable
    }
    throw "GitHub CLI was not found. Install it or place portable gh.exe under .tools."
}

$Gh = Find-Gh
& $Gh auth status
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run: `"$Gh`" auth login --hostname github.com --git-protocol https --web"
}

& powershell -ExecutionPolicy Bypass -File (Join-Path $Root "scripts/check_public_release.ps1")
& git -C $Root status --short --branch

$Remote = (& git -C $Root remote get-url origin 2>$null)
if ($LASTEXITCODE -eq 0 -and $Remote) {
    Write-Host "origin already exists: $Remote"
    & git -C $Root push -u origin main
    exit $LASTEXITCODE
}

$VisibilityFlag = if ($Visibility -eq "public") { "--public" } else { "--private" }
& $Gh repo create $RepoName $VisibilityFlag --source $Root --remote origin --push --description $Description
