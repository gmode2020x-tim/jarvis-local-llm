from __future__ import annotations

from datetime import datetime

from .brain import LlmVmJarvisBrain, LocalJarvisBrain
from .config import ROOT, load_config
from .memory import MemoryStore
from .speech import record_wav


def run_text() -> None:
    config = load_config()
    memory = MemoryStore(config.memory_path, config.notes_path, config.time_zone)
    brain = build_brain(config, memory)

    print(f"Jarvis text mode ({config.backend}). Type 'exit' to stop.")
    while True:
        user_text = input("\nYou: ").strip()
        if user_text.lower() in {"exit", "quit", "stop"}:
            break
        if not user_text:
            continue
        print(f"Jarvis: {brain.respond(user_text)}")


def run_voice() -> None:
    config = load_config()
    if config.backend != "local":
        raise SystemExit("Voice mode is currently local-verification only. Wire a local STT/TTS backend before live voice use.")
    memory = MemoryStore(config.memory_path, config.notes_path, config.time_zone)
    brain = build_brain(config, memory)

    print(f"Jarvis voice mode ({config.backend}). Press Enter to speak, or type 'exit' to stop.")
    while True:
        command = input("\nReady> ").strip().lower()
        if command in {"exit", "quit", "stop"}:
            break

        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        wav_path = config.audio_dir / f"input-{stamp}.wav"
        record_wav(wav_path, config.record_seconds, config.sample_rate)
        user_text = "local voice verification"
        print(f"You: {user_text}")
        if not user_text:
            continue
        response = brain.respond(user_text)
        print(f"Jarvis: {response}")


def build_brain(config, memory: MemoryStore):
    if config.backend == "local":
        return LocalJarvisBrain(ROOT, memory, config)
    if config.backend in {"llm_vm", "llm-vm", "vm"}:
        return LlmVmJarvisBrain(config, ROOT, memory)
    raise SystemExit(f"Unsupported JARVIS_BACKEND: {config.backend}")
