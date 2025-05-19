# Use a slim and secure base JRE image
FROM eclipse-temurin:21-jre-jammy

# Set working directory inside container
WORKDIR /app

# Copy the built JAR (can be overridden in CI)
COPY target/*.jar app.jar

# Expose application port
EXPOSE 15000

# Run the JAR
ENTRYPOINT ["java", "-jar", "app.jar"]
