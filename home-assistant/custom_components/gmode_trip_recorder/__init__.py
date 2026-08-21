"""Local GMODE trip recorder API for Home Assistant."""
from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone
import hashlib
import json
import logging
from math import atan2, cos, radians, sin, sqrt
from pathlib import Path
from typing import Any

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.const import CONF_URL
from homeassistant.core import HomeAssistant
from homeassistant.helpers.aiohttp_client import async_get_clientsession

DOMAIN = "gmode_trip_recorder"
DEFAULT_TRACKING_ENTITY = "device_tracker.phone"
DEFAULT_OSRM_URL = "http://127.0.0.1:5000"
DEFAULT_STATE_FILE = "gmode_trip_recorder.json"
MOBILE_SOURCE = "gmode_android"
MOBILE_PROTOCOL_VERSION = 1
MAX_MOBILE_BATCH_POINTS = 500
LOCATION_FRESH_MINUTES = 360
OFF_ROAD_MIN_POINTS = 4
OFF_ROAD_SAMPLE_LIMIT = 14
OFF_ROAD_MAX_AVERAGE_SPEED_KMH = 55
OFF_ROAD_NEAR_ROAD_M = 90
OFF_ROAD_FAR_FROM_ROAD_M = 180
OFF_ROAD_FAR_RATIO = 0.30
OFF_ROAD_MIN_FAR_POINTS = 3
OFF_ROAD_LOCAL_MAX_AVERAGE_SPEED_KMH = 40
OFF_ROAD_LOCAL_MAX_DISTANCE_KM = 80
OFF_ROAD_LOCAL_MIN_DISTANCE_KM = 0.5
OFF_ROAD_LOCAL_AREA_MIN_LAT = 45.0
OFF_ROAD_COTTAGE_RADIUS_M = 35000
TRIP_TYPE_LABELS = {
    "street": "Street",
    "off_road": "Off road",
    "snow": "Snow",
    "water": "Water",
}

_LOGGER = logging.getLogger(__name__)

async def async_setup(hass: HomeAssistant, config: dict[str, Any]) -> bool:
    """Set up the local trip recorder HTTP API."""
    domain_config = config.get(DOMAIN, {}) or {}
    recorder = TripRecorder(hass, domain_config)
    hass.data[DOMAIN] = recorder
    hass.http.register_view(TripSnapshotView(recorder))
    hass.http.register_view(LocationSnapshotView(recorder))
    hass.http.register_view(TripManageView(recorder))
    hass.http.register_view(MobileTripUploadView(recorder))
    return True


class TripSnapshotView(HomeAssistantView):
    """Return the current trip snapshot."""

    url = "/api/gmode_trip_recorder/snapshot"
    name = "api:gmode_trip_recorder:snapshot"
    requires_auth = False

    def __init__(self, recorder: "TripRecorder") -> None:
        self._recorder = recorder

    async def get(self, request: web.Request) -> web.Response:
        return self.json(await self._recorder.update_trip_recorder_snapshot())


class LocationSnapshotView(HomeAssistantView):
    """Return the current location map snapshot."""

    url = "/api/gmode_trip_recorder/locations"
    name = "api:gmode_trip_recorder:locations"
    requires_auth = False

    def __init__(self, recorder: "TripRecorder") -> None:
        self._recorder = recorder

    async def get(self, request: web.Request) -> web.Response:
        return self.json(await self._recorder.get_location_map_snapshot())


class TripManageView(HomeAssistantView):
    """Rename or hide trips."""

    url = "/api/gmode_trip_recorder/manage"
    name = "api:gmode_trip_recorder:manage"
    requires_auth = False

    def __init__(self, recorder: "TripRecorder") -> None:
        self._recorder = recorder

    async def post(self, request: web.Request) -> web.Response:
        try:
            body = await request.json()
            return self.json(await self._recorder.manage_trip_recorder(body))
        except TripRecorderError as err:
            return self.json(
                {"status": "error", "updated_at": utc_now().isoformat(), "error": str(err)},
                status_code=err.status_code,
            )
        except Exception as err:  # noqa: BLE001
            _LOGGER.exception("Trip recorder manage request failed")
            return self.json(
                {"status": "error", "updated_at": utc_now().isoformat(), "error": str(err)},
                status_code=400,
            )


class MobileTripUploadView(HomeAssistantView):
    """Receive offline-first Android trip batches."""

    url = "/api/gmode_trip_recorder/mobile/upload"
    name = "api:gmode_trip_recorder:mobile_upload"
    requires_auth = True

    def __init__(self, recorder: "TripRecorder") -> None:
        self._recorder = recorder

    async def post(self, request: web.Request) -> web.Response:
        try:
            body = await request.json()
            return self.json(await self._recorder.import_mobile_trip(body))
        except TripRecorderError as err:
            return self.json(
                {"status": "error", "updated_at": utc_now().isoformat(), "error": str(err)},
                status_code=err.status_code,
            )
        except Exception as err:  # noqa: BLE001
            _LOGGER.exception("Mobile trip upload failed")
            return self.json(
                {"status": "error", "updated_at": utc_now().isoformat(), "error": str(err)},
                status_code=400,
            )


class TripRecorderError(Exception):
    """Trip recorder API error."""

    def __init__(self, message: str, status_code: int = 400) -> None:
        super().__init__(message)
        self.status_code = status_code


