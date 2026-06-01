# Build stage
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:resolve -B || true

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Add non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Run the application — heap ~67% of the 1.5G container memory limit (compose
# `deploy.resources.limits.memory`), leaving ~500MB for metaspace/threads/direct
# buffers and transient CQL-execution allocations. The previous 716m was sized for
# a 1G limit (stale); the container is actually 1.5G, so the old gen sat at ~87%
# of its 477MB cap and fired HighHeapUsage despite a flat, non-leaking live set
# (BUG-122). At -Xmx1024m the SerialGC old gen is ~680MB → ~61% used.
ENTRYPOINT ["java", "-Xmx1024m", "-Xms512m", "-jar", "app.jar"]
