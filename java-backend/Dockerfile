# Multi-stage build for the BuyAssist Spring Boot backend.

# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Copy Gradle wrapper + build files first for better layer caching.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true
# Copy sources and build the executable jar.
COPY src src
RUN ./gradlew --no-daemon bootJar

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/buyassist-backend-0.0.1-SNAPSHOT.jar app.jar
# Render provides $PORT; Spring reads SERVER_PORT. Map one to the other.
ENV SERVER_PORT=10000
EXPOSE 10000
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar app.jar"]
