# ─────────────────────────────────────────────────────────────
# 1. build your JAR
# ─────────────────────────────────────────────────────────────
FROM maven:3.9.7-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ─────────────────────────────────────────────────────────────
# 2. runtime image + install scanners
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# install curl, tar, python (for ZAP)
RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      curl ca-certificates tar python3 python3-pip && \
    rm -rf /var/lib/apt/lists/*

# ─── Install Trivy (SCA) ───────────────────────────────────────
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
    | sh -s -- -b /usr/local/bin

# ─── Install ZAP CLI (DAST) ───────────────────────────────────
ENV ZAP_VERSION=2.14.0
RUN mkdir -p /opt/zap && \
    curl -sfL "https://github.com/zaproxy/zaproxy/releases/download/v${ZAP_VERSION}/ZAP_${ZAP_VERSION}_unix.tar.gz" \
      | tar xz -C /opt/zap && \
    ln -s /opt/zap/ZAP_${ZAP_VERSION}/zap.sh       /usr/local/bin/zap.sh && \
    ln -s /opt/zap/ZAP_${ZAP_VERSION}/zap-full-scan.py /usr/local/bin/zap-full-scan.py

# ─────────────────────────────────────────────────────────────
# 3. copy your app
# ─────────────────────────────────────────────────────────────
WORKDIR /app
COPY --from=builder /app/target/devsecops2-challenge-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
