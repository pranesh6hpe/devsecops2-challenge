FROM docker.io/library/maven:3.9.7-eclipse-temurin-21 AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM docker.io/eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /build/target/devsecops2-challenge-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
