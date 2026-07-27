from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from assistant.brain import LocalJarvisBrain
from assistant.config import load_config
from assistant.memory import MemoryStore


def main() -> None:
    config = load_config()
    memory = MemoryStore(config.memory_path, config.notes_path, config.time_zone)
    brain = LocalJarvisBrain(ROOT, memory, config)

    checks = [
        "Give me local status",
        "List project files",
        "Remember this offline verification completed",
        "Ping localhost",
        "Check LLM VM model status",
        "What are my plans tomorrow?",
    ]

    for check in checks:
        response = brain.respond(check)
        print(f"USER: {check}")
        print(f"JARVIS: {response[:500]}")
        print()

    data = memory.load()
    assert data.get("recent_turns"), "memory did not record turns"
    assert data.get("facts", {}).get("offline_test"), "memory fact was not stored"
    assert "Calendar access is not connected" in brain.respond("What are my plans tomorrow?")
    print("jarvis verification passed")


if __name__ == "__main__":
    main()
