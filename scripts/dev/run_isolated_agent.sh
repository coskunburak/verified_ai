#!/usr/bin/env bash
# Runs an isolated, zero-secret development runtime or coding agent inside Docker.
# Enforces complete workspace secret isolation using a curated sanitized workspace mirror.
# Prevents access to host ~/.ssh, ~/.aws, Keychain, host environment variables, and Docker socket.

set -euo pipefail

WORKSPACE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE_NAME="verified-ai-isolated-dev:latest"
CURATED_WORKSPACE="/tmp/verified-ai-sanitized-workspace"

# 1. Build isolated image if not already present
if ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
  printf "==> Building isolated development runtime image...\n"
  docker build -t "$IMAGE_NAME" -f "$WORKSPACE_ROOT/.devcontainer/Dockerfile" "$WORKSPACE_ROOT/.devcontainer"
fi

# 2. Provision Curated Sanitized Workspace (excluding all secret-bearing files)
printf "==> Curating sanitized workspace in %s...\n" "$CURATED_WORKSPACE"
mkdir -p "$CURATED_WORKSPACE"

# Sync tracked and non-secret files, excluding any .env, private keys, certificates, backups, or credentials
rsync -a --delete \
  --exclude='.env*' \
  --exclude='*.key' \
  --exclude='*.pem' \
  --exclude='*.p12' \
  --exclude='*.bak' \
  --exclude='*.credentials' \
  --exclude='*.secret' \
  --exclude='*.session' \
  --exclude='*.cookie' \
  --exclude='owner.env' \
  --exclude='Secrets.xcconfig' \
  --exclude='.git/hooks' \
  "$WORKSPACE_ROOT/" "$CURATED_WORKSPACE/"

# Place safe development template as container .env
if [[ -f "$WORKSPACE_ROOT/.env.example" ]]; then
  cp "$WORKSPACE_ROOT/.env.example" "$CURATED_WORKSPACE/.env"
fi

# Function to sync back changes made by agent inside container on exit
sync_back_on_exit() {
  printf "\n==> Syncing modified non-secret files back to repository...\n"
  rsync -a \
    --exclude='.env*' \
    --exclude='*.key' \
    --exclude='*.pem' \
    --exclude='*.p12' \
    --exclude='*.bak' \
    --exclude='*.credentials' \
    --exclude='*.secret' \
    --exclude='*.session' \
    --exclude='*.cookie' \
    --exclude='owner.env' \
    --exclude='Secrets.xcconfig' \
    --exclude='.git/hooks' \
    "$CURATED_WORKSPACE/" "$WORKSPACE_ROOT/" 2>/dev/null || true
}
trap sync_back_on_exit EXIT

printf "==> Starting isolated runtime (Zero Secrets / Curated Workspace / No Host Mounts)...\n"

# 3. Execute container with strict privilege and isolation flags
exec docker run --rm -it \
  --cap-drop=ALL \
  --security-opt=no-new-privileges:true \
  --pids-limit=256 \
  --memory=4g \
  --cpus=4 \
  --volume "$CURATED_WORKSPACE:/workspace:rw" \
  --tmpfs /tmp:size=256m,mode=1777 \
  --workdir /workspace \
  --user 1000:1000 \
  --env APP_ENV=local \
  --env PATH="/home/developer/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
  "$IMAGE_NAME" \
  "${@:-/bin/bash}"
