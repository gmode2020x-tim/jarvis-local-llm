from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class AssistantConfig:
    backend: str = "llm_vm"
    model: str = "llama3.2:3b"
    user_name: str = "Operator"
    time_zone: str = "UTC"
    llm_vm_base_url: str | None = None
    llm_vm_api_key: str = "not-needed"
    llm_vm_host: str = "127.0.0.1"
    llm_vm_user: str = "jarvis"
    llm_vm_timeout_seconds: int = 180
    llm_vm_keep_alive: str = "30m"
    llm_vm_password: str | None = None
    llm_vm_key_path: Path | None = None
    llm_vm_allow_agent: bool = True
    memory_path: Path = ROOT / "data" / "assistant_memory.json"
    notes_path: Path = ROOT / "data" / "assistant_notes.md"
    audio_dir: Path = ROOT / "runs" / "assistant_audio"
    record_seconds: float = 5.0
    sample_rate: int = 16_000


def env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def env_path(name: str) -> Path | None:
    value = os.getenv(name)
    if not value:
        return None
    return Path(value).expanduser()


def load_dotenv(path: Path = ROOT / ".env") -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def load_config() -> AssistantConfig:
    load_dotenv()
    backend = os.getenv("JARVIS_BACKEND", AssistantConfig.backend).lower()
    return AssistantConfig(
        backend=backend,
        model=os.getenv("JARVIS_MODEL") or default_model(backend),
        user_name=os.getenv("JARVIS_USER_NAME", AssistantConfig.user_name),
        time_zone=os.getenv("JARVIS_TIME_ZONE", AssistantConfig.time_zone),
        llm_vm_base_url=os.getenv("JARVIS_LLM_VM_BASE_URL")
        or f"http://{os.getenv('JARVIS_LLM_VM_HOST', AssistantConfig.llm_vm_host)}:11434/v1",
        llm_vm_api_key=os.getenv("JARVIS_LLM_VM_API_KEY", AssistantConfig.llm_vm_api_key),
        llm_vm_host=os.getenv("JARVIS_LLM_VM_HOST", AssistantConfig.llm_vm_host),
        llm_vm_user=os.getenv("JARVIS_LLM_VM_USER", AssistantConfig.llm_vm_user),
        llm_vm_timeout_seconds=int(
            os.getenv("JARVIS_LLM_VM_TIMEOUT_SECONDS", AssistantConfig.llm_vm_timeout_seconds)
        ),
        llm_vm_keep_alive=os.getenv("JARVIS_LLM_VM_KEEP_ALIVE", AssistantConfig.llm_vm_keep_alive),
        llm_vm_password=os.getenv("JARVIS_LLM_VM_PASSWORD"),
        llm_vm_key_path=env_path("JARVIS_LLM_VM_KEY_PATH"),
        llm_vm_allow_agent=env_bool("JARVIS_LLM_VM_ALLOW_AGENT", AssistantConfig.llm_vm_allow_agent),
        record_seconds=float(os.getenv("JARVIS_RECORD_SECONDS", AssistantConfig.record_seconds)),
        sample_rate=int(os.getenv("JARVIS_SAMPLE_RATE", AssistantConfig.sample_rate)),
    )


def default_model(backend: str) -> str:
    return AssistantConfig.model
