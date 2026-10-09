# syntax=docker/dockerfile:1
# Imagen de cualquier servicio del backend. El servicio se elige con el
# argumento SERVICIO (lo pasa docker-compose.yml), p. ej. SERVICIO=auth-service.

# ---------- 1. Compilar el .jar con Maven ----------
FROM maven:3.9-eclipse-temurin-17 AS compilacion
ARG SERVICIO
WORKDIR /app
COPY ${SERVICIO}/pom.xml .
COPY ${SERVICIO}/src ./src
# La caché de ~/.m2 acelera las siguientes compilaciones
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# ---------- 2. Imagen final: solo Java y el .jar ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=compilacion /app/target/*.jar app.jar

# Memoria acotada para que los 11 servicios quepan en un servidor de 8 GB
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=192m -Xss512k -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
