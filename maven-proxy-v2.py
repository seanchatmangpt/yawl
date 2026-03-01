#!/usr/bin/env python3
"""Local proxy that adds auth when forwarding to upstream proxy.

Fix: use two blocking threads per connection (one per direction).
The original select+non-blocking+sendall pattern caused BlockingIOError
when the client receive buffer filled during large JAR downloads, causing
premature connection termination and truncated files.
"""
import socket, threading, os, base64, sys
from urllib.parse import urlparse

LOCAL_PORT = 3128
UPSTREAM = os.environ.get('https_proxy') or os.environ.get('HTTPS_PROXY')
BUFFER_SIZE = 131072  # 128KB

def log(msg):
    print(f"[proxy] {msg}", file=sys.stderr, flush=True)

def get_upstream():
    if not UPSTREAM:
        log("ERROR: No upstream proxy in environment")
        sys.exit(1)
    p = urlparse(UPSTREAM)
    return p.hostname, p.port, p.username or '', p.password or ''

def forward(src, dst, label):
    """Copy bytes from src to dst until src closes. Blocking I/O, no size limit."""
    try:
        while True:
            data = src.recv(BUFFER_SIZE)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        # Signal the other direction to stop by shutting down the write side
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass

def handle(client):
    upstream = None
    try:
        # Read the initial HTTP request (CONNECT method)
        req = b''
        while b'\r\n\r\n' not in req and len(req) < 8192:
            chunk = client.recv(4096)
            if not chunk:
                return
            req += chunk

        request_line = req.split(b'\r\n')[0].decode('utf-8', errors='ignore')
        log(f"Request: {request_line}")

        parts = request_line.split()
        if len(parts) < 2:
            return

        target = parts[1]
        if ':' in target and not target.startswith('['):
            host, port = target.rsplit(':', 1)
            try:
                port = int(port)
            except ValueError:
                port = 443
        else:
            host = target
            port = 443

        log(f"Connecting to {host}:{port}")

        proxy_host, proxy_port, user, pwd = get_upstream()
        auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()

        upstream = socket.socket()
        upstream.connect((proxy_host, proxy_port))

        connect_req = (
            f"CONNECT {host}:{port} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            f"Proxy-Authorization: Basic {auth}\r\n"
            f"\r\n"
        )
        upstream.sendall(connect_req.encode())

        # Read upstream CONNECT response
        resp = b''
        while b'\r\n\r\n' not in resp:
            chunk = upstream.recv(4096)
            if not chunk:
                log("Upstream closed during CONNECT handshake")
                return
            resp += chunk

        if b'200' not in resp.split(b'\r\n')[0]:
            log(f"CONNECT failed: {resp[:100]}")
            return

        # Tell client the tunnel is established
        client.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')

        # Two threads, blocking I/O — no sendall-on-nonblocking bug
        t1 = threading.Thread(target=forward, args=(client, upstream, 'c→u'), daemon=True)
        t2 = threading.Thread(target=forward, args=(upstream, client, 'u→c'), daemon=True)
        t1.start()
        t2.start()
        t1.join()
        t2.join()

    except Exception as e:
        log(f"Error: {e}")
    finally:
        for s in (client, upstream):
            if s:
                try:
                    s.close()
                except OSError:
                    pass

if __name__ == '__main__':
    if not UPSTREAM:
        log("ERROR: https_proxy not set")
        sys.exit(1)

    log(f"Starting on 127.0.0.1:{LOCAL_PORT} -> {UPSTREAM}")
    log(f"Buffer size: {BUFFER_SIZE // 1024}KB per recv")

    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(('127.0.0.1', LOCAL_PORT))
    srv.listen(256)

    while True:
        c, addr = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()
