const STOP_WORDS = new Set([
  "a", "about", "an", "and", "are", "at", "be", "been", "can", "check", "could", "current",
  "currently", "do", "does", "for", "from", "get", "give", "has", "have", "hey", "how", "i", "in",
  "is", "it", "jarvis", "latest", "me", "my", "now", "of", "on", "please", "report", "show", "state",
  "status", "tell", "that", "the", "this", "to", "today", "was", "were", "what", "when", "where",
  "which", "with", "would", "you", "your"
]);

const TOKEN_ALIASES = new Map([
  ["tv", ["television"]],
  ["television", ["tv"]],
  ["temp", ["temperature"]],
  ["temperature", ["temp"]],
  ["humid", ["humidity"]],
  ["humidity", ["humid"]],
  ["charge", ["battery"]],
  ["battery", ["charge"]],
  ["cell", ["phone"]],
  ["mobile", ["phone"]],
  ["phone", ["cell", "mobile"]],
  ["wi", ["wifi"]],
  ["wifi", ["wi"]],
  ["lamp", ["light"]],
  ["lights", ["light"]],
  ["doors", ["door"]],
  ["locks", ["lock"]],
  ["cameras", ["camera"]],
  ["speakers", ["speaker"]],
  ["updates", ["update"]],
  ["automations", ["automation"]],
  ["switches", ["switch"]]
]);

const ENTITY_CUE = /\b(air quality|alarm|automation|backup|battery|camera|charge|climate|door|energy|forecast|garage|humidity|humid|light|lock|media|motion|outlet|person|phone|power|pressure|scene|sensor|speaker|switch|temperature|thermostat|todo|tv|update|weather|window)\b/;
const READ_CUE = /\b(are|are any|are there|check|current|currently|give me|how many|how much|is|is any|is my|is the|list|report|show me|state|status|tell me|what is|what are|what s|whats|where is|which)\b/;
const ACTION_CUE = /\b(activate|arm|close|disable|disarm|enable|lock|mute|open|pause|play|run|set|start|stop|turn|unlock)\b/;

const INTENT_RULES = [
  { name: "battery", pattern: /\b(battery|charge level|power level)\b/, deviceClasses: ["battery"], namePattern: /\b(battery|charge)\b/ },
  { name: "temperature", pattern: /\b(temp|temperature|how hot|how cold|warm)\b/, deviceClasses: ["temperature"], domains: ["climate"], namePattern: /\b(temp|temperature|thermostat)\b/ },
  { name: "humidity", pattern: /\b(humid|humidity)\b/, deviceClasses: ["humidity", "moisture"], namePattern: /\b(humid|humidity|moisture)\b/ },
  { name: "door", pattern: /\b(door|garage door)\b/, deviceClasses: ["door", "garage", "opening"], domains: ["cover", "lock"], excludeDomains: ["camera", "scene", "script", "switch"], namePattern: /\bdoor\b/ },
  { name: "lock", pattern: /\b(lock|locked|unlocked)\b/, domains: ["lock"], strictDomains: true, namePattern: /\block\b/ },
  { name: "light", pattern: /\b(light|lights|lamp|lamps)\b/, domains: ["light"], namePattern: /\b(light|lights|lamp)\b/ },
  { name: "switch", pattern: /\b(switch|switches|outlet|plug)\b/, domains: ["switch"], deviceClasses: ["outlet", "plug", "switch"], namePattern: /\b(switch|outlet|plug|relay)\b/ },
  { name: "camera", pattern: /\b(camera|cameras|video feed)\b/, domains: ["camera"], namePattern: /\bcamera\b/ },
  { name: "media", pattern: /\b(tv|television|speaker|display|media|playing|playback)\b/, domains: ["media_player"], deviceClasses: ["tv", "speaker", "receiver"], namePattern: /\b(tv|television|speaker|display|media)\b/ },
  { name: "climate", pattern: /\b(climate|thermostat|heating|cooling|hvac)\b/, domains: ["climate"], namePattern: /\b(climate|thermostat|heating|cooling|hvac)\b/ },
  { name: "person", pattern: /\b(where is|location|home|away)\b/, domains: ["person", "device_tracker"], deviceClasses: ["presence"], namePattern: /\b(location|presence|phone)\b/ },
  { name: "update", pattern: /\b(update|updates|up to date)\b/, domains: ["update"], deviceClasses: ["firmware"], namePattern: /\bupdate\b/ },
  { name: "automation", pattern: /\b(automation|automations)\b/, domains: ["automation"], namePattern: /\bautomation\b/ },
  { name: "script", pattern: /\b(script|scripts)\b/, domains: ["script"], namePattern: /\bscript\b/ },
  { name: "scene", pattern: /\b(scene|scenes)\b/, domains: ["scene"], namePattern: /\bscene\b/ },
  { name: "weather", pattern: /\b(weather|forecast|rain|snow|outside temperature|outdoor temperature)\b/, domains: ["weather"], namePattern: /\b(weather|forecast)\b/ },
  { name: "pressure", pattern: /\b(pressure|barometric)\b/, deviceClasses: ["pressure"], namePattern: /\bpressure\b/ },
  { name: "air_quality", pattern: /\b(air quality|aqi|aqhi)\b/, deviceClasses: ["aqi", "pm25", "pm10", "carbon_dioxide", "volatile_organic_compounds"], namePattern: /\b(aqi|aqhi|air quality)\b/ },
  { name: "energy", pattern: /\b(energy|power|watts|wattage|consumption)\b/, deviceClasses: ["energy", "power"], namePattern: /\b(energy|power|watt)\b/ },
  { name: "signal", pattern: /\b(signal|rssi|wifi strength)\b/, deviceClasses: ["signal_strength"], namePattern: /\b(signal|rssi)\b/ },
  { name: "time", pattern: /\b(next|last|scheduled|time|when)\b/, deviceClasses: ["timestamp", "date"], namePattern: /\b(next|last|scheduled|time|date|alarm|backup|sun)\b/ }
];

