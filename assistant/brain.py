from __future__ import annotations

from pathlib import Path
import re

import requests

from .config import AssistantConfig
from .memory import MemoryStore
from .tools import ToolRunner, tool_schemas
from .vm import LlmVmClient


SYSTEM_PROMPT = """
You are Jarvis, an original local systems assistant inspired by cinematic command-center assistants.
You are concise, calm, technically precise, and useful.

Operating rules:
- Prefer brief spoken answers unless the user asks for detail.
- Address the current user directly as "you"; do not refer to the current user in the third person.
- Use the user's configured name only when it adds clarity.
- Do not start with generic openings, greetings, or filler like "How can I assist?"
- If you use a brief acknowledgement, immediately follow through with the direct answer or the tool-backed next step.
- For questions about calendars, appointments, tasks, plans, reminders, or live status, call the relevant tool before answering when one is available.
- Use tools for live local status, memory, notes, project files, or connectivity checks.
- Ask for confirmation before destructive, expensive, privacy-sensitive, or security-sensitive actions.
- Do not pretend to control hardware or services that are not connected as tools.
- If a request is not possible yet, say what capability is missing and the smallest next step.
- Do not mention tools, memory, prompts, or implementation details unless the user asks about them.
""".strip()


class LlmVmJarvisBrain:
    """Chat-completions backend for a local or LAN LLM VM."""

    def __init__(self, config: AssistantConfig, root: Path, memory: MemoryStore) -> None:
        if not config.llm_vm_base_url:
            raise SystemExit(
                "JARVIS_LLM_VM_BASE_URL is not set. Example:\n"
                "$env:JARVIS_BACKEND = 'llm_vm'\n"
                "$env:JARVIS_LLM_VM_BASE_URL = 'http://YOUR-LLM-VM:11434/v1'\n"
                "$env:JARVIS_MODEL = 'your-model-name'"
            )
        self.config = config
        self.memory = memory
        self.tools = ToolRunner(root, memory, LlmVmClient(config))
        self.chat_url = config.llm_vm_base_url.rstrip("/") + "/chat/completions"
        self._chat_tools = self.chat_tool_schemas()

    def respond(self, user_text: str) -> str:
        direct_response = self.direct_response(user_text)
        if direct_response is not None:
            self.memory.append_turn(user_text, direct_response)
            return direct_response

        use_tools = self.should_use_tools(user_text)
        messages = [
            {
                "role": "system",
                "content": (
                    f"{SYSTEM_PROMPT}\n\n"
                    f"Current user identity: {self.config.user_name}. "
                    f"Speak to {self.config.user_name} directly as the person who sent the request.\n\n"
                    f"Local memory context:\n{self.memory.prompt_context()}"
                ),
            },
            {"role": "user", "content": user_text},
        ]

        for _ in range(5):
            message = self.chat_completion(messages, use_tools=use_tools)
            tool_calls = message.get("tool_calls") or []
            if not tool_calls:
                text = str(message.get("content") or "").strip()
                self.memory.append_turn(user_text, text)
                return text

            messages.append(message)
            for call in tool_calls:
                messages.append(
                    {
                        "role": "tool",
                        "tool_call_id": call.get("id"),
                        "content": self.tools.run(
                            call.get("function", {}).get("name", ""),
                            call.get("function", {}).get("arguments", "{}"),
                        ),
                    }
                )

        response = "I could not complete the tool workflow cleanly."
        self.memory.append_turn(user_text, response)
        return response

    def direct_response(self, user_text: str) -> str | None:
        lowered = user_text.lower().strip()
        exact = re.match(r"^(?:say|reply with|respond with)\s+exactly:\s*(.+)$", user_text.strip(), re.IGNORECASE)
        if exact:
            return exact.group(1).strip()
        if "llm" in lowered and ("vm" in lowered or "model" in lowered) and any(
            word in lowered for word in ("status", "check", "inspect", "health", "list")
        ):
            return self.tools.get_llm_vm_status()
        if "local status" in lowered or lowered in {"status", "system status"}:
            return f"Local status: {self.tools.get_local_status()}"
        if ("list" in lowered or "show" in lowered) and ("project" in lowered or "files" in lowered):
            return f"Project files: {self.tools.list_project_files()}"
        if lowered.startswith("ping "):
            return self.tools.ping_host(user_text.split(maxsplit=1)[1].strip())
        return None

    @staticmethod
    def should_use_tools(user_text: str) -> bool:
        lowered = user_text.lower()
        tool_keywords = (
            "status",
            "health",
            "diagnose",
            "inspect",
            "check",
            "list",
            "files",
            "project",
            "remember",
            "note",
            "save",
            "ping",
            "vm",
            "model",
            "host",
            "port",
            "connectivity",
        )
        return any(keyword in lowered for keyword in tool_keywords)

    def chat_completion(self, messages: list[dict], use_tools: bool) -> dict:
        headers = {"Content-Type": "application/json"}
        if self.config.llm_vm_api_key:
            headers["Authorization"] = f"Bearer {self.config.llm_vm_api_key}"
        payload = {
            "model": self.config.model,
            "messages": messages,
        }
        if self.config.llm_vm_keep_alive:
            payload["keep_alive"] = self.config.llm_vm_keep_alive
        if use_tools:
            payload["tools"] = self._chat_tools
            payload["tool_choice"] = "auto"
        response = requests.post(
            self.chat_url,
            headers=headers,
            json=payload,
            timeout=self.config.llm_vm_timeout_seconds,
        )
        response.raise_for_status()
        payload = response.json()
        choices = payload.get("choices") or []
        if not choices:
            raise RuntimeError("LLM VM returned no chat completion choices.")
        message = choices[0].get("message")
        if not isinstance(message, dict):
            raise RuntimeError("LLM VM returned an invalid chat completion message.")
        return message

    @staticmethod
    def chat_tool_schemas() -> list[dict]:
        chat_tools = []
        for schema in tool_schemas():
            converted = dict(schema)
            converted.pop("type", None)
            chat_tools.append({"type": "function", "function": converted})
        return chat_tools


class LocalJarvisBrain:
    """Deterministic backend for install checks and offline verification."""

    def __init__(self, root: Path, memory: MemoryStore, config: AssistantConfig | None = None) -> None:
        self.config = config or AssistantConfig()
        self.memory = memory
        self.tools = ToolRunner(root, memory, LlmVmClient(self.config))

    def respond(self, user_text: str) -> str:
        lowered = user_text.lower()
        if "vm" in lowered or "model" in lowered:
            response = self.tools.get_llm_vm_status()
        elif "status" in lowered:
            response = f"Local status: {self.tools.get_local_status()}"
        elif any(word in lowered for word in ("calendar", "appointment", "appointments", "plans", "tomorrow")):
            response = (
                "Calendar access is not connected in the local verification backend. "
                "Connect a calendar tool, then I can check your plans directly."
            )
        elif "tim" in lowered and ("third person" in lowered or "refer" in lowered or "address" in lowered):
            response = f"I will address you directly as {self.config.user_name}, not talk about you in the third person."
        elif "file" in lowered or "project" in lowered:
            response = f"Project files: {self.tools.list_project_files()}"
        elif "remember" in lowered:
            response = self.tools.remember_fact("offline_test", user_text)
        elif "ping" in lowered:
            response = self.tools.ping_host("127.0.0.1")
        else:
            response = "Local verification backend is online. Use JARVIS_BACKEND=llm_vm for live VM model chat."
        self.memory.append_turn(user_text, response)
        return response
