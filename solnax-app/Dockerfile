FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
COPY solnax-app/pom.xml solnax-app/pom.xml
COPY solnax-frontend/pom.xml solnax-frontend/pom.xml

RUN mvn -B -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/solnax-app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
