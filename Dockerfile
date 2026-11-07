# ---- Public demo image: the program + its own throw-away PostgreSQL, one container ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql postgresql-client \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/outpass-1.0.0.jar /app/app.jar
COPY docker/demo-start.sh /app/demo-start.sh
RUN chmod +x /app/demo-start.sh && mkdir -p /app/photos && chmod 777 /app/photos

# runs as a normal (non-root) user, which PostgreSQL requires and free hosts expect
USER 1000
ENV DEMO_MODE=true \
    SEED_TEST_ACCOUNTS=false \
    DB_URL=jdbc:postgresql://127.0.0.1:5432/outpass \
    DB_USER=outpass \
    DB_PASSWORD=demo \
    PORT=7860 \
    SESSION_SAMESITE=none \
    SESSION_SECURE=true \
    DEMO_RESET_MINUTES=30 \
    PHOTO_DIR=/app/photos \
    TZ=Asia/Kolkata
EXPOSE 7860
CMD ["/app/demo-start.sh"]
