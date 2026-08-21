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
    sys.modules.update(
        {
            "homeassistant": homeassistant,
            "homeassistant.components": components,
            "homeassistant.components.http": http,
            "homeassistant.const": const,
            "homeassistant.core": core,
            "homeassistant.helpers": helpers,
            "homeassistant.helpers.aiohttp_client": aiohttp_client,
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

    async def async_add_executor_job(self, function):
        return await asyncio.to_thread(function)


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


if __name__ == "__main__":
    unittest.main()
