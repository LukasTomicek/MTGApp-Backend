FROM gradle:8.14.3-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/install/mtg-backend /app
EXPOSE 8080
CMD ["sh", "-c", "./bin/mtg-backend"]
