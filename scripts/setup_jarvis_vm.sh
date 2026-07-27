#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${JARVIS_APP_DIR:-/opt/jarvis}"
MODEL="${JARVIS_MODEL:-llama3.2:3b}"
SERVICE_FILE="/etc/systemd/system/jarvis-llm-ui.service"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run with sudo: sudo JARVIS_MODEL=${MODEL} bash scripts/setup_jarvis_vm.sh" >&2
  exit 1
fi

apt-get update
apt-get install -y ca-certificates curl rsync

if ! command -v node >/dev/null 2>&1 || [[ "$(node -p 'Number(process.versions.node.split(`.`)[0])' 2>/dev/null || echo 0)" -lt 20 ]]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi

if ! command -v ollama >/dev/null 2>&1; then
  curl -fsSL https://ollama.com/install.sh | sh
fi

install -d -m 0755 /etc/systemd/system/ollama.service.d
cat >/etc/systemd/system/ollama.service.d/jarvis.conf <<EOF
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
Environment="OLLAMA_KEEP_ALIVE=30m"
EOF

systemctl daemon-reload
systemctl enable --now ollama
systemctl restart ollama
ollama pull "${MODEL}"

useradd --system --home "${APP_DIR}" --shell /usr/sbin/nologin jarvis 2>/dev/null || true
mkdir -p "${APP_DIR}"
rsync -a --delete \
  --exclude ".git" \
  --exclude ".venv" \
  --exclude ".env" \
  --exclude ".tools" \
  --exclude "data/assistant_memory.json" \
  --exclude "data/assistant_notes.md" \
  "${REPO_DIR}/" "${APP_DIR}/"

cd "${APP_DIR}/vm/llm-ui"
if [[ ! -f .env ]]; then
  cp .env.example .env
  sed -i "s/^DEFAULT_MODEL=.*/DEFAULT_MODEL=${MODEL}/" .env
  sed -i "s/^VOICE_MODEL=.*/VOICE_MODEL=${MODEL}/" .env
fi

chown -R jarvis:jarvis "${APP_DIR}"
cp "${APP_DIR}/vm/llm-ui/systemd/jarvis-llm-ui.service" "${SERVICE_FILE}"
systemctl daemon-reload
systemctl enable --now jarvis-llm-ui
systemctl restart jarvis-llm-ui

echo "Jarvis VM is installed."
echo "UI: http://$(hostname -I | awk '{print $1}'):8787"
echo "Edit: ${APP_DIR}/vm/llm-ui/.env"
