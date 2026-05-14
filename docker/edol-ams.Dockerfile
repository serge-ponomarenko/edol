FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# poms
COPY pom.xml .
COPY edol-core-api/pom.xml edol-core-api/
COPY edol-core/pom.xml edol-core/
COPY edol-dashboard/pom.xml edol-dashboard/
COPY edol-notify/pom.xml edol-notify/
COPY edol-ams/pom.xml edol-ams/

# preload deps
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl edol-ams -am dependency:go-offline -B

# sources
COPY edol-core-api/src edol-core-api/src
COPY edol-ams/src edol-ams/src

# build dashboard + required modules
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl edol-ams -am clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/edol-ams/target/*.jar app.jar

EXPOSE 8099

ENTRYPOINT ["java", "-jar", "app.jar"]