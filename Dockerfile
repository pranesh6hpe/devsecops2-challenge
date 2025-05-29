# 1) Build your app
FROM maven:3.9.7-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# 2) Runtime image with Trivy + ZAP CLI installed
FROM eclipse-temurin:21-jre-jammy

# install Trivy
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
      | sh -s -- -b /usr/local/bin

# install ZAP CLI
ENV ZAP_VERSION=2.14.0
RUN mkdir -p /opt/zap \
 && curl -fsSL "https://github.com/zaproxy/zaproxy/releases/download/v${ZAP_VERSION}/ZAP_${ZAP_VERSION}_unix.tar.gz" \
      | tar -xz -C /opt/zap \
 && ln -s /opt/zap/ZAP_${ZAP_VERSION}/zap.sh /usr/local/bin/zap.sh \
 && ln -s /opt/zap/ZAP_${ZAP_VERSION}/zap-full-scan.py /usr/local/bin/zap-full-scan.py

# copy your app artifact
WORKDIR /app
COPY --from=builder /app/target/devsecops2-challenge-*.jar app.jar

# expose and entrypoint
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
