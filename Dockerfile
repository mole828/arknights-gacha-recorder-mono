FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY gradle ./gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
COPY platform ./platform

RUN ./gradlew linkReleaseExecutableLinuxX64 --no-daemon

FROM debian:bookworm-slim AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/build/bin/linuxX64/releaseExecutable/arknights-gacha-recorder.kexe /app/arknights-gacha-recorder

EXPOSE 8080
ENTRYPOINT ["/app/arknights-gacha-recorder"]
