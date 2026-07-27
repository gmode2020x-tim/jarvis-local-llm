# Contributing

Keep changes small, verified, and safe for public release.

Before opening a pull request:

```powershell
$env:JARVIS_BACKEND = "local"
.\.venv\Scripts\python.exe .\scripts\verify_assistant.py
powershell -ExecutionPolicy Bypass -File .\scripts\check_public_release.ps1
```

Do not add private hostnames, tokens, recordings, model weights, runtime memory, or generated datasets.
