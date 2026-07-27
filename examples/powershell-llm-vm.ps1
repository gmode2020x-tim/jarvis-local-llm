$ErrorActionPreference = "Stop"
$env:JARVIS_BACKEND = "llm_vm"
$env:JARVIS_LLM_VM_HOST = "YOUR-LLM-HOST"
$env:JARVIS_LLM_VM_BASE_URL = "http://YOUR-LLM-HOST:11434/v1"
$env:JARVIS_LLM_VM_USER = "jarvis"
$env:JARVIS_MODEL = "llama3.2:3b"
.\.venv\Scripts\python.exe .\scripts\inspect_llm_vm.py
.\.venv\Scripts\python.exe .\scripts\run_assistant.py
