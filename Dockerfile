FROM docker.io/library/maven:3.9.7-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Provide city and OpenWeather API-key at runtime (or Docker/K8s env-vars)
ENV CITY="London" \
    OPENWEATHER_KEY=""

COPY --from=builder /build/target/devsecops2-challenge-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
