# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Copy sources
COPY src/main/java ./src/main/java

# Compile classes
RUN mkdir -p /app/classes \
 && find src/main/java -name "*.java" -print0 | xargs -0 javac -encoding UTF-8 -d /app/classes

# Package runnable JAR with WebServer as entrypoint
RUN jar cfe /app/playlist-webserver.jar com.example.playlist.WebServer -C /app/classes .

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy app jar and playlist data
COPY --from=build /app/playlist-webserver.jar /app/playlist-webserver.jar
COPY playlist /app/playlist

ENV PORT=8080
EXPOSE 8080

CMD ["java","-jar","/app/playlist-webserver.jar"]

