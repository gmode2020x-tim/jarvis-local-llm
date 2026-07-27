from __future__ import annotations

import argparse
import csv
import hashlib
import os
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

from tqdm import tqdm


AUDIO_EXTENSIONS = {".wav", ".mp3", ".flac", ".m4a", ".ogg", ".opus", ".aac", ".wma"}


@dataclass(frozen=True)
class Clip:
    source: Path
    text: str
    start: float | None = None
    end: float | None = None


def ffmpeg_path() -> str:
    from_path = shutil.which("ffmpeg")
    if from_path:
        return from_path

    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        winget_root = Path(local_app_data) / "Microsoft" / "WinGet" / "Packages"
        matches = sorted(winget_root.glob("Gyan.FFmpeg_*/*/bin/ffmpeg.exe"), reverse=True)
        if matches:
            return str(matches[0])

    raise SystemExit(
        "FFmpeg is required but was not found. "
        "Install with: winget install --id Gyan.FFmpeg -e"
    )


def require_ffmpeg() -> str:
    path = ffmpeg_path()
    if not Path(path).exists() and shutil.which(path) is None:
        raise SystemExit(
            "FFmpeg is required but was not found on PATH. "
            "Install with: winget install --id Gyan.FFmpeg -e"
        )
    return path


def audio_files(input_dir: Path) -> list[Path]:
    return sorted(
        path
        for path in input_dir.rglob("*")
        if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS
    )


def read_transcripts(path: Path, input_dir: Path) -> list[Clip]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))

    required = {"file", "text"}
    if not rows or not required.issubset(rows[0].keys()):
        raise SystemExit("Transcript CSV must have columns: file,text")

    clips: list[Clip] = []
    for row in rows:
        rel_file = (row.get("file") or "").strip()
        text = normalize_text(row.get("text") or "")
        if not rel_file or not text:
            continue
        source = input_dir / rel_file
        if not source.exists():
            raise SystemExit(f"Transcript references a missing file: {source}")
        clips.append(Clip(source=source, text=text))
    return clips


def transcribe_files(files: list[Path], model_name: str) -> list[Clip]:
    try:
        from faster_whisper import WhisperModel
    except ImportError as exc:
        raise SystemExit("Install faster-whisper first: pip install -r requirements.txt") from exc

    model = WhisperModel(model_name, device="auto", compute_type="auto")
    clips: list[Clip] = []
    for source in tqdm(files, desc="Transcribing"):
        segments, _info = model.transcribe(str(source), vad_filter=True)
        for segment in segments:
            text = normalize_text(segment.text)
            if not text:
                continue
            clips.append(Clip(source=source, text=text, start=segment.start, end=segment.end))
    return clips


def normalize_text(text: str) -> str:
    return " ".join(text.strip().split())


def stable_id(clip: Clip, index: int) -> str:
    payload = f"{clip.source}|{clip.start}|{clip.end}|{clip.text}|{index}".encode("utf-8")
    return hashlib.sha1(payload).hexdigest()[:12]


def write_clip(clip: Clip, wav_path: Path, sample_rate: int, ffmpeg: str) -> None:
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
    ]

    if clip.start is not None:
        command.extend(["-ss", f"{clip.start:.3f}"])
    if clip.end is not None and clip.start is not None:
        command.extend(["-t", f"{max(0.1, clip.end - clip.start):.3f}"])

    command.extend(
        [
            "-i",
            str(clip.source),
            "-ac",
            "1",
            "-ar",
            str(sample_rate),
            "-sample_fmt",
            "s16",
            str(wav_path),
        ]
    )
    subprocess.run(command, check=True)


def write_metadata(clips: list[Clip], output_dir: Path, sample_rate: int, ffmpeg: str) -> None:
    wavs_dir = output_dir / "wavs"
    wavs_dir.mkdir(parents=True, exist_ok=True)

    metadata_path = output_dir / "metadata.csv"
    with metadata_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="|", lineterminator="\n")
        for index, clip in enumerate(tqdm(clips, desc="Writing clips"), start=1):
            clip_id = stable_id(clip, index)
            wav_path = wavs_dir / f"{clip_id}.wav"
            write_clip(clip, wav_path, sample_rate, ffmpeg)
            writer.writerow([f"wavs/{clip_id}.wav", clip.text, clip.text])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare a neural voice training dataset.")
    parser.add_argument("--input", type=Path, default=Path("data/raw"), help="Folder containing source audio.")
    parser.add_argument("--output", type=Path, default=Path("datasets/ljspeech"), help="Output dataset folder.")
    parser.add_argument("--transcripts", type=Path, help="CSV with columns: file,text")
    parser.add_argument("--transcribe", action="store_true", help="Use faster-whisper to create timestamped clips.")
    parser.add_argument("--whisper-model", default="small", help="faster-whisper model name.")
    parser.add_argument("--sample-rate", type=int, default=22050, help="Output WAV sample rate.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    ffmpeg = require_ffmpeg()

    files = audio_files(args.input)
    if not files:
        raise SystemExit(f"No audio files found in {args.input}")

    if args.transcripts:
        clips = read_transcripts(args.transcripts, args.input)
    elif args.transcribe:
        clips = transcribe_files(files, args.whisper_model)
    else:
        raise SystemExit("Provide --transcripts data/transcripts.csv or use --transcribe.")

    if not clips:
        raise SystemExit("No usable clips were created.")

    args.output.mkdir(parents=True, exist_ok=True)
    write_metadata(clips, args.output, args.sample_rate, ffmpeg)
    print(f"Prepared {len(clips)} clips in {args.output}")


if __name__ == "__main__":
    main()
