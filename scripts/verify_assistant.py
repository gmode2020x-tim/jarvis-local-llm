from __future__ import annotations

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from assistant.brain import LlmVmJarvisBrain, LocalJarvisBrain
from assistant.config import load_config
from assistant.memory import MemoryStore


def main() -> None:
    config = load_config()
    memory = MemoryStore(config.memory_path, config.notes_path, config.time_zone)
    brain = LocalJarvisBrain(ROOT, memory, config)
    archive_path = memory.conversation_archive_path
    archive_count_before = count_jsonl(archive_path)

    checks = [
        "Give me local status",
        "List project files",
        "Remember this offline verification completed",
        "Ping localhost",
        "Check LLM VM model status",
        "What are my plans tomorrow?",
        "How are you, Jarvis?",
    ]

    for check in checks:
        response = brain.respond(check)
        print(f"USER: {check}")
        print(f"JARVIS: {response[:500]}")
        print()

    data = memory.load()
    assert data.get("recent_turns"), "memory did not record turns"
    assert data.get("facts", {}).get("offline_test"), "memory fact was not stored"
    calendar_reply = brain.respond("What are my plans tomorrow?")
    personality_reply = brain.respond("How are you, Jarvis?")
    assert "calendar access isn't connected" in calendar_reply.lower()
    assert config.user_name in calendar_reply
    assert config.user_name in personality_reply
    assert "Apparently one of us should be" in personality_reply
    assert "sir" not in personality_reply.lower()
    archived = read_jsonl(archive_path)
    assert len(archived) >= archive_count_before + len(checks) + 2, "conversation archive did not record every turn"
    assert archived[-1]["user"] == "How are you, Jarvis?"
    assert archived[-1]["assistant"] == personality_reply
    assert all(row.get("id") and row.get("at") for row in archived[-(len(checks) + 2):])

    failing_brain = LlmVmJarvisBrain(config, ROOT, memory)

    def fail_completion(*_args, **_kwargs):
        raise RuntimeError("archive failure probe")

    failing_brain.chat_completion = fail_completion
    try:
        failing_brain.respond("Tell me a tiny joke")
    except RuntimeError:
        pass
    else:
        raise AssertionError("failure probe did not fail")
    failed_turn = read_jsonl(archive_path)[-1]
    assert failed_turn["user"] == "Tell me a tiny joke"
    assert failed_turn["status"] == "error"
    assert failed_turn["error"] == "archive failure probe"
    print("jarvis verification passed")


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def count_jsonl(path: Path) -> int:
    return len(read_jsonl(path))


if __name__ == "__main__":
    main()
