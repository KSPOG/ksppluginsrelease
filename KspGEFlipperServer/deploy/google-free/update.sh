#!/usr/bin/env bash
# Builds and safely replaces the deployed JAR. Run with: sudo ./update.sh
set -Eeuo pipefail

readonly APP_DIR=/opt/ksp-ge-flipper
readonly SOURCE_DIR="$APP_DIR/source"
readonly ENV_FILE=/etc/ksp-ge-flipper.env
readonly SERVICE_NAME=ksp-ge-flipper
readonly JAR_NAME=ksp-ge-flipper-server-1.0.0.jar

[[ ${EUID} -eq 0 ]] || { echo 'Run this script with sudo.' >&2; exit 1; }
[[ -d "$SOURCE_DIR/.git" ]] || { echo "Deployment source is missing: $SOURCE_DIR" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { echo "Environment file is missing: $ENV_FILE" >&2; exit 1; }

health_check() {
  set -a; source "$ENV_FILE"; set +a
  curl --fail --silent --show-error --retry 5 --retry-connrefused \
    -H "X-KSP-API-Key: $KSP_API_KEY" "http://127.0.0.1:${KSP_PORT}/health" >/dev/null
}

public_health_check() {
  local public_url
  public_url="$(tailscale funnel status 2>/dev/null | grep -Eo 'https://[^[:space:]]+' | head -n1 || true)"
  [[ -n "$public_url" ]] || { echo 'No Funnel URL found.' >&2; return 1; }
  set -a; source "$ENV_FILE"; set +a
  curl --fail --silent --show-error --retry 5 \
    -H "X-KSP-API-Key: $KSP_API_KEY" "${public_url%/}/health" >/dev/null
}

rollback() {
  echo 'Update failed; restoring the previous JAR.' >&2
  if [[ -f "$APP_DIR/server.jar.previous" ]]; then
    mv -f "$APP_DIR/server.jar.previous" "$APP_DIR/server.jar"
    systemctl restart "$SERVICE_NAME" || true
    health_check || true
  fi
}
trap rollback ERR

git -C "$SOURCE_DIR" fetch --quiet origin main
git -C "$SOURCE_DIR" checkout --quiet main
git -C "$SOURCE_DIR" pull --ff-only --quiet origin main
pushd "$SOURCE_DIR/KspGEFlipperServer" >/dev/null
mvn --batch-mode clean package
java -cp target/classes com.ksp.geflipper.selftest.SelfTest
install -o root -g kspge -m 0640 "target/$JAR_NAME" "$APP_DIR/server.jar.new"
popd >/dev/null

systemctl stop "$SERVICE_NAME"
[[ ! -f "$APP_DIR/server.jar" ]] || cp -f "$APP_DIR/server.jar" "$APP_DIR/server.jar.previous"
mv -f "$APP_DIR/server.jar.new" "$APP_DIR/server.jar"
systemctl start "$SERVICE_NAME"
health_check
public_health_check
trap - ERR
echo 'Update completed and local/public health checks passed.'
