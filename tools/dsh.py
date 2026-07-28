#!/usr/bin/env python3
"""Device shell over ttyd — logs in and runs a command as root.

Fallback channel for when adb-over-wifi is down.
"""
import json
import os
import sys
import time

import ttyd

# Cihazin ttyd kabugu parola sorar. Repoya gomulmemesi icin ortamdan okunur;
# varsayilan UFI-TOOLS kurulum degeridir.
PASSWORD = os.environ.get("U30P_SHELL_PASSWORD", "admin")
BEGIN = "__DSH_B__"
END = "__DSH_E__"


def dsh(cmd, timeout=40.0):
    """Runs `cmd` as root on the device. Output is delimited by markers rather
    than by line position — the terminal echoes and wraps the command line, so
    positional parsing is unreliable."""
    sock, rest = ttyd.ws_connect(ttyd.HOST, ttyd.PORT)
    r = ttyd.Reader(sock, rest)
    ttyd.ws_send(sock, json.dumps({"AuthToken": "", "columns": 220, "rows": 60}))
    time.sleep(1.2)
    ttyd.ws_send(sock, "0" + PASSWORD + "\n")
    time.sleep(1.5)
    ttyd.ws_send(sock, f"0echo {BEGIN}\n" + cmd + f"\necho {END}\n")

    out = b""
    sock.settimeout(timeout)
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            op, p = r.frame()
        except Exception:
            break
        if op == 0x8:
            break
        if op in (0x1, 0x2) and p[:1] == b"0":
            out += p[1:]
            # the echoed command carries one copy of each marker; the real
            # output is bounded by the second pair
            if out.count(END.encode()) >= 2:
                break
    sock.close()

    clean = ttyd.ANSI.sub(b"", out).decode(errors="replace")
    b = clean.rfind(BEGIN)
    e = clean.rfind(END)
    if b < 0 or e < 0 or e < b:
        return clean.strip()
    body = clean[b + len(BEGIN):e]
    return "\n".join(body.split("\n")[1:-1]).strip("\n")


if __name__ == "__main__":
    print(dsh(" ".join(sys.argv[1:])))
