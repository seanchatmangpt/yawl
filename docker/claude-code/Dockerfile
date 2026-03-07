FROM eclipse-temurin:25-jdk-noble

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    ca-certificates \
    gnupg \
    maven \
    jq \
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
