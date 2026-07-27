const baseUrl = process.env.JARVIS_LLM_UI_URL || `http://127.0.0.1:${process.env.PORT || 8787}`;

for (const path of ["/api/health", "/api/models", "/api/performance", "/api/dashboard"]) {
  const response = await fetch(`${baseUrl}${path}`);
  if (!response.ok) throw new Error(`${path} returned HTTP ${response.status}`);
  const data = await response.json();
  console.log(`${path}: ${data.status || "ok"}`);
}

console.log("jarvis llm-ui smoke test passed");
