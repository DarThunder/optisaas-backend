#!/bin/bash
echo "[OptiSaaS] Levantando entorno Docker..."
docker-compose down -v && docker compose build --no-cache app && docker compose up
echo ""
echo "[!] DB corriendo en: localhost:5432"
echo "[!] Adminer (GUI) en: http://localhost:8080"