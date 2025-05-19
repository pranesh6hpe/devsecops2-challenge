# Use Eclipse Temurin Java 21 JRE base image
FROM eclipse-temurin:21-jre-jammy

# Set working directory inside container
WORKDIR /app

# Copy the Spring Boot fat jar (built by Maven)
COPY target/my-app-1.0-SNAPSHOT.jar app.jar

# Expose port your Spring Boot app runs on (default 8080)
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
