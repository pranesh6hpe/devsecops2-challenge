# Use Eclipse Temurin Java 21 JRE base image
FROM eclipse-temurin:21-jre-jammy

# Set working directory inside container
WORKDIR /app

<<<<<<< HEAD
<<<<<<< HEAD
# Copy the Spring Boot fat jar (built by Maven)
COPY target/my-app-1.0-SNAPSHOT.jar app.jar

# Expose port your Spring Boot app runs on (default 8080)
EXPOSE 8080

# Run the jar file
=======
COPY target/*.jar app.jar
=======
# Copy the Spring Boot fat jar (built by Maven)
COPY target/my-app-1.0-SNAPSHOT.jar app.jar
>>>>>>> 26d0d08d8dbafd34c49418349d68e2c0ef013326

# Expose port your Spring Boot app runs on (default 8080)
EXPOSE 8080
<<<<<<< HEAD
>>>>>>> b402fe2d95c016a6119a22f2b73ad682971b1518
=======

# Run the jar file
>>>>>>> 26d0d08d8dbafd34c49418349d68e2c0ef013326
ENTRYPOINT ["java", "-jar", "app.jar"]
