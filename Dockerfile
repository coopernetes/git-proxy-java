# syntax=docker/dockerfile:1

# ── Build stage ──────────────────────────────────────────────────────────────
FROM docker.io/eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
COPY . .

# Build the distribution (all deps bundled in lib/)
RUN ./gradlew :jgit-proxy-server:installDist --no-daemon -q

# Prepend a conf/ directory to the classpath so that a mounted git-proxy-local.yml
# is picked up by JettyConfigurationLoader at runtime.
RUN sed -i \
    's|^CLASSPATH=\$APP_HOME/lib|CLASSPATH=$APP_HOME/conf:$APP_HOME/lib|' \
    jgit-proxy-server/build/install/jgit-proxy-server/bin/jgit-proxy-server

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM docker.io/eclipse-temurin:21-jre

WORKDIR /app

# Copy the built distribution
COPY --from=builder \
    /workspace/jgit-proxy-server/build/install/jgit-proxy-server/ /app/

# Create the conf directory; mount a git-proxy-local.yml here to override config.
# Example: -v ./docker/git-proxy-local.yml:/app/conf/git-proxy-local.yml:ro
RUN mkdir -p /app/conf

# Data directory for file-based databases (h2-file, sqlite)
RUN mkdir -p /app/.data

EXPOSE 8080

ENTRYPOINT ["/app/bin/jgit-proxy-server"]
