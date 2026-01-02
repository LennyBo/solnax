ARG BUILDPLATFORM=linux/amd64
ARG TARGETPLATFORM=linux/arm/v7

# ---------- BUILD ----------
FROM --platform=${BUILDPLATFORM} maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# Copy parent + module POMs first (parent must exist)
COPY pom.xml .
COPY solnax-app/pom.xml solnax-app/pom.xml
COPY solnax-frontend/pom.xml solnax-frontend/pom.xml

# Prime Maven cache
RUN mvn -B -DskipTests dependency:go-offline

# Copy full source tree
COPY . .

# Build full multi-module project
RUN mvn -B -DskipTests package

# ---------- RUNTIME ----------
FROM --platform=${TARGETPLATFORM} arm32v7/eclipse-temurin:17-jre
WORKDIR /app

# Copy ONLY the Spring Boot executable JAR
COPY --from=build /build/solnax-app/target/solnax-app-*.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]
