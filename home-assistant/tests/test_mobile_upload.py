"""Unit tests for the Android trip ingestion contract."""
from __future__ import annotations

import asyncio
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest


def load_component():
    aiohttp = types.ModuleType("aiohttp")
    aiohttp.web = types.SimpleNamespace(Request=object, Response=object)
    sys.modules.setdefault("aiohttp", aiohttp)

    homeassistant = types.ModuleType("homeassistant")
    components = types.ModuleType("homeassistant.components")
    http = types.ModuleType("homeassistant.components.http")
    http.HomeAssistantView = type("HomeAssistantView", (), {})
    const = types.ModuleType("homeassistant.const")
    const.CONF_URL = "url"
    core = types.ModuleType("homeassistant.core")
    core.HomeAssistant = object
    helpers = types.ModuleType("homeassistant.helpers")
    aiohttp_client = types.ModuleType("homeassistant.helpers.aiohttp_client")
    aiohttp_client.async_get_clientsession = lambda _hass: None
    event = types.ModuleType("homeassistant.helpers.event")
    event.async_track_state_change_event = lambda *_args, **_kwargs: None
    sys.modules.update(
        {
            "homeassistant": homeassistant,
            "homeassistant.components": components,
            "homeassistant.components.http": http,
            "homeassistant.const": const,
            "homeassistant.core": core,
            "homeassistant.helpers": helpers,
            "homeassistant.helpers.aiohttp_client": aiohttp_client,
            "homeassistant.helpers.event": event,
        }
    )

    source = Path(__file__).parents[1] / "custom_components" / "gmode_trip_recorder" / "__init__.py"
    spec = importlib.util.spec_from_file_location("gmode_trip_recorder_under_test", source)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


component = load_component()


class FakeConfig:
    def __init__(self, root: Path) -> None:
        self.root = root

    def path(self, relative: str) -> str:
        return str(self.root / relative)


class FakeHass:
    def __init__(self, root: Path) -> None:
        self.config = FakeConfig(root)
        self.states = FakeStates()

    async def async_add_executor_job(self, function):
        return await asyncio.to_thread(function)


class FakeState:
    def __init__(self, state: str = "not_home") -> None:
        self.state = state
        self.attributes = {"latitude": 45.05, "longitude": -79.10}
        self.last_updated = None


class FakeStates:
    def __init__(self) -> None:
        self.values = {"device_tracker.phone": FakeState()}

    def get(self, entity_id: str):
        return self.values.get(entity_id)

    def async_set(self, entity_id: str, state: str, attributes: dict | None = None):
        value = FakeState(state)
        value.attributes = attributes or {}
        self.values[entity_id] = value


def payload(status: str = "active") -> dict:
    return {
        "protocolVersion": 1,
        "appVersion": "1.0.0",
        "deviceId": "test-s24",
        "trip": {
            "id": "trip-123",
            "title": "Test off-road trip",
            "tripType": "off_road",
            "status": status,
            "startAt": "2026-08-21T12:00:00Z",
            "endAt": "2026-08-21T12:00:05Z" if status == "complete" else None,
        },
        "points": [
            {
                "pointId": "trip-123:0",
                "sequence": 0,
                "at": "2026-08-21T12:00:00Z",
                "latitude": 45.05,
                "longitude": -79.10,
                "accuracyMeters": 4.0,
                "altitudeMeters": 280.0,
                "speedMps": 2.5,
                "pressureHpa": 1002.4,
                "accelerationRmsMs2": 0.8,
                "accelerationPeakMs2": 3.2,
                "accelerationPeakXMs2": 1.1,
                "accelerationPeakYMs2": -2.2,
                "accelerationPeakZMs2": 2.0,
                "gyroscopePeakRadS": 0.4,
                "batteryPercent": 90,
                "networkType": "offline",
            },
            {
                "pointId": "trip-123:1",
                "sequence": 1,
                "at": "2026-08-21T12:00:05Z",
                "latitude": 45.0501,
                "longitude": -79.1001,
                "accuracyMeters": 5.0,
            },
        ],
    }


def diagnostics_payload(command_id: str = "") -> dict:
    return {
        "protocolVersion": 1,
        "appVersion": "2.1.0",
        "deviceId": "test-s24",
        "sentAt": "2026-08-28T19:00:00Z",
        "acknowledgedCommandId": command_id,
        "snapshot": {
            "overallStatus": "ready",
            "syncState": "Up to date",
            "syncMessage": "All diagnostics stored",
            "gpsStatus": "GPS standby",
            "gpsRetryCount": 0,
            "autoEnabled": True,
            "autoStatus": "Armed",
            "pendingPoints": 0,
            "backgroundLocationGranted": True,
            "fineLocationGranted": True,
            "notificationsGranted": True,
            "batteryUnrestricted": True,
            "locationEnabled": True,
            "batteryPercent": 71,
        },
        "logs": [
            {
                "id": "event-1",
                "at": "2026-08-28T18:59:00Z",
                "category": "gps",
                "state": "ready",
                "message": "GPS fix received",
            }
        ],
    }


class MobileUploadTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.recorder = component.TripRecorder(FakeHass(self.root), {})

    async def asyncTearDown(self) -> None:
        self.temp.cleanup()

    async def test_batch_is_idempotent_and_preserves_telemetry(self) -> None:
        first = await self.recorder.import_mobile_trip(payload())
        second = await self.recorder.import_mobile_trip(payload())

        self.assertEqual(2, len(first["acceptedPointIds"]))
        self.assertEqual(0, len(second["acceptedPointIds"]))
        self.assertEqual(2, len(second["duplicatePointIds"]))
        self.assertEqual(2, second["storedPointCount"])

        saved = json.loads((self.root / "gmode_trip_recorder.json").read_text(encoding="utf-8"))
        trip = saved["trips"][0]
        self.assertEqual("gmode_android", trip["source"])
        self.assertEqual("off_road", trip["trip_type"])
        self.assertEqual(1002.4, trip["points"][0]["pressure_hpa"])
        self.assertEqual(1.1, trip["points"][0]["acceleration_peak_x_ms2"])
        self.assertEqual(-2.2, trip["points"][0]["acceleration_peak_y_ms2"])
        self.assertEqual(2.0, trip["points"][0]["acceleration_peak_z_ms2"])

    async def test_completed_trip_cannot_be_downgraded_by_late_retry(self) -> None:
        completed = await self.recorder.import_mobile_trip(payload("complete"))
        retried = await self.recorder.import_mobile_trip(payload("active"))
        self.assertEqual("complete", completed["tripStatus"])
        self.assertEqual("complete", retried["tripStatus"])

    async def test_invalid_coordinates_are_rejected(self) -> None:
        invalid = payload()
        invalid["points"][0]["latitude"] = 120
        with self.assertRaisesRegex(component.TripRecorderError, "point.latitude"):
            await self.recorder.import_mobile_trip(invalid)

    async def test_snapshot_is_read_only_when_automatic_tracking_is_disabled(self) -> None:
        recorder = component.TripRecorder(FakeHass(self.root), {"automatic_tracking": False})
        state_file = self.root / "gmode_trip_recorder.json"
        original = {
            "version": 1,
            "updated_at": "2026-08-21T12:00:00+00:00",
            "active_trip_id": None,
            "last_location": "away",
            "trips": [],
        }
        state_file.write_text(json.dumps(original, indent=2), encoding="utf-8")

        snapshot = await recorder.update_trip_recorder_snapshot()

        self.assertEqual("ok", snapshot["status"])
        self.assertFalse(snapshot["automatic_tracking"])
        self.assertEqual("mobile_upload", snapshot["tracking_mode"])
        self.assertEqual(original, json.loads(state_file.read_text(encoding="utf-8")))

    async def test_diagnostics_are_deduplicated_and_exposed_as_entities(self) -> None:
        first = await self.recorder.import_mobile_diagnostics(diagnostics_payload())
        second = await self.recorder.import_mobile_diagnostics(diagnostics_payload())

        self.assertEqual(["event-1"], first["acceptedLogIds"])
        self.assertEqual([], second["acceptedLogIds"])
        saved = json.loads((self.root / "gmode_trip_recorder.json").read_text(encoding="utf-8"))
        client = saved["mobile_clients"]["test-s24"]
        self.assertEqual("2.1.0", client["app_version"])
        self.assertEqual(1, len(client["logs"]))
        status = self.recorder.hass.states.get(component.MOBILE_STATUS_ENTITY)
        self.assertEqual("ready", status.state)
        self.assertEqual(0, status.attributes["pendingPoints"])

    async def test_ha_control_is_returned_and_command_acknowledgement_clears_it(self) -> None:
        control = await self.recorder.set_mobile_control(
            {
                "notice": "Comparison logging enabled",
                "latest_version": "2.1.0",
                "download_url": "https://example.invalid/gmode.apk",
                "settings": {"locationIntervalSeconds": 3, "minimumDistanceMeters": 2},
                "command_action": "rearm",
            }
        )
        command_id = control["command"]["id"]
        response = await self.recorder.import_mobile_diagnostics(diagnostics_payload())
        self.assertEqual("rearm", response["control"]["command"]["action"])

        acknowledged = await self.recorder.import_mobile_diagnostics(diagnostics_payload(command_id))
        self.assertNotIn("command", acknowledged["control"])
        self.assertEqual(3, acknowledged["control"]["settings"]["locationIntervalSeconds"])


if __name__ == "__main__":
    unittest.main()
