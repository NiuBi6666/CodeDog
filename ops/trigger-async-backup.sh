#!/usr/bin/env bash
set -euo pipefail

reason="${1:-manual}"
printf 'Queueing CodeDog backup: %s\n' "$reason"
sudo systemctl start --no-block codedog-backup.service
