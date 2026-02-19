# Docker image for YAWL MCP-A2A Security Testing
# Based on Ubuntu with security testing tools

FROM ubuntu:22.04

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    jq \
    python3 \
    python3-pip \
    sqlmap \
    netcat-openbsd \
    telnet \
    nmap \
    whois \
    dnsutils \
    vim \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install OWASP ZAP
RUN cd /opt && \
    wget -O zap_latest.tar.gz https://github.com/zaproxy/zaproxy/releases/download/v2.15.0/zaproxy_2.15.0-1ubuntu_amd64.deb && \
    dpkg -i zap_latest.tar.gz && \
    rm zap_latest.tar.gz

# Install Burp Suite (Community Edition)
RUN wget -O burpsuite_community_linux.zip https://portswigger.net/burp/releases/download?product=community&version=2023.12.1.1&type=Linux && \
    unzip burpsuite_community_linux.zip -d /opt/ && \
    rm burpsuite_community_linux.zip

# Install JWT tools
RUN pip3 install pyjwt cryptography

# Install additional security tools
RUN pip3 install requests html5lib beautifulsoup4

# Create working directory
WORKDIR /app

# Copy security testing scripts
COPY scripts/security/ /app/scripts/

# Set environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64

# Default command
CMD ["/bin/bash"]
