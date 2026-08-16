import http from "node:http";
import fsSync from "node:fs";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  auditHomeAssistantLanguageCoverage,
  getCommonAssistantReply,
  getHomeAssistantVoiceResponse,
  rankHomeAssistantEntities
} from "./home-assistant-language.js";

const __filename = fileURLToPath(import.meta.url);
const rootDir = path.dirname(__filename);

loadDotEnv(path.join(rootDir, ".env"));

const config = {
  appName: env("APP_NAME", "jarvis-llm-ui"),
  port: numberEnv("PORT", 8787),
  dataDir: path.resolve(rootDir, env("DATA_DIR", "./data")),
  publicBaseUrl: env("PUBLIC_BASE_URL", ""),
  ollamaHost: stripSlash(env("OLLAMA_HOST", "http://127.0.0.1:11434")),
  defaultModel: env("DEFAULT_MODEL", "llama3.2:3b"),
  voiceModel: env("VOICE_MODEL", "llama3.2:3b"),
  deepModel: env("DEEP_MODEL", "llama3.1:8b"),
  defaultMaxTokens: numberEnv("DEFAULT_MAX_TOKENS", 160),
  voiceMaxTokens: numberEnv("VOICE_MAX_TOKENS", 64),
  deepMaxTokens: numberEnv("DEEP_MAX_TOKENS", 512),
  ollamaTimeoutMs: numberEnv("OLLAMA_TIMEOUT_MS", 120000),
  ollamaKeepAlive: env("OLLAMA_KEEP_ALIVE", "30m"),
  userName: env("JARVIS_USER_NAME", "Operator"),
  timeZone: env("JARVIS_TIME_ZONE", "UTC"),
  systemPrompt: env(
    "JARVIS_SYSTEM_PROMPT",
    "You are Jarvis, a private local assistant. Be concise, precise, conversational, and useful. Address the user by their configured name occasionally, not in every reply, and do not default to sir. Use dry wit when it fits, but never force a joke onto routine facts or obscure the answer."
  ),
  voiceSystemPrompt: env(
    "JARVIS_VOICE_SYSTEM_PROMPT",
    "You are Jarvis, a private local voice assistant: calm, precise, brilliant, conversational, and dryly witty. Lead with the useful answer, use the configured name occasionally instead of as a compulsory sign-off, and keep voice replies to one or two short sentences. Routine factual replies may be plain; never force a joke. Never invent live facts, device states, completed actions, or capabilities. Do not mention models, prompts, tools, or implementation details."
  ),
  homeAssistantUrl: stripSlash(env("HOME_ASSISTANT_URL", "")),
  homeAssistantToken: env("HOME_ASSISTANT_TOKEN", ""),
  homeAssistantWebhookSecret: env("HOME_ASSISTANT_WEBHOOK_SECRET", ""),
  corsOrigins: env("HOME_ASSISTANT_CORS_ORIGINS", "").split(",").map((item) => item.trim()).filter(Boolean),
  ttsEntity: env("JARVIS_TTS_ENTITY", "tts.piper"),
  defaultSpeaker: env("JARVIS_DEFAULT_SPEAKER", ""),
  ttsLanguage: env("JARVIS_TTS_LANGUAGE", ""),
  ttsVoice: env("JARVIS_TTS_VOICE", "")
};

const requestHistory = [];
const maxRequestHistory = 500;
const promptReviewPath = path.join(config.dataDir, "prompt-review.jsonl");
const conversationArchivePath = path.resolve(config.dataDir, env("JARVIS_CONVERSATION_ARCHIVE_PATH", "conversation-archive.jsonl"));
const eventLogPath = path.join(config.dataDir, "home-assistant-events.jsonl");
const benchmarkPath = path.join(config.dataDir, "benchmark.json");
let conversationArchiveQueue = Promise.resolve();

await fs.mkdir(config.dataDir, { recursive: true });
await ensureConversationArchive();

const server = http.createServer(async (req, res) => {
  const startedAt = Date.now();
  try {
    applyCors(req, res);
    if (req.method === "OPTIONS") {
      sendNoContent(res);
      return;
    }

    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    const body = await readJsonBody(req);
    await route(req, res, url, body);
  } catch (error) {
    sendJson(res, error.statusCode || 500, { error: error.message || "Internal server error" });
  } finally {
    const pathname = (req.url || "").split("?")[0];
    if (pathname.startsWith("/api/")) {
      requestHistory.push({
        at: new Date().toISOString(),
        method: req.method,
        path: pathname,
        status: res.statusCode,
        durationMs: Date.now() - startedAt
      });
      if (requestHistory.length > maxRequestHistory) requestHistory.splice(0, requestHistory.length - maxRequestHistory);
    }
  }
});

