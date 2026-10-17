FROM eclipse-temurin:21-jre AS runtime-base
WORKDIR /app
RUN groupadd --system aegis && useradd --system --gid aegis aegis
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
