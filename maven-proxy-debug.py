#!/usr/bin/env python3
"""Local HTTPS proxy with debug logging."""
import socket, threading, os, base64, select, sys
from urllib.parse import urlparse

LOCAL_PORT = 443
UPSTREAM = os.environ.get('https_proxy') or os.environ.get('HTTPS_PROXY')

def log(msg):
    print(f"[proxy] {msg}", file=sys.stderr, flush=True)

def get_upstream():
    if not UPSTREAM:
        log("ERROR: No upstream proxy in environment")
        sys.exit(1)
    p = urlparse(UPSTREAM)
    return p.hostname, p.port, p.username or '', p.password or ''

def handle(client):
    client_ip = client.getpeername()[0]
    log(f"[{client_ip}] Connection received")
    try:
        req = b''
        # Try to read first packet
        client.settimeout(5)
        chunk = client.recv(4096)
        req += chunk
        log(f"[{client_ip}] First packet ({len(chunk)} bytes): {chunk[:100]}")

        # Check if it's a CONNECT request or TLS handshake
        if chunk.startswith(b'CONNECT'):
            log(f"[{client_ip}] CONNECT request detected")
            request_line = req.split(b'\r\n')[0].decode('utf-8', errors='ignore')
            parts = request_line.split()
            target = parts[1] if len(parts) > 1 else ''
            log(f"[{client_ip}] Target: {target}")

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

            log(f"[{client_ip}] Connecting to upstream proxy {proxy_host}:{proxy_port}")
            upstream = socket.socket()
            upstream.connect((proxy_host, proxy_port))

            connect_req = f"CONNECT {host}:{port} HTTP/1.1\r\nProxy-Authorization: Basic {auth}\r\n\r\n"
            upstream.send(connect_req.encode())

            resp = b''
            upstream.settimeout(10)
            while b'\r\n\r\n' not in resp and len(resp) < 8192:
                resp += upstream.recv(4096)

            resp_line = resp.split(b'\r\n')[0]
            log(f"[{client_ip}] Proxy response: {resp_line}")

            if b'200' in resp.split(b'\r\n')[0]:
                log(f"[{client_ip}] CONNECT succeeded, relaying traffic")
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
                            log(f"[{client_ip}] Connection closed")
                            return
                        (upstream if s is client else client).sendall(data)
        else:
            log(f"[{client_ip}] Not a CONNECT request, might be TLS. Closing.")
            client.close()
    except Exception as e:
        log(f"[{client_ip}] Error: {e}")
    finally:
        try:
            upstream.close()
        except:
            pass
        try:
            client.close()
        except:
            pass

if __name__ == '__main__':
    if not UPSTREAM:
        log("ERROR: https_proxy not set")
        sys.exit(1)

    log(f"Starting on 0.0.0.0:443 -> {UPSTREAM}")

    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(('0.0.0.0', LOCAL_PORT))
    srv.listen(128)
    log(f"Listening on 0.0.0.0:443")

    while True:
        try:
            c, addr = srv.accept()
            threading.Thread(target=handle, args=(c,), daemon=True).start()
        except Exception as e:
            log(f"Accept error: {e}")
