# Jarvis LLM UI

This is the VM-side service for a full Jarvis setup. It runs beside Ollama and provides:

- web dashboard at `http://YOUR-LLM-HOST:8787`
- chat API at `POST /api/chat`
- Home Assistant Assist webhook at `POST /api/home-assistant/webhook/:secret`
- model inventory at `GET /api/models`
- performance and request history at `GET /api/performance`
- combined dashboard JSON at `GET /api/dashboard`
- Home Assistant entity audit at `GET /api/home-assistant/audit`
- prompt review log at `GET /api/prompt-review`

## Install On The VM

Install Node 20+ and Ollama:

```bash
sudo apt-get update
sudo apt-get install -y nodejs npm
curl -fsSL https://ollama.com/install.sh | sh
sudo systemctl enable --now ollama
ollama pull llama3.2:3b
```

Copy this repository to `/opt/jarvis`, then configure:

```bash
cd /opt/jarvis/vm/llm-ui
cp .env.example .env
nano .env
node server.js
```

Open:

```text
http://YOUR-LLM-HOST:8787
```

## Systemd

```bash
sudo useradd --system --home /opt/jarvis --shell /usr/sbin/nologin jarvis || true
sudo chown -R jarvis:jarvis /opt/jarvis
sudo cp /opt/jarvis/vm/llm-ui/systemd/jarvis-llm-ui.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now jarvis-llm-ui
sudo systemctl status jarvis-llm-ui --no-pager
```

## Docker Compose

```bash
cd /opt/jarvis/vm/llm-ui
cp .env.example .env
docker compose up -d --build
```

If Ollama runs on the host, set:

```dotenv
OLLAMA_HOST=http://host.docker.internal:11434
```

## Home Assistant Assist Webhook

Set a long random secret:

```dotenv
HOME_ASSISTANT_WEBHOOK_SECRET=change-this-long-random-secret
```

Then call:

```text
POST http://YOUR-LLM-HOST:8787/api/home-assistant/webhook/change-this-long-random-secret
```

Example body:

```json
{
  "source": "assist",
  "text": "What is sensor.office_temperature?",
  "speak": true
}
```

## Smoke Test

```bash
npm run smoke
```
