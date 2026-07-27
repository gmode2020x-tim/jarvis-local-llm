from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from assistant.config import load_config
from assistant.vm import LlmVmClient, compact_json


def main() -> None:
    config = load_config()
    report = LlmVmClient(config).inspect()
    print(compact_json(report))


if __name__ == "__main__":
    main()
