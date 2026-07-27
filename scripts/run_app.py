from __future__ import annotations

import argparse
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from assistant.config import load_config
from assistant.memory import MemoryStore
from assistant.runtime import build_brain
from assistant.vm import LlmVmClient


INDEX_HTML = r"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Jarvis Console</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #0b0f12;
      --panel: #12191f;
      --panel-2: #17212a;
      --text: #e8edf2;
      --muted: #9aa8b5;
      --line: #26323d;
      --accent: #58d6a3;
      --warn: #f4bd5f;
      --bad: #ff7a7a;
      --blue: #76a9ff;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: radial-gradient(circle at top left, #16302a 0, transparent 34rem), var(--bg);
      color: var(--text);
    }
    main {
      min-height: 100vh;
      display: grid;
      grid-template-columns: minmax(280px, 380px) minmax(0, 1fr);
    }
    aside {
      border-right: 1px solid var(--line);
      padding: 24px;
      background: rgba(18, 25, 31, 0.84);
    }
    section { padding: 24px; }
    h1 { margin: 0 0 8px; font-size: 28px; line-height: 1.1; letter-spacing: 0; }
    h2 { margin: 24px 0 10px; font-size: 14px; text-transform: uppercase; color: var(--muted); letter-spacing: .08em; }
    p { color: var(--muted); line-height: 1.5; }
    .status-grid { display: grid; gap: 10px; margin-top: 20px; }
    .row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 0;
      border-bottom: 1px solid var(--line);
      font-size: 14px;
    }
    .value { color: var(--text); font-family: ui-monospace, SFMono-Regular, Consolas, monospace; text-align: right; overflow-wrap: anywhere; }
    .dot { width: 10px; height: 10px; border-radius: 50%; background: var(--muted); display: inline-block; margin-right: 8px; }
    .dot.ok { background: var(--accent); }
    .dot.warn { background: var(--warn); }
    .dot.bad { background: var(--bad); }
    button {
      border: 1px solid var(--line);
      background: var(--panel-2);
      color: var(--text);
      border-radius: 7px;
      padding: 10px 12px;
      font-weight: 650;
      cursor: pointer;
    }
    button.primary { background: var(--accent); color: #06110d; border-color: transparent; }
    button:disabled { opacity: .55; cursor: wait; }
    a.inline-link { color: var(--blue); text-decoration: none; }
    a.inline-link:hover { text-decoration: underline; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
    .chat {
      display: grid;
      grid-template-rows: 1fr auto;
      gap: 16px;
      height: calc(100vh - 48px);
    }
    .messages {
      overflow: auto;
      border: 1px solid var(--line);
      background: rgba(11, 15, 18, 0.72);
      padding: 18px;
      border-radius: 8px;
    }
    .msg { max-width: 860px; margin: 0 0 14px; padding: 12px 14px; border-radius: 8px; line-height: 1.45; white-space: pre-wrap; }
    .user { margin-left: auto; background: #1c3650; }
    .jarvis { background: var(--panel); border: 1px solid var(--line); }
    form { display: grid; grid-template-columns: 1fr auto; gap: 10px; }
    input {
      width: 100%;
      border: 1px solid var(--line);
      background: var(--panel);
      color: var(--text);
      border-radius: 7px;
      padding: 12px 14px;
      font-size: 16px;
    }
    pre {
      max-height: 280px;
      overflow: auto;
      background: #081015;
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 12px;
      color: #c8d7e3;
      font-size: 12px;
      line-height: 1.45;
    }
    @media (max-width: 840px) {
      main { grid-template-columns: 1fr; }
      aside { border-right: 0; border-bottom: 1px solid var(--line); }
      .chat { height: auto; min-height: 62vh; }
      form { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <main>
    <aside>
      <h1>Jarvis Console</h1>
      <p>Local assistant runtime with VM and model status for the LLM host.</p>
      <div class="status-grid">
        <div class="row"><span>Backend</span><span class="value" id="backend">...</span></div>
        <div class="row"><span>Model</span><span class="value" id="model">...</span></div>
        <div class="row"><span>VM</span><span class="value" id="vm">...</span></div>
        <div class="row"><span>API</span><span class="value" id="api">...</span></div>
        <div class="row"><span>House voice</span><span class="value" id="voice">optional</span></div>
        <div class="row"><span>LLM UI</span><span class="value" id="llmUi">not configured</span></div>
      </div>
      <div class="actions">
        <button id="refreshVm">Refresh VM</button>
        <button id="refreshModels">Models</button>
      </div>
      <h2>VM Status</h2>
      <p><span class="dot" id="vmDot"></span><span id="vmSummary">Not checked yet.</span></p>
      <pre id="vmReport"></pre>
    </aside>
    <section>
      <div class="chat">
        <div class="messages" id="messages">
          <div class="msg jarvis">Jarvis UI is ready. Ask for status, models, files, memory, or a VM check.</div>
        </div>
        <form id="chatForm">
          <input id="prompt" autocomplete="off" placeholder="Ask Jarvis..." />
          <button class="primary" id="send" type="submit">Send</button>
        </form>
      </div>
    </section>
  </main>
  <script>
    const $ = (id) => document.getElementById(id);
    const addMsg = (text, who) => {
      const node = document.createElement("div");
      node.className = `msg ${who}`;
      node.textContent = text;
      $("messages").appendChild(node);
      $("messages").scrollTop = $("messages").scrollHeight;
    };
    const postJson = async (url, body) => {
      const response = await fetch(url, { method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body) });
      if (!response.ok) throw new Error(await response.text());
      return response.json();
    };
    const getJson = async (url) => {
      const response = await fetch(url);
      if (!response.ok) throw new Error(await response.text());
      return response.json();
    };
    async function loadConfig() {
      const cfg = await getJson("/api/config");
      $("backend").textContent = cfg.backend;
      $("model").textContent = cfg.model;
      $("vm").textContent = `${cfg.llm_vm_user}@${cfg.llm_vm_host}`;
      $("api").textContent = cfg.llm_vm_base_url || "not set";
      const uiUrl = `http://${cfg.llm_vm_host}:8787`;
      $("llmUi").innerHTML = `<a class="inline-link" href="${uiUrl}" target="_blank" rel="noreferrer">${cfg.llm_vm_host}:8787</a>`;
    }
    async function refreshVm() {
      $("refreshVm").disabled = true;
      $("vmSummary").textContent = "Checking...";
      try {
        const report = await getJson("/api/vm");
        $("vmReport").textContent = JSON.stringify(report, null, 2);
        const anyPort = (report.ports || []).some((p) => p.open);
        const sshOk = report.ssh && report.ssh.ok;
        $("vmDot").className = `dot ${anyPort || sshOk ? "ok" : "warn"}`;
        $("vmSummary").textContent = sshOk ? "SSH available" : (anyPort ? "Model API port reachable" : "Host reachable, model API not exposed");
      } catch (err) {
        $("vmDot").className = "dot bad";
        $("vmSummary").textContent = err.message;
      } finally {
        $("refreshVm").disabled = false;
      }
    }
    async function refreshModels() {
      $("refreshModels").disabled = true;
      try {
        const report = await getJson("/api/models");
        $("vmReport").textContent = JSON.stringify(report, null, 2);
        addMsg(report.ok ? `Models: ${report.models.join(", ") || "none returned"}` : "No model HTTP API answered from this workstation.", "jarvis");
      } catch (err) {
        addMsg(err.message, "jarvis");
      } finally {
        $("refreshModels").disabled = false;
      }
    }
    $("chatForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      const text = $("prompt").value.trim();
      if (!text) return;
      $("prompt").value = "";
      $("send").disabled = true;
      addMsg(text, "user");
      try {
        const reply = await postJson("/api/chat", {message: text});
        addMsg(reply.response, "jarvis");
      } catch (err) {
        addMsg(err.message, "jarvis");
      } finally {
        $("send").disabled = false;
        $("prompt").focus();
      }
    });
    $("refreshVm").addEventListener("click", refreshVm);
    $("refreshModels").addEventListener("click", refreshModels);
    loadConfig().then(refreshVm).catch((err) => addMsg(err.message, "jarvis"));
  </script>
</body>
</html>
"""


class AppState:
    def __init__(self) -> None:
        self.config = load_config()
        self.memory = MemoryStore(self.config.memory_path, self.config.notes_path, self.config.time_zone)
        self.brain = build_brain(self.config, self.memory)
        self.vm = LlmVmClient(self.config)


class Handler(BaseHTTPRequestHandler):
    state: AppState

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/":
            self._html(INDEX_HTML)
        elif path == "/api/config":
            self._json(
                {
                    "backend": self.state.config.backend,
                    "model": self.state.config.model,
                    "llm_vm_host": self.state.config.llm_vm_host,
                    "llm_vm_user": self.state.config.llm_vm_user,
                    "llm_vm_base_url": self.state.config.llm_vm_base_url,
                }
            )
        elif path == "/api/vm":
            self._json(self.state.vm.inspect())
        elif path == "/api/models":
            self._json(self.state.vm.list_http_models())
        else:
            self.send_error(404)

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/api/chat":
            self.send_error(404)
            return
        payload = self._read_json()
        message = str(payload.get("message", "")).strip()
        if not message:
            self.send_error(400, "message is required")
            return
        self._json({"response": self.state.brain.respond(message)})

    def log_message(self, format: str, *args) -> None:
        return

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        return json.loads(raw)

    def _json(self, payload: object, status: int = 200) -> None:
        body = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _html(self, body: str) -> None:
        encoded = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the Jarvis local web console.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8765, type=int)
    args = parser.parse_args()

    Handler.state = AppState()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Jarvis app UI: http://{args.host}:{args.port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
