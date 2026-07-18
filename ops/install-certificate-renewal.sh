#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/codedog}"

sudo install -d -m 755 /etc/letsencrypt/renewal-hooks/deploy
sudo tee /etc/letsencrypt/renewal-hooks/deploy/codedog-reload.sh >/dev/null <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd ${PROJECT_DIR}
docker compose exec -T frontend nginx -s reload
EOF
sudo chmod 750 /etc/letsencrypt/renewal-hooks/deploy/codedog-reload.sh

sudo tee /etc/systemd/system/codedog-certbot-renew.service >/dev/null <<'EOF'
[Unit]
Description=Renew CodeDog certificates
After=network-online.target docker.service

[Service]
Type=oneshot
ExecStart=/opt/certbot-venv/bin/certbot renew --quiet
EOF

sudo tee /etc/systemd/system/codedog-certbot-renew.timer >/dev/null <<'EOF'
[Unit]
Description=Check CodeDog certificates twice daily

[Timer]
OnCalendar=*-*-* 00,12:25:00
Persistent=true
RandomizedDelaySec=20m

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now codedog-certbot-renew.timer

printf 'CodeDog certificate reload hook installed\n'
