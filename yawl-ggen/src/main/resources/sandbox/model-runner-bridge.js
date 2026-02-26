/**
 * Model Runner Bridge for YAWL Docker Sandbox.
 * Forwards HTTP requests from the sandbox to the Model Runner on the host
 * via the configured egress proxy. Listens on localhost:54321.
 *
 * Usage: node model-runner-bridge.js
 */
"use strict";

const http = require("http");

const PROXY_HOST = process.env.PROXY_HOST || "host.docker.internal";
const PROXY_PORT = parseInt(process.env.PROXY_PORT || "3128", 10);
const TARGET_HOST = process.env.TARGET_HOST || "localhost";
const TARGET_PORT = parseInt(process.env.TARGET_PORT || "12434", 10);
const LISTEN_PORT = parseInt(process.env.LISTEN_PORT || "54321", 10);

const server = http.createServer((req, res) => {
  const targetUrl = `http://${TARGET_HOST}:${TARGET_PORT}${req.url}`;
  const proxyReq = http.request(
    {
      hostname: PROXY_HOST,
      port: PROXY_PORT,
      path: targetUrl,
      method: req.method,
      headers: {
        ...req.headers,
        host: `${TARGET_HOST}:${TARGET_PORT}`,
      },
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode, proxyRes.headers);
      proxyRes.pipe(res);
    }
  );

  proxyReq.on("error", (err) => {
    res.writeHead(502);
    res.end(JSON.stringify({ error: "Proxy connection failed", detail: err.message }));
  });

  req.pipe(proxyReq);
});

server.listen(LISTEN_PORT, "127.0.0.1", () => {
  process.stderr.write(`Model Runner Bridge listening on 127.0.0.1:${LISTEN_PORT}\n`);
  process.stderr.write(`Proxying to ${TARGET_HOST}:${TARGET_PORT} via ${PROXY_HOST}:${PROXY_PORT}\n`);
});

server.on("error", (err) => {
  process.stderr.write(`Bridge server error: ${err.message}\n`);
  process.exit(1);
});
