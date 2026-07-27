$ErrorActionPreference = "Stop"
$env:JARVIS_BACKEND = "local"
$env:JARVIS_USER_NAME = "Operator"
$env:JARVIS_TIME_ZONE = "UTC"
.\.venv\Scripts\python.exe .\scripts\verify_assistant.py
.\.venv\Scripts\python.exe .\scripts\run_app.py
