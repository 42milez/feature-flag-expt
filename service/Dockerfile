FROM eclipse-temurin:25-jdk@sha256:edb3aa0f621796d8f5f9d602c7611ffdf015cd89e6ddda1894d85a3a99d170a8 AS build

WORKDIR /workspace

COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY service/build.gradle.kts ./service/build.gradle.kts
COPY service/src ./service/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :service:bootJar --no-daemon

FROM gcr.io/distroless/java25-debian13:nonroot@sha256:dade01b669efd3bea3977f73cc196c56f1ee678a71ec8305f84ec15fd5a23c8d

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Single-layer fat jar by design. Revisit layered-jar extraction if a
# registry-push workflow is added.
COPY --from=build /workspace/service/build/libs/feature-flag-platform.jar /app/app.jar

EXPOSE 8080

CMD ["/app/app.jar"]