class TripRecorder:
    """Trip recorder backed by Home Assistant state."""

    def __init__(self, hass: HomeAssistant, config: dict[str, Any]) -> None:
        self.hass = hass
        self.tracking_entity = str(config.get("tracking_entity", DEFAULT_TRACKING_ENTITY))
        self.osrm_base_url = str(config.get("osrm_url", DEFAULT_OSRM_URL)).rstrip("/")
        self.state_path = Path(hass.config.path(str(config.get("state_file", DEFAULT_STATE_FILE))))
        self.location_fresh_ms = LOCATION_FRESH_MINUTES * 60 * 1000
        self.location_entities = parse_location_entities(config.get("location_entities"), self.tracking_entity)
        self.route_places = parse_route_places(config.get("route_places"), hass)
        self.off_road_reference_place = parse_off_road_reference_place(config.get("off_road_reference_place"))
        self.state_lock = asyncio.Lock()

    async def import_mobile_trip(self, body: dict[str, Any]) -> dict[str, Any]:
        """Merge an authenticated Android batch without duplicating points."""
        if not isinstance(body, dict):
            raise TripRecorderError("JSON object required")
        protocol_version = int(body.get("protocolVersion") or 0)
        if protocol_version != MOBILE_PROTOCOL_VERSION:
            raise TripRecorderError(f"protocolVersion must be {MOBILE_PROTOCOL_VERSION}")

        device_id = clean_identifier(body.get("deviceId"), "deviceId")
        incoming_trip = body.get("trip")
        if not isinstance(incoming_trip, dict):
            raise TripRecorderError("trip object is required")
        source_trip_id = clean_identifier(incoming_trip.get("id"), "trip.id")
        incoming_points = body.get("points")
        if not isinstance(incoming_points, list):
            raise TripRecorderError("points array is required")
        if len(incoming_points) > MAX_MOBILE_BATCH_POINTS:
            raise TripRecorderError(f"points cannot exceed {MAX_MOBILE_BATCH_POINTS} per request", 413)

        server_trip_id = make_mobile_trip_id(device_id, source_trip_id)
        normalized_points = [normalize_mobile_point(point, source_trip_id) for point in incoming_points]

        async with self.state_lock:
            recorder = await self.read_trip_recorder()
            trips = recorder.get("trips") if isinstance(recorder.get("trips"), list) else []
            trip = next((item for item in trips if item.get("id") == server_trip_id), None)
            created = trip is None
            if trip is None:
                trip = {
                    "id": server_trip_id,
                    "source": MOBILE_SOURCE,
                    "source_device_id": device_id,
                    "source_trip_id": source_trip_id,
                    "imported": True,
                    "mode": "motor_vehicle",
                    "points": [],
                }
                trips.append(trip)

            existing_points = get_trip_points(trip)
            existing_point_ids = {
                str(point.get("point_id"))
                for point in existing_points
                if point.get("point_id") is not None
            }
            accepted = []
            duplicates = []
            for point in normalized_points:
                point_id = str(point["point_id"])
                if point_id in existing_point_ids:
                    duplicates.append(point_id)
                    continue
                existing_points.append(point)
                existing_point_ids.add(point_id)
                accepted.append(point_id)

            existing_points.sort(key=lambda point: (parse_date(point.get("at")), int(point.get("sequence") or 0)))
            trip["points"] = existing_points
            trip["title"] = clean_trip_title(incoming_trip.get("title"), trip.get("title"), existing_points)
            trip["trip_type"] = clean_trip_type(incoming_trip.get("tripType") or trip.get("trip_type"))
            trip["trip_type_source"] = "manual"
            trip["start_at"] = clean_mobile_date(incoming_trip.get("startAt")) or (
                existing_points[0].get("at") if existing_points else trip.get("start_at")
            )
            incoming_status = str(incoming_trip.get("status") or "active").strip().lower()
            if incoming_status not in {"active", "complete"}:
                raise TripRecorderError("trip.status must be active or complete")
            if trip.get("status") != "complete":
                trip["status"] = incoming_status
            if trip.get("status") == "complete":
                trip["end_at"] = clean_mobile_date(incoming_trip.get("endAt")) or (
                    existing_points[-1].get("at") if existing_points else trip.get("end_at")
                )
                trip["completed_reason"] = "android_app"
            else:
                trip["end_at"] = None
            trip["mobile_protocol_version"] = protocol_version
            trip["mobile_app_version"] = str(body.get("appVersion") or "")[:32]
            trip["last_mobile_upload_at"] = utc_now().isoformat()

            recorder["trips"] = trips
            recorder["updated_at"] = utc_now().isoformat()
            await self.write_trip_recorder(recorder)

        return {
            "status": "ok",
            "updated_at": recorder["updated_at"],
            "tripId": server_trip_id,
            "created": created,
            "acceptedPointIds": accepted,
            "duplicatePointIds": duplicates,
            "acknowledgedPointIds": [str(point["point_id"]) for point in normalized_points],
            "storedPointCount": len(existing_points),
            "tripStatus": trip["status"],
        }

    async def update_trip_recorder_snapshot(self) -> dict[str, Any]:
        async with self.state_lock:
            return await self._update_trip_recorder_snapshot_locked()

    async def _update_trip_recorder_snapshot_locked(self) -> dict[str, Any]:
        now = utc_now()
        phone = self.hass.states.get(self.tracking_entity)
        if phone is None:
            return {
                "status": "error",
                "updated_at": now.isoformat(),
                "error": f"{self.tracking_entity} not found",
            }

        recorder = await self.read_trip_recorder()
        point = make_trip_point(phone, now)
        is_home = is_home_state(phone)
        previous_location = recorder.get("last_location") or "unknown"
        recorder["trips"] = recorder.get("trips") if isinstance(recorder.get("trips"), list) else []
        road_cache: dict[str, float | None] = {}

        if not is_home:
            trip = next((item for item in recorder["trips"] if item.get("id") == recorder.get("active_trip_id")), None)
            if trip is None and should_start_away_trip(recorder, point, previous_location):
                trip = {
                    "id": f"trip-{format_trip_id(now)}",
                    "title": f"Trip {format_trip_title(now)}",
                    "mode": "motor_vehicle",
                    "trip_type": "street",
                    "trip_type_source": "auto",
                    "status": "active",
                    "start_at": now.isoformat(),
                    "end_at": None,
                    "points": [],
                }
                recorder["trips"].append(trip)
                recorder["active_trip_id"] = trip["id"]
            if trip is not None:
                append_trip_point(trip, point)
                await self.update_trip_auto_classification(trip, road_cache)
                arrival_point = None
                if not await self.should_hold_off_road_trip_open(trip, road_cache):
                    arrival_point = get_away_arrival_point(trip)
                if arrival_point is not None:
                    trip["status"] = "complete"
                    trip["end_at"] = arrival_point.get("at")
                    trip["completed_reason"] = "stationary_away"
                    recorder["active_trip_id"] = None
        elif recorder.get("active_trip_id"):
            trip = next((item for item in recorder["trips"] if item.get("id") == recorder.get("active_trip_id")), None)
            if trip is not None:
                append_trip_point(trip, point)
                trip["status"] = "complete"
                trip["end_at"] = now.isoformat()
            recorder["active_trip_id"] = None

        recorder["last_location"] = "home" if is_home else "away"
        recorder["updated_at"] = now.isoformat()
        recorder["trips"] = trim_trip_history(recorder["trips"])
        await self.update_trip_auto_classifications(recorder["trips"], road_cache)
        await self.write_trip_recorder(recorder)

        visible_trips = [trip for trip in recorder["trips"] if not trip.get("hidden")]
        trips = visible_trips
        route_cache: dict[str, list[dict[str, float]]] = {}
        trip_summaries = [item for item in await asyncio.gather(*(self.summarize_trip_with_route(trip, route_cache) for trip in trips)) if item]
        all_trips = [item for item in await asyncio.gather(*(self.summarize_trip_with_route(trip, route_cache) for trip in visible_trips)) if item]
        active_trip = next((trip for trip in recorder["trips"] if trip.get("id") == recorder.get("active_trip_id")), None)
        active_trip_summary = await self.summarize_trip_with_route(active_trip, route_cache) if active_trip else None
        latest_trip_summary = active_trip_summary or (trip_summaries[-1] if trip_summaries else None)
        collection_summary = summarize_trip_collection(trip_summaries)
        route_groups = summarize_trip_route_groups(trip_summaries)

        return {
            "status": "ok",
            "updated_at": recorder["updated_at"],
            "tracking_entity": self.tracking_entity,
            "mode": "motor_vehicle",
            "home_state": "home" if is_home else "away",
            "previous_location": previous_location,
            "active_trip": active_trip_summary,
            "latest_trip": latest_trip_summary,
            "summary": {
                "all_history": len(trips),
                "last_7_days": len(get_trips_within_days(visible_trips, 7)),
                "active": bool(active_trip),
                "points": sum(len(get_trip_points(trip)) for trip in trips),
                "route_groups": len(route_groups),
                "total_duration_minutes": collection_summary["total_duration_minutes"],
                "total_distance_km": collection_summary["total_distance_km"],
                "average_speed_kmh": collection_summary["average_speed_kmh"],
            },
            "trips": trip_summaries,
            "route_groups": route_groups,
            "history": {
                "retained_days": "all",
                "summary": summarize_trip_collection(all_trips),
                "trips": all_trips,
                "route_groups": summarize_trip_route_groups(all_trips),
            },
            "error": None,
        }

    async def get_location_map_snapshot(self) -> dict[str, Any]:
        now_ms = utc_now().timestamp() * 1000
        wanted = set(self.location_entities) | {"zone.home"}
        locations = []
        for entity_id in wanted:
            entity = self.hass.states.get(entity_id)
            if entity is None:
                continue
            attrs = entity.attributes
            lat = to_number(attrs.get("latitude"))
            lon = to_number(attrs.get("longitude"))
            updated_at = entity.last_updated.isoformat() if entity.last_updated else None
            updated_ms = entity.last_updated.timestamp() * 1000 if entity.last_updated else None
            fresh = entity_id == "zone.home" or (updated_ms is not None and now_ms - updated_ms <= self.location_fresh_ms)
            location = {
                "entity_id": entity_id,
                "name": attrs.get("friendly_name") or entity_id,
                "state": entity.state or "unknown",
                "latitude": lat,
                "longitude": lon,
                "accuracy": to_number(attrs.get("gps_accuracy")),
                "updated_at": updated_at,
                "fresh": fresh,
            }
            if entity_id == "zone.home" or (
                fresh and lat is not None and lon is not None and location["state"] not in {"unknown", "unavailable"}
            ):
                locations.append(location)

        home = next((loc for loc in locations if loc["entity_id"] == "zone.home" and loc["latitude"] is not None), None)
        return {
            "status": "ok",
            "updated_at": utc_now().isoformat(),
            "home": {"lat": home["latitude"], "lon": home["longitude"]} if home else home_coordinates(self.hass),
            "radius_km": 100,
            "freshness_minutes": LOCATION_FRESH_MINUTES,
            "locations": locations,
            "error": None,
        }

    async def manage_trip_recorder(self, body: dict[str, Any]) -> dict[str, Any]:
        async with self.state_lock:
            return await self._manage_trip_recorder_locked(body)

    async def _manage_trip_recorder_locked(self, body: dict[str, Any]) -> dict[str, Any]:
        action = str(body.get("action") or "").strip().lower()
        trip_id = str(body.get("tripId") or body.get("id") or "").strip()
        if not trip_id:
            raise TripRecorderError("tripId is required")

        recorder = await self.read_trip_recorder()
        trips = recorder.get("trips") if isinstance(recorder.get("trips"), list) else []
        trip = next((item for item in trips if item.get("id") == trip_id), None)
        if trip is None:
            raise TripRecorderError("Trip not found", 404)

        if action == "rename":
            from_name = clean_trip_label(body.get("fromName"))
            to_name = clean_trip_label(body.get("toName"))
            if not from_name or not to_name:
                raise TripRecorderError("fromName and toName are required")
            trip["custom_from_name"] = from_name
            trip["custom_to_name"] = to_name
            trip["custom_route_label"] = f"{from_name} to {to_name}"
            if body.get("tripType") is not None or body.get("type") is not None:
                trip["trip_type"] = clean_trip_type(body.get("tripType") or body.get("type"))
                trip["trip_type_source"] = "manual"
            trip["hidden"] = False
        elif action == "set_type":
            trip["trip_type"] = clean_trip_type(body.get("tripType") or body.get("type"))
            trip["trip_type_source"] = "manual"
            trip["hidden"] = False
        elif action == "remove":
            trip["hidden"] = True
            trip["hidden_at"] = utc_now().isoformat()
            if recorder.get("active_trip_id") == trip_id:
                recorder["active_trip_id"] = None
        else:
            raise TripRecorderError("action must be rename, set_type, or remove")

        recorder["trips"] = trips
        recorder["updated_at"] = utc_now().isoformat()
        await self.write_trip_recorder(recorder)
        snapshot = await self._update_trip_recorder_snapshot_locked()
        return {
            "status": "ok",
            "action": action,
            "tripId": trip_id,
            "updated_at": recorder["updated_at"],
            "snapshot": snapshot,
        }

    async def read_trip_recorder(self) -> dict[str, Any]:
        def read_file() -> dict[str, Any]:
            try:
                return json.loads(self.state_path.read_text(encoding="utf-8"))
            except Exception:
                return {
                    "version": 1,
                    "updated_at": None,
                    "active_trip_id": None,
                    "last_location": "unknown",
                    "trips": [],
                }

        return await self.hass.async_add_executor_job(read_file)

    async def write_trip_recorder(self, recorder: dict[str, Any]) -> None:
        def write_file() -> None:
            self.state_path.parent.mkdir(parents=True, exist_ok=True)
            temporary_path = self.state_path.with_suffix(f"{self.state_path.suffix}.tmp")
            temporary_path.write_text(json.dumps(recorder, indent=2), encoding="utf-8")
            temporary_path.replace(self.state_path)

        await self.hass.async_add_executor_job(write_file)

    async def summarize_trip_with_route(self, trip: dict[str, Any] | None, cache: dict[str, Any]) -> dict[str, Any] | None:
        summary = summarize_trip(trip)
        if summary is None:
            return None
        if is_road_routed_trip(summary):
            route_points = await self.get_trip_route_points(trip or {}, cache)
            summary["route_points"] = route_points
            summary["route_source"] = "osrm" if len(route_points) > 1 else "gps"
        else:
            summary["route_points"] = get_trip_points(summary)
            summary["route_source"] = clean_trip_type(summary.get("trip_type"))
        summary["route"] = apply_custom_trip_route_names(classify_trip_route(summary, self.route_places), summary)
        return summary

    async def get_trip_route_points(self, trip: dict[str, Any], cache: dict[str, Any]) -> list[dict[str, float]]:
        waypoints = get_trip_route_waypoints(trip)
        if len(waypoints) < 2:
            return []
        cache_key = "|".join(f"{point['lat']:.6f},{point['lon']:.6f}" for point in waypoints)
        if cache_key in cache:
            return cache[cache_key]
        try:
            route_points = await self.fetch_osrm_route_geometry(waypoints)
            if not route_points_cover_trip(route_points, trip):
                _LOGGER.warning("Trip route geometry rejected for %s: OSRM geometry does not reach trip endpoints", trip.get("id", "unknown trip"))
                cache[cache_key] = []
                return []
            cache[cache_key] = route_points
            return route_points
        except Exception as err:  # noqa: BLE001
            _LOGGER.warning("Trip route geometry unavailable for %s: %s", trip.get("id", "unknown trip"), err)
            cache[cache_key] = []
            return []

    async def fetch_osrm_route_geometry(self, waypoints: list[dict[str, Any]]) -> list[dict[str, float]]:
        coordinates = ";".join(f"{point['lon']},{point['lat']}" for point in waypoints)
        url = f"{self.osrm_base_url}/route/v1/driving/{coordinates}?overview=full&geometries=geojson&alternatives=false&steps=false"
        session = async_get_clientsession(self.hass)
        async with session.get(url, timeout=12) as response:
            data = await response.json()
            if response.status >= 400 or data.get("code") != "Ok":
                raise RuntimeError(data.get("message") or data.get("code") or f"OSRM returned HTTP {response.status}")
        coordinates_out = data.get("routes", [{}])[0].get("geometry", {}).get("coordinates", [])
        return [
            {"lat": float(coord[1]), "lon": float(coord[0])}
            for coord in coordinates_out
            if isinstance(coord, list) and len(coord) >= 2 and is_finite(coord[0]) and is_finite(coord[1])
        ]

    async def update_trip_auto_classifications(self, trips: list[dict[str, Any]], cache: dict[str, float | None]) -> None:
        for trip in trips:
            await self.update_trip_auto_classification(trip, cache)

    async def update_trip_auto_classification(self, trip: dict[str, Any], cache: dict[str, float | None]) -> None:
        if trip.get("source") == "arcgis" or trip.get("trip_type_source") == "manual":
            return
        if clean_trip_type(trip.get("trip_type")) != "street":
            return
        result = await self.detect_off_road_trip(trip, cache)
        if not result["off_road"]:
            return
        trip["trip_type"] = "off_road"
        trip["trip_type_source"] = "auto"
        trip["auto_trip_type_reason"] = result["reason"]
        trip["auto_trip_type_at"] = utc_now().isoformat()

    async def should_hold_off_road_trip_open(self, trip: dict[str, Any], cache: dict[str, float | None]) -> bool:
        if clean_trip_type(trip.get("trip_type")) != "off_road":
            return False
        latest = next((point for point in reversed(get_trip_points(trip)) if is_finite(point.get("lat")) and is_finite(point.get("lon"))), None)
        if latest is None:
            return False
        distance_m = await self.get_nearest_road_distance_m(latest, cache)
        if distance_m is None:
            return True
        trip["latest_road_distance_m"] = round(distance_m)
        return distance_m > OFF_ROAD_NEAR_ROAD_M

    async def detect_off_road_trip(self, trip: dict[str, Any], cache: dict[str, float | None]) -> dict[str, Any]:
        points = sample_trip_points(trip, OFF_ROAD_SAMPLE_LIMIT)
        if len(points) < OFF_ROAD_MIN_POINTS:
            return {"off_road": False, "reason": "not_enough_points"}
        duration_minutes = get_trip_duration_minutes(trip)
        distance_km = get_trip_distance_meters(get_trip_points(trip)) / 1000
        average_speed_kmh = (distance_km / duration_minutes) * 60 if duration_minutes > 0 else 0
        if average_speed_kmh > OFF_ROAD_MAX_AVERAGE_SPEED_KMH:
            return {"off_road": False, "reason": "too_fast_for_off_road"}

        distances = [await self.get_nearest_road_distance_m(point, cache) for point in points]
        known_distances = [distance for distance in distances if distance is not None]
        if len(known_distances) < max(3, len(points) // 2):
            return {"off_road": False, "reason": "insufficient_road_matches"}
        far_count = sum(1 for distance in known_distances if distance > OFF_ROAD_FAR_FROM_ROAD_M)
        far_ratio = far_count / len(known_distances)
        if far_count >= OFF_ROAD_MIN_FAR_POINTS and far_ratio >= OFF_ROAD_FAR_RATIO:
            return {
                "off_road": True,
                "reason": f"{far_count}/{len(known_distances)} sampled GPS points are more than {OFF_ROAD_FAR_FROM_ROAD_M} m from OSRM roads",
            }
        if is_slow_local_off_road_candidate(
            trip,
            average_speed_kmh,
            distance_km,
            self.route_places,
            self.off_road_reference_place,
        ):
            return {
                "off_road": True,
                "reason": f"slow local trip at {average_speed_kmh:.1f} km/h in a cottage/northern off-road area",
            }
        return {"off_road": False, "reason": "near_known_roads"}

    async def get_nearest_road_distance_m(self, point: dict[str, Any], cache: dict[str, float | None]) -> float | None:
        if not is_finite(point.get("lat")) or not is_finite(point.get("lon")):
            return None
        cache_key = f"{float(point['lat']):.5f},{float(point['lon']):.5f}"
        if cache_key in cache:
            return cache[cache_key]
        url = f"{self.osrm_base_url}/nearest/v1/driving/{float(point['lon'])},{float(point['lat'])}?number=1"
        try:
            session = async_get_clientsession(self.hass)
            async with session.get(url, timeout=5) as response:
                data = await response.json()
                if response.status >= 400 or data.get("code") != "Ok":
                    cache[cache_key] = None
                    return None
            waypoints = data.get("waypoints") if isinstance(data, dict) else None
            distance = waypoints[0].get("distance") if isinstance(waypoints, list) and waypoints else None
            cache[cache_key] = float(distance) if is_finite(distance) else None
            return cache[cache_key]
        except Exception as err:  # noqa: BLE001
            _LOGGER.debug("Nearest road lookup unavailable for trip point: %s", err)
            cache[cache_key] = None
            return None


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def to_number(value: Any) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number == number else None


def is_finite(value: Any) -> bool:
    return to_number(value) is not None


def clean_trip_label(value: Any) -> str:
    return " ".join(str(value or "").strip().split())[:48]


def parse_location_entities(value: Any, tracking_entity: str) -> list[str]:
    entities = value if isinstance(value, list) else [tracking_entity]
    cleaned = [str(entity).strip() for entity in entities if str(entity).strip()]
    return list(dict.fromkeys(cleaned or [tracking_entity]))


def parse_route_places(value: Any, hass: HomeAssistant) -> list[dict[str, Any]]:
    places = []
    configured = value if isinstance(value, list) else []
    for item in configured:
        if not isinstance(item, dict):
            continue
        lat = to_number(item.get("lat") if item.get("lat") is not None else item.get("latitude"))
        lon = to_number(item.get("lon") if item.get("lon") is not None else item.get("longitude"))
        key = "".join(char.lower() if char.isalnum() else "_" for char in str(item.get("key") or "")).strip("_")
        name = clean_trip_label(item.get("name") or key.replace("_", " ").title())
        if not key or not name or lat is None or lon is None or not (-90 <= lat <= 90) or not (-180 <= lon <= 180):
            continue
        places.append({"key": key, "name": name, "lat": lat, "lon": lon})

    if not any(place["key"] == "home" for place in places):
        home = home_coordinates(hass)
        if home["lat"] != 0 or home["lon"] != 0:
            places.insert(0, {"key": "home", "name": "Home", **home})
    return places


def parse_off_road_reference_place(value: Any) -> dict[str, Any] | None:
    if not isinstance(value, dict):
        return None
    lat = to_number(value.get("lat") if value.get("lat") is not None else value.get("latitude"))
    lon = to_number(value.get("lon") if value.get("lon") is not None else value.get("longitude"))
    radius_m = to_number(value.get("radius_m")) or OFF_ROAD_COTTAGE_RADIUS_M
    if lat is None or lon is None or not (-90 <= lat <= 90) or not (-180 <= lon <= 180):
        return None
    return {"lat": lat, "lon": lon, "radius_m": max(100.0, radius_m)}


def home_coordinates(hass: HomeAssistant) -> dict[str, float]:
    lat = to_number(getattr(hass.config, "latitude", None))
    lon = to_number(getattr(hass.config, "longitude", None))
    return {"lat": lat if lat is not None else 0.0, "lon": lon if lon is not None else 0.0}


def clean_identifier(value: Any, field_name: str) -> str:
    text = str(value or "").strip()
    if not text or len(text) > 128:
        raise TripRecorderError(f"{field_name} must be between 1 and 128 characters")
    return text


def make_mobile_trip_id(device_id: str, source_trip_id: str) -> str:
    digest = hashlib.sha256(f"{device_id}\n{source_trip_id}".encode("utf-8")).hexdigest()[:24]
    return f"mobile-{digest}"


def clean_mobile_date(value: Any, field_name: str = "date") -> str | None:
    if value is None or str(value).strip() == "":
        return None
    try:
        parsed = datetime.fromisoformat(str(value).strip().replace("Z", "+00:00"))
    except (TypeError, ValueError) as err:
        raise TripRecorderError(f"{field_name} must be an ISO 8601 timestamp") from err
    if parsed.tzinfo is None:
        raise TripRecorderError(f"{field_name} must include a timezone")
    return parsed.astimezone(timezone.utc).isoformat()


def clean_mobile_number(
    value: Any,
    field_name: str,
    minimum: float,
    maximum: float,
    *,
    required: bool = False,
) -> float | None:
    number = to_number(value)
    if number is None:
        if required:
            raise TripRecorderError(f"{field_name} is required")
        return None
    if number < minimum or number > maximum:
        raise TripRecorderError(f"{field_name} must be between {minimum} and {maximum}")
    return number


def normalize_mobile_point(value: Any, source_trip_id: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise TripRecorderError("each point must be an object")
    point_id = clean_identifier(value.get("pointId"), "point.pointId")
    try:
        sequence = int(value.get("sequence"))
    except (TypeError, ValueError) as err:
        raise TripRecorderError("point.sequence must be an integer") from err
    if sequence < 0:
        raise TripRecorderError("point.sequence cannot be negative")

    point = {
        "point_id": point_id,
        "source_trip_id": source_trip_id,
        "sequence": sequence,
        "at": clean_mobile_date(value.get("at"), "point.at"),
        "lat": clean_mobile_number(value.get("latitude"), "point.latitude", -90, 90, required=True),
        "lon": clean_mobile_number(value.get("longitude"), "point.longitude", -180, 180, required=True),
        "altitude": clean_mobile_number(value.get("altitudeMeters"), "point.altitudeMeters", -1000, 15000),
        "accuracy": clean_mobile_number(value.get("accuracyMeters"), "point.accuracyMeters", 0, 100000),
        "vertical_accuracy": clean_mobile_number(
            value.get("verticalAccuracyMeters"), "point.verticalAccuracyMeters", 0, 100000
        ),
        "speed": clean_mobile_number(value.get("speedMps"), "point.speedMps", 0, 250),
        "bearing": clean_mobile_number(value.get("bearingDegrees"), "point.bearingDegrees", 0, 360),
        "pressure_hpa": clean_mobile_number(value.get("pressureHpa"), "point.pressureHpa", 100, 1200),
        "acceleration_rms_ms2": clean_mobile_number(
            value.get("accelerationRmsMs2"), "point.accelerationRmsMs2", 0, 200
        ),
        "acceleration_peak_ms2": clean_mobile_number(
            value.get("accelerationPeakMs2"), "point.accelerationPeakMs2", 0, 200
        ),
        "gyroscope_peak_rads": clean_mobile_number(
            value.get("gyroscopePeakRadS"), "point.gyroscopePeakRadS", 0, 100
        ),
        "battery_percent": clean_mobile_number(value.get("batteryPercent"), "point.batteryPercent", 0, 100),
        "satellite_count": clean_mobile_number(value.get("satelliteCount"), "point.satelliteCount", 0, 200),
        "network_type": str(value.get("networkType") or "unknown").strip().lower()[:24],
        "is_charging": bool(value.get("isCharging")),
        "state": "not_home",
    }
    if point["at"] is None:
        raise TripRecorderError("point.at is required")
    return {key: item for key, item in point.items() if item is not None}


def clean_trip_title(value: Any, previous: Any, points: list[dict[str, Any]]) -> str:
    title = " ".join(str(value or previous or "").strip().split())[:80]
    if title:
        return title
    date = parse_date(points[0].get("at")) if points else utc_now()
    return f"Phone trip {format_trip_title(date)}"


def clean_trip_type(value: Any) -> str:
    text = str(value or "").strip().lower().replace("-", "_").replace(" ", "_")
    if text == "offroad":
        text = "off_road"
    if text == "steet":
        text = "street"
    return text if text in TRIP_TYPE_LABELS else "street"


def trip_type_label(value: Any) -> str:
    return TRIP_TYPE_LABELS.get(clean_trip_type(value), TRIP_TYPE_LABELS["street"])


def is_road_routed_trip(trip: dict[str, Any]) -> bool:
    return clean_trip_type(trip.get("trip_type") or trip.get("mode")) == "street"


def make_trip_point(entity: Any, fallback_date: datetime) -> dict[str, Any]:
    attrs = entity.attributes
    return {
        "at": entity.last_updated.isoformat() if entity.last_updated else fallback_date.isoformat(),
        "lat": to_number(attrs.get("latitude")),
        "lon": to_number(attrs.get("longitude")),
        "altitude": to_number(attrs.get("altitude")),
        "accuracy": to_number(attrs.get("gps_accuracy")),
        "vertical_accuracy": to_number(attrs.get("vertical_accuracy")),
        "speed": to_number(attrs.get("speed")),
        "state": entity.state or "unknown",
    }


def append_trip_point(trip: dict[str, Any], point: dict[str, Any]) -> None:
    if point.get("lat") is None or point.get("lon") is None:
        return
    trip["points"] = get_trip_points(trip)
    previous = trip["points"][-1] if trip["points"] else None
    if previous:
        distance_m = haversine_meters(previous["lat"], previous["lon"], point["lat"], point["lon"])
        elapsed_s = abs((parse_date(point.get("at")) - parse_date(previous.get("at"))).total_seconds())
        if distance_m < 25 and elapsed_s < 5 * 60:
            return
    trip["points"].append(point)


def get_away_arrival_point(trip: dict[str, Any]) -> dict[str, Any] | None:
    points = get_trip_points(trip)
    if trip.get("status") != "active" or len(points) < 3:
        return None
    latest = points[-1]
    cluster_start_index = len(points) - 1
    for index in range(len(points) - 2, -1, -1):
        if haversine_meters(points[index]["lat"], points[index]["lon"], latest["lat"], latest["lon"]) > 250:
            break
        cluster_start_index = index
    if cluster_start_index == 0:
        return None
    arrival = points[cluster_start_index]
    distance_from_start = haversine_meters(points[0]["lat"], points[0]["lon"], arrival["lat"], arrival["lon"])
    stationary_minutes = (parse_date(latest.get("at")) - parse_date(arrival.get("at"))).total_seconds() / 60
    if distance_from_start >= 500 and stationary_minutes >= 10:
        return arrival
    return None


def is_home_state(entity: Any) -> bool:
    state = str(entity.state or "").lower()
    zones = entity.attributes.get("in_zones")
    zones = zones if isinstance(zones, list) else []
    return state == "home" or "zone.home" in zones


def trim_trip_history(trips: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return trips


def get_trips_within_days(trips: list[dict[str, Any]], days: int) -> list[dict[str, Any]]:
    cutoff = utc_now() - timedelta(days=days)
    return [trip for trip in trips if trip.get("imported") or parse_date(trip.get("end_at") or trip.get("start_at")) >= cutoff]


def summarize_trip_collection(trips: list[dict[str, Any]]) -> dict[str, Any]:
    total_distance_km = sum(float(trip.get("distance_km") or 0) for trip in trips)
    total_duration_minutes = sum(float(trip.get("duration_minutes") or 0) for trip in trips)
    elevation = summarize_elevation_collection(trips)
    completed = sum(1 for trip in trips if trip.get("status") == "complete")
    summary = {
        "count": len(trips),
        "complete": completed,
        "active": len(trips) - completed,
        "total_distance_km": round(total_distance_km, 1),
        "total_duration_minutes": round(total_duration_minutes),
        "average_speed_kmh": round((total_distance_km / total_duration_minutes) * 60, 1) if total_duration_minutes > 0 else 0,
        "min_average_speed_kmh": min_number([float(trip.get("average_speed_kmh") or 0) for trip in trips], 1),
        "max_average_speed_kmh": max_number([float(trip.get("average_speed_kmh") or 0) for trip in trips], 1),
        "point_count": sum(int(trip.get("point_count") or 0) for trip in trips),
    }
    summary.update(elevation)
    return summary


def summarize_trip_route_groups(trips: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: dict[str, dict[str, Any]] = {}
    for trip in trips:
        route = trip.get("route") or classify_trip_route(trip)
        if not route or not route.get("group_key"):
            continue
        group = groups.setdefault(
            route["group_key"],
            {
                "group_key": route["group_key"],
                "reverse_group_key": route["reverse_group_key"],
                "label": route["label"],
                "start": route["start"],
                "end": route["end"],
                "trips": [],
            },
        )
        group["trips"].append(trip)
    return sorted((summarize_route_group(group) for group in groups.values()), key=lambda item: (-item["count"], item["label"]))


def summarize_route_group(group: dict[str, Any]) -> dict[str, Any]:
    trips = sorted(
        [trip for trip in group["trips"] if is_finite(trip.get("duration_minutes")) and is_finite(trip.get("distance_km"))],
        key=lambda trip: parse_date(trip.get("start_at")),
    )
    durations = [float(trip.get("duration_minutes") or 0) for trip in trips]
    distances = [float(trip.get("distance_km") or 0) for trip in trips]
    speeds = [float(trip.get("average_speed_kmh") or 0) for trip in trips]
    total_duration_minutes = sum(durations)
    total_distance_km = sum(distances)
    elevation = summarize_elevation_collection(trips)
    best = min(trips, key=lambda trip: float(trip.get("duration_minutes") or 0), default=None)
    worst = max(trips, key=lambda trip: float(trip.get("duration_minutes") or 0), default=None)
    latest = trips[-1] if trips else None
    summary = {
        "group_key": group["group_key"],
        "reverse_group_key": group["reverse_group_key"],
        "label": group["label"],
        "start": group["start"],
        "end": group["end"],
        "count": len(trips),
        "average_duration_minutes": average_number(durations, 0),
        "min_duration_minutes": min_number(durations, 0),
        "max_duration_minutes": max_number(durations, 0),
        "duration_spread_minutes": max_number(durations, 0) - min_number(durations, 0),
        "average_distance_km": average_number(distances, 1),
        "min_distance_km": min_number(distances, 1),
        "max_distance_km": max_number(distances, 1),
        "total_distance_km": round(total_distance_km, 1),
        "total_duration_minutes": round(total_duration_minutes),
        "average_speed_kmh": round((total_distance_km / total_duration_minutes) * 60, 1) if total_duration_minutes > 0 else 0,
        "min_average_speed_kmh": min_number(speeds, 1),
        "max_average_speed_kmh": max_number(speeds, 1),
        "best_trip_id": best.get("id") if best else None,
        "best_duration_minutes": float(best.get("duration_minutes") or 0) if best else 0,
        "worst_trip_id": worst.get("id") if worst else None,
        "worst_duration_minutes": float(worst.get("duration_minutes") or 0) if worst else 0,
        "latest_trip_id": latest.get("id") if latest else None,
        "latest_duration_minutes": float(latest.get("duration_minutes") or 0) if latest else 0,
        "latest_at": latest.get("start_at") if latest else None,
        "sample_trip_ids": [trip.get("id") for trip in trips[-6:]],
    }
    summary.update(elevation)
    return summary


def summarize_trip(trip: dict[str, Any] | None) -> dict[str, Any] | None:
    if not trip:
        return None
    points = get_trip_points(trip)
    distance_km = round(get_trip_distance_meters(points) / 1000, 1)
    if trip.get("source") == "arcgis" and is_finite(trip.get("source_length_m")):
        distance_km = round(float(trip.get("source_length_m") or 0) / 1000, 1)
    duration_minutes = get_trip_duration_minutes(trip)
    average_speed_kmh = round((distance_km / duration_minutes) * 60, 1) if duration_minutes > 0 else 0
    elevation = summarize_elevation_points(points)
    summary = {
        "id": trip.get("id"),
        "title": trip.get("title"),
        "mode": trip.get("mode") or "motor_vehicle",
        "trip_type": clean_trip_type(trip.get("trip_type") or trip.get("mode")),
        "trip_type_label": trip_type_label(trip.get("trip_type") or trip.get("mode")),
        "status": trip.get("status") or "complete",
        "start_at": trip.get("start_at"),
        "end_at": trip.get("end_at"),
        "completed_reason": trip.get("completed_reason"),
        "duration_minutes": duration_minutes,
        "distance_km": distance_km,
        "average_speed_kmh": average_speed_kmh,
        "max_reported_speed_kmh": get_max_reported_speed_kmh(points),
        "point_count": len(points),
        "custom_from_name": trip.get("custom_from_name") or "",
        "custom_to_name": trip.get("custom_to_name") or "",
        "custom_route_label": trip.get("custom_route_label") or "",
        "list_suppressed": bool(trip.get("list_suppressed")),
        "trail_status": trip.get("trail_status") or "",
        "trail_location": trip.get("trail_location") or "",
        "trail_class": trip.get("trail_class") or "",
        "trail_use": trip.get("trail_use") or "",
        "source_url": trip.get("source_url") or "",
        "points": points,
    }
    summary.update(elevation)
    return summary


def classify_trip_route(
    trip: dict[str, Any],
    route_places: list[dict[str, Any]] | None = None,
) -> dict[str, Any] | None:
    points = [point for point in get_trip_points(trip) if is_finite(point.get("lat")) and is_finite(point.get("lon"))]
    if len(points) < 2:
        return None
    start = classify_route_endpoint(points[0], route_places)
    end = classify_route_endpoint(points[-1], route_places)
    if not start or not end:
        return None
    return {
        "group_key": f"{start['key']}__to__{end['key']}",
        "reverse_group_key": f"{end['key']}__to__{start['key']}",
        "label": f"{start['name']} to {end['name']}",
        "start": start,
        "end": end,
    }


def classify_route_endpoint(
    point: dict[str, Any],
    route_places: list[dict[str, Any]] | None = None,
) -> dict[str, Any] | None:
    lat = to_number(point.get("lat"))
    lon = to_number(point.get("lon"))
    if lat is None or lon is None:
        return None
    nearest = None
    for place in route_places or []:
        distance_m = haversine_meters(lat, lon, place["lat"], place["lon"])
        if nearest is None or distance_m < nearest["distance_m"]:
            nearest = {
                "key": place["key"],
                "name": place["name"],
                "lat": place["lat"],
                "lon": place["lon"],
                "distance_m": round(distance_m),
            }
    if nearest and nearest["distance_m"] <= 900:
        return nearest
    lat_bucket = round(lat * 200) / 200
    lon_bucket = round(lon * 200) / 200
    return {
        "key": f"area_{lat_bucket:.3f}_{lon_bucket:.3f}".replace("-", "_").replace(".", "_"),
        "name": f"Area {lat_bucket:.3f}, {lon_bucket:.3f}",
        "lat": lat_bucket,
        "lon": lon_bucket,
        "distance_m": 0,
    }


def apply_custom_trip_route_names(route: dict[str, Any] | None, trip: dict[str, Any]) -> dict[str, Any] | None:
    if route is None:
        return None
    from_name = clean_trip_label(trip.get("custom_from_name"))
    to_name = clean_trip_label(trip.get("custom_to_name"))
    route_label = clean_trip_label(trip.get("custom_route_label"))
    if not from_name and not to_name and not route_label:
        return route
    start = dict(route["start"])
    end = dict(route["end"])
    if from_name:
        start["name"] = from_name
    if to_name:
        end["name"] = to_name
    route = dict(route)
    route["start"] = start
    route["end"] = end
    route["label"] = route_label or f"{start['name']} to {end['name']}"
    if route_label:
        route["group_key"] = "custom_" + "".join(char.lower() if char.isalnum() else "_" for char in route_label).strip("_")
    return route


def get_trip_route_waypoints(trip: dict[str, Any]) -> list[dict[str, Any]]:
    points = [
        {"lat": float(point["lat"]), "lon": float(point["lon"]), "at": point.get("at")}
        for point in get_trip_points(trip)
        if is_finite(point.get("lat")) and is_finite(point.get("lon"))
    ]
    if len(points) < 2:
        return points
    end_time = parse_date(trip.get("end_at") or utc_now().isoformat())
    clipped = [point for point in points if parse_date(point.get("at")) <= end_time + timedelta(seconds=60)]
    source = clipped if len(clipped) >= 2 else points
    waypoints = [source[0]]
    trip_distance_m = get_trip_distance_meters(source)
    minimum_gap_m = 125 if trip_distance_m < 5000 else 1000
    for point in source[1:-1]:
        previous = waypoints[-1]
        if haversine_meters(previous["lat"], previous["lon"], point["lat"], point["lon"]) >= minimum_gap_m:
            waypoints.append(point)
    last = source[-1]
    previous = waypoints[-1]
    if haversine_meters(previous["lat"], previous["lon"], last["lat"], last["lon"]) >= 100:
        waypoints.append(last)
    return limit_route_waypoints(waypoints, 25)


def limit_route_waypoints(waypoints: list[dict[str, Any]], limit: int) -> list[dict[str, Any]]:
    if len(waypoints) <= limit:
        return waypoints
    if limit <= 2:
        return [waypoints[0], waypoints[-1]]
    last_index = len(waypoints) - 1
    selected_indexes = {0, last_index}
    for slot in range(1, limit - 1):
        selected_indexes.add(round(slot * last_index / (limit - 1)))
    return [waypoints[index] for index in sorted(selected_indexes)]


def should_start_away_trip(recorder: dict[str, Any], point: dict[str, Any], previous_location: str) -> bool:
    if previous_location != "away":
        return True
    if not is_finite(point.get("lat")) or not is_finite(point.get("lon")):
        return False
    trips = recorder.get("trips") if isinstance(recorder.get("trips"), list) else []
    for trip in reversed(trips):
        if trip.get("status") != "complete":
            continue
        points = [
            trip_point
            for trip_point in get_trip_points(trip)
            if is_finite(trip_point.get("lat")) and is_finite(trip_point.get("lon"))
        ]
        if not points:
            continue
        last = points[-1]
        return haversine_meters(last["lat"], last["lon"], point["lat"], point["lon"]) >= 500
    return True


def route_points_cover_trip(route_points: list[dict[str, Any]], trip: dict[str, Any]) -> bool:
    gps_points = [
        point
        for point in get_trip_points(trip)
        if is_finite(point.get("lat")) and is_finite(point.get("lon"))
    ]
    if len(route_points) < 2 or len(gps_points) < 2:
        return False
    start_gap_m = haversine_meters(route_points[0]["lat"], route_points[0]["lon"], gps_points[0]["lat"], gps_points[0]["lon"])
    end_gap_m = haversine_meters(route_points[-1]["lat"], route_points[-1]["lon"], gps_points[-1]["lat"], gps_points[-1]["lon"])
    return start_gap_m <= 5000 and end_gap_m <= 5000


def get_trip_points(trip: dict[str, Any] | None) -> list[dict[str, Any]]:
    points = trip.get("points") if isinstance(trip, dict) else []
    return points if isinstance(points, list) else []


def sample_trip_points(trip: dict[str, Any], limit: int) -> list[dict[str, Any]]:
    points = [
        point
        for point in get_trip_points(trip)
        if is_finite(point.get("lat")) and is_finite(point.get("lon"))
    ]
    if len(points) <= limit:
        return points
    last_index = len(points) - 1
    selected_indexes = {0, last_index}
    for slot in range(1, limit - 1):
        selected_indexes.add(round(slot * last_index / (limit - 1)))
    return [points[index] for index in sorted(selected_indexes)]


def is_slow_local_off_road_candidate(
    trip: dict[str, Any],
    average_speed_kmh: float,
    distance_km: float,
    route_places: list[dict[str, Any]] | None = None,
    reference_place: dict[str, Any] | None = None,
) -> bool:
    if average_speed_kmh > OFF_ROAD_LOCAL_MAX_AVERAGE_SPEED_KMH:
        return False
    if distance_km < OFF_ROAD_LOCAL_MIN_DISTANCE_KM or distance_km > OFF_ROAD_LOCAL_MAX_DISTANCE_KM:
        return False
    route = classify_trip_route(trip, route_places)
    endpoint_keys = {
        (route.get("start") or {}).get("key"),
        (route.get("end") or {}).get("key"),
    } if route else set()
    if endpoint_keys & {"home", "work", "office"}:
        return False
    points = sample_trip_points(trip, OFF_ROAD_SAMPLE_LIMIT)
    reference = reference_place or {}
    reference_lat = to_number(reference.get("lat"))
    reference_lon = to_number(reference.get("lon"))
    reference_radius_m = to_number(reference.get("radius_m")) or OFF_ROAD_COTTAGE_RADIUS_M
    return any(
        float(point["lat"]) >= OFF_ROAD_LOCAL_AREA_MIN_LAT
        or (
            reference_lat is not None
            and reference_lon is not None
            and haversine_meters(point["lat"], point["lon"], reference_lat, reference_lon) <= reference_radius_m
        )
        for point in points
    )


def get_trip_duration_minutes(trip: dict[str, Any]) -> int:
    if trip.get("duration_estimated") and is_finite(trip.get("duration_minutes")):
        return max(1, round(float(trip.get("duration_minutes") or 0)))
    start = parse_date(trip.get("start_at"))
    end = parse_date(trip.get("end_at") or utc_now().isoformat())
    if end < start:
        return 0
    return round((end - start).total_seconds() / 60)


def get_trip_distance_meters(points: list[dict[str, Any]]) -> float:
    total = 0.0
    for index in range(1, len(points)):
        total += haversine_meters(points[index - 1].get("lat"), points[index - 1].get("lon"), points[index].get("lat"), points[index].get("lon"))
    return total


def get_max_reported_speed_kmh(points: list[dict[str, Any]]) -> float:
    speeds = [float(point.get("speed")) * 3.6 for point in points if is_finite(point.get("speed")) and float(point.get("speed")) > 0]
    return round(max(speeds), 1) if speeds else 0


def summarize_elevation_collection(trips: list[dict[str, Any]]) -> dict[str, Any]:
    gains = [float(trip.get("elevation_gain_m") or 0) for trip in trips if is_finite(trip.get("elevation_gain_m"))]
    losses = [float(trip.get("elevation_loss_m") or 0) for trip in trips if is_finite(trip.get("elevation_loss_m"))]
    mins = [float(trip.get("min_elevation_m") or 0) for trip in trips if is_finite(trip.get("min_elevation_m"))]
    maxes = [float(trip.get("max_elevation_m") or 0) for trip in trips if is_finite(trip.get("max_elevation_m"))]
    elevation_point_count = sum(int(trip.get("elevation_point_count") or 0) for trip in trips)
    minimum = min(mins) if mins else None
    maximum = max(maxes) if maxes else None
    return {
        "elevation_point_count": elevation_point_count,
        "elevation_gain_m": round(sum(gains)),
        "elevation_loss_m": round(sum(losses)),
        "min_elevation_m": round(minimum) if minimum is not None else None,
        "max_elevation_m": round(maximum) if maximum is not None else None,
        "elevation_range_m": round(maximum - minimum) if minimum is not None and maximum is not None else None,
        "average_elevation_gain_m": average_number(gains, 0),
        "average_elevation_loss_m": average_number(losses, 0),
    }


def summarize_elevation_points(points: list[dict[str, Any]]) -> dict[str, Any]:
    samples = [
        {"at": point.get("at"), "altitude": float(point.get("altitude"))}
        for point in points
        if is_finite(point.get("altitude"))
    ]
    if not samples:
        return {
            "elevation_point_count": 0,
            "elevation_gain_m": None,
            "elevation_loss_m": None,
            "min_elevation_m": None,
            "max_elevation_m": None,
            "elevation_range_m": None,
        }
    gain = 0.0
    loss = 0.0
    previous = samples[0]["altitude"]
    for sample in samples[1:]:
        delta = sample["altitude"] - previous
        if abs(delta) >= 1:
            if delta > 0:
                gain += delta
            else:
                loss += abs(delta)
        previous = sample["altitude"]
    values = [sample["altitude"] for sample in samples]
    minimum = min(values)
    maximum = max(values)
    return {
        "elevation_point_count": len(samples),
        "elevation_gain_m": round(gain),
        "elevation_loss_m": round(loss),
        "min_elevation_m": round(minimum),
        "max_elevation_m": round(maximum),
        "elevation_range_m": round(maximum - minimum),
    }


def haversine_meters(lat1: Any, lon1: Any, lat2: Any, lon2: Any) -> float:
    lat1 = float(lat1)
    lon1 = float(lon1)
    lat2 = float(lat2)
    lon2 = float(lon2)
    radius = 6371000
    d_lat = radians(lat2 - lat1)
    d_lon = radians(lon2 - lon1)
    a = sin(d_lat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(d_lon / 2) ** 2
    return 2 * radius * atan2(sqrt(a), sqrt(1 - a))


def average_number(values: list[float], decimals: int = 1) -> float:
    usable = [float(value) for value in values if is_finite(value)]
    return round(sum(usable) / len(usable), decimals) if usable else 0


def min_number(values: list[float], decimals: int = 1) -> float:
    usable = [float(value) for value in values if is_finite(value)]
    return round(min(usable), decimals) if usable else 0


def max_number(values: list[float], decimals: int = 1) -> float:
    usable = [float(value) for value in values if is_finite(value)]
    return round(max(usable), decimals) if usable else 0


def parse_date(value: Any) -> datetime:
    if not value:
        return datetime.fromtimestamp(0, timezone.utc)
    try:
        text = str(value).replace("Z", "+00:00")
        parsed = datetime.fromisoformat(text)
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)
    except ValueError:
        return datetime.fromtimestamp(0, timezone.utc)


def format_trip_id(date: datetime) -> str:
    return date.strftime("%Y%m%dT%H%M%SZ")


def format_trip_title(date: datetime) -> str:
    local = date.astimezone()
    return local.strftime("%b %d, %I:%M %p")
