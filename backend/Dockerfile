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

# Run the application.
#
# GC: explicit G1 (BUG-127). Without a -XX GC flag the JVM ergonomics pick SerialGC
# whenever -Xmx (1024m) sits below the 1792m "server-class" threshold — and SerialGC's
# single-threaded, whole-heap stop-the-world full GC froze every request thread. 30-day
# Prometheus history showed MarkSweepCompact pauses peaking at 209s (and Copy minor GCs
# at 24.6s), which is why unrelated endpoints (translate/execute/login/refresh/even the
# /actuator/prometheus scrape) all spiked to 20-205s at the same instant. CPU never
# exceeded 71% of the 2 cores, so this was GC-pause-bound, not CPU-bound. G1 is
# concurrent/parallel with a 200ms pause target — no whole-heap STW freezes.
#
# Heap stays -Xmx1024m: 30-day peak heap *used* was only 553M (~54%), so the size was
# never the problem — the collector was. G1's dynamic regions also drop SerialGC's fixed
# ~680M old-gen cap (the BUG-122 HighHeapUsage tuning target), lowering the per-pool
# used/max ratio rather than raising it.
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", "-Xmx1024m", "-Xms512m", "-jar", "app.jar"]
