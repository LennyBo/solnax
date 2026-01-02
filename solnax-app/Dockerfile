ARG BUILDPLATFORM=linux/amd64
ARG TARGETPLATFORM=linux/arm/v7

# ---------- BUILD ----------
FROM --platform=${BUILDPLATFORM} maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# Copy parent + module poms first (cache + parent resolution)
COPY pom.xml .
COPY solnax-app/pom.xml solnax-app/pom.xml
COPY solnax-frontend/pom.xml solnax-frontend/pom.xml

RUN mvn -B -DskipTests dependency:go-offline

# Copy full source
COPY . .

# Build entire multi-module project
RUN mvn -B -DskipTests package

# ---------- RUNTIME ----------
FROM --platform=${TARGETPLATFORM} arm32v7/eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/solnax-app/target/*.jar app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]