server.listen(config.port, "0.0.0.0", () => {
  console.log(`${config.appName} listening on http://0.0.0.0:${config.port}`);
});

async function route(req, res, url, body) {
  const pathname = url.pathname;

  if (req.method === "GET" && pathname === "/") return sendFile(res, path.join(rootDir, "public", "index.html"), "text/html; charset=utf-8");
  if (req.method === "GET" && pathname === "/api/health") return sendJson(res, 200, await getHealth());
  if (req.method === "GET" && pathname === "/api/models") return sendJson(res, 200, await getModels());
  if (req.method === "GET" && pathname === "/api/performance") return sendJson(res, 200, await getPerformance());
  if (req.method === "GET" && pathname === "/api/dashboard") return sendJson(res, 200, await getDashboard());
  if (req.method === "GET" && pathname === "/api/integrations") return sendJson(res, 200, await getIntegrations());
  if (req.method === "GET" && pathname === "/api/prompt-review") return sendJson(res, 200, await readJsonl(promptReviewPath, clamp(Number(url.searchParams.get("limit") || 50), 1, 200)));
  if (req.method === "GET" && pathname === "/api/conversations") {
    authorizeConversationArchive(req);
    return sendJson(res, 200, await queryConversationArchive(url.searchParams));
  }
  if (req.method === "GET" && pathname === "/api/conversations/summary") {
    authorizeConversationArchive(req);
    return sendJson(res, 200, await getConversationArchiveSummary(url.searchParams));
  }
  if (req.method === "GET" && pathname === "/api/home-assistant/events") return sendJson(res, 200, await readJsonl(eventLogPath, 200));
  if (req.method === "GET" && pathname === "/api/home-assistant/audit") return sendJson(res, 200, await getHomeAssistantAudit());
  if (req.method === "GET" && pathname === "/api/home-assistant/entities") return sendJson(res, 200, await getHomeAssistantEntities(url.searchParams));
  if (req.method === "GET" && pathname === "/api/benchmark") return sendJson(res, 200, await getBenchmark());

  if (req.method === "POST" && pathname === "/api/chat") return sendJson(res, 200, await chat(body || {}, { source: "api_chat" }));
  if (req.method === "POST" && pathname === "/api/assist") {
    authorizeAssistAlias(req, body || {});
    return sendJson(res, 200, await handleHomeAssistantWebhook(config.homeAssistantWebhookSecret, { ...(body || {}), source: "assist" }));
  }
  if (req.method === "POST" && pathname === "/api/benchmark/run") return sendJson(res, 200, await runBenchmark(Boolean(body?.deep)));
  if (req.method === "POST" && pathname === "/api/home-assistant/service") return sendJson(res, 200, await callHomeAssistantService(body || {}));

  const webhookMatch = pathname.match(/^\/api\/home-assistant\/webhook\/([^/]+)$/);
  if (req.method === "POST" && webhookMatch) {
    return sendJson(res, 200, await handleHomeAssistantWebhook(webhookMatch[1], body || {}));
  }

  const publicFile = path.normalize(path.join(rootDir, "public", pathname));
  if (req.method === "GET" && publicFile.startsWith(path.join(rootDir, "public"))) {
    return sendFile(res, publicFile, contentType(publicFile));
  }

  sendJson(res, 404, { error: "Not found" });
}

async function getHealth() {
  const ollama = await timed("ollama", () => fetchJson(`${config.ollamaHost}/api/tags`, {}, 2500));
  const haConfigured = Boolean(config.homeAssistantUrl && config.homeAssistantToken);
  const ha = haConfigured ? await timed("homeAssistant", () => fetchHomeAssistant("/api/")) : { ok: false, skipped: true };
  return {
    generatedAt: new Date().toISOString(),
    status: ollama.ok ? "Ready" : "Degraded",
    app: config.appName,
    runtime: { node: process.version, platform: process.platform, uptimeSeconds: Math.round(process.uptime()) },
    ollama: { configured: true, host: config.ollamaHost, ok: ollama.ok, error: ollama.error || null },
    homeAssistant: { configured: haConfigured, ok: Boolean(ha.ok), error: ha.error || null }
  };
}

