from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
MAX_FACT_CHARS = 300
MAX_TURN_TEXT_CHARS = 180
MAX_PROMPT_CONTEXT_CHARS = 900


@dataclass
class MemoryStore:
    path: Path
    notes_path: Path
    time_zone: str = "UTC"

    def now(self) -> datetime:
        try:
            from zoneinfo import ZoneInfo

            return datetime.now(ZoneInfo(self.time_zone))
        except Exception:
            return datetime.now()

    def load(self) -> dict:
        if not self.path.exists():
            return {"facts": {}, "recent_turns": []}
        with self.path.open("r", encoding="utf-8") as handle:
            return json.load(handle)

    def save(self, data: dict) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=2, sort_keys=True)

    def remember_fact(self, key: str, value: str) -> str:
        data = self.load()
        data.setdefault("facts", {})[key] = value
        self.save(data)
        return f"Remembered {key}."

    def append_turn(self, user_text: str, assistant_text: str) -> None:
        data = self.load()
        turns = data.setdefault("recent_turns", [])
        turns.append(
            {
                "at": self.now().isoformat(timespec="seconds"),
                "user": user_text,
                "assistant": assistant_text,
            }
        )
        data["recent_turns"] = turns[-20:]
        self.save(data)

    def append_note(self, note: str) -> str:
        self.notes_path.parent.mkdir(parents=True, exist_ok=True)
        stamp = self.now().isoformat(timespec="seconds")
        with self.notes_path.open("a", encoding="utf-8") as handle:
            handle.write(f"\n## {stamp}\n\n{note.strip()}\n")
        return "Saved note."

    def prompt_context(self) -> str:
        data = self.load()
        facts = data.get("facts", {})
        turns = data.get("recent_turns", [])[-2:]
        facts_text = "\n".join(f"- {key}: {self._shorten(str(value), MAX_FACT_CHARS)}" for key, value in facts.items()) or "- none"
        turns_text = "\n".join(
            f"- User: {self._shorten(str(turn.get('user', '')), MAX_TURN_TEXT_CHARS)}\n"
            f"  Assistant: {self._shorten(str(turn.get('assistant', '')), MAX_TURN_TEXT_CHARS)}"
            for turn in turns
        ) or "- none"
        return self._shorten(f"Known facts:\n{facts_text}\n\nRecent turns:\n{turns_text}", MAX_PROMPT_CONTEXT_CHARS)

    @staticmethod
    def _shorten(value: str, limit: int) -> str:
        compact = " ".join(value.split())
        if len(compact) <= limit:
            return compact
        return compact[: limit - 15].rstrip() + " ... [truncated]"
