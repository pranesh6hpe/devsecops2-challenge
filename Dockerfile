# ---------- STAGE 1: build the app ----------
FROM docker.io/library/maven:3.9.7-eclipse-temurin-21-alpine AS builder

WORKDIR /build
# copy only pom and sources to leverage layer caching
COPY pom.xml .
COPY src ./src

# build and package your application (produces target/*.jar)
RUN mvn clean package -DskipTests

# ---------- STAGE 2: run the app ----------
FROM docker.io/eclipse-temurin:21-jre-jammy

# adjust to wherever you want logs/config at runtime
WORKDIR /app

# copy the shaded jar from the builder stage
COPY --from=builder /build/target/devsecops2-challenge-1.0.0-SNAPSHOT.jar app.jar

# expose the same port your HelloServer uses
EXPOSE 8080

# run your application
ENTRYPOINT ["java", "-jar", "app.jar"]
