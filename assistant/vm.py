from __future__ import annotations

import json
import socket
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import paramiko
import requests

from .config import AssistantConfig


COMMON_MODEL_PORTS = (11434, 1234, 8000, 8080, 5000, 7860)
SSH_PORT = 22


@dataclass(frozen=True)
class VmCommandResult:
    ok: bool
    stdout: str = ""
    stderr: str = ""
    error: str = ""


class LlmVmClient:
    def __init__(self, config: AssistantConfig) -> None:
        self.config = config

    def ping(self) -> str:
        command = ["ping", "-n", "2", self.config.llm_vm_host]
        result = subprocess.run(command, capture_output=True, text=True, timeout=10)
        return (result.stdout or result.stderr).strip()[-2000:]

    def tcp_probe(self, ports: tuple[int, ...] = COMMON_MODEL_PORTS) -> list[dict[str, Any]]:
        results = []
        for port in ports:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                sock.settimeout(1.0)
                code = sock.connect_ex((self.config.llm_vm_host, port))
            results.append({"port": port, "open": code == 0})
        return results

    def tcp_port_open(self, port: int) -> bool:
        return self.tcp_probe((port,))[0]["open"]

    def list_http_models(self) -> dict[str, Any]:
        attempts = []
        urls = [
            self.config.llm_vm_base_url.rstrip("/") + "/models" if self.config.llm_vm_base_url else "",
            f"http://{self.config.llm_vm_host}:11434/api/tags",
            f"http://{self.config.llm_vm_host}:1234/v1/models",
            f"http://{self.config.llm_vm_host}:8000/v1/models",
            f"http://{self.config.llm_vm_host}:8080/v1/models",
        ]
        for url in [item for item in dict.fromkeys(urls) if item]:
            try:
                response = requests.get(url, timeout=4)
                attempts.append({"url": url, "status": response.status_code})
                if response.ok:
                    return {"ok": True, "url": url, "models": self._extract_models(response.json()), "attempts": attempts}
            except requests.RequestException as exc:
                attempts.append({"url": url, "error": str(exc)})
            except ValueError as exc:
                attempts.append({"url": url, "error": f"invalid JSON: {exc}"})
        return {"ok": False, "models": [], "attempts": attempts}

    def ssh_command(self, command: str, timeout: int = 20) -> VmCommandResult:
        key_filename = self._ssh_key_filename()
        if not any((self.config.llm_vm_password, key_filename, self.config.llm_vm_allow_agent)):
            return VmCommandResult(
                ok=False,
                error=(
                    "No SSH credential is configured. Set JARVIS_LLM_VM_PASSWORD, "
                    "JARVIS_LLM_VM_KEY_PATH, or JARVIS_LLM_VM_ALLOW_AGENT=1."
                ),
            )

        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        try:
            client.connect(
                self.config.llm_vm_host,
                username=self.config.llm_vm_user,
                password=self.config.llm_vm_password,
                key_filename=key_filename,
                timeout=10,
                banner_timeout=10,
                auth_timeout=10,
                look_for_keys=not key_filename,
                allow_agent=self.config.llm_vm_allow_agent,
            )
            _, stdout, stderr = client.exec_command(command, timeout=timeout)
            exit_code = stdout.channel.recv_exit_status()
            return VmCommandResult(
                ok=exit_code == 0,
                stdout=stdout.read().decode(errors="replace").strip(),
                stderr=stderr.read().decode(errors="replace").strip(),
            )
        except paramiko.ssh_exception.BadAuthenticationType as exc:
            return VmCommandResult(
                ok=False,
                error=f"SSH server rejected password auth; allowed types: {', '.join(exc.allowed_types)}",
            )
        except Exception as exc:
            return VmCommandResult(ok=False, error=str(exc))
        finally:
            client.close()

    def inspect(self) -> dict[str, Any]:
        return {
            "host": self.config.llm_vm_host,
            "user": self.config.llm_vm_user,
            "base_url": self.config.llm_vm_base_url,
            "ping": self.ping(),
            "ssh_port": {"port": SSH_PORT, "open": self.tcp_port_open(SSH_PORT)},
            "ports": self.tcp_probe(),
            "http_models": self.list_http_models(),
            "ssh": self.ssh_command(
                "hostname; whoami; "
                "ss -ltnp 2>/dev/null | sed -n '1,40p'; "
                "command -v ollama || true; command -v docker || true; "
                "find \"$HOME\" -maxdepth 3 -type f \\( -iname 'README*' -o -iname '*.md' \\) 2>/dev/null | head -40"
            ).__dict__,
        }

    def _ssh_key_filename(self) -> str | None:
        key_path: Path | None = self.config.llm_vm_key_path
        if key_path is None:
            return None
        if not key_path.exists():
            return None
        return str(key_path)

    @staticmethod
    def _extract_models(payload: dict[str, Any]) -> list[str]:
        if isinstance(payload.get("models"), list):
            return [str(item.get("name") or item.get("id") or item) for item in payload["models"]]
        if isinstance(payload.get("data"), list):
            return [str(item.get("id") or item.get("name") or item) for item in payload["data"]]
        return []


def compact_json(data: Any) -> str:
    return json.dumps(data, indent=2, sort_keys=True)
