#!/usr/bin/env python3
"""Push new ZoneMinder events to Home Assistant with low latency."""
from __future__ import annotations

import argparse
import http.cookiejar
import json
import logging
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request

LOG = logging.getLogger("gmode_zoneminder_event_push")


def load_env(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    env_path = Path(path)
    if env_path.exists():
        for raw_line in env_path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    values.update({key: value for key, value in os.environ.items() if key.startswith(("HA_", "ZM_", "POLL_", "EVENT_", "STATE_"))})
    return values


class ZoneMinderSender:
    def __init__(self, config: dict[str, str]) -> None:
        self.ha_push_url = required(config, "HA_PUSH_URL")
        self.ha_push_token = required(config, "HA_PUSH_TOKEN")
        self.zm_base_url = config.get("ZM_BASE_URL", "http://127.0.0.1/zm").rstrip("/")
        self.zm_username = required(config, "ZM_USERNAME")
        self.zm_password = required(config, "ZM_PASSWORD")
        self.poll_seconds = float(config.get("POLL_SECONDS", "1.0"))
        self.event_limit = int(config.get("EVENT_LIMIT", "10"))
        self.state_path = Path(config.get("STATE_FILE", "/var/lib/gmode-zoneminder-event-push/state.json"))
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.session_ready = False
        self.last_event_id = self.load_last_event_id()

    def load_last_event_id(self) -> int | None:
        try:
            if not self.state_path.exists():
                return None
            return int(json.loads(self.state_path.read_text(encoding="utf-8")).get("last_event_id"))
        except Exception as err:  # noqa: BLE001
            LOG.warning("Could not load state file %s: %s", self.state_path, err)
            return None

    def save_last_event_id(self, event_id: int) -> None:
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        self.state_path.write_text(json.dumps({"last_event_id": event_id, "updated_at": time.time()}, indent=2), encoding="utf-8")

    def login(self) -> None:
        body = urllib.parse.urlencode({
            "view": "console",
            "action": "login",
            "username": self.zm_username,
            "password": self.zm_password,
        }).encode("utf-8")
        request = urllib.request.Request(f"{self.zm_base_url}/index.php", data=body, method="POST")
        with self.opener.open(request, timeout=20) as response:
            response.read()
        self.session_ready = True

    def get_json(self, path: str) -> dict:
        if not self.session_ready:
            self.login()
        url = f"{self.zm_base_url}{path}"
        request = urllib.request.Request(url, headers={"Accept": "application/json"})
        try:
            with self.opener.open(request, timeout=20) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as err:
            if err.code == 401:
                self.session_ready = False
                self.login()
                with self.opener.open(request, timeout=20) as response:
                    return json.loads(response.read().decode("utf-8"))
            raise

    def latest_events(self) -> list[dict]:
        payload = self.get_json(f"/api/events/index.json?sort=StartDateTime&direction=desc&page=1&limit={self.event_limit}")
        events = []
        for item in payload.get("events", []):
            event = item.get("Event", {})
            try:
                event_id = int(float(event.get("Id")))
            except (TypeError, ValueError):
                continue
            events.append({
                "event_id": event_id,
                "monitor_id": event.get("MonitorId"),
                "name": event.get("Name"),
                "state": event.get("State"),
                "reason": "zoneminder-push-sender",
            })
        return events

    def push_event(self, event: dict) -> None:
        body = json.dumps(event).encode("utf-8")
        request = urllib.request.Request(
            self.ha_push_url,
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "X-GMode-ZoneMinder-Token": self.ha_push_token,
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            response.read()
        LOG.info("Pushed ZoneMinder event %s to Home Assistant", event["event_id"])

    def run_once(self, initialize_only: bool = False) -> int:
        events = self.latest_events()
        if not events:
            return 0
        latest_id = max(event["event_id"] for event in events)
        if self.last_event_id is None:
            self.last_event_id = latest_id
            self.save_last_event_id(latest_id)
            LOG.info("Initialized last ZoneMinder event id at %s", latest_id)
            return 0
        if initialize_only:
            return 0
        new_events = sorted((event for event in events if event["event_id"] > self.last_event_id), key=lambda item: item["event_id"])
        for event in new_events:
            self.push_event(event)
            self.last_event_id = event["event_id"]
            self.save_last_event_id(self.last_event_id)
        return len(new_events)

    def run_forever(self) -> None:
        while True:
            try:
                self.run_once()
            except Exception as err:  # noqa: BLE001
                LOG.exception("ZoneMinder event push cycle failed: %s", err)
                self.session_ready = False
            time.sleep(self.poll_seconds)


def required(config: dict[str, str], key: str) -> str:
    value = str(config.get(key) or "").strip()
    if not value:
        raise RuntimeError(f"{key} is required")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", default="/etc/gmode-zoneminder-event-push.env")
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--initialize-only", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    sender = ZoneMinderSender(load_env(args.env))
    if args.once or args.initialize_only:
        count = sender.run_once(initialize_only=args.initialize_only)
        LOG.info("Processed %s new event(s)", count)
    else:
        sender.run_forever()


if __name__ == "__main__":
    main()
