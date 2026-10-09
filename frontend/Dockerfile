# syntax=docker/dockerfile:1
# Frontend de VITA+ servido por Caddy, que además pone el HTTPS automático
# (certificado de Let's Encrypt) y reenvía /api al api-gateway.

# ---------- 1. Compilar Angular ----------
FROM node:24-alpine AS compilacion
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build

# ---------- 2. Servirlo con Caddy ----------
FROM caddy:2-alpine
COPY --from=compilacion /app/dist/frontend/browser /srv
COPY Caddyfile /etc/caddy/Caddyfile
