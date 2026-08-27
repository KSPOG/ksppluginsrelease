#!/usr/bin/env bash
# Archives persistent server state and retains seven daily backups.
set -Eeuo pipefail

readonly DATA_DIR=/var/lib/ksp-ge-flipper
readonly BACKUP_DIR=/var/backups/ksp-ge-flipper
[[ ${EUID} -eq 0 ]] || { echo 'Run this script with sudo.' >&2; exit 1; }
[[ -d "$DATA_DIR" ]] || { echo "Data directory is missing: $DATA_DIR" >&2; exit 1; }

install -d -m 0750 "$BACKUP_DIR"
archive="$BACKUP_DIR/ksp-ge-flipper-$(date -u +%F).tar.gz"
tar -C /var/lib -czf "$archive" ksp-ge-flipper
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'ksp-ge-flipper-*.tar.gz' -mtime +6 -delete
echo "Backup created: $archive"
