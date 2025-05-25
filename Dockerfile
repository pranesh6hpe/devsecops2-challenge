FROM maven:3.9.7-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# default to London; override in Kubernetes or docker run
ENV CITY="London" \
    LAT="51.5074" \
    LON="-0.1278"

COPY --from=builder /app/target/devsecops2-challenge-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
