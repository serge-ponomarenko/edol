FROM maven:3.9.6-eclipse-temurin-21 AS build

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
    mvn -pl edol-core -am dependency:go-offline -B

# sources
COPY edol-core-api/src edol-core-api/src
COPY edol-core/src edol-core/src

# build core + required modules
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl edol-core -am clean package -DskipTests

# -----------------------
# Runtime stage
# -----------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ffmpeg \
        curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Copy jar from build stage
COPY --from=build /app/edol-core/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]