# Quickstart

## 1. Install Python Dependencies

```powershell
Copy-Item .env.example .env
.\scripts\install_prereqs.ps1
```

If FFmpeg is missing:

```powershell
winget install --id Gyan.FFmpeg -e
```

Reopen PowerShell after installing FFmpeg.

## 2. Verify Without A Model Server

```powershell
$env:JARVIS_BACKEND = "local"
.\.venv\Scripts\python.exe .\scripts\verify_assistant.py
```

This verifies imports, tool execution, memory writes, project listing, localhost ping, and missing-calendar behavior.

## 3. Start The Console

```powershell
$env:JARVIS_BACKEND = "local"
.\.venv\Scripts\python.exe .\scripts\run_app.py
```

Open `http://127.0.0.1:8765`.

## 4. Connect Ollama

Install Ollama on a Linux host:

```bash
sudo JARVIS_MODEL=llama3.2:3b bash scripts/setup_ollama_linux.sh
```

Update `.env`:

```dotenv
JARVIS_BACKEND=llm_vm
JARVIS_LLM_VM_HOST=YOUR-LLM-HOST
JARVIS_LLM_VM_BASE_URL=http://YOUR-LLM-HOST:11434/v1
JARVIS_MODEL=llama3.2:3b
```

Verify:

```powershell
.\.venv\Scripts\python.exe .\scripts\inspect_llm_vm.py
```
