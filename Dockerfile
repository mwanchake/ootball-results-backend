## Multi-stage Dockerfile for the Spring Boot backend
# Build stage
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy only what we need to build (keep layers cache-friendly)
COPY pom.xml mvnw .mvn/ ./
COPY .mvn/ .mvn/
COPY mvnw mvnw
RUN chmod +x mvnw || true

# Copy source
COPY src ./src

# Package the application (skip tests for faster image builds)
RUN ./mvnw -B -DskipTests package

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /workspace/target/footbal-api-0.0.1-SNAPSHOT.jar /app/app.jar

ENV JAVA_OPTS=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