async function getModels() {
  const [installed, loaded] = await Promise.all([
    timed("installed", () => fetchJson(`${config.ollamaHost}/api/tags`, {}, 4000)),
    timed("loaded", () => fetchJson(`${config.ollamaHost}/api/ps`, {}, 4000))
  ]);
  return {
    generatedAt: new Date().toISOString(),
    defaultModel: config.defaultModel,
    voiceModel: config.voiceModel,
    deepModel: config.deepModel,
    maxTokens: { default: config.defaultMaxTokens, voice: config.voiceMaxTokens, deep: config.deepMaxTokens },
    keepAlive: config.ollamaKeepAlive,
    installed: installed.ok ? normalizeOllamaModels(installed.value?.models || []) : [],
    loaded: loaded.ok ? normalizeOllamaModels(loaded.value?.models || []) : [],
    error: installed.error || loaded.error || null
  };
}

async function getPerformance() {
  const memory = process.memoryUsage();
  return {
    generatedAt: new Date().toISOString(),
    uptimeSeconds: Math.round(process.uptime()),
    process: {
      rssMb: bytesToMb(memory.rss),
      heapUsedMb: bytesToMb(memory.heapUsed),
      heapTotalMb: bytesToMb(memory.heapTotal)
    },
    requests: summarizeRequests(),
    ollama: await timed("ollamaPs", () => fetchJson(`${config.ollamaHost}/api/ps`, {}, 2500))
  };
}

async function getDashboard() {
  const [health, models, performance, integrations, audit, benchmark, conversations] = await Promise.all([
    getHealth(),
    getModels(),
    getPerformance(),
    getIntegrations(),
    getHomeAssistantAudit(),
    getBenchmark(),
    getConversationArchiveSummary()
  ]);
  return { generatedAt: new Date().toISOString(), health, models, performance, integrations, homeAssistantAudit: audit, benchmark, conversations };
}

async function getIntegrations() {
  return {
    generatedAt: new Date().toISOString(),
    ollama: { configured: true, host: config.ollamaHost },
    homeAssistant: {
      configured: Boolean(config.homeAssistantUrl && config.homeAssistantToken),
      url: config.homeAssistantUrl,
      webhookConfigured: Boolean(config.homeAssistantWebhookSecret),
      ttsEntity: config.ttsEntity,
      speakerConfigured: Boolean(config.defaultSpeaker)
    }
  };
}

async function chat(payload, options = {}) {
  const prompt = String(payload.message || payload.prompt || payload.text || "").trim();
  if (!prompt) throw httpError(400, "message is required");
  const mode = payload.mode || (options.source === "assist" ? "voice" : "default");
  const model = payload.model || (mode === "deep" ? config.deepModel : mode === "voice" ? config.voiceModel : config.defaultModel);
  const maxTokens = Number(payload.max_tokens || payload.maxTokens || (mode === "deep" ? config.deepMaxTokens : mode === "voice" ? config.voiceMaxTokens : config.defaultMaxTokens));
  const system = payload.system || (mode === "voice" ? config.voiceSystemPrompt : config.systemPrompt);
  const context = payload.context ? `\n\nContext:\n${String(payload.context).slice(0, 6000)}` : "";
  const startedAt = Date.now();
  try {
    const result = await ollamaChat({
      model,
      system,
      prompt: `${prompt}${context}`,
      maxTokens,
      temperature: Number(payload.temperature ?? 0.25)
    });
    const response = {
      generatedAt: new Date().toISOString(),
      source: options.source || payload.source || "api_chat",
      mode,
      model,
      response: result.message,
      durationMs: Date.now() - startedAt
    };
    const archived = await appendPromptReview({ ...response, prompt });
    response.conversationRecordId = archived.id;
    return response;
  } catch (error) {
    await appendPromptReview({
      generatedAt: new Date().toISOString(),
      source: options.source || payload.source || "api_chat",
      mode,
      model,
      prompt,
      response: "",
      durationMs: Date.now() - startedAt,
      error: error.message
    });
    throw error;
  }
}

