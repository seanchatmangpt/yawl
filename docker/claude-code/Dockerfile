FROM eclipse-temurin:25-jdk-noble

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    ca-certificates \
    gnupg \
    apt-transport-https \
    maven \
    jq \
    python3 \
    && rm -rf /var/lib/apt/lists/*

# Install Google Cloud SDK (provides gcloud credential helper for CSR)
RUN curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
        | gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" \
        > /etc/apt/sources.list.d/google-cloud-sdk.list && \
    apt-get update && apt-get install -y --no-install-recommends google-cloud-cli \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 22 (LTS) for Claude Code
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Install Claude Code CLI
RUN npm install -g @anthropic-ai/claude-code

# Create non-root user for running Claude Code
RUN useradd -m -s /bin/bash claude && \
    mkdir -p /workspace && \
    chown -R claude:claude /workspace

# Copy entrypoint
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

WORKDIR /workspace

# Claude Code needs HOME set and a writable config dir
ENV HOME=/home/claude
ENV CLAUDE_CODE_REMOTE=true
ENV CLAUDE_TELEMETRY_DISABLED=1

USER claude

ENTRYPOINT ["/entrypoint.sh"]
