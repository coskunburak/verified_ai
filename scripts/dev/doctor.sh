#!/usr/bin/env bash
set -u

failures=0

require_command() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then
    printf "OK   %-18s %s\n" "$name" "$(command -v "$name")"
  else
    printf "MISS %-18s not found on PATH\n" "$name"
    failures=$((failures + 1))
  fi
}

print_version() {
  local label="$1"
  shift
  if "$@" >/tmp/verified-ai-doctor-version.txt 2>&1; then
    printf "INFO %-18s %s\n" "$label" "$(head -n 1 /tmp/verified-ai-doctor-version.txt)"
  else
    printf "WARN %-18s version command failed\n" "$label"
  fi
}

require_command git
require_command java
require_command mvn
require_command xcodebuild
require_command swift
require_command python3
require_command docker

if docker compose version >/dev/null 2>&1; then
  printf "OK   %-18s %s\n" "docker compose" "$(docker compose version | head -n 1)"
else
  printf "MISS %-18s docker compose unavailable\n" "docker compose"
  failures=$((failures + 1))
fi

if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | awk -F[\".] '/version/ {print $2; exit}')"
  if [[ "$java_major" == "21" ]]; then
    printf "OK   %-18s Java 21 active\n" "java version"
  else
    printf "MISS %-18s Java 21 required, active major is %s\n" "java version" "${java_major:-unknown}"
    failures=$((failures + 1))
  fi
fi

print_version git git --version
print_version maven mvn -version
print_version xcode xcodebuild -version
print_version swift swift --version
print_version python python3 --version
print_version docker docker --version

if command -v psql >/dev/null 2>&1; then
  print_version psql psql --version
else
  printf "WARN %-18s optional PostgreSQL client not found\n" "psql"
fi

if command -v gh >/dev/null 2>&1; then
  print_version gh gh --version
else
  printf "WARN %-18s GitHub CLI not found; local GitHub inspection unavailable\n" "gh"
fi

if find . -name '.DS_Store' -print | grep -q .; then
  printf "MISS %-18s .DS_Store files are present\n" "repo hygiene"
  find . -name '.DS_Store' -print
  failures=$((failures + 1))
else
  printf "OK   %-18s no .DS_Store files found\n" "repo hygiene"
fi

if [[ "$failures" -gt 0 ]]; then
  printf "doctor completed with %d blocking issue(s)\n" "$failures"
  exit 1
fi

printf "doctor completed successfully\n"

