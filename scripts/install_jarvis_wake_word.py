from __future__ import annotations

import argparse
import json
import shutil
from datetime import datetime
from pathlib import Path


DEFAULT_MODEL_DIR = Path(r"\\homeassistant\share\openwakeword")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Install a Jarvis openWakeWord model into Home Assistant.")
    parser.add_argument("model", type=Path, help="Path to the trained .tflite wake-word model.")
    parser.add_argument("--model-dir", type=Path, default=DEFAULT_MODEL_DIR)
    parser.add_argument(
        "--pipeline-file",
        type=Path,
        required=True,
        help="Path to Home Assistant .storage/assist_pipeline.pipelines. Back this up before editing.",
    )
    parser.add_argument("--wake-word-id", default="hey_jarvis", help="Wake word id exposed by openWakeWord.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.model.suffix.lower() != ".tflite":
        raise SystemExit("Home Assistant openWakeWord expects a .tflite model file.")
    if not args.model.exists():
        raise SystemExit(f"Model file not found: {args.model}")
    if not args.pipeline_file.exists():
        raise SystemExit(f"Assist pipeline file not found: {args.pipeline_file}")

    args.model_dir.mkdir(parents=True, exist_ok=True)
    target = args.model_dir / args.model.name
    shutil.copy2(args.model, target)

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = args.pipeline_file.with_name(f"{args.pipeline_file.name}.bak-jarvis-wakeword-{stamp}")
    shutil.copy2(args.pipeline_file, backup)

    data = json.loads(args.pipeline_file.read_text(encoding="utf-8"))
    preferred_id = data.get("data", {}).get("preferred_pipeline")
    for item in data.get("data", {}).get("items", []):
        if item.get("id") == preferred_id:
            item["wake_word_entity"] = "wake_word.openwakeword"
            item["wake_word_id"] = args.wake_word_id
            break
    else:
        raise SystemExit(f"Preferred pipeline not found: {preferred_id}")

    args.pipeline_file.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"Installed model: {target}")
    print(f"Backed up pipeline: {backup}")
    print(f"Set Jarvis wake_word_id: {args.wake_word_id}")


if __name__ == "__main__":
    main()
