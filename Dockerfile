# Maven comes with the image. The Maven wrapper is distributionType=only-script with no
# committed jar, so ./mvnw downloads the whole Maven distribution on every uncached build —
# a network dependency that fails on hosts without open access to repo.maven.apache.org.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies resolve in their own layer, so editing src/ does not refetch them.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
