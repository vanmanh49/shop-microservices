# syntax=docker/dockerfile:1

# One build stage compiles every module; each image then takes its own jar.
# Docker reuses the build stage across the eight service images.
FROM eclipse-temurin:27-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:27-jre
ARG MODULE
WORKDIR /app
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar /app/app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
