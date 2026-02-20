#!/usr/bin/env python3
"""Local HTTPS proxy that forwards CONNECT to upstream proxy."""
import socket, threading, os, base64, select, sys
from urllib.parse import urlparse

LOCAL_PORT = 443
UPSTREAM = os.environ.get('https_proxy') or os.environ.get('HTTPS_PROXY')

def get_upstream():
    if not UPSTREAM:
        print("[proxy] ERROR: No upstream proxy in environment", file=sys.stderr)
        sys.exit(1)
    p = urlparse(UPSTREAM)
    return p.hostname, p.port, p.username or '', p.password or ''

def handle(client):
    try:
        req = b''
        while b'\r\n\r\n' not in req and len(req) < 8192:
            chunk = client.recv(4096)
            if not chunk:
                return
            req += chunk

        request_line = req.split(b'\r\n')[0].decode('utf-8', errors='ignore')

        parts = request_line.split()
        if len(parts) < 2:
            client.close()
            return

        target = parts[1]
        if ':' in target:
            host, port = target.rsplit(':', 1)
            try:
                port = int(port)
            except:
                port = 443
        else:
            host = target
            port = 443

        proxy_host, proxy_port, user, pwd = get_upstream()
        auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()

        upstream = socket.socket()
        upstream.connect((proxy_host, proxy_port))

        connect_req = f"CONNECT {host}:{port} HTTP/1.1\r\nProxy-Authorization: Basic {auth}\r\n\r\n"
        upstream.send(connect_req.encode())

        resp = b''
        while b'\r\n\r\n' not in resp:
            resp += upstream.recv(4096)

        if b'200' in resp.split(b'\r\n')[0]:
            client.send(b'HTTP/1.1 200 Connection Established\r\n\r\n')
            upstream.setblocking(False)
            client.setblocking(False)
            while True:
                r, _, _ = select.select([client, upstream], [], [], 30)
                if not r:
                    break
                for s in r:
                    data = s.recv(8192)
                    if not data:
                        return
                    (upstream if s is client else client).sendall(data)
    except Exception as e:
        pass
    finally:
        try:
            upstream.close()
        except:
            pass
        client.close()

if __name__ == '__main__':
    if not UPSTREAM:
        print("[proxy] ERROR: https_proxy not set", file=sys.stderr)
        sys.exit(1)

    print(f"[proxy] Starting on 0.0.0.0:{LOCAL_PORT} -> {UPSTREAM}", file=sys.stderr, flush=True)

    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(('0.0.0.0', LOCAL_PORT))
    srv.listen(128)
    print(f"[proxy] Listening on 0.0.0.0:{LOCAL_PORT}", file=sys.stderr, flush=True)

    while True:
        c, addr = srv.accept()
        threading.Thread(target=handle, args=(c,), daemon=True).start()
