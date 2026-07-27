from __future__ import annotations

import argparse
import csv
from pathlib import Path

import soundfile as sf


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check an LJSpeech-style dataset.")
    parser.add_argument("dataset", type=Path, help="Dataset folder containing metadata.csv and wavs/")
    parser.add_argument("--min-seconds", type=float, default=1.0)
    parser.add_argument("--max-seconds", type=float, default=15.0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    metadata = args.dataset / "metadata.csv"
    if not metadata.exists():
        raise SystemExit(f"Missing metadata file: {metadata}")

    rows: list[list[str]] = []
    with metadata.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.reader(handle, delimiter="|")
        rows = [row for row in reader if row]

    if not rows:
        raise SystemExit("metadata.csv is empty.")

    durations: list[float] = []
    sample_rates: dict[int, int] = {}
    issues: list[str] = []

    for line_number, row in enumerate(rows, start=1):
        if len(row) < 2:
            issues.append(f"line {line_number}: expected at least wav|text")
            continue

        wav_path = args.dataset / row[0]
        text = row[1].strip()
        if not wav_path.exists():
            issues.append(f"line {line_number}: missing wav {wav_path}")
            continue
        if not text:
            issues.append(f"line {line_number}: empty transcript")

        info = sf.info(str(wav_path))
        duration = info.frames / float(info.samplerate)
        durations.append(duration)
        sample_rates[info.samplerate] = sample_rates.get(info.samplerate, 0) + 1

        if duration < args.min_seconds:
            issues.append(f"line {line_number}: clip is short ({duration:.2f}s)")
        if duration > args.max_seconds:
            issues.append(f"line {line_number}: clip is long ({duration:.2f}s)")
        if info.channels != 1:
            issues.append(f"line {line_number}: expected mono audio, found {info.channels} channels")

    total = sum(durations)
    print(f"clips: {len(rows)}")
    if durations:
        print(f"total audio: {total / 60:.1f} minutes")
        print(f"duration min/avg/max: {min(durations):.2f}s / {total / len(durations):.2f}s / {max(durations):.2f}s")
    else:
        print("total audio: 0.0 minutes")
        issues.append("no readable wav files were found")
    print(f"sample rates: {sample_rates}")

    if issues:
        print("\nIssues:")
        for issue in issues[:100]:
            print(f"- {issue}")
        if len(issues) > 100:
            print(f"- ... {len(issues) - 100} more")
        raise SystemExit(1)

    print("dataset check passed")


if __name__ == "__main__":
    main()
