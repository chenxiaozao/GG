# The release archive supplies a tested JAR: no Maven or source build on the small server.
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app \
    && mkdir -p /app/data/avatars && chown -R app:app /app
WORKDIR /app
COPY --chown=app:app server.jar /app/server.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
