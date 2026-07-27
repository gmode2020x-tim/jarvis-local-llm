# Local LLM Host

Jarvis talks to any OpenAI-compatible chat-completions server. The quickest path is Ollama on the same computer or on a small LAN machine.

See [LLM_SERVER_SETUP.md](LLM_SERVER_SETUP.md) for installation and [TUNING.md](TUNING.md) for model and latency settings.

## Verify Access

```powershell
.\.venv\Scripts\python.exe .\scripts\inspect_llm_vm.py
```

The inspection checks:

- ping to `JARVIS_LLM_VM_HOST`
- SSH port `22`
- common model API ports: `11434`, `1234`, `8000`, `8080`, `5000`, `7860`
- `/v1/models` endpoints and Ollama `/api/tags`
- optional SSH access as `JARVIS_LLM_VM_USER`

## Run Jarvis Against The Host

```powershell
$env:JARVIS_BACKEND = "llm_vm"
$env:JARVIS_MODEL = "llama3.2:3b"
$env:JARVIS_LLM_VM_BASE_URL = "http://YOUR-LLM-HOST:11434/v1"
.\.venv\Scripts\python.exe .\scripts\run_assistant.py
```

Start the browser UI with the same environment:

```powershell
.\.venv\Scripts\python.exe .\scripts\run_app.py
```

Open `http://127.0.0.1:8765`.
