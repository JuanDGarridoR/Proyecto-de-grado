# syntax=docker/dockerfile:1
# Frontend de VITA+ servido por Caddy, que además pone el HTTPS automático
# (certificado de Let's Encrypt) y reenvía /api al api-gateway.

# Las imágenes van con su versión exacta, para que cada compilación use
# exactamente lo mismo. Dependabot propone las actualizaciones (ver
# .github/dependabot.yml).

# ---------- 1. Compilar Angular ----------
FROM node:26.12.0-alpine AS compilacion
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build

# ---------- 2. Servirlo con Caddy ----------
FROM caddy:2.11.7-alpine
COPY --from=compilacion /app/dist/frontend/browser /srv
COPY Caddyfile /etc/caddy/Caddyfile
# Usuario sin privilegios. Puede abrir los puertos 80 y 443 porque la imagen
# oficial le da a /usr/bin/caddy el permiso cap_net_bind_service. Es dueño
# de /data y /config, donde guarda los certificados HTTPS.
RUN addgroup -S vita && adduser -S -G vita -H -s /sbin/nologin vita \
    && chown -R vita:vita /data /config
USER vita
