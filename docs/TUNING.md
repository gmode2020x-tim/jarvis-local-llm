# Tuning

## Model Choice

Start small, then increase size only if the hardware can keep up.

| Hardware | First model | Notes |
| --- | --- | --- |
| CPU-only mini PC or small VM | `llama3.2:1b` | Fastest responses, weaker reasoning. |
| Modern CPU with enough RAM | `llama3.2:3b` | Good default for a responsive assistant. |
| GPU or high-memory host | `llama3.1:8b` or larger | Better answers, slower cold starts. |

Pull a model:

```bash
ollama pull llama3.2:3b
```

Set it:

```dotenv
JARVIS_MODEL=llama3.2:3b
```

## Latency

Useful settings:

```dotenv
JARVIS_LLM_VM_TIMEOUT_SECONDS=180
JARVIS_LLM_VM_KEEP_ALIVE=30m
```

Tradeoffs:

- Longer keep-alive improves repeat latency but keeps RAM in use.
- Smaller models improve voice-style responsiveness.
- CPU-only hosts benefit from shorter prompts and fewer tool calls.

## Prompt Behavior

The system prompt in `assistant/brain.py` is intentionally concise. Tune it for:

- direct address
- occasional natural use of `JARVIS_USER_NAME` instead of repetitive names or honorifics
- conversational contractions and dry wit only when it fits
- no generic greetings
- confirmation before risky actions
- no claims about tools or devices that are not connected

Keep humor subordinate to facts. Home Assistant state, safety warnings, failed actions, and unavailable capabilities must remain literal. Do not use a global finalizer that appends the same joke to unrelated answers; a plain one-sentence fact is more natural than a mandatory sarcastic aside.

After changing behavior, run:

```powershell
$env:JARVIS_BACKEND = "local"
.\.venv\Scripts\python.exe .\scripts\verify_assistant.py
```

Add verifier checks before relying on prompt behavior in a live model.
