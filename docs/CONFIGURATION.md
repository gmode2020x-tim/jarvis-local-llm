# Configuration

Jarvis reads environment variables from the shell and then from a local `.env` file. Start by copying `.env.example` to `.env`.

## Runtime

| Variable | Default | Notes |
| --- | --- | --- |
| `JARVIS_BACKEND` | `llm_vm` | Supported values: `local`, `llm_vm`, `llm-vm`, `vm`. |
| `JARVIS_MODEL` | `llama3.2:3b` | Model name sent to the LLM server. |
| `JARVIS_USER_NAME` | `Operator` | Name Jarvis may use when clarity helps. Jarvis should still speak directly as `you`. |
| `JARVIS_TIME_ZONE` | `UTC` | IANA time zone for local status and memory timestamps, for example `America/Toronto`. |

Use `local` for deterministic installation checks. Use `llm_vm` for live model chat.

## LLM Server

| Variable | Default | Notes |
| --- | --- | --- |
| `JARVIS_LLM_VM_HOST` | `127.0.0.1` | Host used for ping, port checks, model discovery fallbacks, and SSH. |
| `JARVIS_LLM_VM_USER` | `jarvis` | SSH username for optional inspection. |
| `JARVIS_LLM_VM_BASE_URL` | `http://{JARVIS_LLM_VM_HOST}:11434/v1` | OpenAI-compatible chat-completions API base URL. |
| `JARVIS_LLM_VM_API_KEY` | `not-needed` | Bearer token for servers that require one. Ollama commonly ignores it. |
| `JARVIS_LLM_VM_TIMEOUT_SECONDS` | `180` | HTTP timeout for chat calls. CPU-only prompts can exceed 60 seconds when cold. |
| `JARVIS_LLM_VM_KEEP_ALIVE` | `30m` | Ollama keep-alive hint. |
| `JARVIS_LLM_VM_PASSWORD` | unset | Optional SSH password. Do not commit it. |
| `JARVIS_LLM_VM_KEY_PATH` | unset | Optional private key path for SSH inspection. |
| `JARVIS_LLM_VM_ALLOW_AGENT` | `1` | Enables SSH agent/default-key attempts. |

## Voice And Audio

The built-in voice mode is a local microphone verification loop, not a complete production STT/TTS stack.

| Variable | Default | Notes |
| --- | --- | --- |
| `JARVIS_RECORD_SECONDS` | `5` | Microphone recording duration for each local voice verification turn. |
| `JARVIS_SAMPLE_RATE` | `16000` | Microphone input sample rate. |

## Runtime Files

| Path | Purpose |
| --- | --- |
| `data/assistant_memory.json` | Durable facts and recent turns. |
| `data/assistant_notes.md` | Append-only notes. |
| `runs/assistant_audio/` | Temporary local voice input files. |

These paths are ignored by git.
