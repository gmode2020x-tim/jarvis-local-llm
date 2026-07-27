from __future__ import annotations

import json
import platform
import subprocess
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

from .memory import MemoryStore
from .vm import LlmVmClient, compact_json


def tool_schemas() -> list[dict[str, Any]]:
    return [
        {
            "type": "function",
            "name": "get_local_status",
            "description": "Return local time, OS, current project path, and basic runtime status.",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
        {
            "type": "function",
            "name": "list_project_files",
            "description": "List top-level files and folders in the Jarvis workspace.",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
        {
            "type": "function",
            "name": "remember_fact",
            "description": "Store a durable user-approved fact or preference.",
            "parameters": {
                "type": "object",
                "properties": {
                    "key": {"type": "string", "description": "Short stable key, like preferred_name."},
                    "value": {"type": "string", "description": "The fact or preference to remember."},
                },
                "required": ["key", "value"],
                "additionalProperties": False,
            },
        },
        {
            "type": "function",
            "name": "save_note",
            "description": "Save a longer note to the assistant notes file.",
            "parameters": {
                "type": "object",
                "properties": {"note": {"type": "string"}},
                "required": ["note"],
                "additionalProperties": False,
            },
        },
        {
            "type": "function",
            "name": "ping_host",
            "description": "Ping a host and return a short connectivity summary.",
            "parameters": {
                "type": "object",
                "properties": {"host": {"type": "string", "description": "Hostname or IP address."}},
                "required": ["host"],
                "additionalProperties": False,
            },
        },
        {
            "type": "function",
            "name": "get_llm_vm_status",
            "description": "Check the configured local LLM VM host, model API ports, and SSH access status.",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
        {
            "type": "function",
            "name": "list_llm_vm_models",
            "description": "List models from the configured local LLM VM chat-completions or Ollama HTTP API.",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
    ]


class ToolRunner:
    def __init__(self, root: Path, memory: MemoryStore, vm_client: LlmVmClient | None = None) -> None:
        self.root = root
        self.memory = memory
        self.vm_client = vm_client
        self.handlers: dict[str, Callable[..., str]] = {
            "get_local_status": self.get_local_status,
            "list_project_files": self.list_project_files,
            "remember_fact": self.remember_fact,
            "save_note": self.save_note,
            "ping_host": self.ping_host,
            "get_llm_vm_status": self.get_llm_vm_status,
            "list_llm_vm_models": self.list_llm_vm_models,
        }

    def run(self, name: str, arguments: str) -> str:
        handler = self.handlers.get(name)
        if handler is None:
            return f"Unknown tool: {name}"
        try:
            parsed = json.loads(arguments or "{}")
            return handler(**parsed)
        except Exception as exc:  # Keep tool failures visible to the model.
            return f"Tool {name} failed: {exc}"

    def get_local_status(self) -> str:
        status = {
            "time": self.memory.now().isoformat(timespec="seconds"),
            "os": platform.platform(),
            "project": str(self.root),
        }
        return json.dumps(status)

    def list_project_files(self) -> str:
        items = []
        for path in sorted(self.root.iterdir(), key=lambda p: p.name.lower()):
            if path.name in {".agents", ".git", ".venv", ".env"} or path.name.startswith(".tmp") or path.name.startswith(".codex"):
                continue
            items.append({"name": path.name, "type": "dir" if path.is_dir() else "file"})
        return json.dumps(items)

    def remember_fact(self, key: str, value: str) -> str:
        return self.memory.remember_fact(key.strip(), value.strip())

    def save_note(self, note: str) -> str:
        return self.memory.append_note(note)

    def ping_host(self, host: str) -> str:
        command = ["ping", "-n", "4", host]
        result = subprocess.run(command, capture_output=True, text=True, timeout=15)
        output = (result.stdout or result.stderr).strip()
        return output[-2000:]

    def get_llm_vm_status(self) -> str:
        if self.vm_client is None:
            return "LLM VM client is not configured."
        return compact_json(self.vm_client.inspect())[-5000:]

    def list_llm_vm_models(self) -> str:
        if self.vm_client is None:
            return "LLM VM client is not configured."
        return compact_json(self.vm_client.list_http_models())[-5000:]