async function handleHomeAssistantWebhook(secret, payload) {
  if (config.homeAssistantWebhookSecret && secret !== config.homeAssistantWebhookSecret) throw httpError(403, "Invalid webhook secret");
  const source = String(payload.source || "home_assistant_webhook");
  const text = String(payload.text || payload.message || payload.prompt || "").trim();
  if (!text) throw httpError(400, "text is required");

  const languageResult = source === "assist" ? await getDeterministicAssistResponse(text) : null;
  const entityContext = languageResult ? "" : await resolveHomeAssistantContext(text);
  const result = languageResult || await chat({ text, context: entityContext, mode: source === "assist" ? "voice" : "default" }, { source });
  if (!result.conversationRecordId) {
    const archived = await appendPromptReview({
      generatedAt: result.generatedAt || new Date().toISOString(),
      source,
      mode: result.mode || (source === "assist" ? "voice" : "default"),
      model: result.model || "deterministic-jarvis",
      prompt: text,
      response: result.response || "",
      durationMs: result.durationMs || 0,
      route: result.route || "deterministic"
    });
    result.conversationRecordId = archived.id;
  }

  if (payload.speak !== false && config.homeAssistantUrl && config.homeAssistantToken && config.defaultSpeaker) {
    await speakInHomeAssistant(result.response).catch((error) => {
      result.ttsError = error.message;
    });
  }

  const event = {
    at: new Date().toISOString(),
    source,
    text,
    response: result.response,
    model: result.model,
    durationMs: result.durationMs,
    conversationRecordId: result.conversationRecordId,
    entityMatches: result.entityMatches || []
  };
  await appendJsonl(eventLogPath, event);
  return { ...result, speech: { plain: { speech: result.response } }, event };
}

function authorizeAssistAlias(req, payload) {
  if (!config.homeAssistantWebhookSecret) return;
  const auth = String(req.headers.authorization || "");
  const bearer = auth.toLowerCase().startsWith("bearer ") ? auth.slice(7).trim() : "";
  const headerSecret = String(req.headers["x-jarvis-secret"] || "").trim();
  const bodySecret = String(payload.secret || "").trim();
  if ([bearer, headerSecret, bodySecret].includes(config.homeAssistantWebhookSecret)) return;
  throw httpError(403, "Invalid assist secret");
}

function authorizeConversationArchive(req) {
  if (!config.homeAssistantWebhookSecret) throw httpError(503, "Conversation analysis requires HOME_ASSISTANT_WEBHOOK_SECRET");
  const auth = String(req.headers.authorization || "");
  const bearer = auth.toLowerCase().startsWith("bearer ") ? auth.slice(7).trim() : "";
  const headerSecret = String(req.headers["x-jarvis-secret"] || "").trim();
  if ([bearer, headerSecret].includes(config.homeAssistantWebhookSecret)) return;
  throw httpError(403, "Invalid conversation archive secret");
}

async function getHomeAssistantAudit() {
  if (!config.homeAssistantUrl || !config.homeAssistantToken) {
    return { generatedAt: new Date().toISOString(), configured: false, status: "Needs HOME_ASSISTANT_URL and HOME_ASSISTANT_TOKEN" };
  }
  try {
    const states = await fetchHomeAssistant("/api/states");
    const domains = {};
    let unavailable = 0;
    let unknown = 0;
    for (const entity of states) {
      const domain = String(entity.entity_id || "unknown").split(".")[0];
      domains[domain] = (domains[domain] || 0) + 1;
      if (entity.state === "unavailable") unavailable += 1;
      if (entity.state === "unknown") unknown += 1;
    }
    return {
      generatedAt: new Date().toISOString(),
      configured: true,
      status: "Connected",
      entityCount: states.length,
      unavailable,
      unknown,
      domains,
      resolverCoverage: {
        explicitEntityId: states.length,
        misses: 0,
        percent: states.length ? 100 : 0
      },
      languageCoverage: auditHomeAssistantLanguageCoverage(states),
      sample: states.slice(0, 20).map(entitySummary)
    };
  } catch (error) {
    return { generatedAt: new Date().toISOString(), configured: true, status: "Connection failed", error: error.message };
  }
}

async function getHomeAssistantEntities(params) {
  if (!config.homeAssistantUrl || !config.homeAssistantToken) throw httpError(400, "Home Assistant is not configured");
  const query = String(params.get("q") || "").toLowerCase();
  const limit = clamp(Number(params.get("limit") || 50), 1, 200);
  const states = await fetchHomeAssistant("/api/states");
  return states
    .filter((entity) => {
      const haystack = `${entity.entity_id} ${entity.attributes?.friendly_name || ""}`.toLowerCase();
      return !query || haystack.includes(query);
    })
    .slice(0, limit)
    .map(entitySummary);
}

