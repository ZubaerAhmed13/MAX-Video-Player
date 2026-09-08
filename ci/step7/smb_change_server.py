#!/usr/bin/env python3
"""CI-only control endpoint for deterministic SMB reconnect/change certification."""

import json
import os
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


CONFIG_PATH = str(Path(sys.argv[1]).resolve())
TARGET_PATH = Path(sys.argv[2]).resolve()
MUTATION_LOCK = threading.Lock()


def wait_for_smb(timeout_seconds: float = 15.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", 1445), timeout=0.5):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("Samba did not restart on port 1445")


def replace_target_and_restart_smb() -> None:
    with MUTATION_LOCK:
        original = TARGET_PATH.read_bytes()
        if not original:
            raise RuntimeError("SMB change-detection fixture is empty")

        changed = bytearray(original)
        changed[min(4096, len(changed) - 1)] ^= 0xFF
        replacement = TARGET_PATH.with_suffix(".replacement")
        replacement.write_bytes(changed)
        replacement.chmod(0o644)
        os.replace(replacement, TARGET_PATH)

        # Destroy the existing transport so the Android DataSource must reconnect and re-stat the
        # same path. The replacement has the same length, proving detection is not size-only.
        subprocess.run(["pkill", "-9", "smbd"], check=False)
        subprocess.run(["smbd", f"--configfile={CONFIG_PATH}", "--daemon"], check=True)
        wait_for_smb()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self) -> None:
        if self.path != "/replace-same-size":
            self.send_error(404)
            return
        try:
            replace_target_and_restart_smb()
            payload = json.dumps({"changed": True, "size": TARGET_PATH.stat().st_size}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except Exception as error:  # pragma: no cover - failure is surfaced to instrumentation.
            payload = str(error).encode()
            self.send_response(500)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    def log_message(self, message: str, *args: object) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), message % args))


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 18080), Handler).serve_forever()
