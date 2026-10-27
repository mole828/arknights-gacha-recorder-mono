# Build from the monorepo root: docker build --platform linux/amd64 -f backend/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN ./scripts/package-native.sh linuxX64 release

FROM debian:bookworm-slim AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/dist/linuxX64/arknights-gacha-recorder.kexe /app/arknights-gacha-recorder
EXPOSE 8080
ENTRYPOINT ["/app/arknights-gacha-recorder"]