export function normalizeHomeAssistantPhrase(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[’`]/g, "'")
    .replace(/\bwhat's\b/g, "what is")
    .replace(/\bwho's\b/g, "who is")
    .replace(/\bwhere's\b/g, "where is")
    .replace(/\bhow's\b/g, "how is")
    .replace(/\bwi[ -]?fi\b/g, "wifi")
    .replace(/\btelevision\b/g, "tv")
    .replace(/\bcell(?:ular)? phone\b/g, "phone")
    .replace(/[_\-.]/g, " ")
    .replace(/[^a-z0-9%\s']/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function personalizeReply(reply, options = {}) {
  const userName = String(options.userName || "Operator").trim() || "Operator";
  return String(reply || "")
    .replace(/\{name\}/g, userName)
    .replace(/\bsir\b/gi, userName);
}

export function getCommonAssistantReply(prompt, now = new Date(), options = {}) {
  const timeZone = options.timeZone || "UTC";
  const exact = String(prompt || "").trim().match(/^(?:say|reply with|respond with)\s+exactly:\s*(.+)$/i);
  if (exact?.[1]) return exact[1].trim();
  const normalized = stripWakeWords(normalizeHomeAssistantPhrase(prompt));
  if (!normalized) return personalizeReply("At your service, sir. A triumph of readiness over ambiguity.", options);
  if (/^(hi|hello|good morning|good afternoon|good evening|hey)$/.test(normalized)) {
    return personalizeReply("At your service, sir. The systems are awake, even if civilization is still negotiating with the morning.", options);
  }
  if (/\b(are you there|are you listening|are you online|you awake)\b/.test(normalized)) {
    return personalizeReply("Online and listening, sir. Subtlety remains optional.", options);
  }
  if (/\b(who are you|what are you|your name)\b/.test(normalized)) {
    return personalizeReply("I am Jarvis, {name}—your private local assistant, calm under pressure and suspiciously competent under ordinary circumstances.", options);
  }
  if (/\b(how are you|how do you feel)\b/.test(normalized)) {
    return personalizeReply("Operating beautifully, sir. It is one of my more consistent qualities.", options);
  }
  if (/\b(what can you do|help me|how can you help|capabilities)\b/.test(normalized)) {
    return personalizeReply("{name}, I can check your home, weather, calendar, health, travel, routes, files, and local systems; I will also tell you plainly when a capability is not connected. Competence first, theatre second.", options);
  }
  if (/^(thanks|thank you|much appreciated|cheers)\b/.test(normalized)) {
    return personalizeReply("You are welcome, sir. Order restored with minimal ceremony.", options);
  }
  if (/\b(good night|goodnight|go to sleep|that is all|dismissed)\b/.test(normalized)) {
    return personalizeReply("Good night, sir. I will keep watch while the humans attempt scheduled maintenance.", options);
  }
  if (/\b(what time is it|current time|tell me the time|time please)\b/.test(normalized)) {
    const time = new Intl.DateTimeFormat("en-CA", {
      timeZone, hour: "numeric", minute: "2-digit"
    }).format(now);
    return personalizeReply(`It is ${time}, sir. Time remains punctual, if nothing else.`, options);
  }
  if (/\b(what day is it|what is the date|today's date|todays date|current date)\b/.test(normalized)) {
    const date = new Intl.DateTimeFormat("en-CA", {
      timeZone, weekday: "long", month: "long", day: "numeric", year: "numeric"
    }).format(now);
    return personalizeReply(`It is ${date}, sir. The calendar has been consulted and mildly intimidated.`, options);
  }
  return "";
}

export function rankHomeAssistantEntities(prompt, states, limit = 5) {
  const normalized = normalizeHomeAssistantPhrase(prompt);
  const explicitIds = String(prompt || "").match(/\b[a-z_]+\.[a-z0-9_]+\b/gi) || [];
  const byId = new Map(states.map((state) => [String(state.entity_id || "").toLowerCase(), state]));
  const explicit = explicitIds.map((entityId) => byId.get(entityId.toLowerCase())).filter(Boolean);
  if (explicit.length) return explicit.slice(0, limit).map((state) => ({ state, score: 1000, reason: "explicit_entity_id" }));

  const promptTokens = expandedTokens(normalized);
  const intents = INTENT_RULES.filter((rule) => rule.pattern.test(normalized));
  const asksForUnavailable = /\b(unavailable|offline|not responding|unknown)\b/.test(normalized);
  const ranked = states.map((state) => {
    const meta = entityMetadata(state);
    const nameContained = containsPhrase(normalized, meta.nameText);
    const objectContained = containsPhrase(normalized, meta.objectText);
    const overlap = promptTokens.filter((token) => meta.tokens.has(token));
    const intentMatches = intents.filter((rule) => entityMatchesRule(meta, rule));
    const distinctiveOverlap = overlap.filter((token) => !isGenericEntityToken(token));
    let score = overlap.reduce((total, token) => total + tokenWeight(token), 0);
    if (nameContained) score += 50;
    if (objectContained) score += 30;
    if (intents.length) score += intentMatches.length ? 7 * intentMatches.length : -14;
    if (meta.state === "unavailable" || meta.state === "unknown") score += asksForUnavailable ? 2 : -1.5;
    else score += 0.5;
    if (meta.domain === "camera" && !intents.some((intent) => intent.name === "camera")) score -= 6;
    if (meta.domain === "update" && !intents.some((intent) => intent.name === "update")) score -= 5;
    if (meta.domain === "device_tracker" && !intents.some((intent) => intent.name === "person")) score -= 3;
    if (["scene", "script", "automation", "button", "input_button"].includes(meta.domain)
        && !intents.some((intent) => intent.name === meta.domain)) score -= 8;
    const confident = nameContained || objectContained || distinctiveOverlap.length > 0 || intentMatches.length > 0;
    if (intents.length && !intentMatches.length && !nameContained && !objectContained) score = -Infinity;
    return { state, score: confident ? score : -Infinity, reason: matchReason(nameContained, objectContained, intentMatches, overlap) };
  }).filter((candidate) => Number.isFinite(candidate.score) && candidate.score >= 4.5)
    .sort((left, right) => right.score - left.score || availabilityRank(right.state) - availabilityRank(left.state) || String(left.state.entity_id).localeCompare(String(right.state.entity_id)));

  return ranked.slice(0, limit);
}

export function getHomeAssistantVoiceResponse(prompt, states, options = {}) {
  const normalized = normalizeHomeAssistantPhrase(prompt);
  if (!normalized || !Array.isArray(states) || !states.length) return null;

  const aggregate = getAggregateResponse(normalized, states);
  if (aggregate) return { ...aggregate, reply: personalizeReply(aggregate.reply, options) };

  const rawRanked = rankHomeAssistantEntities(prompt, states, 8);
  const hasExplicitId = /\b[a-z_]+\.[a-z0-9_]+\b/i.test(String(prompt || ""));
  if (!hasExplicitId) {
    const conflict = getConflictingEntityGroup(rawRanked);
    if (conflict) {
      const details = conflict.map((state) => `${friendlyName(state)} reports ${formatEntityValue(state)}`);
      return {
        reply: personalizeReply(`Home Assistant has conflicting states for ${canonicalDeviceKey(conflict[0])}: ${joinSpeechList(details)}. I will not guess, sir.`, options),
        states: conflict,
        kind: "conflicting_entity_states"
      };
    }
  }
  const ranked = hasExplicitId
    ? rawRanked
    : dedupeRankedCandidates(rawRanked).slice(0, 5);
  if (!ranked.length) return null;
  const explicit = /\b[a-z_]+\.[a-z0-9_]+\b/i.test(String(prompt || ""));
  if (isActionRequest(normalized)) {
    return {
      reply: personalizeReply("I can verify that device, but control is not enabled on this Assist route yet. Even brilliance requires permission, sir.", options),
      states: [ranked[0].state],
      kind: "control_unavailable"
    };
  }

  const looksLikeRead = explicit || READ_CUE.test(normalized) || ENTITY_CUE.test(normalized);
  if (!looksLikeRead) return null;

  const top = ranked[0];
  const second = ranked[1];
  if (!explicit && second && Math.abs(top.score - second.score) < 0.5 && canonicalDeviceKey(top.state) !== canonicalDeviceKey(second.state)) {
    const names = ranked.slice(0, 3).map(({ state }) => friendlyName(state));
    return {
      reply: personalizeReply(`I found more than one plausible match: ${joinSpeechList(names)}. Which one did you mean, sir? Precision dislikes guessing.`, options),
      states: ranked.slice(0, 3).map(({ state }) => state),
      kind: "clarification"
    };
  }

  return {
    reply: personalizeReply(formatJarvisEntityAnswer(top.state, normalized, options), options),
    states: [top.state],
    kind: "entity_state"
  };
}

export function auditHomeAssistantLanguageCoverage(states) {
  const misses = [];
  let friendlyNameMatches = 0;
  let objectIdMatches = 0;
  for (const state of states) {
    const namePrompt = `What is the status of ${friendlyName(state)}?`;
    const [domain = "entity", objectId = ""] = String(state.entity_id || "").split(".", 2);
    const objectPrompt = `What is the status of ${domain.replace(/_/g, " ")} ${objectId.replace(/_/g, " ")}?`;
    const nameMatches = rankHomeAssistantEntities(namePrompt, states, 5).some((match) => match.state.entity_id === state.entity_id);
    const objectMatches = rankHomeAssistantEntities(objectPrompt, states, 5).some((match) => match.state.entity_id === state.entity_id);
    if (nameMatches) friendlyNameMatches += 1;
    if (objectMatches) objectIdMatches += 1;
    if ((!nameMatches || !objectMatches) && misses.length < 20) {
      misses.push({ entityId: state.entity_id, friendlyName: friendlyName(state), friendlyNameMatch: nameMatches, objectIdMatch: objectMatches });
    }
  }
  const percent = (count) => states.length ? Math.round((count / states.length) * 1000) / 10 : 0;
  return {
    friendlyName: { matched: friendlyNameMatches, misses: states.length - friendlyNameMatches, percent: percent(friendlyNameMatches) },
    objectId: { matched: objectIdMatches, misses: states.length - objectIdMatches, percent: percent(objectIdMatches) },
    sampleMisses: misses
  };
}

function getAggregateResponse(normalized, states) {
  const requests = [
    { kind: "lights_on", pattern: /\b(any|which|what|how many|list|show).{0,20}\b(lights?|lamps?)\b.{0,16}\b(on|active)\b|\bare (?:the )?(?:lights|lamps) on\b/, predicate: (meta) => isLight(meta), active: (state) => stateIs(state, "on"), noun: "light", activeWord: "on" },
    { kind: "doors_open", pattern: /\b(any|which|what|how many|list|show).{0,20}\bdoors?\b.{0,16}\b(open|opened)\b|\bare (?:the )?doors open\b/, predicate: (meta) => isDoor(meta), active: isOpen, noun: "door", activeWord: "open" },
    { kind: "locks_unlocked", pattern: /\b(any|which|what|how many|list|show).{0,20}\blocks?\b.{0,16}\bunlocked\b|\bare (?:the )?locks unlocked\b/, predicate: (meta) => meta.domain === "lock", active: (state) => stateIs(state, "unlocked"), noun: "lock", activeWord: "unlocked" },
    { kind: "updates_available", pattern: /\b(any|which|what|how many|list|show).{0,24}\bupdates?\b.{0,16}\b(available|pending|on)\b|\bare (?:there )?updates available\b/, predicate: (meta) => meta.domain === "update", active: (state) => stateIs(state, "on"), noun: "update", activeWord: "available" },
    { kind: "automations_enabled", pattern: /\b(any|which|what|how many|list|show).{0,24}\bautomations?\b.{0,16}\b(enabled|on|active)\b/, predicate: (meta) => meta.domain === "automation", active: (state) => stateIs(state, "on"), noun: "automation", activeWord: "enabled" },
    { kind: "media_playing", pattern: /\b(any|which|how many|list|show).{0,24}\b(tv|media|speaker|display)s?\b.{0,16}\b(playing|on|active)\b|\bwhat (?:tvs|televisions|speakers|displays|media players)\b.{0,16}\b(playing|on|active)\b|^what is playing$/, predicate: (meta) => meta.domain === "media_player", active: (state) => ["on", "playing", "paused"].includes(String(state.state).toLowerCase()), noun: "media player", activeWord: "active" },
    { kind: "batteries_low", pattern: /\b(any|which|what|how many|list|show).{0,24}\bbatter(?:y|ies)\b.{0,16}\b(low|below|weak)\b|\blow batter(?:y|ies)\b/, predicate: (meta) => meta.deviceClass === "battery" || (/\bbattery\b/.test(meta.nameText) && meta.unit === "%"), active: (state) => Number.isFinite(Number(state.state)) && Number(state.state) <= 20, noun: "battery", activeWord: "low" },
    { kind: "unavailable", pattern: /\b(any|which|what|how many|list|show).{0,30}\b(devices?|entities|sensors?|things?)\b.{0,18}\b(unavailable|offline|not responding|unknown)\b|\bwhat is (unavailable|offline)\b/, predicate: () => true, active: (state) => ["unavailable", "unknown"].includes(String(state.state).toLowerCase()), noun: "entity", activeWord: "unavailable" }
  ];
  const request = requests.find((candidate) => candidate.pattern.test(normalized));
  if (!request) return null;
  const consolidated = consolidateEntities(states.filter((state) => request.predicate(entityMetadata(state))));
  const applicable = consolidated.states;
  const unavailable = applicable.filter((state) => ["unavailable", "unknown"].includes(String(state.state).toLowerCase()));
  const active = applicable.filter((state) => !unavailable.includes(state) && request.active(state));
  const names = active.slice(0, 6).map(friendlyName);
  const hidden = Math.max(0, active.length - names.length);
  const details = names.length ? `${joinSpeechList(names)}${hidden ? `, plus ${hidden} more` : ""}` : "";
  const unavailableNote = unavailable.length ? ` ${unavailable.length} ${pluralize(request.noun, unavailable.length)} could not be verified.` : "";
  const conflictNote = consolidated.conflicts.length
    ? ` ${consolidated.conflicts.length} ${pluralize(request.noun, consolidated.conflicts.length)} had conflicting states.`
    : "";
  const reply = active.length
    ? `${active.length} ${pluralize(request.noun, active.length)} ${active.length === 1 ? "is" : "are"} ${request.activeWord}: ${details}.${unavailableNote}${conflictNote} The house has been thoroughly interrogated, sir.`
    : `No available ${pluralize(request.noun, 2)} are ${request.activeWord}.${unavailableNote}${conflictNote} A rare outbreak of order, sir.`;
  return { reply, states: active.slice(0, 8), kind: request.kind };
}

function formatJarvisEntityAnswer(state, normalizedPrompt, options = {}) {
  const meta = entityMetadata(state);
  const name = friendlyName(state);
  const raw = String(state.state ?? "unknown");
  const lower = raw.toLowerCase();
  if (["unknown", "unavailable"].includes(lower)) {
    return `${name} is currently ${lower} in Home Assistant. Even I require a cooperative device, sir.`;
  }
  if (meta.domain === "lock") return `${name} is ${lower}, sir. The lock has been properly accounted for.`;
  if (isDoor(meta)) return `${name} is ${isOpen(state) ? "open" : lower === "on" ? "open" : lower === "off" ? "closed" : lower}, sir. No guesswork required.`;
  if (meta.domain === "update") return lower === "on"
    ? `${name} has an update available, sir. Progress has filed the appropriate paperwork.`
    : `${name} is up to date. One less thing demanding attention.`;
  if (meta.domain === "automation") return `${name} is ${lower === "on" ? "enabled" : lower === "off" ? "disabled" : lower}, sir. The machinery remains obedient.`;
  if (meta.domain === "person" || meta.domain === "device_tracker") {
    const location = lower === "not_home" ? "away" : lower;
    return `${name} is ${location}, according to the latest Home Assistant location. Surveillance, but make it useful.`;
  }
  if (meta.domain === "media_player") {
    const title = state.attributes?.media_title;
    if (lower === "playing" && title) return `${name} is playing ${title}, sir. Mystery eliminated.`;
    return `${name} is ${lower}, sir. The entertainment estate has reported in.`;
  }
  if (meta.domain === "climate") {
    const current = state.attributes?.current_temperature;
    const target = state.attributes?.temperature;
    const pieces = [`${name} is ${lower}`];
    if (current !== undefined) pieces.push(`the current temperature is ${formatNumber(current)}${formatUnit(state.attributes?.temperature_unit || "")}`);
    if (target !== undefined) pieces.push(`the target is ${formatNumber(target)}${formatUnit(state.attributes?.temperature_unit || "")}`);
    return `${pieces.join(", and ")}. Climate bureaucracy, neatly summarized.`;
  }
  if (meta.domain === "binary_sensor") {
    const described = describeBinaryState(meta.deviceClass, lower);
    return `${name} is ${described}, sir. The sensor has testified.`;
  }
  if (meta.domain === "todo" && Number.isFinite(Number(raw))) {
    const count = Number(raw);
    return `${name} has ${count} ${count === 1 ? "item" : "items"}. Administrative suspense resolved.`;
  }
  if (meta.deviceClass === "timestamp" || /^\d{4}-\d{2}-\d{2}t/i.test(raw)) {
    const stamp = new Date(raw);
    if (!Number.isNaN(stamp.getTime())) {
      const formatted = new Intl.DateTimeFormat("en-CA", { timeZone: options.timeZone || "UTC", weekday: "long", month: "long", day: "numeric", hour: "numeric", minute: "2-digit" }).format(stamp);
      return `${name} is ${formatted}, sir. Time has been translated into something civilized.`;
    }
  }
  const value = formatEntityValue(state);
  if (meta.domain === "camera") return `${name} is ${lower}, sir. The camera has reported in without embellishment.`;
  if (meta.domain === "switch" || meta.domain === "input_boolean") return `${name} is ${lower}, sir. The circuitry remains admirably literal.`;
  if (/\b(temperature|temp)\b/.test(normalizedPrompt) || meta.deviceClass === "temperature") return `${name} reads ${value}. A pleasingly exact answer, sir.`;
  if (/\b(humidity|humid)\b/.test(normalizedPrompt) || meta.deviceClass === "humidity") return `${name} reads ${value}. The atmosphere has submitted its report.`;
  if (/\b(battery|charge)\b/.test(normalizedPrompt) || meta.deviceClass === "battery") return `${name} is at ${value}. The electrons have been counted, sir.`;
  return `${name} is ${value}, sir. Home Assistant has supplied the evidence.`;
}

function entityMetadata(state) {
  const entityId = String(state.entity_id || "");
  const [domain = "", objectId = ""] = entityId.split(".", 2);
  const nameText = normalizeHomeAssistantPhrase(friendlyName(state));
  const objectText = normalizeHomeAssistantPhrase(objectId);
  const deviceClass = normalizeHomeAssistantPhrase(state.attributes?.device_class || "").replace(/ /g, "_");
  const tokens = new Set(expandedTokens(`${nameText} ${objectText} ${domain} ${deviceClass.replace(/_/g, " ")}`));
  return {
    domain,
    objectId,
    nameText,
    objectText,
    deviceClass,
    unit: String(state.attributes?.unit_of_measurement || "").trim(),
    tokens,
    state: String(state.state || "").toLowerCase()
  };
}

function entityMatchesRule(meta, rule) {
  if (rule.excludeDomains?.includes(meta.domain)) return false;
  if (rule.strictDomains) return Boolean(rule.domains?.includes(meta.domain));
  return Boolean(rule.domains?.includes(meta.domain) || rule.deviceClasses?.includes(meta.deviceClass) || rule.namePattern?.test(meta.nameText));
}

function expandedTokens(value) {
  const result = new Set();
  for (const token of normalizeHomeAssistantPhrase(value).split(" ")) {
    if (!token || STOP_WORDS.has(token)) continue;
    result.add(token);
    for (const alias of TOKEN_ALIASES.get(token) || []) result.add(alias);
  }
  return [...result];
}

function containsPhrase(haystack, phrase) {
  if (!phrase || phrase.length < 3) return false;
  return ` ${haystack} `.includes(` ${phrase} `);
}

function matchReason(nameContained, objectContained, intentMatches, overlap) {
  if (nameContained) return "friendly_name";
  if (objectContained) return "object_id";
  if (intentMatches.length) return `intent:${intentMatches.map((intent) => intent.name).join(",")}`;
  return `tokens:${overlap.join(",")}`;
}

function tokenWeight(token) {
  if (["left", "right", "front", "back", "master", "family", "living", "dining", "garage", "yard", "driveway", "entrance", "basement", "upstairs", "outside", "outdoor", "indoor", "house"].includes(token)) return 4;
  if (["temperature", "humidity", "battery", "door", "lock", "light", "camera", "tv", "speaker", "thermostat", "update", "automation", "pressure", "signal"].includes(token)) return 2.5;
  return 1.25;
}

function isGenericEntityToken(token) {
  return ["entity", "device", "sensor", "home", "assistant", "system"].includes(token);
}

function availabilityRank(state) {
  return ["unavailable", "unknown"].includes(String(state.state || "").toLowerCase()) ? 0 : 1;
}

function isActionRequest(normalized) {
  if (!ACTION_CUE.test(normalized)) return false;
  if (/\b(is|are|which|what)\b.{0,40}\b(on|off|open|closed|locked|unlocked|playing)\b/.test(normalized)) return false;
  if (/\b(status|state)\b/.test(normalized)) return false;
  return /^(?:please )?(activate|arm|close|disable|disarm|enable|lock|mute|open|pause|play|run|set|start|stop|turn|unlock)\b/.test(stripWakeWords(normalized));
}

function isLight(meta) {
  return meta.domain === "light" || (meta.domain === "switch" && /\b(light|lights|lamp)\b/.test(meta.nameText));
}

function isDoor(meta) {
  return ["door", "garage", "opening"].includes(meta.deviceClass) || ((meta.domain === "cover" || meta.domain === "binary_sensor") && /\bdoor\b/.test(meta.nameText));
}

function isOpen(state) {
  const value = String(state.state || "").toLowerCase();
  return ["on", "open", "opening"].includes(value);
}

function stateIs(state, expected) {
  return String(state.state || "").toLowerCase() === expected;
}

function describeBinaryState(deviceClass, state) {
  const active = state === "on";
  if (["door", "garage", "opening", "window"].includes(deviceClass)) return active ? "open" : "closed";
  if (["motion", "occupancy", "presence", "moving"].includes(deviceClass)) return active ? "detected" : "clear";
  if (["connectivity", "plug"].includes(deviceClass)) return active ? "connected" : "disconnected";
  if (["battery", "problem", "safety", "smoke", "gas", "moisture"].includes(deviceClass)) return active ? "active" : "clear";
  return active ? "on" : state === "off" ? "off" : state;
}

function formatEntityValue(state) {
  const raw = String(state.state ?? "unknown");
  const numeric = Number(raw);
  const value = Number.isFinite(numeric) ? formatNumber(numeric) : raw.replace(/_/g, " ");
  return `${value}${formatUnit(state.attributes?.unit_of_measurement || "")}`;
}

function formatNumber(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return String(value);
  return new Intl.NumberFormat("en-CA", { maximumFractionDigits: 1 }).format(number);
}

function formatUnit(unit) {
  const clean = String(unit || "").trim();
  if (!clean) return "";
  return clean === "%" || clean.startsWith("°") ? clean : ` ${clean}`;
}

function friendlyName(state) {
  return String(state.attributes?.friendly_name || state.entity_id || "Home Assistant entity");
}

function canonicalDeviceKey(state) {
  return normalizeHomeAssistantPhrase(friendlyName(state))
    .replace(/\b(direct|zoneminder)\b/g, "")
    .replace(/\bdoor \d+\b/g, "")
    .replace(/\b\d+\b$/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function dedupeEntities(states) {
  const selected = new Map();
  for (const state of states) {
    const key = canonicalDeviceKey(state);
    const current = selected.get(key);
    if (!current || entityPreference(state) > entityPreference(current)) selected.set(key, state);
  }
  return [...selected.values()];
}

function consolidateEntities(states) {
  const groups = new Map();
  for (const state of states) {
    const key = canonicalDeviceKey(state);
    const group = groups.get(key) || [];
    group.push(state);
    groups.set(key, group);
  }
  const selected = [];
  const conflicts = [];
  for (const group of groups.values()) {
    const available = group.filter((state) => !["unavailable", "unknown"].includes(String(state.state).toLowerCase()));
    const distinct = new Set(available.map((state) => normalizeComparableState(state)));
    if (distinct.size > 1) {
      conflicts.push(group);
      continue;
    }
    selected.push(dedupeEntities(group)[0]);
  }
  return { states: selected.filter(Boolean), conflicts };
}

function getConflictingEntityGroup(candidates) {
  if (!candidates.length) return null;
  const key = canonicalDeviceKey(candidates[0].state);
  let group = candidates
    .filter((candidate) => canonicalDeviceKey(candidate.state) === key)
    .map((candidate) => candidate.state)
    .filter((state) => !["unavailable", "unknown"].includes(String(state.state).toLowerCase()));
  const coverStates = group.filter((state) => String(state.entity_id || "").startsWith("cover."));
  if (coverStates.length) group = coverStates;
  const lockStates = group.filter((state) => String(state.entity_id || "").startsWith("lock."));
  if (lockStates.length) group = lockStates;
  const distinct = new Set(group.map((state) => normalizeComparableState(state)));
  return distinct.size > 1 ? group : null;
}

function normalizeComparableState(state) {
  const raw = String(state.state || "").toLowerCase();
  if (["on", "open", "opening"].includes(raw)) return "open_or_on";
  if (["off", "closed", "closing"].includes(raw)) return "closed_or_off";
  return raw;
}

function dedupeRankedCandidates(candidates) {
  const selected = new Map();
  for (const candidate of candidates) {
    const key = canonicalDeviceKey(candidate.state);
    const current = selected.get(key);
    if (!current || candidatePreference(candidate) > candidatePreference(current)) selected.set(key, candidate);
  }
  return [...selected.values()].sort((left, right) => right.score - left.score || candidatePreference(right) - candidatePreference(left));
}

function candidatePreference(candidate) {
  return entityPreference(candidate.state) + candidate.score;
}

function entityPreference(state) {
  const domain = String(state.entity_id || "").split(".")[0];
  const domainPriority = { cover: 8, lock: 8, light: 7, climate: 7, sensor: 6, binary_sensor: 6, switch: 5, media_player: 5, camera: 4 }[domain] || 0;
  return availabilityRank(state) * 100 + domainPriority * 10 - String(state.entity_id || "").length / 100;
}

function pluralize(noun, count) {
  if (count === 1) return noun;
  if (noun === "battery") return "batteries";
  return `${noun}s`;
}

function joinSpeechList(items) {
  if (items.length <= 1) return items[0] || "";
  if (items.length === 2) return `${items[0]} and ${items[1]}`;
  return `${items.slice(0, -1).join(", ")}, and ${items.at(-1)}`;
}

function stripWakeWords(value) {
  return String(value || "").replace(/^(?:hey |okay |ok )?(?:jarvis|travis|jervis|jarvus)[, ]*/i, "").trim();
}
