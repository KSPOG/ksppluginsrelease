#!/usr/bin/env bash
# Bootstraps an Ubuntu 24.04 e2-micro VM. Run with: sudo ./setup-vm.sh
set -Eeuo pipefail

readonly APP_USER=kspge
readonly APP_DIR=/opt/ksp-ge-flipper
readonly SOURCE_DIR="$APP_DIR/source"
readonly DATA_DIR=/var/lib/ksp-ge-flipper
readonly ENV_FILE=/etc/ksp-ge-flipper.env
readonly SERVICE_NAME=ksp-ge-flipper
readonly REPO_URL="${KSP_REPO_URL:-https://github.com/KSPOG/ksppluginsrelease.git}"
readonly REPO_BRANCH="${KSP_REPO_BRANCH:-main}"
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

require_root() {
  if [[ ${EUID} -ne 0 ]]; then
    echo "Run this script with sudo." >&2
    exit 1
  fi
}

configure_swap() {
  if ! swapon --show=NAME --noheadings | grep -qx '/swapfile'; then
    if [[ ! -f /swapfile ]]; then
      fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
      chmod 600 /swapfile
      mkswap /swapfile
    fi
    swapon /swapfile
  fi
  grep -qxF '/swapfile none swap sw 0 0' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
}

install_source() {
  if [[ ! -d "$SOURCE_DIR/.git" ]]; then
    install -d -o root -g "$APP_USER" -m 0750 "$APP_DIR"
    git clone --branch "$REPO_BRANCH" --single-branch "$REPO_URL" "$SOURCE_DIR"
  fi
  git -C "$SOURCE_DIR" fetch --quiet origin "$REPO_BRANCH"
  git -C "$SOURCE_DIR" checkout --quiet "$REPO_BRANCH"
  git -C "$SOURCE_DIR" pull --ff-only --quiet origin "$REPO_BRANCH"
}

create_environment() {
  if [[ ! -f "$ENV_FILE" ]]; then
    umask 077
    cat > "$ENV_FILE" <<EOF
KSP_BIND_HOST=127.0.0.1
KSP_PORT=8181
KSP_API_KEY=$(openssl rand -hex 32)
KSP_DB_URL=
KSP_DATA_DIR=$DATA_DIR
KSP_WIKI_POLL_SECONDS=30
KSP_DUMP_POLL_SECONDS=15
KSP_MARKET_HISTORY_LIMIT=4096
KSP_LATEST_WARN_SECONDS=120
KSP_LATEST_REJECT_SECONDS=300
EOF
  fi
  chmod 600 "$ENV_FILE"
  chown root:root "$ENV_FILE"
}

health_check() {
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  curl --fail --silent --show-error --retry 5 --retry-connrefused \
    -H "X-KSP-API-Key: $KSP_API_KEY" "http://127.0.0.1:${KSP_PORT}/health" >/dev/null
}

funnel_url() {
  tailscale funnel status 2>/dev/null | grep -Eo 'https://[^[:space:]]+' | head -n1 || true
}

configure_tailscale() {
  curl -fsSL https://tailscale.com/install.sh | sh
  systemctl enable --now tailscaled
  if [[ -n "${TAILSCALE_AUTH_KEY:-}" ]]; then
    tailscale up --auth-key="$TAILSCALE_AUTH_KEY" --hostname=ksp-ge-backend
  else
    echo 'Authenticate this VM in Tailscale using the URL printed next.'
    tailscale up --hostname=ksp-ge-backend
  fi
  tailscale funnel --https=443 --bg "http://127.0.0.1:${KSP_PORT}"
  local public_url
  public_url="$(funnel_url)"
  if [[ -z "$public_url" ]]; then
    echo 'Funnel was configured but its public URL could not be determined. Run: sudo tailscale funnel status' >&2
    return 1
  fi
  set -a; source "$ENV_FILE"; set +a
  curl --fail --silent --show-error --retry 5 \
    -H "X-KSP-API-Key: $KSP_API_KEY" "${public_url%/}/health" >/dev/null
  echo "Funnel endpoint verified: $public_url"
}

main() {
  require_root
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jdk-headless maven git curl openssl ca-certificates
  configure_swap
  id -u "$APP_USER" >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$APP_USER"
  install -d -o root -g "$APP_USER" -m 0750 "$APP_DIR"
  install -d -o "$APP_USER" -g "$APP_USER" -m 0750 "$DATA_DIR"
  install_source
  create_environment
  pushd "$SOURCE_DIR/KspGEFlipperServer" >/dev/null
  mvn --batch-mode clean package
  java -cp target/classes com.ksp.geflipper.selftest.SelfTest
  install -o root -g "$APP_USER" -m 0640 target/ksp-ge-flipper-server-1.0.0.jar "$APP_DIR/server.jar"
  popd >/dev/null
  install -m 0644 "$SCRIPT_DIR/ksp-ge-flipper.service" "/etc/systemd/system/$SERVICE_NAME.service"
  install -d -m 0755 /etc/systemd/journald.conf.d
  printf '[Journal]\nSystemMaxUse=200M\nRuntimeMaxUse=100M\n' > /etc/systemd/journald.conf.d/ksp-ge-flipper.conf
  systemctl daemon-reload
  systemctl enable --now "$SERVICE_NAME"
  health_check
  configure_tailscale
}

main "$@"
