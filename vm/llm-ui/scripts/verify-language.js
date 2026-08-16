import assert from "node:assert/strict";
import {
  auditHomeAssistantLanguageCoverage,
  getCommonAssistantReply,
  getHomeAssistantVoiceResponse,
  rankHomeAssistantEntities
} from "../home-assistant-language.js";

const state = (entityId, friendlyName, value, attributes = {}) => ({
  entity_id: entityId,
  state: value,
  attributes: { friendly_name: friendlyName, ...attributes }
});

const states = [
  state("sensor.garage_temperature", "Garage Temperature", "18.6", { device_class: "temperature", unit_of_measurement: "°C" }),
  state("sensor.living_room_humidity", "Living Room Humidity", "47", { device_class: "humidity", unit_of_measurement: "%" }),
  state("sensor.garage_sensor_battery", "Garage Climate Sensor Battery", "16", { device_class: "battery", unit_of_measurement: "%" }),
  state("sensor.phone_battery", "Phone Battery Level", "73", { device_class: "battery", unit_of_measurement: "%" }),
  state("cover.garage_door_left", "Garage Door Left", "closed", { device_class: "garage" }),
  state("cover.garage_door_right", "Garage Door Right", "open", { device_class: "garage" }),
  state("lock.front_door", "Front Door Lock", "locked"),
  state("switch.hall_light", "Hall Light", "on"),
  state("switch.kitchen_lights", "Kitchen Lights", "off"),
  state("media_player.family_room_tv", "Family Room TV", "playing", { device_class: "tv", media_title: "Nature Documentary" }),
  state("camera.garage", "Garage Camera", "idle"),
  state("update.home_assistant_core_update", "Home Assistant Core Update", "on"),
  state("automation.night_lights", "Night Lights", "on"),
  state("binary_sensor.wan", "Internet Connection", "on", { device_class: "connectivity" }),
  state("sensor.backup_next", "Backup Next Scheduled", "2026-08-16T08:49:00+00:00", { device_class: "timestamp" }),
  state("sensor.office_temperature", "Office Temperature", "unavailable", { device_class: "temperature", unit_of_measurement: "°C" })
];

const cases = [
  ["What's the garage temperature?", "sensor.garage_temperature", /18\.6°C/],
  ["How humid is the living room?", "sensor.living_room_humidity", /47%/],
  ["How much charge does the garage climate sensor have?", "sensor.garage_sensor_battery", /16%/],
  ["What's my phone charge?", "sensor.phone_battery", /73%/],
  ["Is the left garage door open?", "cover.garage_door_left", /closed/],
  ["Is the right garage door closed?", "cover.garage_door_right", /open/],
  ["Is the front door locked?", "lock.front_door", /locked/],
  ["What is the family room TV playing?", "media_player.family_room_tv", /Nature Documentary/],
  ["What is the garage camera status?", "camera.garage", /idle/],
  ["Is Home Assistant Core up to date?", "update.home_assistant_core_update", /update available/],
  ["When is the next backup scheduled?", "sensor.backup_next", /Sunday, August 16/],
  ["What is sensor.office_temperature?", "sensor.office_temperature", /unavailable/]
];

for (const [prompt, entityId, replyPattern] of cases) {
  const result = getHomeAssistantVoiceResponse(prompt, states, { timeZone: "America/Toronto" });
  assert(result, `No result for: ${prompt}`);
  assert.equal(result.states[0]?.entity_id, entityId, `Wrong entity for: ${prompt}`);
  assert.match(result.reply, replyPattern, `Wrong reply for: ${prompt}`);
  assert.doesNotMatch(result.reply, /As an AI|language model|Tim is|Tim's/i, `Persona leak for: ${prompt}`);
}

const aggregates = [
  ["Which lights are on?", /1 light is on: Hall Light/],
  ["Are any doors open?", /1 door is open: Garage Door Right/],
  ["Are any locks unlocked?", /No available locks are unlocked/],
  ["Are any updates available?", /1 update is available/],
  ["Which automations are enabled?", /1 automation is enabled/],
  ["Are any batteries low?", /1 battery is low: Garage Climate Sensor Battery/]
];

for (const [prompt, replyPattern] of aggregates) {
  const result = getHomeAssistantVoiceResponse(prompt, states);
  assert(result, `No aggregate result for: ${prompt}`);
  assert.match(result.reply, replyPattern, `Wrong aggregate reply for: ${prompt}`);
}

const control = getHomeAssistantVoiceResponse("Turn on the kitchen lights", states);
assert.match(control.reply, /control is not enabled/i);

const conflictingDoors = [
  state("cover.garage_door_left", "Garage Door Left", "closed", { device_class: "garage" }),
  state("cover.garage_door_left_door", "Garage Door Left Door 1", "open", { device_class: "garage" })
];
const conflict = getHomeAssistantVoiceResponse("Is the left garage door open?", conflictingDoors);
assert.match(conflict.reply, /conflicting states/i);
assert.match(conflict.reply, /will not guess, Operator/i);

const duplicateRightDoor = [
  state("cover.garage_door_right", "Garage Door Right", "closed", { device_class: "garage" }),
  state("cover.garage_door_right_door", "Garage Door Right Door 1", "closed", { device_class: "garage" }),
  state("switch.garage_door_right", "Garage Door Right", "on")
];
assert.match(getHomeAssistantVoiceResponse("Is the right garage door open?", duplicateRightDoor).reply, /closed/);

const lockAndCamera = [
  state("lock.front_door_lock", "Front Door Lock", "unavailable"),
  state("camera.front_door", "Front Door Camera", "idle")
];
const lockResult = getHomeAssistantVoiceResponse("Is the front door locked?", lockAndCamera);
assert.equal(lockResult.states[0].entity_id, "lock.front_door_lock");

assert.equal(getHomeAssistantVoiceResponse("Explain the rings of Saturn", states), null);
assert.equal(rankHomeAssistantEntities("garage temperature", states, 1)[0].state.entity_id, "sensor.garage_temperature");

for (const prompt of ["Who are you?", "Are you there?", "What can you do?", "Thank you", "What time is it?", "What day is it?"]) {
  const reply = getCommonAssistantReply(prompt, new Date("2026-08-16T03:30:00Z"), { userName: "Tim" });
  assert(reply, `Missing common reply for: ${prompt}`);
  assert.doesNotMatch(reply, /As an AI|language model|Tim is|Tim's/i);
  assert.doesNotMatch(reply, /\bsir\b/i);
  assert.match(reply, /\bTim\b/);
}
assert.equal(getCommonAssistantReply("Say exactly: Systems nominal"), "Systems nominal");

const audit = auditHomeAssistantLanguageCoverage(states);
assert.equal(audit.friendlyName.percent, 100);
assert.equal(audit.objectId.percent, 100);

console.log(`jarvis language verification passed: ${cases.length + aggregates.length + 7} phrase checks; ${states.length} entity aliases audited`);
