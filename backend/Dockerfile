# Multi-Stage Build for Spring Boot Application on Render
# Stage 1: Build Stage
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy the entire source code after dependencies are cached
COPY src src

# Build the application, skipping tests to speed up the process
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime Stage
FROM eclipse-temurin:21-jre-jammy

# Set working directory
WORKDIR /app

# Copy the built JAR file from the builder stage
COPY --from=builder /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Set environment variables
ENV PORT=8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:${PORT}/actuator/health || exit 1

# Run the application with memory optimization and dynamic port binding
ENTRYPOINT ["java", "-Xmx512m", "-Dserver.port=${PORT}", "-jar", "app.jar"]
