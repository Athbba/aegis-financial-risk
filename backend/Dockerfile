FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555 AS runtime-base
ARG SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.revision=$SOURCE_COMMIT
WORKDIR /app
RUN addgroup -S aegis && adduser -S -G aegis aegis
USER aegis
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]

# Optional offline packaging path after a successful local `mvn verify`.
FROM runtime-base AS prebuilt
COPY --chown=aegis:aegis target/aegis-0.1.0.jar app.jar

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM runtime-base AS final
COPY --from=build --chown=aegis:aegis /build/target/aegis-0.1.0.jar app.jar
