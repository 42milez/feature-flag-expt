FROM eclipse-temurin:25.0.3_9-jdk@sha256:68868d04fa9cfd5f5c6abec0b5cef86d8de2bf9c62c37c7d3e4f0f80f5cfd7ff AS build

WORKDIR /workspace

COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY service/build.gradle.kts ./service/build.gradle.kts
COPY service/src ./service/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :service:bootJar --no-daemon

FROM gcr.io/distroless/java25-debian13:nonroot@sha256:9ccf2b8bce700d9f450523e2055afabe4d4ee795e6d69a0647a2dcd6e180e411

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Xlog:gc*:stderr:utctime,level,tags"

# Single-layer fat jar by design. Revisit layered-jar extraction if a
# registry-push workflow is added.
COPY --from=build /workspace/service/build/libs/feature-flag-platform.jar /app/app.jar

EXPOSE 8080

CMD ["/app/app.jar"]
