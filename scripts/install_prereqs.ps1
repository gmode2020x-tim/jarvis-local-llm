param(
    [string]$Python = "python"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Venv = Join-Path $Root ".venv"

Write-Host "Creating Python environment in $Venv"
& $Python -m venv $Venv

$Pip = Join-Path $Venv "Scripts\pip.exe"
$Py = Join-Path $Venv "Scripts\python.exe"

& $Py -m pip install --upgrade pip
& $Pip install -r (Join-Path $Root "requirements.txt")

$FfmpegCommand = Get-Command ffmpeg -ErrorAction SilentlyContinue
$WingetFfmpeg = Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages") -Recurse -Filter ffmpeg.exe -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $FfmpegCommand -and -not $WingetFfmpeg) {
    Write-Warning "FFmpeg was not found on PATH."
    Write-Host "Install it with: winget install --id Gyan.FFmpeg -e"
    Write-Host "Then reopen PowerShell and rerun your dataset command."
} elseif ($FfmpegCommand) {
    ffmpeg -version | Select-Object -First 1
} else {
    Write-Host "FFmpeg found at $($WingetFfmpeg.FullName)"
}

Write-Host "Ready. Use .\.venv\Scripts\python.exe to run the scripts."
