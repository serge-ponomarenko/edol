FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# poms
COPY pom.xml .
COPY edol-core-api/pom.xml edol-core-api/
COPY edol-core/pom.xml edol-core/
COPY edol-hub/pom.xml edol-hub/
COPY edol-notify/pom.xml edol-notify/
COPY edol-ams/pom.xml edol-ams/

# preload deps
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl edol-hub -am dependency:go-offline -B

# sources
COPY edol-core-api/src edol-core-api/src
COPY edol-hub/src edol-hub/src

# build hub + required modules
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl edol-hub -am clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/edol-hub/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]