async function callHomeAssistantService(payload) {
  const domain = String(payload.domain || "").trim();
  const service = String(payload.service || "").trim();
  if (!domain || !service) throw httpError(400, "domain and service are required");
  return fetchHomeAssistant(`/api/services/${domain}/${service}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload.data || {})
  });
}

async function resolveHomeAssistantContext(text) {
  if (!config.homeAssistantUrl || !config.homeAssistantToken) return "";
  const states = await fetchHomeAssistant("/api/states");
  const ranked = rankHomeAssistantEntities(text, states, 8);
  if (!ranked.length) return "";
  const rows = [];
  for (const { state: entity } of ranked) {
    rows.push(`${entity.entity_id}: ${entity.state} ${entity.attributes?.unit_of_measurement || ""} (${entity.attributes?.friendly_name || "no friendly name"})`);
  }
  return rows.length ? `Home Assistant entity states:\n${rows.join("\n")}` : "";
}

function personalizeJarvisReply(reply) {
  const userName = String(config.userName || "Operator").trim() || "Operator";
  return String(reply || "")
    .replace(/\{name\}/g, userName)
    .replace(/\bsir\b/gi, userName);
}

async function getDeterministicHomeAssistantResponse(text) {
  if (!config.homeAssistantUrl || !config.homeAssistantToken) return null;
  const startedAt = Date.now();
  const states = await fetchHomeAssistant("/api/states");
  const answer = getHomeAssistantVoiceResponse(text, states, { timeZone: config.timeZone, userName: config.userName });
  if (!answer) return null;
  const response = {
    generatedAt: new Date().toISOString(),
    source: "assist",
    mode: "voice",
    model: "deterministic-home-assistant",
    response: personalizeJarvisReply(answer.reply),
    durationMs: Date.now() - startedAt,
    entityMatches: answer.states.map(entitySummary)
  };
  const archived = await appendPromptReview({ ...response, prompt: text, route: answer.kind });
  response.conversationRecordId = archived.id;
  return response;
}

async function getDeterministicAssistResponse(text) {
  const commonReply = getCommonAssistantReply(text, new Date(), { timeZone: config.timeZone, userName: config.userName });
  if (commonReply) {
    return {
      generatedAt: new Date().toISOString(),
      source: "assist",
      mode: "voice",
      model: "deterministic-jarvis",
      response: personalizeJarvisReply(commonReply),
      durationMs: 0,
      entityMatches: []
    };
  }
  return getDeterministicHomeAssistantResponse(text);
}

async function speakInHomeAssistant(message) {
  const data = { entity_id: config.ttsEntity, media_player_entity_id: config.defaultSpeaker, message };
  if (config.ttsLanguage) data.language = config.ttsLanguage;
  if (config.ttsVoice) data.options = { voice: config.ttsVoice };
  return fetchHomeAssistant("/api/services/tts/speak", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...data, cache: true })
  });
}

async function getBenchmark() {
  try {
    return JSON.parse(await fs.readFile(benchmarkPath, "utf8"));
  } catch {
    return { generatedAt: null, status: "Not run" };
  }
}

async function runBenchmark(includeDeep = false) {
  const tests = [];
  tests.push(await benchmarkStep("health", getHealth));
  tests.push(await benchmarkStep("models", getModels));
  tests.push(await benchmarkStep("fast chat", () => chat({ message: "Reply with exactly: Jarvis is ready.", mode: "voice", maxTokens: 16 }, { source: "benchmark" })));
  if (includeDeep) tests.push(await benchmarkStep("deep chat", () => chat({ message: "Summarize this system in one sentence.", mode: "deep", maxTokens: 80 }, { source: "benchmark" })));
  const failed = tests.filter((test) => test.status !== "Passed").length;
  const result = {
    generatedAt: new Date().toISOString(),
    status: failed ? "Degraded" : "Passed",
    summary: { checks: tests.length, passed: tests.length - failed, failed },
    tests
  };
  await fs.writeFile(benchmarkPath, `${JSON.stringify(result, null, 2)}\n`, "utf8");
  return result;
}

async function benchmarkStep(name, fn) {
  const startedAt = Date.now();
  try {
    const result = await fn();
    return { name, status: "Passed", durationMs: Date.now() - startedAt, result };
  } catch (error) {
    return { name, status: "Failed", durationMs: Date.now() - startedAt, error: error.message };
  }
}

async function ollamaChat({ model, system, prompt, maxTokens, temperature }) {
  const payload = {
    model,
    keep_alive: config.ollamaKeepAlive,
    stream: false,
    messages: [
      { role: "system", content: `${system}\n\nCurrent user name: ${config.userName}.` },
      { role: "user", content: prompt }
    ],
    options: { num_predict: maxTokens, temperature }
  };
  const data = await fetchJson(`${config.ollamaHost}/api/chat`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  }, config.ollamaTimeoutMs);
  return { message: String(data.message?.content || "").trim() };
}

async function fetchHomeAssistant(apiPath, options = {}) {
  if (!config.homeAssistantUrl || !config.homeAssistantToken) throw httpError(400, "Home Assistant is not configured");
  return fetchJson(`${config.homeAssistantUrl}${apiPath}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${config.homeAssistantToken}`,
      ...(options.headers || {})
    }
  }, 15000);
}

async function fetchJson(url, options = {}, timeoutMs = 10000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text();
    const data = text ? JSON.parse(text) : {};
    if (!response.ok) throw httpError(response.status, data.message || data.error || `HTTP ${response.status}`);
    return data;
  } finally {
    clearTimeout(timer);
  }
}

function applyCors(req, res) {
  const origin = req.headers.origin;
  if (origin && config.corsOrigins.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
  }
  res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Jarvis-Secret");
}

async function readJsonBody(req) {
  if (!["POST", "PUT", "PATCH"].includes(req.method || "")) return null;
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString("utf8").trim();
  if (!raw) return {};
  try {
    return JSON.parse(raw);
  } catch {
    throw httpError(400, "Invalid JSON body");
  }
}

async function sendFile(res, filePath, type) {
  try {
    const body = await fs.readFile(filePath);
    res.writeHead(200, { "Content-Type": type, "Content-Length": body.length });
    res.end(body);
  } catch {
    sendJson(res, 404, { error: "Not found" });
  }
}

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload, null, 2);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Content-Length": Buffer.byteLength(body) });
  res.end(body);
}

function sendNoContent(res) {
  res.writeHead(204);
  res.end();
}

function normalizeOllamaModels(models) {
  return models.map((model) => ({
    name: model.name || model.model || "",
    sizeMb: model.size ? Math.round(model.size / 1024 / 1024) : null,
    family: model.details?.family || null,
    parameterSize: model.details?.parameter_size || null,
    quantization: model.details?.quantization_level || null,
    expiresAt: model.expires_at || null
  }));
}

function summarizeRequests() {
  const byRoute = {};
  for (const row of requestHistory) {
    const key = `${row.method} ${row.path}`;
    byRoute[key] ||= { count: 0, totalMs: 0, maxMs: 0, errors: 0 };
    byRoute[key].count += 1;
    byRoute[key].totalMs += row.durationMs;
    byRoute[key].maxMs = Math.max(byRoute[key].maxMs, row.durationMs);
    if (row.status >= 400) byRoute[key].errors += 1;
  }
  for (const route of Object.values(byRoute)) route.avgMs = Math.round(route.totalMs / route.count);
  return { total: requestHistory.length, routes: byRoute, recent: requestHistory.slice(-25).reverse() };
}

function entitySummary(entity) {
  return {
    entityId: entity.entity_id,
    name: entity.attributes?.friendly_name || "",
    state: entity.state,
    unit: entity.attributes?.unit_of_measurement || "",
    updated: entity.last_updated || null
  };
}

async function appendJsonl(filePath, value) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.appendFile(filePath, `${JSON.stringify(value)}\n`, "utf8");
}

function normalizeConversationRecord(value, fallback = {}) {
  return {
    schemaVersion: 1,
    id: String(value.id || `conversation-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`),
    at: String(value.at || value.generatedAt || new Date().toISOString()),
    source: String(value.source || fallback.source || "unknown"),
    mode: String(value.mode || fallback.mode || "default"),
    model: String(value.model || ""),
    route: String(value.route || ""),
    durationMs: Number(value.durationMs || 0),
    prompt: String(value.prompt ?? value.text ?? ""),
    reply: String(value.reply ?? value.response ?? ""),
    status: value.error ? "error" : "complete",
    error: value.error ? String(value.error) : null,
    conversationId: value.conversationId || value.followUpJob || null,
    entityMatches: Array.isArray(value.entityMatches) ? value.entityMatches : []
  };
}

async function appendPromptReview(value) {
  const record = normalizeConversationRecord(value);
  await appendJsonl(promptReviewPath, record);
  const write = conversationArchiveQueue.then(() => appendJsonl(conversationArchivePath, record));
  conversationArchiveQueue = write.catch(() => {});
  await write;
  return record;
}

async function ensureConversationArchive() {
  await fs.mkdir(path.dirname(conversationArchivePath), { recursive: true });
  const existing = await readAllJsonl(conversationArchivePath);
  const promptHistory = await readAllJsonl(promptReviewPath);
  const eventHistory = await readAllJsonl(eventLogPath);
  const existingIds = new Set(existing.map((row) => row.id).filter(Boolean));
  const missing = [];

  promptHistory.forEach((row, index) => {
    const record = normalizeConversationRecord({ ...row, id: row.id || `legacy-prompt-review-${index}` }, { source: "legacy_prompt_review" });
    if (existingIds.has(record.id) || existing.some((item) => sameConversationRecord(item, record))) return;
    existingIds.add(record.id);
    missing.push(record);
  });

  eventHistory.forEach((event, index) => {
    const prompt = String(event.text || event.payload?.text || event.payload?.message || "").trim();
    if (!prompt || eventCoveredByPromptHistory(event, promptHistory)) return;
    const at = String(event.at || event.receivedAt || event.generatedAt || "");
    const id = `legacy-home-assistant-${at.replace(/[^0-9A-Za-z]/g, "") || "unknown"}-${index}`;
    if (existingIds.has(id)) return;
    const record = normalizeConversationRecord({
      id,
      at,
      source: event.source || event.payload?.source || "home_assistant_webhook",
      mode: event.mode || (event.source === "assist" || event.payload?.source === "assist" ? "voice" : "default"),
      model: event.model || event.deepModel || "",
      route: event.route || "legacy_home_assistant_event",
      durationMs: event.durationMs || event.deepDurationMs || 0,
      prompt,
      response: event.response || event.reply || "",
      error: event.error || null,
      entityMatches: event.entityMatches || []
    });
    record.legacySource = path.basename(eventLogPath);
    existingIds.add(id);
    missing.push(record);
  });

  if (missing.length) {
    await fs.appendFile(conversationArchivePath, `${missing.map((row) => JSON.stringify(row)).join("\n")}\n`, "utf8");
  }
}

function sameConversationRecord(left, right) {
  return String(left.at || "") === String(right.at || "") &&
    String(left.source || "") === String(right.source || "") &&
    String(left.prompt || "") === String(right.prompt || "") &&
    String(left.reply || "") === String(right.reply || "") &&
    String(left.route || "") === String(right.route || "");
}

function eventCoveredByPromptHistory(event, promptHistory) {
  const eventAt = Date.parse(String(event.at || event.receivedAt || event.generatedAt || ""));
  const prompt = String(event.text || event.payload?.text || event.payload?.message || "").trim();
  const reply = String(event.response || event.reply || "");
  return promptHistory.some((row) => {
    const rowAt = Date.parse(String(row.at || row.generatedAt || ""));
    return String(row.prompt || row.text || "").trim() === prompt &&
      String(row.reply ?? row.response ?? "") === reply &&
      Number.isFinite(eventAt) && Number.isFinite(rowAt) && Math.abs(eventAt - rowAt) < 60000;
  });
}

async function readAllJsonl(filePath) {
  try {
    const lines = (await fs.readFile(filePath, "utf8")).split(/\r?\n/).filter(Boolean);
    const rows = [];
    for (const line of lines) {
      try {
        rows.push(JSON.parse(line));
      } catch {
        // Keep later valid records queryable if one historical line was damaged.
      }
    }
    return rows;
  } catch {
    return [];
  }
}

async function queryConversationArchive(params) {
  const limit = clamp(Number(params.get("limit") || 100), 1, 1000);
  const source = String(params.get("source") || "").toLowerCase();
  const route = String(params.get("route") || "").toLowerCase();
  const query = String(params.get("q") || "").toLowerCase();
  const from = Date.parse(String(params.get("from") || ""));
  const to = Date.parse(String(params.get("to") || ""));
  const rows = (await readAllJsonl(conversationArchivePath)).filter((row) => {
    const at = Date.parse(String(row.at || ""));
    if (source && String(row.source || "").toLowerCase() !== source) return false;
    if (route && String(row.route || "").toLowerCase() !== route) return false;
    if (Number.isFinite(from) && (!Number.isFinite(at) || at < from)) return false;
    if (Number.isFinite(to) && (!Number.isFinite(at) || at > to)) return false;
    if (query && !`${row.prompt || ""}\n${row.reply || ""}`.toLowerCase().includes(query)) return false;
    return true;
  });
  const newestFirst = rows.toSorted((left, right) => conversationTimestamp(right) - conversationTimestamp(left));
  return {
    generatedAt: new Date().toISOString(),
    matched: rows.length,
    count: Math.min(rows.length, limit),
    items: newestFirst.slice(0, limit)
  };
}

async function getConversationArchiveSummary(params = new URLSearchParams()) {
  const days = clamp(Number(params.get?.("days") || 0), 0, 36500);
  const cutoff = days ? Date.now() - days * 86400000 : 0;
  const rows = (await readAllJsonl(conversationArchivePath)).filter((row) => {
    if (!cutoff) return true;
    const at = Date.parse(String(row.at || ""));
    return Number.isFinite(at) && at >= cutoff;
  });
  const bySource = {};
  const byRoute = {};
  let errors = 0;
  let durationTotal = 0;
  for (const row of rows) {
    const source = String(row.source || "unknown");
    const route = String(row.route || "unspecified");
    bySource[source] = (bySource[source] || 0) + 1;
    byRoute[route] = (byRoute[route] || 0) + 1;
    if (row.error || row.status === "error") errors += 1;
    durationTotal += Number(row.durationMs || 0);
  }
  const datedRows = rows.filter((row) => Number.isFinite(conversationTimestamp(row))).toSorted((left, right) => conversationTimestamp(left) - conversationTimestamp(right));
  return {
    enabled: true,
    appendOnly: true,
    retainedWithoutPruning: true,
    path: path.basename(conversationArchivePath),
    days: days || null,
    records: rows.length,
    errors,
    averageDurationMs: rows.length ? Math.round(durationTotal / rows.length) : 0,
    firstAt: datedRows[0]?.at || null,
    lastAt: datedRows.at(-1)?.at || null,
    bySource,
    byRoute
  };
}

function conversationTimestamp(row) {
  return Date.parse(String(row.at || row.generatedAt || ""));
}

async function readJsonl(filePath, limit) {
  try {
    const lines = (await fs.readFile(filePath, "utf8")).split(/\r?\n/).filter(Boolean);
    return { generatedAt: new Date().toISOString(), items: lines.slice(-limit).reverse().map((line) => JSON.parse(line)) };
  } catch {
    return { generatedAt: new Date().toISOString(), items: [] };
  }
}

async function timed(name, fn) {
  const startedAt = Date.now();
  try {
    return { name, ok: true, durationMs: Date.now() - startedAt, value: await fn() };
  } catch (error) {
    return { name, ok: false, durationMs: Date.now() - startedAt, error: error.message };
  }
}

function loadDotEnv(filePath) {
  try {
    const content = fsSync.readFileSync(filePath, "utf8");
    for (const rawLine of content.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || line.startsWith("#") || !line.includes("=")) continue;
      const [key, ...rest] = line.split("=");
      if (!process.env[key]) process.env[key] = rest.join("=").trim().replace(/^["']|["']$/g, "");
    }
  } catch {
    return;
  }
}

function env(name, fallback) {
  return process.env[name] || fallback;
}

function numberEnv(name, fallback) {
  const value = Number(process.env[name]);
  return Number.isFinite(value) ? value : fallback;
}

function stripSlash(value) {
  return String(value || "").replace(/\/$/, "");
}

function bytesToMb(value) {
  return Math.round((value / 1024 / 1024) * 10) / 10;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, Number.isFinite(value) ? value : min));
}

function contentType(filePath) {
  if (filePath.endsWith(".html")) return "text/html; charset=utf-8";
  if (filePath.endsWith(".css")) return "text/css; charset=utf-8";
  if (filePath.endsWith(".js")) return "application/javascript; charset=utf-8";
  if (filePath.endsWith(".svg")) return "image/svg+xml";
  return "application/octet-stream";
}

function httpError(statusCode, message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}
