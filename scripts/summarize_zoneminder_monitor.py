"""Summarize a ZoneMinder monitor JSONL file."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from monitor_zoneminder_events import summarize


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log_path", nargs="?", help="Path to monitor JSONL. Defaults to runs/zoneminder-monitor/latest.json log_path.")
    args = parser.parse_args()

    if args.log_path:
        log_path = Path(args.log_path)
    else:
        latest_path = Path("runs/zoneminder-monitor/latest.json")
        if not latest_path.exists():
            print(f"Missing {latest_path}", file=sys.stderr)
            return 1
        latest = json.loads(latest_path.read_text(encoding="utf-8"))
        log_path = Path(latest["log_path"])

    if not log_path.exists():
        print(f"Missing {log_path}", file=sys.stderr)
        return 1
    print(json.dumps(summarize(log_path), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
