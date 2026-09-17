# ============================================================
#  Dockerfile - AJT Backend (Spring Boot)
#  Multi-stage: builda com Maven, roda só o JAR final
# ============================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copia só o pom.xml primeiro pra aproveitar cache de dependências
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Agora copia o restante do código e builda
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Roda sem privilegios de root
RUN addgroup -S ajt && adduser -S ajt -G ajt

COPY --from=build --chown=ajt:ajt /app/target/*.jar app.jar

USER ajt

EXPOSE 8080

# Respeita o limite de memoria do container
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
