FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app && mkdir -p /app/data/avatars && chown -R app:app /app
COPY --from=build /build/target/robotmall-server-1.0.0.jar /app/server.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
