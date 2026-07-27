"""Collect ZoneMinder event dashboard reliability/performance samples."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import statistics
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "http://127.0.0.1:8123"
DEFAULT_INTERVAL_SECONDS = 60
DEFAULT_DURATION_HOURS = 4
MIN_EXPECTED_IMAGE_BYTES = 500_000


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def fetch_json(url: str, timeout: float) -> tuple[dict[str, Any] | None, dict[str, Any]]:
    started = time.perf_counter()
    try:
        with urlopen(Request(url, headers={"Cache-Control": "no-cache"}), timeout=timeout) as response:
            body = response.read()
            elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
            return json.loads(body.decode("utf-8")), {
                "ok": True,
                "status": response.status,
                "elapsed_ms": elapsed_ms,
                "bytes": len(body),
            }
    except HTTPError as err:
        return None, {
            "ok": False,
            "status": err.code,
            "elapsed_ms": round((time.perf_counter() - started) * 1000, 2),
            "error": str(err),
        }
    except (OSError, URLError, TimeoutError) as err:
        return None, {
            "ok": False,
            "status": None,
            "elapsed_ms": round((time.perf_counter() - started) * 1000, 2),
            "error": str(err),
        }


def fetch_bytes(url: str, timeout: float) -> dict[str, Any]:
    started = time.perf_counter()
    try:
        with urlopen(Request(url, headers={"Cache-Control": "no-cache"}), timeout=timeout) as response:
            body = response.read()
            elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
            content_type = response.headers.get("content-type")
            return {
                "ok": True,
                "status": response.status,
                "elapsed_ms": elapsed_ms,
                "bytes": len(body),
                "content_type": content_type,
                "suspect_small_image": len(body) < MIN_EXPECTED_IMAGE_BYTES,
            }
    except HTTPError as err:
        return {
            "ok": False,
            "status": err.code,
            "elapsed_ms": round((time.perf_counter() - started) * 1000, 2),
            "error": str(err),
        }
    except (OSError, URLError, TimeoutError) as err:
        return {
            "ok": False,
            "status": None,
            "elapsed_ms": round((time.perf_counter() - started) * 1000, 2),
            "error": str(err),
        }


def summarize(log_path: Path) -> dict[str, Any]:
    rows = []
    with log_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                rows.append(json.loads(line))

    snapshot_latencies = [
        row["snapshot_request"]["elapsed_ms"]
        for row in rows
        if row.get("snapshot_request", {}).get("ok")
    ]
    preview_latencies = [
        check["request"]["elapsed_ms"]
        for row in rows
        for check in row.get("preview_checks", [])
        if check.get("request", {}).get("ok")
    ]
    preview_sizes = [
        check["request"]["bytes"]
        for row in rows
        for check in row.get("preview_checks", [])
        if check.get("request", {}).get("ok")
    ]
    event_ids = {
        check["event_id"]
        for row in rows
        for check in row.get("preview_checks", [])
        if check.get("event_id") is not None
    }
    failures = [
        row
        for row in rows
        if not row.get("snapshot_request", {}).get("ok")
        or any(not check.get("request", {}).get("ok") for check in row.get("preview_checks", []))
    ]
    suspect_images = [
        check
        for row in rows
        for check in row.get("preview_checks", [])
        if check.get("request", {}).get("suspect_small_image")
    ]

    return {
        "generated_at": utc_now(),
        "samples": len(rows),
        "events_checked": sorted(event_ids),
        "snapshot": latency_summary(snapshot_latencies),
        "preview": latency_summary(preview_latencies),
        "preview_image_bytes": value_summary(preview_sizes),
        "failure_count": len(failures),
        "suspect_preview_count": len(suspect_images),
        "latest_sample_at": rows[-1]["sampled_at"] if rows else None,
    }


def latency_summary(values: list[float]) -> dict[str, Any]:
    summary = value_summary(values)
    if summary:
        summary["unit"] = "ms"
    return summary


def value_summary(values: list[float | int]) -> dict[str, Any]:
    if not values:
        return {}
    sorted_values = sorted(values)
    p95_index = max(0, min(len(sorted_values) - 1, round((len(sorted_values) - 1) * 0.95)))
    return {
        "count": len(values),
        "min": round(sorted_values[0], 2),
        "avg": round(statistics.fmean(values), 2),
        "p95": round(sorted_values[p95_index], 2),
        "max": round(sorted_values[-1], 2),
    }


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--duration-hours", type=float, default=DEFAULT_DURATION_HOURS)
    parser.add_argument("--interval-seconds", type=float, default=DEFAULT_INTERVAL_SECONDS)
    parser.add_argument("--timeout-seconds", type=float, default=30)
    parser.add_argument("--recent-events", type=int, default=5)
    parser.add_argument("--out-dir", default="runs/zoneminder-monitor")
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    run_id = datetime.now().strftime("%Y%m%d-%H%M%S")
    log_path = out_dir / f"zoneminder-monitor-{run_id}.jsonl"
    summary_path = out_dir / f"zoneminder-monitor-{run_id}.summary.json"
    latest_path = out_dir / "latest.json"
    stop_at = time.monotonic() + (args.duration_hours * 3600)

    while time.monotonic() < stop_at:
        sample = collect_sample(args)
        with log_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(sample, sort_keys=True) + "\n")
        write_json(latest_path, {"log_path": str(log_path), "summary_path": str(summary_path), **sample})
        sleep_for = max(0, min(args.interval_seconds, stop_at - time.monotonic()))
        time.sleep(sleep_for)

    write_json(summary_path, summarize(log_path))
    write_json(latest_path, {"log_path": str(log_path), "summary_path": str(summary_path), "completed_at": utc_now()})
    return 0


def collect_sample(args: argparse.Namespace) -> dict[str, Any]:
    base_url = args.base_url.rstrip("/")
    snapshot_url = f"{base_url}/api/gmode_zoneminder_events/snapshot"
    snapshot, snapshot_request = fetch_json(snapshot_url, args.timeout_seconds)
    sample: dict[str, Any] = {
        "sampled_at": utc_now(),
        "snapshot_url": snapshot_url,
        "snapshot_request": snapshot_request,
        "snapshot_status": snapshot.get("status") if snapshot else None,
        "event_count": len(snapshot.get("events", [])) if snapshot else 0,
        "monitor_count": len(snapshot.get("monitors", [])) if snapshot else 0,
        "latest_event_id": snapshot.get("summary", {}).get("latest_event_id") if snapshot else None,
        "latest_event_monitor": snapshot.get("summary", {}).get("latest_monitor") if snapshot else None,
        "preview_checks": [],
    }
    if not snapshot:
        return sample

    events = snapshot.get("events", [])[: max(1, args.recent_events)]
    for event in events:
        image_url = str(event.get("image_url") or "")
        if not image_url:
            continue
        full_image_url = image_url if image_url.startswith("http") else f"{base_url}{image_url}"
        separator = "&" if "?" in full_image_url else "?"
        request_url = f"{full_image_url}{separator}_monitor={int(time.time())}"
        sample["preview_checks"].append(
            {
                "event_id": event.get("id"),
                "monitor": event.get("monitor_name"),
                "frames": event.get("frames"),
                "alarm_frames": event.get("alarm_frames"),
                "max_score": event.get("max_score"),
                "image_url": full_image_url,
                "request": fetch_bytes(request_url, args.timeout_seconds),
            }
        )
    return sample


if __name__ == "__main__":
    raise SystemExit(main())
