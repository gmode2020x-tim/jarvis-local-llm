from __future__ import annotations

import subprocess
import wave
from pathlib import Path

import sounddevice as sd


def input_devices() -> list[dict]:
    devices = sd.query_devices()
    return [
        {"index": index, "name": device["name"], "inputs": int(device["max_input_channels"])}
        for index, device in enumerate(devices)
        if int(device["max_input_channels"]) > 0
    ]


def require_input_device() -> None:
    if not input_devices():
        raise SystemExit(
            "No microphone input device is visible to Python. "
            "Connect or enable a microphone, then retry voice mode."
        )


def record_wav(path: Path, seconds: float, sample_rate: int) -> Path:
    require_input_device()
    path.parent.mkdir(parents=True, exist_ok=True)
    print(f"Listening for {seconds:.1f}s...")
    audio = sd.rec(int(seconds * sample_rate), samplerate=sample_rate, channels=1, dtype="int16")
    sd.wait()
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(audio.tobytes())
    return path


def play_wav(path: Path) -> None:
    players = [
        ["ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", str(path)],
        ["powershell", "-NoProfile", "-Command", f"(New-Object Media.SoundPlayer '{path}').PlaySync()"],
    ]
    for command in players:
        try:
            subprocess.run(command, check=True, timeout=60)
            return
        except Exception:
            continue
    print(f"Audio saved to {path}")
