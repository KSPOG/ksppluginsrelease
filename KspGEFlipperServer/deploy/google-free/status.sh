#!/usr/bin/env bash
# Displays the backend, host, Tailscale, and Funnel state without printing secrets.
set -Eeuo pipefail

readonly ENV_FILE=/etc/ksp-ge-flipper.env
echo '=== Backend service ==='
systemctl --no-pager --full status ksp-ge-flipper || true
echo '=== Java memory ==='
ps -C java -o pid,%mem,rss,cmd --no-headers || true
echo '=== System RAM and swap ==='
free -h
echo '=== Disk ==='
df -h / /var/lib/ksp-ge-flipper 2>/dev/null || df -h /
echo '=== Local health ==='
if [[ ${EUID} -eq 0 && -r "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
  curl --fail --silent --show-error -H "X-KSP-API-Key: $KSP_API_KEY" "http://127.0.0.1:${KSP_PORT}/health" || true
  echo
else
  echo 'Run with sudo to check authenticated local health.'
fi
echo '=== Tailscale ==='
tailscale status || true
echo '=== Funnel ==='
tailscale funnel status || true
