FROM rust:1.82-slim AS builder
WORKDIR /app
# Build from workspace root (context must be repo root)
COPY rust/Cargo.toml rust/Cargo.lock* ./
COPY rust/yawl-native/Cargo.toml yawl-native/Cargo.toml
COPY rust/yawl-native/src yawl-native/src
RUN cargo build --release -p yawl-native

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y libssl3 ca-certificates curl && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/yawl-native /usr/local/bin/
EXPOSE 8083
ENV PORT=8083
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:8083/health || exit 1
CMD ["yawl-native"]
