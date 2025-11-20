# Build the Spring Boot application
FROM eclipse-temurin:25.0.1_8-jdk AS builder
WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew --no-daemon bootJar -x test \
    && JAR_FILE=$(find build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*-plain.jar" | head -n 1) \
    && cp "${JAR_FILE}" /app/application.jar

# Run the packaged jar with a lightweight JRE
FROM eclipse-temurin:25.0.1_8-jre
WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/application.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
