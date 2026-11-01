# Zero-Secret Isolated Development Environment
# Unprivileged, scoped runtime for AI coding agents and development tasks.

FROM debian:12-slim

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Install base development tools, runtimes, and dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    curl \
    git \
    make \
    ripgrep \
    python3 \
    python3-pip \
    python3-venv \
    openjdk-17-jdk-headless \
    nodejs \
    npm \
    libcap2-bin \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install CLI coding agent tools inside container runtime
RUN npm install -g @openai/codex @anthropic-ai/claude-code --omit=dev 2>/dev/null || true

# Create unprivileged developer user (UID 1000, GID 1000)
RUN groupadd -g 1000 developer && \
    useradd -u 1000 -g developer -m -s /bin/bash developer

# Setup workspace directory
RUN mkdir -p /workspace && chown -R developer:developer /workspace

# Pre-create isolated agent configuration roots in developer home
RUN mkdir -p /home/developer/.codex /home/developer/.claude /home/developer/.gemini && \
    chmod 700 /home/developer/.codex /home/developer/.claude /home/developer/.gemini && \
    chown -R developer:developer /home/developer

USER developer
WORKDIR /workspace

# Environment is scrubbed: only safe variables are inherited
ENV APP_ENV=local
ENV PATH="/home/developer/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

CMD ["/bin/bash"]
