#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/codedog}"

sudo tee /etc/systemd/system/codedog-backup.service >/dev/null <<EOF
[Unit]
Description=CodeDog migration backup
After=docker.service

[Service]
Type=oneshot
User=ubuntu
Environment=PROJECT_DIR=${PROJECT_DIR}
ExecStart=${PROJECT_DIR}/ops/backup.sh
EOF

sudo tee /etc/systemd/system/codedog-backup.timer >/dev/null <<'EOF'
[Unit]
Description=Daily CodeDog migration backup

[Timer]
OnCalendar=*-*-* 03:05:00
Persistent=true
RandomizedDelaySec=20m

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now codedog-backup.timer
