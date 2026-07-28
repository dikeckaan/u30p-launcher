#!/usr/bin/env python3
"""Minimal ttyd websocket client — runs a command on the device and prints output.

ttyd 1.7 protocol: first client message is the init JSON (unprefixed), then
input frames are prefixed with '0'. Server output frames are prefixed with '0'.
"""
import base64
import json
import os
import re
import socket
import struct
import sys
import time

HOST = "192.168.0.1"
PORT = 1146
MARK = "__TTYD_DONE__"


def ws_connect(host, port, path="/ws", subproto="tty"):
    s = socket.create_connection((host, port), timeout=10)
    key = base64.b64encode(os.urandom(16)).decode()
    req = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        f"Sec-WebSocket-Protocol: {subproto}\r\n"
        "\r\n"
    )
    s.sendall(req.encode())
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = s.recv(4096)
        if not chunk:
            raise RuntimeError("handshake closed")
        buf += chunk
    head, rest = buf.split(b"\r\n\r\n", 1)
    if b"101" not in head.split(b"\r\n")[0]:
        raise RuntimeError("handshake failed: " + head.decode(errors="replace")[:300])
    return s, rest


def ws_send(sock, payload, opcode=0x1):
    if isinstance(payload, str):
        payload = payload.encode()
    header = bytearray()
    header.append(0x80 | opcode)
    n = len(payload)
    mask_bit = 0x80
    if n < 126:
        header.append(mask_bit | n)
    elif n < 65536:
        header.append(mask_bit | 126)
        header += struct.pack(">H", n)
    else:
        header.append(mask_bit | 127)
        header += struct.pack(">Q", n)
    mask = os.urandom(4)
    header += mask
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    sock.sendall(bytes(header) + masked)


class Reader:
    def __init__(self, sock, initial=b""):
        self.sock = sock
        self.buf = initial

    def _need(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise RuntimeError("closed")
            self.buf += chunk

    def frame(self):
        self._need(2)
        b0, b1 = self.buf[0], self.buf[1]
        opcode = b0 & 0x0F
        masked = b1 & 0x80
        ln = b1 & 0x7F
        off = 2
        if ln == 126:
            self._need(4)
            ln = struct.unpack(">H", self.buf[2:4])[0]
            off = 4
        elif ln == 127:
            self._need(10)
            ln = struct.unpack(">Q", self.buf[2:10])[0]
            off = 10
        if masked:
            self._need(off + 4)
            mask = self.buf[off:off + 4]
            off += 4
        self._need(off + ln)
        payload = self.buf[off:off + ln]
        if masked:
            payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        self.buf = self.buf[off + ln:]
        return opcode, payload


ANSI = re.compile(rb"\x1b\[[0-9;?]*[a-zA-Z]|\x1b\][^\x07]*\x07|\r")


def run(cmd, settle=1.0, timeout=25.0):
    sock, rest = ws_connect(HOST, PORT)
    r = Reader(sock, rest)
    ws_send(sock, json.dumps({"AuthToken": "", "columns": 200, "rows": 50}))
    time.sleep(settle)

    ws_send(sock, "0" + cmd + f"\necho {MARK}\n")

    out = b""
    sock.settimeout(timeout)
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            opcode, payload = r.frame()
        except Exception:
            break
        if opcode == 0x8:
            break
        if opcode in (0x1, 0x2) and payload[:1] == b"0":
            out += payload[1:]
            if MARK.encode() in out:
                break
    sock.close()
    clean = ANSI.sub(b"", out).decode(errors="replace")
    lines = [l for l in clean.split("\n")]
    # strip the echoed command and everything after the marker
    res = []
    for l in lines:
        if MARK in l:
            break
        res.append(l)
    return "\n".join(res)


if __name__ == "__main__":
    print(run(" ".join(sys.argv[1:])))
