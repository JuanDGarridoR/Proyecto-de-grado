# syntax=docker/dockerfile:1
# Imagen de cualquier servicio del backend. El servicio se elige con el
# argumento SERVICIO (lo pasa docker-compose.yml), p. ej. SERVICIO=auth-service.

# Las imágenes van con su versión exacta, para que cada compilación use
# exactamente lo mismo. Dependabot propone las actualizaciones (ver
# .github/dependabot.yml).

# ---------- 1. Compilar el .jar con Maven ----------
FROM maven:3.9.16-eclipse-temurin-17 AS compilacion
ARG SERVICIO
WORKDIR /app
COPY ${SERVICIO}/pom.xml .
COPY ${SERVICIO}/src ./src
# La caché de ~/.m2 acelera las siguientes compilaciones
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# ---------- 2. Imagen final: solo Java y el .jar ----------
FROM eclipse-temurin:24.0.2_12-jre
# curl para el health check de docker-compose.yml (/actuator/health/liveness)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Usuario sin privilegios: si alguien lograra ejecutar código en el
# servicio, no tendría permisos de root dentro del contenedor.
RUN groupadd --system vita \
    && useradd --system --gid vita --no-create-home --shell /usr/sbin/nologin vita
WORKDIR /app
COPY --from=compilacion /app/target/*.jar app.jar
USER vita

# Memoria acotada para que los 12 servicios quepan en un servidor de 8 GB
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -Xss512k -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
