# LLM Server Setup

Jarvis expects an OpenAI-compatible chat-completions API. Ollama is the default supported path because it exposes `http://HOST:11434/v1/chat/completions`.

## Linux Ollama Host

Copy this repo to the Linux host or copy only `scripts/setup_ollama_linux.sh`, then run:

```bash
sudo JARVIS_MODEL=llama3.2:3b bash scripts/setup_ollama_linux.sh
```

The script:

- installs Ollama if missing
- configures `OLLAMA_HOST=0.0.0.0:11434`
- sets `OLLAMA_KEEP_ALIVE=30m`
- enables and restarts the service
- pulls the selected model

## Workstation Configuration

In `.env` on the Jarvis workstation:

```dotenv
JARVIS_BACKEND=llm_vm
JARVIS_LLM_VM_HOST=YOUR-LLM-HOST
JARVIS_LLM_VM_BASE_URL=http://YOUR-LLM-HOST:11434/v1
JARVIS_MODEL=llama3.2:3b
```

## Smoke Tests

From the workstation:

```powershell
.\.venv\Scripts\python.exe .\scripts\inspect_llm_vm.py
```

Direct API check:

```powershell
Invoke-RestMethod http://YOUR-LLM-HOST:11434/v1/models
```

Terminal chat:

```powershell
.\.venv\Scripts\python.exe .\scripts\run_assistant.py
```

## Firewall

Only expose Ollama to networks you trust. For a LAN-only Linux host with UFW:

```bash
sudo ufw allow from YOUR-LAN-CIDR to any port 11434 proto tcp
```

Avoid exposing port `11434` directly to the public internet.
