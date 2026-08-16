# LLM Server Setup

Jarvis has two VM-side pieces:

- Ollama on port `11434`, used by the workstation backend and the VM UI.
- Jarvis LLM UI/API on port `8787`, used for dashboards, Home Assistant webhooks, benchmarks, and VM-side chat.

## Full Linux VM Install

Clone this repository on the VM, then run:

```bash
sudo JARVIS_MODEL=llama3.2:3b bash scripts/setup_jarvis_vm.sh
```

The script:

- installs Node 22 when Node 20+ is missing
- installs Ollama if missing
- configures `OLLAMA_HOST=0.0.0.0:11434`
- sets `OLLAMA_KEEP_ALIVE=30m`
- pulls the selected model
- copies the repo to `/opt/jarvis`
- creates `/opt/jarvis/vm/llm-ui/.env` if missing
- installs and starts `jarvis-llm-ui.service`
- exposes the dashboard/API on port `8787`

For an Ollama-only host without the Jarvis UI:

```bash
sudo JARVIS_MODEL=llama3.2:3b bash scripts/setup_ollama_linux.sh
```
- pulls the selected model

## Workstation Configuration

In `.env` on the Jarvis workstation:

```dotenv
JARVIS_BACKEND=llm_vm
JARVIS_LLM_VM_HOST=YOUR-LLM-HOST
JARVIS_LLM_VM_BASE_URL=http://YOUR-LLM-HOST:11434/v1
JARVIS_MODEL=llama3.2:3b
```

## VM UI Configuration

On the VM:

```bash
sudo nano /opt/jarvis/vm/llm-ui/.env
sudo systemctl restart jarvis-llm-ui
```

Useful values:

```dotenv
PORT=8787
OLLAMA_HOST=http://127.0.0.1:11434
DEFAULT_MODEL=llama3.2:3b
VOICE_MODEL=llama3.2:3b
DEEP_MODEL=llama3.1:8b
HOME_ASSISTANT_URL=http://homeassistant.local:8123
HOME_ASSISTANT_TOKEN=
HOME_ASSISTANT_WEBHOOK_SECRET=change-this-long-random-secret
JARVIS_CONVERSATION_ARCHIVE_PATH=conversation-archive.jsonl
```

## Conversation Archive

The VM service appends every API chat, Home Assistant Assist answer, deterministic response, failed model call, and deep-mode result to `conversation-archive.jsonl` in `DATA_DIR`. Existing prompt-review history and older Home Assistant text events are reconciled into the archive at startup without duplicating records. The file is append-only and has no automatic retention limit.

The dashboard reports the archive record count. Full text and analysis summaries are deliberately protected by the same secret used for the Home Assistant webhook:

```powershell
$headers = @{ Authorization = "Bearer $env:HOME_ASSISTANT_WEBHOOK_SECRET" }
Invoke-RestMethod -Headers $headers "http://YOUR-LLM-HOST:8787/api/conversations/summary?days=30"
Invoke-RestMethod -Headers $headers "http://YOUR-LLM-HOST:8787/api/conversations?source=assist&limit=100"
```

Query parameters are `limit` (maximum 1000), `source`, `route`, `q`, `from`, and `to`. Dates use ISO 8601. Because the archive contains full prompts and replies, keep port `8787` on a trusted network, never commit the data directory, and include `DATA_DIR` in private backups. A container or service restart does not remove history; loss of the underlying VM disk still will.

## Smoke Tests

From the workstation:

```powershell
.\.venv\Scripts\python.exe .\scripts\inspect_llm_vm.py
```

Direct API check:

```powershell
Invoke-RestMethod http://YOUR-LLM-HOST:11434/v1/models
Invoke-RestMethod http://YOUR-LLM-HOST:8787/api/health
Invoke-RestMethod http://YOUR-LLM-HOST:8787/api/models
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

Also keep port `8787` LAN-only unless you put it behind authentication and TLS.
