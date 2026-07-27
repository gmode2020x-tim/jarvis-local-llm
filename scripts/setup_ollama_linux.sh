#!/usr/bin/env bash
set -euo pipefail

MODEL="${JARVIS_MODEL:-llama3.2:3b}"
OLLAMA_HOST_VALUE="${OLLAMA_HOST:-0.0.0.0:11434}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run with sudo: sudo JARVIS_MODEL=${MODEL} bash scripts/setup_ollama_linux.sh" >&2
  exit 1
fi

apt-get update
apt-get install -y curl ca-certificates

if ! command -v ollama >/dev/null 2>&1; then
  curl -fsSL https://ollama.com/install.sh | sh
fi

install -d -m 0755 /etc/systemd/system/ollama.service.d
cat >/etc/systemd/system/ollama.service.d/jarvis.conf <<EOF
[Service]
Environment="OLLAMA_HOST=${OLLAMA_HOST_VALUE}"
Environment="OLLAMA_KEEP_ALIVE=30m"
EOF

systemctl daemon-reload
systemctl enable --now ollama
systemctl restart ollama

echo "Pulling ${MODEL}. This can take a while."
ollama pull "${MODEL}"

echo "Ollama is ready on ${OLLAMA_HOST_VALUE} with ${MODEL}."
