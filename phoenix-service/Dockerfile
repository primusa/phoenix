# Stage 1: Build using a stable Java 21 Temurin image
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependencies: Copy only pom.xml first
COPY pom.xml .
# Download dependencies in batch mode for CI/CD stability
# Resolve dependencies (using a more reliable combo than go-offline)
# RUN mvn dependency:go-offline -B || \
#    mvn dependency:resolve-plugins && mvn dependency:resolve
#RUN mvn dependency:resolve-plugins dependency:resolve

# Copy source and build the JAR
COPY src ./src
# Skip tests and use Java 25 preview features if needed
RUN mvn clean package -DskipTests

# Stage 2: Runtime - use a small Java 21 runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for runtime
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy final JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
