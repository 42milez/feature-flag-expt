FROM eclipse-temurin:25.0.3_9-jdk@sha256:dfc0093e3dbf43dae57827111c6e374f5b44fac19a9451584b2b336b81474d64 AS build

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

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Xlog:gc*:stderr:utctime,level,tags"

# Single-layer fat jar by design. Revisit layered-jar extraction if a
# registry-push workflow is added.
COPY --from=build /workspace/service/build/libs/feature-flag-platform.jar /app/app.jar

EXPOSE 8080

CMD ["/app/app.jar"]
