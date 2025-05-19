FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy any jar from target folder
COPY target/*.jar app.jar

EXPOSE 15000
ENTRYPOINT ["java", "-jar", "app.jar"]
