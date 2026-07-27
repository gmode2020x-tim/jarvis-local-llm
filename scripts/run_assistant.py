from __future__ import annotations

import argparse
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from assistant.runtime import run_text, run_voice


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the Jarvis assistant runtime.")
    parser.add_argument("--voice", action="store_true", help="Use microphone input and spoken output.")
    args = parser.parse_args()

    if args.voice:
        run_voice()
    else:
        run_text()


if __name__ == "__main__":
    main()
